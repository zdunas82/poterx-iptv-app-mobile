package tv.own.owntv.mobile.poterx

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tv.own.owntv.core.util.CrashRecorder
import tv.own.owntv.mobile.BuildConfig
import tv.own.owntv.player.PlaybackErrorLog
import java.security.MessageDigest
import java.util.UUID

/**
 * Poterx: what the provider's server learns about the installed base, and nothing more.
 *
 * Once a day, shortly after start, the app sends a heartbeat: a random device id made on first
 * run (no hardware identifier, no account name), the version, the device model and Android
 * version, the language, and how many playback errors the last day had — marked `app: mobile`, so
 * the server's report tells phones from televisions. After a crash the next start sends the crash
 * text once, with every address masked ([PoterxPrivacy.hideEveryHost]) — the same text the playback
 * report would carry. Both go to the update server (`/api/v1/ping`, `/api/v1/crash`, see the
 * television repository's `docs/poterx/vps/`); a server that does not answer costs nothing but the
 * one request, and nothing is ever retried in a loop.
 *
 * The device id is shown on Settings › Account so a customer can read it to the provider.
 */
class PoterxTelemetry(private val context: Context, private val http: OkHttpClient) {

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** The random id of this installation, made once; cleared only with the app's data. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    /** The id as the Account page shows it: the first eight characters, upper case, in two groups. */
    val shortId: String get() = deviceId.replace("-", "").take(8).uppercase().chunked(4).joinToString("-")

    private val baseUrl: String? get() = LockedSource.account?.updateUrl?.takeIf { it.startsWith("https://") }?.trimEnd('/')

    /** Runs the day's heartbeat and any pending crash report, [START_DELAY_MS] after start. */
    fun start() {
        if (!LockedSource.enabled) return
        scope.launch {
            // Reading the address opens the sealed account (a signature lookup): off the main thread.
            if (baseUrl == null) return@launch
            delay(START_DELAY_MS)
            runCatching { reportCrashIfAny() }
            runCatching { pingIfDue() }
        }
    }

    private suspend fun pingIfDue() {
        val today = System.currentTimeMillis() / DAY_MS
        if (prefs.getLong(KEY_PING_DAY, -1L) == today) return
        val since = System.currentTimeMillis() - DAY_MS
        val errors = withContext(Dispatchers.IO) { PlaybackErrorLog.read(context).count { it.atMs >= since && it.kind == PlaybackErrorLog.Kind.ERROR } }
        val body = device()
            .put("playbackErrors24h", errors)
            .put("locale", context.resources.configuration.locales[0].toLanguageTag())
        if (post("/api/v1/ping", body)) prefs.edit().putLong(KEY_PING_DAY, today).apply()
    }

    private suspend fun reportCrashIfAny() {
        val crash = withContext(Dispatchers.IO) { CrashRecorder.read(context) }?.takeIf { it.isNotBlank() } ?: return
        val digest = MessageDigest.getInstance("SHA-256").digest(crash.toByteArray()).joinToString("") { "%02x".format(it) }
        if (prefs.getString(KEY_CRASH_SENT, null) == digest) return
        val body = device().put("crash", PoterxPrivacy.hideEveryHost(crash).take(MAX_CRASH_CHARS))
        if (post("/api/v1/crash", body)) prefs.edit().putString(KEY_CRASH_SENT, digest).apply()
    }

    private fun device(): JSONObject = JSONObject()
        .put("device", deviceId)
        .put("app", APP)
        .put("version", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
        .put("android", Build.VERSION.RELEASE)
        .put("sdk", Build.VERSION.SDK_INT)

    private suspend fun post(path: String, body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val base = baseUrl ?: return@withContext false
        runCatching {
            val request = Request.Builder()
                .url(base + path)
                .header("User-Agent", PoterxAccount.userAgent)
                .post(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { it.isSuccessful }
        }.onFailure { Log.i(TAG, "telemetry skipped: ${it.javaClass.simpleName}") }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "PoterxTelemetry"
        private const val PREFS = "poterx_telemetry"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PING_DAY = "ping_day"
        private const val KEY_CRASH_SENT = "crash_sent"
        /** Which app this ping comes from; the television sends none and is counted as one. */
        private const val APP = "mobile"
        private const val START_DELAY_MS = 20_000L
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val MAX_CRASH_CHARS = 16_000
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
