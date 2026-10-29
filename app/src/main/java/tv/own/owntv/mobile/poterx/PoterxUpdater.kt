package tv.own.owntv.mobile.poterx

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.own.owntv.mobile.BuildConfig
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.core.update.UpdateManager.Failure
import tv.own.owntv.core.update.UpdateManager.State
import tv.own.owntv.core.update.UpdateManager.UpdateInfo
import tv.own.owntv.mobile.poterx.AppUpdater
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Poterx: in-app updates from the provider's own server instead of GitHub Releases. The server
 * publishes `update-mobile.json` (the customers' channel) and `update-mobile-test.json` (the provider's
 * own phones, chosen under Settings › Account behind the service PIN) — its own pair, beside the
 * television's `update.json`, since the two apps are different packages with their own versions: version code and name, the
 * APK's address and SHA-256, the notes shown in the card, and the lowest version code still
 * served — an installed version below it gets a card that offers nothing but the update.
 *
 * Speaks core's [UpdateManager.State] so the dialog, the startup card and the About page are
 * unchanged. The download is written once, checked against the SHA-256 and the package name,
 * then handed to [PackageInstaller] as core does, so a damaged or swapped file never installs.
 */
class PoterxUpdater(
    private val context: Context,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
    private val baseUrl: String,
) : AppUpdater {

    private class Release(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val sha256: String,
        val notes: String,
        val minVersionCode: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    override val state: StateFlow<State> = _state.asStateFlow()
    private val _required = MutableStateFlow(false)
    override val required: StateFlow<Boolean> = _required.asStateFlow()
    override val currentVersion: String = BuildConfig.VERSION_NAME

    private var release: Release? = null

    /** The shared client, but an https address never redirects to plain http (the update must not leave TLS). */
    private val safeClient by lazy { client.newBuilder().followSslRedirects(false).build() }

    private class CheckHttpException(val code: Int) : IOException()
    private class DownloadHttpException(val code: Int) : IOException()
    private class InvalidResponseException : IOException()
    private class EmptyDownloadException : IOException()
    private class NotEnoughSpaceException(val requiredBytes: Long) : IOException()
    private class DamagedDownloadException(reason: String) : IOException(reason)
    private class InstallException(cause: Throwable) : IOException(cause)

    /** The channel: `update-mobile-test.json` for the provider's own phones, `update-mobile.json` for everyone else. */
    var testChannel: Boolean
        get() = prefs.getBoolean(KEY_TEST_CHANNEL, false)
        set(value) { prefs.edit().putBoolean(KEY_TEST_CHANNEL, value).apply() }

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun manifestUrl(): String =
        baseUrl.trimEnd('/') + if (testChannel) "/update-mobile-test.json" else "/update-mobile.json"

    override fun check() {
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        _state.value = State.Checking
        scope.launch {
            runCatching {
                val request = Request.Builder()
                    .url(manifestUrl())
                    .header("Accept", "application/json")
                    .header("User-Agent", PoterxAccount.userAgent)
                    .header("Cache-Control", "no-cache")
                    .build()
                safeClient.newCall(request).execute().use { resp ->
                    // No manifest on this channel yet (nothing promoted so far) is "nothing to offer",
                    // not a failure to show at start.
                    if (resp.code == 404) {
                        settings.recordUpdateCheck(System.currentTimeMillis())
                        release = null
                        _required.value = false
                        _state.value = State.UpToDate
                        return@use
                    }
                    if (!resp.isSuccessful) throw CheckHttpException(resp.code)
                    // OkHttp 4 here (the television is on 5): the body is nullable.
                    val body = resp.body?.string() ?: throw InvalidResponseException()
                    val o = runCatching { JSONObject(body) }.getOrElse { throw InvalidResponseException() }
                    val found = Release(
                        versionCode = o.optInt("versionCode", 0).takeIf { it > 0 } ?: throw InvalidResponseException(),
                        versionName = o.optString("versionName").takeIf { it.isNotBlank() } ?: throw InvalidResponseException(),
                        apkUrl = o.optString("apkUrl").takeIf { it.startsWith("https://") } ?: throw InvalidResponseException(),
                        // The checksum is mandatory: a manifest without a valid one is refused, never installed unchecked.
                        sha256 = o.optString("sha256").trim().lowercase().takeIf { SHA256_HEX.matches(it) } ?: throw InvalidResponseException(),
                        notes = o.optString("notes").take(16_000),
                        minVersionCode = o.optInt("minVersionCode", 0),
                    )
                    settings.recordUpdateCheck(System.currentTimeMillis())
                    release = found
                    val installed = BuildConfig.VERSION_CODE
                    _required.value = found.minVersionCode > installed && found.versionCode > installed
                    _state.value = if (found.versionCode > installed) {
                        State.Available(UpdateInfo(found.versionName, found.notes, found.apkUrl))
                    } else {
                        State.UpToDate
                    }
                }
            }.onFailure { error ->
                _state.value = State.Failed(failureFor(error, checking = true))
            }
        }
    }

    override fun downloadAndInstall() {
        val info = (_state.value as? State.Available)?.info ?: return
        val found = release?.takeIf { it.apkUrl == info.apkUrl } ?: return
        _state.value = State.Downloading(0)
        scope.launch {
            runCatching {
                val dir = File(context.filesDir, "updates").apply { mkdirs() }
                val out = File(dir, "poterx-update.apk")
                out.delete()
                val request = Request.Builder().url(found.apkUrl).header("User-Agent", PoterxAccount.userAgent).build()
                try {
                    safeClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) throw DownloadHttpException(resp.code)
                        val body = resp.body ?: throw EmptyDownloadException()
                        val total = body.contentLength()
                        if (total > 0 && dir.usableSpace < total * 2) throw NotEnoughSpaceException(total * 2)
                        val digest = MessageDigest.getInstance("SHA-256")
                        var copied = 0L
                        body.byteStream().use { input ->
                            out.outputStream().use { output ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    output.write(buf, 0, n)
                                    digest.update(buf, 0, n)
                                    copied += n
                                    if (total > 0) _state.value = State.Downloading((copied * 100 / total).toInt())
                                }
                            }
                        }
                        if (copied == 0L) throw EmptyDownloadException()
                        if (total > 0 && copied != total) throw DamagedDownloadException("truncated")
                        val hex = digest.digest().joinToString("") { "%02x".format(it) }
                        if (hex != found.sha256) throw DamagedDownloadException("checksum")
                    }
                    verifyApk(out, found.versionCode)
                    install(out)
                } catch (e: Throwable) {
                    out.delete()
                    throw e
                }
                _state.value = State.Available(info)
            }.onFailure { error ->
                val failure = if (error is InstallException) Failure.Install else failureFor(error, checking = false)
                _state.value = State.Failed(failure, retryInfo = info)
            }
        }
    }

    private fun failureFor(error: Throwable, checking: Boolean): Failure = when (error) {
        is CheckHttpException -> Failure.CheckHttp(error.code)
        is DownloadHttpException -> Failure.DownloadHttp(error.code)
        is InvalidResponseException -> Failure.InvalidReleaseResponse
        is EmptyDownloadException -> Failure.EmptyDownload
        is NotEnoughSpaceException -> Failure.NotEnoughSpace(error.requiredBytes)
        is DamagedDownloadException -> Failure.DamagedDownload
        else -> if (checking) Failure.CheckNetwork else Failure.DownloadNetwork
    }

    /** The file is this app, at the version the manifest announced, and newer than what is installed. */
    private fun verifyApk(apk: File, expectedVersionCode: Int) {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: throw DamagedDownloadException("unparseable")
        if (info.packageName != context.packageName) throw DamagedDownloadException("package")
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (code != expectedVersionCode.toLong() || code <= BuildConfig.VERSION_CODE) throw DamagedDownloadException("version")
    }

    private fun install(apk: File) {
        registerInstallReceiver()
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = try {
            installer.createSession(params)
        } catch (e: IOException) {
            throw InstallException(e)
        } catch (e: SecurityException) {
            throw InstallException(e)
        }
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite(SESSION_ENTRY, 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(INSTALL_STATUS_ACTION).setPackage(context.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                session.commit(PendingIntent.getBroadcast(context, 0, intent, flags).intentSender)
                committed = true
            }
        } catch (e: IOException) {
            throw InstallException(e)
        } catch (e: SecurityException) {
            throw InstallException(e)
        } finally {
            // A session that never committed (disk full, a refused write) must not stay open.
            if (!committed) runCatching { installer.abandonSession(sessionId) }
        }
        apk.delete()
    }

    private var installReceiverRegistered = false

    private fun registerInstallReceiver() {
        if (installReceiverRegistered) return
        installReceiverRegistered = true
        ContextCompat.registerReceiver(context, installStatusReceiver, IntentFilter(INSTALL_STATUS_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private val installStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { confirm?.let { context.startActivity(it) } }
                        .onFailure { _state.value = State.Failed(Failure.Install) }
                }
                PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
                else -> _state.value = State.Failed(Failure.InstallRejected(message?.takeIf { it.isNotBlank() }))
            }
        }
    }

    override fun retry() {
        val failed = _state.value as? State.Failed ?: return
        val info = failed.retryInfo
        val checkFailure = failed.failure is Failure.CheckHttp || failed.failure is Failure.InvalidReleaseResponse || failed.failure is Failure.CheckNetwork
        if (info != null && !checkFailure) {
            _state.value = State.Available(info)
            downloadAndInstall()
        } else {
            check()
        }
    }

    override fun reset() {
        if (_state.value !is State.Downloading) _state.value = State.Idle
    }

    companion object {
        private const val PREFS = "poterx_updates"
        private val SHA256_HEX = Regex("[0-9a-f]{64}")
        private const val KEY_TEST_CHANNEL = "test_channel"
        private const val SESSION_ENTRY = "poterx-update"
        private const val INSTALL_STATUS_ACTION = "tv.own.owntv.mobile.POTERX_INSTALL_STATUS"
    }
}
