// CCT-31 — Top-level build file for codetalker-companion.
// Module-specific config lives in app/build.gradle.kts.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
