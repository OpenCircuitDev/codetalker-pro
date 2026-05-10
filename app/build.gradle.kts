// CCT-31 — :app module build for codetalker-companion.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.opencircuit.codetalker"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.opencircuit.codetalker"
        minSdk = 31           // Android 12 — XREAL Beam Pro ships with Android 14, so 31 is conservative
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // CCT-32 Task G.1 — Sentry DSN flows in via signing config, not
        // via the source tree. Empty string means "crash reporting is
        // not configured" — CrashReporter.init() degrades to a no-op.
        // Real DSNs are set via ~/.gradle/gradle.properties → CCT_SENTRY_DSN
        // or via the GitHub Action secret in CI.
        buildConfigField("String", "SENTRY_DSN",
            "\"${project.findProperty("CCT_SENTRY_DSN") as String? ?: ""}\"")
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    /*
     * CCT-32 Task D.2 — release signing.
     *
     * Reads the four CCT_KEYSTORE_* properties from
     * ~/.gradle/gradle.properties (or GitHub Secrets in CI; see
     * docs/DEVELOPER-GUIDE.md §3).
     *
     * Behaviour matrix:
     *   - All four properties present → AAB ships signed.
     *   - CCT_KEYSTORE_FILE missing → release type drops the
     *     signingConfig and produces an unsigned bundle (CI smoke
     *     test, local-dev path before the keystore is generated).
     */
    signingConfigs {
        create("release") {
            val ksPath = project.findProperty("CCT_KEYSTORE_FILE") as String?
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = project.findProperty("CCT_KEYSTORE_PASSWORD") as String? ?: ""
                keyAlias = project.findProperty("CCT_KEY_ALIAS") as String? ?: "codetalker"
                keyPassword = project.findProperty("CCT_KEY_PASSWORD") as String? ?: ""
            }
        }
    }

    buildTypes {
        release {
            // CCT-32 Task D.2 / D.3 — minified + signed release path.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Only wire the signing config when the keystore is configured.
            // Without it, AGP would NPE inside FinalizeBundleTask.
            val ksPath = project.findProperty("CCT_KEYSTORE_FILE") as String?
            if (ksPath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Debug stays unminified for fast iteration.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)

    // Audio
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)

    // CameraX for the QR scanner
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // QR scanning (decode side; encoding still done in dashboard)
    implementation(libs.zxing.core)

    // Coroutines (used by ConnectionGuard, STT events, audio routing)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // XREAL Nebula SDK (placeholder — drop the official AAR into app/libs/ once
    // downloaded from XREAL's developer portal; uncomment the line below)
    // implementation(files("libs/nebula-sdk-2.x.aar"))

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mockk)
    // org.json is bundled with Android the OS but stubbed in JVM unit tests.
    // This Maven artifact provides the real implementation for tests.
    testImplementation(libs.org.json)

    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    debugImplementation(libs.androidx.compose.ui.tooling)
}
