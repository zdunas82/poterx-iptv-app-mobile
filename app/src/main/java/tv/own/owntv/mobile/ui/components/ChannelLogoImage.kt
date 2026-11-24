package tv.own.owntv.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A channel's logo, with something to show when there isn't one.
 *
 * The reason this exists rather than a bare [AsyncImage]: **a logo URL that exists is not a logo that
 * arrives.** Playlists are full of links that 404, time out or point at a host that is long gone, and
 * every copy of this in the app tested only whether the URL was null — so a broken link drew nothing
 * at all, and the channel list, the guide, the home rows and the player's own plate all showed blank
 * gaps where a logo should be. Only a missing URL fell back; a failed one did not.
 *
 * [fallback] is drawn for both cases, which is the point: to a user there is no difference between a
 * channel with no logo and a channel whose logo will not load.
 */
@Composable
fun ChannelLogoImage(
    url: String?,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    /**
     * The surface the logo sits on, for Stage's `.plate.ink`: a logo whose ink would vanish on it gets a
     * plate of the opposite luminance behind it (the TV's ChannelLogoTile rule). Null = never a plate.
     */
    plateOn: Color? = null,
) {
    // Keyed on the URL so zapping to a channel whose logo is fine tries again rather than inheriting
    // the previous channel's failure.
    var failed by remember(url) { mutableStateOf(url != null && (url in deadLogos || hostOf(url)?.let(::hostIsDead) == true)) }
    if (url.isNullOrBlank() || failed) {
        fallback()
        return
    }
    var ink by remember(url) { mutableIntStateOf(logoInk[url] ?: LOGO_INK_NONE) }
    var pending by remember(url) { mutableStateOf<Bitmap?>(null) }
    // Measuring reads every sampled pixel, so it runs off the main thread, once per logo per run.
    LaunchedEffect(url, pending) {
        val bitmap = pending ?: return@LaunchedEffect
        val measured = withContext(Dispatchers.Default) { if (bitmap.isRecycled) LOGO_INK_NONE else bitmap.measureInk() }
        if (logoInk.size > MAX_MEASURED_LOGOS) logoInk.clear()
        logoInk[url] = measured
        ink = measured
        pending = null
    }
    val plate = plateOn?.takeIf { ink != LOGO_INK_NONE }?.let { surface ->
        val s = surface.luminance()
        if (contrastRatio(Color(ink).luminance(), s) >= MIN_CONTRAST) null
        else if (s < 0.5f) LightPlate else DarkPlate
    }
    // Hardware bitmaps cannot be read back pixel by pixel; logos are tiny, so measuring ones give it up.
    val context = LocalPlatformContext.current
    val model = remember(url, plateOn != null, context) {
        if (plateOn == null) url else ImageRequest.Builder(context).data(url).allowHardware(false).build()
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = contentScale,
        modifier = if (plate != null) modifier.background(plate) else modifier,
        onState = { state ->
            when (state) {
                is AsyncImagePainter.State.Error -> {
                    noteFailure(url, state.result.throwable)
                    failed = true
                }
                is AsyncImagePainter.State.Success ->
                    if (plateOn != null && !logoInk.containsKey(url)) pending = (state.result.image as? BitmapImage)?.bitmap
                else -> Unit
            }
        },
    )
}

/** Mean ink colour per logo URL, measured once per run. [LOGO_INK_NONE] = leave it alone. */
private val logoInk = java.util.concurrent.ConcurrentHashMap<String, Int>()

private const val LOGO_INK_NONE = 0

/** Below this contrast between a logo's ink and the surface, the logo gets a plate. */
private const val MIN_CONTRAST = 3f

private const val MAX_MEASURED_LOGOS = 4096

/** `.plate.ink` on the dark page; the TV's dark plate on the light one. */
private val LightPlate = Color.White.copy(alpha = 0.92f)
private val DarkPlate = Color(0xFF161C24)

/**
 * The logo's mean colour, weighted by alpha so the transparent surround does not drag it towards black;
 * [LOGO_INK_NONE] for essentially opaque artwork, which brings its own background.
 */
private fun Bitmap.measureInk(): Int {
    val stepX = max(1, width / 48)
    val stepY = max(1, height / 48)
    var samples = 0
    var opaque = 0
    var alphaSum = 0.0
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            samples++
            if (alpha > 240) opaque++
            if (alpha > 0) {
                alphaSum += alpha.toDouble()
                r += ((pixel shr 16) and 0xFF).toDouble() * alpha
                g += ((pixel shr 8) and 0xFF).toDouble() * alpha
                b += (pixel and 0xFF).toDouble() * alpha
            }
            x += stepX
        }
        y += stepY
    }
    if (samples == 0 || alphaSum == 0.0 || opaque > samples * 0.9) return LOGO_INK_NONE
    return android.graphics.Color.rgb(
        (r / alphaSum).toInt().coerceIn(0, 255),
        (g / alphaSum).toInt().coerceIn(0, 255),
        (b / alphaSum).toInt().coerceIn(0, 255),
    )
}

/** WCAG contrast ratio between two relative luminances. */
private fun contrastRatio(a: Float, b: Float): Float {
    val lighter = max(a, b)
    val darker = if (lighter == a) b else a
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Logo URLs whose own link is broken: the host answered, and said no (a 4xx other than 408/429), or
 * sent something that is not an image this app can draw. Asking again this run would get the same
 * answer, so the fallback is drawn at once. In memory only; a restart retries everything.
 */
private val deadLogos = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

/**
 * Hosts that cannot be reached, and since when ([android.os.SystemClock.elapsedRealtime]).
 *
 * Per-URL is not enough when a playlist's logo host is *gone* (measured: no ICMP, no TCP/80, a
 * ten-second connect timeout per request): every channel scrolled to would pay the ten seconds
 * itself. So once two different logos from a host fail to connect, the rest of that host's logos fall
 * back at once. Only a failure to connect counts — a 404, a 429 from a CDN rate-limiting a fast scroll,
 * a read timeout on mobile data or one undecodable logo says nothing about the other logos, and one
 * such failure used to blank every logo from the host until the app restarted. The mark expires after
 * [HOST_RETRY_MS], so a host that comes back (or a phone back on Wi-Fi) is tried again.
 */
private val deadHosts = java.util.concurrent.ConcurrentHashMap<String, Long>()

/** Distinct logos per host that failed to connect since the host was last marked. */
private val hostStrikes = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()

private const val HOST_STRIKES = 2
private const val HOST_RETRY_MS = 5 * 60_000L

private fun hostIsDead(host: String): Boolean {
    val since = deadHosts[host] ?: return false
    if (android.os.SystemClock.elapsedRealtime() - since < HOST_RETRY_MS) return true
    deadHosts.remove(host)
    hostStrikes.remove(host)
    return false
}

/** What [url]'s failure says about it, and about its host. */
private fun noteFailure(url: String, failure: Throwable) {
    when {
        failure.isConnectFailure() -> {
            val host = hostOf(url) ?: return
            val strikes = hostStrikes.getOrPut(host) { java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap()) }
            strikes += url
            if (strikes.size >= HOST_STRIKES) deadHosts[host] = android.os.SystemClock.elapsedRealtime()
        }
        failure is coil3.network.HttpException -> {
            val code = failure.response.code
            if (code in 400..499 && code != 408 && code != 429) deadLogos += url
        }
        // A read timeout or a dropped connection: worth another try next time the row is drawn.
        failure is java.io.IOException -> Unit
        // Not an image this app can decode (an SVG, say).
        else -> deadLogos += url
    }
}

/** The host could not be reached at all: no address, refused, or no answer to the connect itself. */
private fun Throwable.isConnectFailure(): Boolean =
    this is java.net.UnknownHostException || this is java.net.ConnectException || this is java.net.NoRouteToHostException ||
        // OkHttp's connect timeout says "failed to connect to …"; a read timeout says "timeout".
        (this is java.net.SocketTimeoutException && message?.contains("connect", ignoreCase = true) == true)

private fun hostOf(url: String?): String? = runCatching { java.net.URI(url).host }.getOrNull()
