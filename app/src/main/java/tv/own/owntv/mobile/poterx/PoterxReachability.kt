package tv.own.owntv.mobile.poterx

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CancellationException
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.parser.XtreamClient
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Poterx: why the panel cannot be reached, told apart so the customer gets the right answer.
 *
 * A failed sign-in has four different causes that used to share one message: the credentials are
 * wrong, the line has run out, the television has no internet, or the customer's internet provider
 * blocks the panel. The last one is the one the built-in VPN exists for, so it is diagnosed in
 * steps, each one answering a question the previous one could not:
 *
 * 1. the panel's own account call ([XtreamClient.fetchAccountStatus]): the panel answered, and said
 *    yes, no, or expired — a panel refuses wrong credentials with `auth: 0`, a 401/403, or (XUI) a
 *    404 page, so any 4xx to a call that carried credentials is "wrong password", never a block;
 * 2. the system's connectivity and the provider's update server over HTTPS: is there any internet;
 * 3. the panel's host name: does the provider's resolver answer, and with a real address
 *    (an ISP blocking by DNS returns nothing, or a page of its own on a private address);
 * 4. a TCP connection to the panel's port: an ISP blocking by address drops or resets it;
 * 5. an HTTP request to `player_api.php` without credentials: a panel answers JSON whatever the
 *    credentials; an ISP's block page answers HTML or 403/451.
 *
 * Nothing here writes to the database or stores anything; the diagnosis is a handful of requests
 * that take a few seconds at most.
 */
class PoterxReachability(
    private val http: OkHttpClient,
    private val connectivity: ConnectivityObserver,
    private val xtream: XtreamClient,
) {

    sealed interface Verdict {
        /** The panel answered as a panel; for a sign-in, it also accepted the credentials. */
        data object Ok : Verdict
        data object AuthFailed : Verdict
        data class Expired(val expiryMs: Long?) : Verdict
        /** No internet at all, or nothing beyond the local network. */
        data object Offline : Verdict
        /** Internet works, the panel is cut off between the television and the server. */
        data class Blocked(val how: Block) : Verdict
        /** Internet works and the panel's host is reachable, yet the panel did not answer properly. */
        data object PanelDown : Verdict
    }

    enum class Block { DNS, CONNECTION, PAGE }

    // The pure part of the diagnosis, kept separate so the decision table is testable.
    enum class Dns { OK, FAILED, BOGUS }
    enum class Tcp { OK, FAILED }
    sealed interface Http {
        /**
         * An answer: [json] when the body is a JSON object or array, [html] when it is a web page,
         * [redirectElsewhere] when a 3xx points at another host (a proxy's page, not the panel's).
         */
        data class Answered(val code: Int, val json: Boolean, val html: Boolean = false, val redirectElsewhere: Boolean = false) : Http
        /** No HTTP answer; [reset] when the connection was closed on us rather than timing out. */
        data class Failed(val reset: Boolean) : Http
    }

    private val probeClient: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /** What the panel said to a call that carried credentials. */
    private sealed interface Answer {
        data object Ok : Answer
        data object AuthFailed : Answer
        data class Expired(val expiryMs: Long?) : Answer
        /** Nothing a panel says: no connection, a timeout, a 5xx. */
        data object None : Answer
    }

    private suspend fun ask(source: SourceEntity): Answer = try {
        val info = xtream.fetchAccountStatus(source)
        // A real panel answers with a status ("Active", "Expired", …). An answer with none that still
        // reads as authorised is a page the core parser could not make sense of: an ISP's block page
        // served with a 200 or after a redirect. It is no verdict on the account, so ask the network.
        if (info.authOk && info.status.isNullOrBlank()) return Answer.None
        val expiry = info.expiryMs
        val expired = info.status?.equals("Expired", ignoreCase = true) == true || (expiry != null && expiry <= System.currentTimeMillis())
        when {
            !info.authOk -> Answer.AuthFailed
            expired -> Answer.Expired(expiry)
            else -> Answer.Ok
        }
    } catch (c: CancellationException) {
        throw c
    } catch (e: HttpClient.HttpStatusException) {
        // The host answered the credentials with a status: 401/403 everywhere, a 404 page on XUI. A
        // timeout (408), a rate limit (429) or a legal block (451) says nothing about the password.
        if (e.code in 400..499 && e.code !in NOT_A_REFUSAL) Answer.AuthFailed else Answer.None
    } catch (e: Exception) {
        Answer.None
    }

    /** A sign-in's worth of checking: the panel's answer first, the network only when it gave none. */
    suspend fun signIn(server: String, username: String, password: String, userAgent: String): Verdict {
        val probe = SourceEntity(
            name = "probe", type = SourceType.XTREAM, url = server.trim(),
            username = username.trim(), password = password, userAgent = userAgent.takeIf { it.isNotBlank() },
        )
        return when (val answer = ask(probe)) {
            Answer.Ok -> Verdict.Ok
            // A refusal from a host the ISP's resolver pointed somewhere private is the ISP's block
            // page answering a 4xx, not the panel; a refusal from the panel's real address is the panel's.
            Answer.AuthFailed -> if (resolvesToBogus(server)) Verdict.Blocked(Block.DNS) else Verdict.AuthFailed
            is Answer.Expired -> Verdict.Expired(answer.expiryMs)
            Answer.None -> diagnose(server).let { if (it is Verdict.Ok) Verdict.PanelDown else it }
        }
    }

    private suspend fun resolvesToBogus(server: String): Boolean = withContext(Dispatchers.IO) {
        val host = server.trim().toHttpUrlOrNull()?.host ?: return@withContext false
        resolve(host).first == Dns.BOGUS
    }

    /**
     * A saved playlist's worth of checking: the panel's own answer to the saved account first — a
     * panel that says yes, no or expired is reachable, whatever the host does without credentials —
     * and the network diagnosis only when it gives none.
     */
    suspend fun check(source: SourceEntity): Verdict = when (ask(source)) {
        Answer.Ok, Answer.AuthFailed, is Answer.Expired -> Verdict.Ok
        Answer.None -> diagnose(source.url).let { if (it is Verdict.Ok) Verdict.PanelDown else it }
    }

    /**
     * Why the panel at [server] does not answer: [Verdict.Offline], [Verdict.Blocked] or
     * [Verdict.PanelDown]; [Verdict.Ok] when its host answers like a panel after all. Never called
     * on its own for a verdict: a panel asked without credentials answers whatever it likes (a JSON
     * refusal, an empty page, a 4xx), so only the account call above can say the panel is fine, and
     * only a web page or a 451 says an ISP stands in its place.
     */
    suspend fun diagnose(server: String): Verdict = withContext(Dispatchers.IO) {
        if (!connectivity.isOnlineNow()) return@withContext Verdict.Offline
        val neutral = LockedSource.account?.updateUrl?.takeIf { it.startsWith("https://") }
        if (neutral != null && !answers(neutral.trimEnd('/') + "/update.json")) return@withContext Verdict.Offline
        val url = server.trim().toHttpUrlOrNull() ?: return@withContext Verdict.PanelDown
        val dns = resolve(url.host)
        val tcp = if (dns.first == Dns.OK) connect(dns.second, url.port) else Tcp.FAILED
        val httpResult = if (tcp == Tcp.OK) request(url.newBuilder().encodedPath("/player_api.php").build().toString()) else Http.Failed(reset = false)
        val verdict = classify(dns.first, tcp, httpResult)
        Log.i(TAG, "diagnose: dns=${dns.first} tcp=$tcp http=$httpResult -> $verdict")
        verdict
    }

    private fun answers(url: String): Boolean = try {
        probeClient.newCall(Request.Builder().url(url).header("User-Agent", PoterxAccount.userAgent).head().build()).execute().use { true }
    } catch (e: IOException) {
        false
    }

    private fun resolve(host: String): Pair<Dns, List<InetAddress>> = try {
        val addresses = InetAddress.getAllByName(host).toList()
        val real = addresses.filterNot { it.isLoopbackAddress || it.isAnyLocalAddress || it.isSiteLocalAddress || it.isLinkLocalAddress }
        if (real.isEmpty()) Dns.BOGUS to emptyList() else Dns.OK to real
    } catch (e: UnknownHostException) {
        Dns.FAILED to emptyList()
    }

    private fun connect(addresses: List<InetAddress>, port: Int): Tcp {
        for (address in addresses) {
            try {
                Socket().use { it.connect(InetSocketAddress(address, port), PROBE_TIMEOUT_MS.toInt()) }
                return Tcp.OK
            } catch (e: IOException) {
                // Try the next address.
            }
        }
        return Tcp.FAILED
    }

    private fun request(url: String): Http = try {
        probeClient.newCall(Request.Builder().url(url).header("User-Agent", PoterxAccount.userAgent).build()).execute().use { resp ->
            val head = resp.peekBody(1024).string().trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            val type = resp.header("Content-Type").orEmpty().lowercase()
            val json = head.startsWith("{") || head.startsWith("[") || type.contains("json")
            val html = !json && (type.contains("html") || head.startsWith("<"))
            val target = resp.header("Location")?.let { resp.request.url.resolve(it) }
            val elsewhere = resp.code in 300..399 && target != null && !target.host.equals(resp.request.url.host, ignoreCase = true)
            Http.Answered(resp.code, json, html, elsewhere)
        }
    } catch (e: SocketTimeoutException) {
        Http.Failed(reset = false)
    } catch (e: IOException) {
        Http.Failed(reset = true)
    }

    companion object {
        private const val TAG = "PoterxReach"
        private const val PROBE_TIMEOUT_MS = 6_000L
        /** 4xx answers that are not a refusal of the credentials: timeout, rate limit, blocked for legal reasons. */
        private val NOT_A_REFUSAL = setOf(408, 429, 451)

        /**
         * The decision table: what the three probes together say about the panel. A block is only
         * ever read from a sign the ISP leaves — no address, no connection, or a page in the
         * panel's place with evidence that it is not the panel's own: a 451, a web page served with
         * a 2xx (a panel answers `player_api.php` with JSON or an error status, never a page with
         * 200), or a redirect to another host. Everything else the host answers is the panel's own
         * doing: JSON, an empty body, a 4xx page (XUI answers wrong credentials and bare calls with
         * a 404 page — v0.3.6 read it as a block page, so a mistyped password offered the VPN), or
         * the error page of the proxy in front of a panel that is down (nginx 502, Cloudflare 52x),
         * which must not turn the VPN on for every customer during an outage.
         */
        fun classify(dns: Dns, tcp: Tcp, http: Http): Verdict = when {
            dns != Dns.OK -> Verdict.Blocked(Block.DNS)
            tcp == Tcp.FAILED -> Verdict.Blocked(Block.CONNECTION)
            http is Http.Failed -> if (http.reset) Verdict.Blocked(Block.CONNECTION) else Verdict.PanelDown
            http is Http.Answered && http.json -> Verdict.Ok
            http is Http.Answered && http.code == 451 -> Verdict.Blocked(Block.PAGE)
            http is Http.Answered && http.html && http.code in 200..299 -> Verdict.Blocked(Block.PAGE)
            http is Http.Answered && http.redirectElsewhere -> Verdict.Blocked(Block.PAGE)
            else -> Verdict.PanelDown
        }
    }
}
