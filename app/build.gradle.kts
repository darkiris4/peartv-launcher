import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Local dev secrets — release-signing credentials and TMDB/TVDB default API
// keys — read from local.properties (gitignored, never committed). CI passes
// signing credentials as -P project properties instead (see
// .github/workflows/release.yml) so the keystore never touches the repo.
// SettingsRepositoryImpl falls back to the baked-in TMDB/TVDB keys only when
// the user hasn't entered their own; an explicit Settings entry always wins.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
fun releaseSigningProperty(key: String): String? =
    (findProperty("peartv.release.$key") as String?) ?: localProperties.getProperty("release.$key")

// versionCode/versionName for release builds come from the git tag (see
// .github/workflows/release.yml, which parses vMAJOR.MINOR.PATCH into
// -Ppeartv.versionCode/-Ppeartv.versionName) so cutting a release never
// depends on remembering to bump a number by hand here. Local/debug builds
// fall back to these defaults, which only need to track the *current*
// in-progress version for day-to-day dev builds, not real release history.
val releaseVersionCode = (findProperty("peartv.versionCode") as String?)?.toInt() ?: 1
val releaseVersionName = (findProperty("peartv.versionName") as String?) ?: "0.5.0"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.peartv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.peartv.launcher"
        // PRODUCT_SPEC.md Decisions Log: API 34 (Android 14) baseline. The
        // reference Shield TV Pro runs LineageOS (Android 15); stock Shield
        // Experience (Android 11) is years past its final OS update and is
        // no longer a supported target. This floor makes RenderEffect blur,
        // AGSL runtime shaders, and framework predictive-back animation
        // callbacks usable without per-API gating.
        minSdk = 34
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        buildConfigField("String", "TMDB_API_KEY_DEFAULT", "\"${localProperties.getProperty("tmdb.api.key", "")}\"")
        buildConfigField("String", "TVDB_API_KEY_DEFAULT", "\"${localProperties.getProperty("tvdb.api.key", "")}\"")
    }

    signingConfigs {
        create("release") {
            // Left unconfigured (storeFile == null) when no credentials are
            // supplied, so local `assembleRelease` without a keystore still
            // succeeds — it just produces an unsigned APK, same as before.
            releaseSigningProperty("storeFile")?.let { storeFile = file(it) }
            storePassword = releaseSigningProperty("storePassword")
            keyAlias = releaseSigningProperty("keyAlias")
            keyPassword = releaseSigningProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // PRODUCT_SPEC.md §2.3 — Baseline Profile + AOT is the primary
            // frame-pacing strategy; R8 full shrink/minify protects the
            // shipped APK. See proguard-rules.pro for the motion-code
            // keep-rule note.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons.core)

    // AndroidX TV Material3 — MaterialTheme/Text/ColorScheme for ui/theme and
    // ui/launcher/*. (androidx.tv:tv-foundation was tried and dropped: its
    // 1.0.0 stable release ships no lazy-list/grid or focus-group APIs — that
    // functionality lives in plain androidx.compose.foundation.lazy(.grid)
    // now, which is what ui/launcher/* actually uses.)
    implementation(libs.androidx.tv.material)

    // Configured in PearTvLauncherApplication with Bitmap.Config.HARDWARE per
    // PRODUCT_SPEC.md §2.2 — used for any remote/enrichment artwork; the
    // core PackageManager banner path (§3.2) decodes directly, bypassing
    // Coil entirely, since no network fetch is involved.
    implementation(libs.coil.compose)

    // Shared with Coil's own network stack (same resolved version, pinned
    // explicitly rather than relied on as an unlisted transitive dependency)
    // — used directly for the small TMDB Discover API JSON call (§3.1.1 Tier
    // 1), which doesn't need a full Retrofit layer for one GET endpoint.
    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.android)

    // Settings persistence (theme choice, TMDB API key) — PRODUCT_SPEC.md §4's
    // narrowly-scoped settings screen.
    implementation(libs.androidx.datastore.preferences)

    // Tier 2 hero fallback (§3.1.2 Template 4) — samples an app icon's
    // dominant color at app-list build time for a flat solid-color backdrop,
    // replacing an abandoned live-blur attempt (see LauncherAppRepositoryImpl).
    implementation(libs.androidx.palette)

    // Tier 3 full-screen carousel trailer playback (COLUMN_PREVIEW_VIDEO_URI)
    // — media3-exoplayer-hls since streaming apps' trailer URIs are plausibly
    // HLS manifests, not just progressive mp4; media3-ui for PlayerView,
    // embedded via Compose's AndroidView.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)

    // Real Gaussian blur for the dock/status-pill/carousel backdrops,
    // replacing the removed downscale/upscale stand-in (`BackdropBlur.kt`,
    // deleted). Not `Modifier.blur()`/`RenderEffect` — API 31+, a generation
    // past this project's confirmed API 30 reference floor. Operates on
    // Bitmap, not a live Compose GraphicsLayer.
    implementation(libs.renderscript.toolkit)

    // Cold-start splash — installSplashScreen() in MainActivity. Native
    // SplashScreen is API 31+ only; this project's API 30 floor means it
    // runs entirely on the library's own compat rendering path, not the
    // OS's, so Theme.PearTvLauncher.Starting's windowSplashScreen* attrs
    // (themes.xml) are load-bearing on every device this ships to, not a
    // progressive-enhancement nicety.
    implementation(libs.androidx.core.splashscreen)
}
