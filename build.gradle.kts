// CCT-31 — Top-level build file for codetalker-companion.
// Module-specific config lives in app/build.gradle.kts.
//
// 2026-05-11 — detekt aliased at top level so the plugin classpath is
// available when applied in subprojects; mrmans0n/compose-rules wires
// in via :app's `detektPlugins` dep. Catches missing-Modifier and other
// Compose smells (e.g. the missing-clickable class found in
// SessionListScreen). https://mrmans0n.github.io/compose-rules/

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
}
