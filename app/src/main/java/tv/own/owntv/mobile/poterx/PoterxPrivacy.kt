package tv.own.owntv.mobile.poterx

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.net.URI

/**
 * Poterx: the built-in server's address never reaches the screen. Core already masks credentials
 * in URLs it shows (`HttpClient.redactUrl`), but the host alone identifies the provider's panel, so
 * every text that may carry a URL or a host name (stream info, error texts, the playback error log
 * and its export, guide feed rows) passes through [hide] before it is drawn. Stock builds pass
 * everything through unchanged.
 */
object PoterxPrivacy {

    private const val MASK = "•••"

    /** `scheme://host[:port]` and bare `host[:port]` of the built-in server, case-insensitive. */
    private val patterns: List<Regex> by lazy {
        val server = LockedSource.account?.server?.takeIf { LockedSource.enabled }.orEmpty()
        val host = runCatching { URI(server).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@lazy emptyList()
        val h = Regex.escape(host)
        listOf(
            Regex("(?i)[a-z][a-z0-9+.-]*://$h(?::\\d+)?"),
            Regex("(?i)(?<![a-z0-9.-])$h(?::\\d+)?"),
        )
    }

    /** TMDB's query key, which core's own URL masking does not cover. */
    private val apiKey = Regex("(?i)(api_key=)[^&\\s]*")

    /** The release repository the updater reads (host and path), so a log line never names it. */
    private val releaseRepo: Regex? by lazy {
        val repo = tv.own.owntv.core.CoreBuildInfo.releaseRepo.takeIf { LockedSource.enabled && it.isNotBlank() } ?: return@lazy null
        Regex("(?i)(?:[a-z][a-z0-9+.-]*://)?(?:api\\.)?github\\.com/(?:repos/)?" + Regex.escape(repo) + "[^\\s\"']*")
    }

    /** [text] with every mention of the built-in server replaced by a mask, and any TMDB key hidden. */
    fun hide(text: String): String {
        var out = text.replace(apiKey, "$1***")
        releaseRepo?.let { out = out.replace(it, MASK) }
        for (p in patterns) out = out.replace(p, MASK)
        return out
    }

    /** True when [text] mentions the built-in server at all. */
    fun mentionsServer(text: String): Boolean = patterns.any { it.containsMatchIn(text) }

    /** Every `scheme://host[:port]`, whatever the host: the report leaves the device, so no address at all. */
    private val anyHost = Regex("(?i)([a-z][a-z0-9+.-]*://)[^/\\s<>'\\x22]+")

    /** A bare IPv4 address, with its port when there is one: connection errors name the peer that way ("failed to connect to /203.0.113.7 (port 80)"). */
    private val bareIpv4 = Regex("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.]*\\d)(?::\\d{1,5})?")

    /** [hide], and every URL's host and every bare IP address masked too — for text that leaves the phone. */
    fun hideEveryHost(text: String): String = hide(text).replace(anyHost, "$1$MASK").replace(bareIpv4, MASK)

    /**
     * The playback report for a locked build: core's own export, masked before a byte is written.
     * Core writes through MediaStore on Android 10+ and hands back a display name, not a path, so
     * rewriting the file afterwards (v0.2.2–v0.3.6) touched nothing and the stream URLs in the live
     * diagnostics carried the panel's host into Download. The text is built here, passed through
     * [hideEveryHost], then written under the app's own name. Returns where it landed, as core does.
     */
    fun exportPlaybackReport(context: Context): String? = runCatching {
        val app = context.applicationContext
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val text = buildString {
            appendLine("Poterx playback report")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Exported ${stamp.format(java.util.Date())}")
            appendLine()
            tv.own.owntv.core.util.CrashRecorder.read(app)?.let {
                appendLine("--- last crash ---")
                appendLine(it)
                appendLine()
            }
            tv.own.owntv.player.PlaybackErrorLog.read(app).forEach { e ->
                appendLine("[${stamp.format(java.util.Date(e.atMs))}] ${e.kind} · ${e.engine} · ${if (e.live) "live" else "vod"}")
                e.reason?.let { appendLine("  reason: $it") }
                e.spec?.let { appendLine("  spec  : $it") }
                e.raw?.let { appendLine("  raw   : $it") }
                appendLine()
            }
            val live = tv.own.owntv.player.LiveDiagnosticsLog.snapshot()
            if (live.isNotBlank()) {
                appendLine("--- live diagnostics (most recent) ---")
                appendLine(live)
            }
        }
        writeReport(app, hideEveryHost(text))
    }.getOrNull()

    private const val REPORT_NAME = "poterx-playback-report.txt"

    private fun writeReport(context: Context, text: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { return writeReportToMediaStore(context, text) }
        }
        runCatching {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            check(downloads.exists() || downloads.mkdirs())
            File(downloads, REPORT_NAME).writeText(text)
            return "${Environment.DIRECTORY_DOWNLOADS}/$REPORT_NAME"
        }
        val fallback = File(context.getExternalFilesDir(null) ?: context.filesDir, REPORT_NAME)
        fallback.writeText(text)
        return fallback.absolutePath
    }

    /** Scoped storage: the one report is replaced in place rather than growing `(1)`, `(2)` copies. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun writeReportToMediaStore(context: Context, text: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/"
        val existing = runCatching {
            resolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(REPORT_NAME, relativePath), "${MediaStore.MediaColumns._ID} DESC",
            )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
        }.getOrNull()
        if (existing != null) {
            val replaced = runCatching { resolver.openOutputStream(existing, "wt")?.use { it.write(text.toByteArray()) } ?: error("no stream") }.isSuccess
            if (replaced) return "$relativePath$REPORT_NAME"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, REPORT_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("no row")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) } ?: error("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return "$relativePath$REPORT_NAME"
    }
}
