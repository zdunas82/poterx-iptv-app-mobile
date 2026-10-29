package tv.own.owntv.mobile.poterx

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import tv.own.owntv.mobile.BuildConfig

/**
 * Poterx: the one Xtream account this build is locked to — the phone's copy of the television's.
 *
 * The account is handed to Gradle at build time (see `app/build.gradle.kts`, "Poterx locked source")
 * and lands in [BuildConfig] AES-GCM encrypted. The key is not in the APK: it is SHA-256 of a
 * per-build seed, the signing certificate's SHA-256 (read from this app's own signature at run
 * time) and the application id, so the strings pulled out of the APK do not open the blob and a
 * repackaged copy signed with another key opens nothing. That raises the cost; it is still not
 * secrecy — the certificate is public and a patient analyst with a debugger reaches the plain text,
 * and the stream URLs travel the network in the clear anyway. What it does buy is that the line
 * never sits in plain text in the APK, never shows on screen, and cannot be edited, exported or
 * synced away from inside the app — every entry point that could do that is switched off while
 * [enabled] is true.
 */
object LockedSource {

    data class Account(
        val server: String,
        /** Blank on a server-only build; the user's own then come from the sign-in screen. */
        val username: String,
        val password: String,
        val preferHls: Boolean,
        /** Digits asked for before signing out; blank means signing out needs no PIN. */
        val servicePin: String = "",
        /** The provider's contact line (phone, e-mail, …), blank when none was built in. */
        val contact: String = "",
        /** The provider's TMDB API key (v3) for posters and descriptions; blank when none was built in. */
        val tmdbKey: String = "",
        /** The provider's update server (`https://…`), read for `update-mobile.json`; blank keeps GitHub Releases. */
        val updateUrl: String = "",
    )

    /** True for a build that carries an account. A build without one behaves like stock OwnTV Mobile. */
    val enabled: Boolean get() = BuildConfig.POTERX_LOCKED

    /** The application context, needed for the signature; set in `Application.attachBaseContext`. */
    @Volatile private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext ?: context
    }

    /** The account, decrypted once; null when the build carries none or the blob is damaged. */
    val account: Account? by lazy { if (enabled) decrypt() else null }

    /** SHA-256 of the first signing certificate of this installed package, or null when unreadable. */
    private fun signingCertSha256(): ByteArray? = runCatching {
        val context = appContext ?: return null
        val pm = context.packageManager
        val encoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
        } ?: return null
        MessageDigest.getInstance("SHA-256").digest(encoded)
    }.getOrNull()

    /** True when only the server is built in: the user signs in with a username and password. */
    val requiresLogin: Boolean get() = enabled && account?.username.isNullOrBlank()

    private fun decrypt(): Account? = runCatching {
        val seed = Base64.decode(BuildConfig.POTERX_SEED, Base64.NO_WRAP)
        val binding = if (BuildConfig.POTERX_BOUND) (signingCertSha256() ?: return null) else ByteArray(0)
        val key = MessageDigest.getInstance("SHA-256").digest(seed + binding + BuildConfig.APPLICATION_ID.toByteArray(Charsets.UTF_8))
        val blob = Base64.decode(BuildConfig.POTERX_BLOB, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, IV_BYTES)
        val body = blob.copyOfRange(IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
        // Lines 0–7, the television's order; a blob with the television's two VPN lines after them opens too.
        val lines = String(cipher.doFinal(body), Charsets.UTF_8).split('\n')
        if (lines.size < 3 || lines[0].isBlank()) return null
        Account(
            server = lines[0].trim(),
            username = lines[1].trim(),
            password = lines[2],
            preferHls = lines.getOrNull(3)?.trim() == "1",
            servicePin = lines.getOrNull(4)?.trim().orEmpty(),
            contact = lines.getOrNull(5)?.trim().orEmpty(),
            tmdbKey = lines.getOrNull(6)?.trim().orEmpty(),
            updateUrl = lines.getOrNull(7)?.trim().orEmpty(),
        )
    }.getOrNull()

    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val ALGORITHM = "AES"
}
