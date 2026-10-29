import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import org.gradle.process.ExecOperations

// Packaged locale qualifiers are read from tools/i18n/locales.json entries where packaged = true.
// That catalogue is owned by the core repo (ahXN00/OwnTV_Core), which holds the strings; the copy
// here exists only because Gradle needs the list before any dependency is resolved, so core's copy
// never reaches this build. The pin-bump PR refreshes both files together — never edit one alone,
// or a new language is silently stripped out of the APK while the build stays green.
// The build consumes the ``resourceQualifier`` field specifically (NOT languageTag, NOT weblateCode):
// a runtime BCP-47 tag fed straight into localeFilters is the bug this schema exists to prevent.
val localesCatalogueFile = rootProject.file("tools/i18n/locales.json")
@Suppress("UNCHECKED_CAST")
val packagedLocaleQualifiers: Set<String> = run {
    if (!localesCatalogueFile.isFile) return@run emptySet()
    val raw = groovy.json.JsonSlurper().parseText(localesCatalogueFile.readText()) as List<Map<String, Any>>
    raw.mapNotNull { entry ->
        if ((entry["packaged"] as? Boolean) == true) entry["resourceQualifier"] as? String else null
    }.toSet()
}

// Poterx: a locked build (server or playlist line supplied, see defaultConfig below) packages Polish
// and English only. Mirrors PoterxLocales.qualifiers in the app, which narrows the language picker.
val poterxLocaleQualifiers: Set<String> = setOf("en", "pl")
val poterxLockedBuild: Boolean = run {
    val props = Properties().apply {
        val f = rootProject.file("poterx.properties")
        if (f.isFile) f.inputStream().use { load(it) }
    }
    fun present(env: String, property: String): Boolean =
        !System.getenv(env).isNullOrBlank() ||
            !providers.gradleProperty(property).orNull.isNullOrBlank() ||
            !props.getProperty(property).isNullOrBlank()
    present("POTERX_PLAYLIST_URL", "poterx.playlistUrl") || present("POTERX_SERVER", "poterx.server")
}

plugins {
    alias(libs.plugins.android.application)
    // Kotlin is provided by AGP 9's built-in Kotlin support; this plugin pins the Compose compiler
    // to the same version the TV app and core use.
    alias(libs.plugins.compose.compiler)
    // Consumes :baselineprofile's recording and packages it as baseline.prof.
    alias(libs.plugins.baselineprofile)
}

android {
    // Distinct from the TV app's tv.own.owntv, so both install side by side on one device and each
    // keeps its own data. This value can never change after the first release — the launcher label
    // and icon can.
    namespace = "tv.own.owntv.mobile"
    compileSdk {
        version = release(37)
    }

    // Signing credentials AND local-only build switches, kept in a standalone properties file OUTSIDE
    // the repo. Gradle only reads gradle.properties from GRADLE_USER_HOME or the project dir, so this
    // one is loaded by hand. Declared here because defaultConfig below already needs it.
    val localSigningProps = Properties().apply {
        val f = File("E:/MEGA/CODE/OwnTV_Gradle/owntv-signing.properties")
        if (f.isFile) f.inputStream().use { load(it) }
    }

    // The SAME keystore and the same property names as the TV app. A signing certificate must be
    // stable per applicationId, not unique per app, and these two apps are maintained by one person
    // — a second keystore would only add a second thing to lose. Env vars first (that is how CI
    // injects the GitHub secrets), then user-wide Gradle properties, then the out-of-repo file.
    // Nothing configured (a fork, a fresh clone) still builds; the APK is just unsigned.
    // Declared here, above defaultConfig, because the sealed account below binds to the certificate.
    fun signingValue(env: String, property: String): String? =
        System.getenv(env)
            ?: providers.gradleProperty(property).orNull
            ?: localSigningProps.getProperty(property)

    val releaseKeystore = signingValue("KEYSTORE_FILE", "owntv.keystoreFile")

    /**
     * Poterx: SHA-256 of the certificate this build will be signed with — the release keystore when
     * one is configured, else the debug keystore every local build signs with. The sealed account's
     * key is derived from it (see `PoterxLink.seal`), so the blob opens only inside an APK carrying
     * this signature: a repackaged copy, or the strings pulled out of the APK, do not decrypt it.
     */
    fun signingCertSha256(): ByteArray? = runCatching {
        val (path, storePass, alias) = if (releaseKeystore != null) {
            Triple(releaseKeystore, signingValue("KEYSTORE_PASSWORD", "owntv.keystorePassword").orEmpty(), signingValue("KEY_ALIAS", "owntv.keyAlias").orEmpty())
        } else {
            Triple(File(System.getProperty("user.home"), ".android/debug.keystore").path, "android", "androiddebugkey")
        }
        val f = File(path)
        if (!f.isFile) return@runCatching null
        val store = listOf("PKCS12", "JKS").firstNotNullOfOrNull { type ->
            runCatching { KeyStore.getInstance(type).also { ks -> f.inputStream().use { ks.load(it, storePass.toCharArray()) } } }.getOrNull()
        } ?: return@runCatching null
        val cert = store.getCertificate(alias) ?: store.aliases().toList().firstNotNullOfOrNull { store.getCertificate(it) } ?: return@runCatching null
        MessageDigest.getInstance("SHA-256").digest(cert.encoded)
    }.getOrNull()

    defaultConfig {
        // Poterx: its own install identity, so it sits next to a stock OwnTV Mobile (and next to
        // Poterx TV on a box) instead of replacing either. Never changes after the first release.
        applicationId = "tv.poterx.mobile"
        minSdk = 26
        targetSdk = 36
        // CI injects these from the git tag, exactly as in the TV app. The fallbacks are only used
        // by local/debug builds and are pinned HIGH (above 99.99.99.99 in the release scale) so a dev
        // APK is always "newer" than a published release and installs straight over it.
        versionCode = (System.getenv("VERSION_CODE") ?: "100000000").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "99.99.99"

        // The three switches core reads through CoreBuildInfo. Same resolution order as the TV app:
        // env var (CI) > Gradle property > the out-of-repo properties file.
        buildConfigField(
            "boolean",
            "DIAGNOSTIC_BUILD",
            (providers.gradleProperty("diagnosticBuild").orNull == "true").toString(),
        )
        buildConfigField(
            "boolean",
            "DEV_TOOLS",
            (
                (
                    providers.gradleProperty("owntv.devTools").orNull
                        ?: localSigningProps.getProperty("owntv.devTools")
                    ) == "true"
                ).toString(),
        )
        // Shared secret the metadata Worker's edge rule requires (`x-owntv-key`). NEVER in the repo.
        // A blank key is a working configuration: core falls back to the unprotected base URL.
        val edgeKey = System.getenv("OWNTV_EDGE_KEY")
            ?: providers.gradleProperty("owntv.edgeKey").orNull
            ?: localSigningProps.getProperty("owntv.edgeKey")
            ?: ""
        buildConfigField("String", "TMDB_EDGE_KEY", "\"${edgeKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ---- Poterx locked source -------------------------------------------------------------
        // The one Xtream account this build is locked to. NEVER in the repo. Sources, first wins:
        //   1. env POTERX_PLAYLIST_URL / POTERX_SERVER + POTERX_USERNAME + POTERX_PASSWORD (CI secrets)
        //   2. Gradle properties poterx.playlistUrl / poterx.server / poterx.username / poterx.password
        //   3. poterx.properties in the project root (gitignored) with the same keys
        // A playlist link is accepted in every form a panel hands out (get.php?username=…,
        // /playlist/USER/PASS/m3u_plus, /live/USER/PASS/…). Two locked shapes:
        //   server + username + password  → everything built in, the user never types anything
        //   server only                   → the server is built in, the user types username + password
        // With no server at all the build is a plain OwnTV Mobile: POTERX_LOCKED is false.
        // The blob's lines are the TV app's, minus the VPN account (lines 8 and 9), which the phone
        // does not carry: server, username, password, prefer-HLS, service PIN, contact, TMDB key,
        // update server.
        val poterxProps = Properties().apply {
            val f = rootProject.file("poterx.properties")
            if (f.isFile) f.inputStream().use { load(it) }
        }
        fun poterxValue(env: String, property: String): String? =
            System.getenv(env)?.takeIf { it.isNotBlank() }
                ?: providers.gradleProperty(property).orNull?.takeIf { it.isNotBlank() }
                ?: poterxProps.getProperty(property)?.takeIf { it.isNotBlank() }
        val poterxAccount: List<String>? = run {
            val link = poterxValue("POTERX_PLAYLIST_URL", "poterx.playlistUrl")?.trim()?.trimEnd('.', ',')
            val fromLink = link?.let { PoterxLink.parse(it) }
            val server = fromLink?.get(0) ?: poterxValue("POTERX_SERVER", "poterx.server")
            val user = fromLink?.get(1) ?: poterxValue("POTERX_USERNAME", "poterx.username")
            val pass = fromLink?.get(2) ?: poterxValue("POTERX_PASSWORD", "poterx.password")
            val hls = fromLink?.get(3) ?: (poterxValue("POTERX_PREFER_HLS", "poterx.preferHls") == "true").let { if (it) "1" else "0" }
            // An optional service PIN (asked before signing out and for the test update channel) and
            // the provider's contact line (shown on sign-in failures and on the Account page).
            val pin = poterxValue("POTERX_SERVICE_PIN", "poterx.servicePin")?.trim().orEmpty()
            val contact = poterxValue("POTERX_CONTACT", "poterx.contact")?.trim().orEmpty()
            // The provider's own TMDB API key (v3): posters and descriptions straight from TMDB.
            val tmdb = poterxValue("POTERX_TMDB_KEY", "poterx.tmdbKey")?.trim().orEmpty()
            // The provider's own update server (https://…); blank keeps GitHub Releases.
            val updateUrl = poterxValue("POTERX_UPDATE_URL", "poterx.updateUrl")?.trim()?.trimEnd('/').orEmpty()
            // Server alone is a valid lock: the user then types only a username and password.
            if (server.isNullOrBlank()) null
            else listOf(PoterxLink.normalizeServer(server), user?.trim().orEmpty(), pass.orEmpty(), hls, pin, contact, tmdb, updateUrl)
        }
        // The key is derived from a random seed, the signing certificate and the application id; the
        // APK carries the seed and the blob, never the key. Without a keystore in reach (fork CI, a
        // bare clone) the binding is empty and the derivation falls back to seed + application id.
        val poterxBinding = if (poterxAccount != null) signingCertSha256() else null
        val poterxSealed = poterxAccount?.let { PoterxLink.seal(it.joinToString("\n"), poterxBinding ?: ByteArray(0), "tv.poterx.mobile") }
        buildConfigField("boolean", "POTERX_LOCKED", (poterxSealed != null).toString())
        buildConfigField("boolean", "POTERX_BOUND", (poterxBinding != null).toString())
        buildConfigField("String", "POTERX_SEED", "\"${poterxSealed?.first ?: ""}\"")
        buildConfigField("String", "POTERX_BLOB", "\"${poterxSealed?.second ?: ""}\"")
        // Never the server address: the build log is public to anyone who can read the Actions run.
        if (poterxAccount != null) logger.lifecycle(if (poterxBinding != null) "Poterx: account sealed to the signing certificate" else "Poterx: account sealed WITHOUT a signing certificate (no keystore in reach)")
        if (poterxAccount != null && poterxAccount[1].isNotBlank()) logger.lifecycle("Poterx: building a LOCKED app, account built in")
        else if (poterxAccount != null) logger.lifecycle("Poterx: building a LOCKED app, server built in, the user signs in")
        else logger.lifecycle("Poterx: no account configured, building an unlocked (stock OwnTV Mobile) app")
    }

    // ABI split via product flavors, mirroring the TV app: `standard` is what real phones and tablets
    // run, `x86_64` exists for the emulator. The player engine ships large prebuilt .so files, so a
    // universal APK would be roughly double the size for no one's benefit.
    flavorDimensions += "abi"
    productFlavors {
        create("standard") {
            dimension = "abi"
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += listOf("x86_64") }
        }
    }

    // Release signing (the keystore and values are resolved above defaultConfig).
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("KEYSTORE_PASSWORD", "owntv.keystorePassword")
                keyAlias = signingValue("KEY_ALIAS", "owntv.keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "owntv.keyPassword")
            }
        }
    }

    testOptions {
        // JVM unit tests reach android.util.Log / SystemClock through core; return defaults instead
        // of "not mocked" crashes.
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        debug {
            // Pseudolocales (en-XA / ar-XB) are the layout-stress instrument: they lengthen every
            // string and mirror the layout, so an overflowing screen shows up before a translator
            // ever sees it. localeFilters below would otherwise strip them, so the debug-only
            // qualifiers are added back through the per-variant API in the androidComponents block.
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Packages only the catalogue entries marked packaged = true — the same 24 locales the TV
        // app ships, because both read the same strings out of core. Without this, every library
        // locale folder ships too (appcompat alone contributes ~85), and nothing can strip a locale
        // afterwards: shrinkResources removes unreferenced resources, never locales. The debug-only
        // pseudolocale qualifiers are added back per variant below; release ships neither.
        // Poterx: Polish and English only on a locked build (smaller APK, quicker updates).
        localeFilters.addAll(if (poterxLockedBuild) poterxLocaleQualifiers else packagedLocaleQualifiers)
    }

    packaging {
        jniLibs {
            // Every .so here is an already-stripped prebuilt from a dependency, so AGP's strip step
            // has nothing to remove and merely fails loudly on a machine with no NDK.
            keepDebugSymbols += "**/*.so"
        }
    }

    lint {
        // CI gates on this (see .github/workflows/android.yml), so an error must mean something.
        abortOnError = true
        warningsAsErrors = false
        // A counted sentence must use Android plural resources; keep this invariant fatal so a new
        // extraction cannot reintroduce English-only quantity wording. The strings live in core, but
        // the call site that needs a plural is here.
        fatal += "PluralsCandidate"
        // Media3's player API surface is almost entirely @UnstableApi; this app is built on core's
        // player, so the check fires ~30 times across the player, Live and Multiview code and
        // carries no signal. Opting in file-by-file would only move the same acknowledgement into a
        // handful of annotations. The television app disables it for the same reason.
        disable += "UnsafeOptInUsageError"
        // local.properties is developer-local and never committed (its Windows SDK path cannot be
        // escaped without breaking the local tooling that writes it). CI has no such file at all.
        disable += "PropertyEscape"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Re-add the debug-only pseudolocale qualifiers that the shared `localeFilters` set above would
// otherwise strip. This is the per-variant SetProperty form (the androidResources block sets the
// MutableSet extension form, which applies to every variant equally and so cannot keep
// pseudolocales out of release). Release variants ship neither pseudolocale.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.androidResources.localeFilters.addAll("en-rXA", "ar-rXB")
    }
}

// A baseline profile is a list of code paths, not machine code, so one recording serves both ABI
// flavors. `mergeIntoMain` writes it to `src/main/generated/baselineProfiles/` rather than the
// recording flavor's own source set, so a profile recorded on an arm phone also ships in the
// x86_64 APK — and so there is one file to review in a diff instead of two.
baselineProfile {
    mergeIntoMain = true
}

// --- hardcoded-literal gate ----------------------------------------------------------------
//
// The same check CI runs, moved onto the developer's own machine. CI is still the enforcing gate —
// this only makes the failure arrive seconds after writing the string instead of minutes after
// pushing it. Wired in while this app is still empty, on purpose: retrofitting it onto twenty
// finished screens is a far worse job.
//
// Deliberately NOT offered: any flag that records the literal and turns the build green. A red build
// means the string moves into core's strings_*.xml or is declared technical — those are the only two
// exits.
abstract class VerifyI18nLiterals : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinSources: ConfigurableFileCollection

    /** The checker and its two reviewed manifests: edit any of them and the verdict may change. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val toolInputs: ConfigurableFileCollection

    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @get:Inject
    abstract val execOps: ExecOperations

    private fun interpreter(): String? = listOf("python", "python3").firstOrNull { candidate ->
        runCatching {
            execOps.exec {
                commandLine(candidate, "--version")
                isIgnoreExitValue = true
                standardOutput = ByteArrayOutputStream()
                errorOutput = ByteArrayOutputStream()
            }.exitValue == 0
        }.getOrDefault(false)
    }

    @TaskAction
    fun verify() {
        val python = interpreter()
        if (python == null) {
            // Failing here would block anyone without Python from building at all. Warn loudly
            // instead — CI still enforces it, so the worst case is a late failure, not a missed one.
            logger.warn(
                "\n  WARNING: Python was not found, so the hardcoded-text check did not run." +
                    "\n  Install Python 3 to catch untranslatable text before pushing; CI will still catch it.\n",
            )
            stamp.get().asFile.writeText("skipped: no python interpreter\n")
            return
        }
        val output = ByteArrayOutputStream()
        val result = execOps.exec {
            workingDir = repoRoot.get().asFile
            commandLine(python, "tools/i18n/check_hardcoded_strings.py", "verify", "--bootstrap")
            environment("PYTHONIOENCODING", "utf-8")
            isIgnoreExitValue = true
            standardOutput = output
            errorOutput = output
        }
        if (result.exitValue != 0) {
            logger.error(output.toString(Charsets.UTF_8))
            throw GradleException("Hardcoded text check failed — see the report above.")
        }
        stamp.get().asFile.writeText("ok\n")
    }
}

val verifyI18nLiterals = tasks.register<VerifyI18nLiterals>("verifyI18nLiterals") {
    group = "verification"
    description = "Fails the build on user-visible text left hardcoded in Kotlin."
    // :app is the only module in this repo. Core's own Kotlin is gated by the identical task in the
    // core repo, so a literal cannot escape by moving between the two. A new module here needs a
    // line added HERE as well as in check_hardcoded_strings.py's SRC_ROOTS — an undeclared input
    // silently skips the gate whenever only that module changed.
    kotlinSources.from(fileTree("src/main/java") { include("**/*.kt") })
    toolInputs.from(
        rootProject.file("tools/i18n/check_hardcoded_strings.py"),
        rootProject.file("tools/i18n/hardcoded_baseline.txt"),
        rootProject.file("tools/i18n/safe_literals.txt"),
    )
    repoRoot.set(rootProject.layout.projectDirectory)
    stamp.set(layout.buildDirectory.file("i18n/literal-inventory.txt"))
}

// preBuild fronts every variant, so debug compile checks and release assembles are both covered.
// Inputs are declared above, so an unchanged source tree makes this UP-TO-DATE and free.
tasks.named("preBuild") { dependsOn(verifyI18nLiterals) }

dependencies {
    // The shared engine, from its own repository — https://github.com/ahXN00/OwnTV_Core. Set
    // owntv.corePath in ~/.gradle/gradle.properties to build against its source instead of the pin.
    implementation(libs.owntv.core)
    // The shared playback engine. It renders nothing itself, which is exactly why the same engine
    // serves the TV HUD and this app's touch controls. Always on core's version.
    implementation(libs.owntv.player.core)
    // libmpv is NOT declared here. `:player-core` exposes it as `api`, so it arrives on this app's
    // compile classpath with core — which it must, because `OwnTVPlayer`'s supertype is
    // `MPVLib.EventObserver`. It was declared explicitly while the pin was older than `1.0.6`, where
    // core still hid it behind `implementation`; the pin is far past that, and two hosts each naming
    // their own version is how a packaging conflict over a native library starts.

    // Core
    implementation(libs.androidx.core.ktx)
    // The launch screen: the flip-card animation, one theme per icon colour (see themes.xml).
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose (BOM-managed) — Material 3 for touch. Never androidx.tv.*; see libs.versions.toml.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    // Preview annotations only: @Preview appears nowhere in main, so this stays out of release.
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    // Window size classes — this app has to lay out for a phone and a tablet from one build.
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.graphics.shapes)

    // Lifecycle / Navigation
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // WorkManager — core's sync/EPG workers, whose auto-initializer core's manifest removes.
    implementation(libs.androidx.work.runtime)

    // Paging — core's catalog DAOs return PagingSource.
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Image loading
    // Media3's SubtitleView, for the image subtitles the engine renders through ExoPlayer.
    implementation(libs.androidx.media3.ui)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Casting. The sender half only: the Chromecast decodes the stream itself, so nothing of the
    // playback engine crosses over — see tv.own.owntv.mobile.cast.
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)

    // Local sync pairing: the camera reads the QR code the other device shows. zxing decodes it —
    // the same library core uses to draw one.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    // Dependency injection
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Debug tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Records the profile this module then packages. Recording needs a real device, so this is
    // never exercised by CI; see baselineprofile/BaselineProfileGenerator.kt.
    baselineProfile(project(":baselineprofile"))
}


// ---- Poterx helpers ---------------------------------------------------------------------------
// Kept in the build script so the account never needs a committed file: the link is parsed here and
// sealed with a fresh AES-GCM key per build. Both halves land in BuildConfig; the app's
// tv.own.owntv.mobile.poterx.LockedSource reverses this at runtime. Identical to the TV app's.
object PoterxLink {
    /** server, username, password, "1"|"0" (prefer HLS) — or null when [raw] is not a panel link. */
    fun parse(raw: String): List<String>? {
        val m = Regex("""^(https?://[^/?#]+)(/[^?#]*)?(\?[^#]*)?""").find(raw.trim()) ?: return null
        val base = m.groupValues[1]
        val path = m.groupValues[2]
        val query = m.groupValues[3].removePrefix("?").replace("&amp;", "&")
        val params = query.split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        params["username"]?.let { u ->
            val p = params["password"] ?: return null
            val out = params["output"]?.lowercase()
            return listOf(base, u, p, if (out == "m3u8" || out == "hls") "1" else "0")
        }
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val idx = if (segments[0] in setOf("playlist", "live", "movie", "series", "timeshift")) 1 else 0
        if (segments.size < idx + 2) return null
        val hls = segments.last().endsWith(".m3u8", ignoreCase = true) || segments.last().equals("m3u8", ignoreCase = true)
        return listOf(base, segments[idx], segments[idx + 1], if (hls) "1" else "0")
    }

    fun normalizeServer(server: String): String {
        val t = server.trim().trimEnd('/')
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    }

    /**
     * Base64 seed to Base64 (iv + ciphertext), AES-256-GCM. The key is SHA-256(seed ‖ binding ‖
     * applicationId): the seed is random per build and travels in the APK, the binding is the signing
     * certificate's SHA-256 (the app recomputes it from its own signature at run time), so the key
     * itself is never written anywhere.
     */
    fun seal(plain: String, binding: ByteArray, applicationId: String): Pair<String, String> {
        val rnd = SecureRandom()
        val seed = ByteArray(32).also { rnd.nextBytes(it) }
        val key = MessageDigest.getInstance("SHA-256").digest(seed + binding + applicationId.toByteArray(Charsets.UTF_8))
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, iv),
        )
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val enc = Base64.getEncoder()
        return enc.encodeToString(seed) to enc.encodeToString(iv + body)
    }
}
