// CCT-31 — Codetalker AR Companion (Android)
// Settings for the Android Studio project. Single :app module for v1.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // XREAL Nebula SDK is currently distributed as local AARs; resolved
        // via app/libs/ once the SDK zip lands in this directory. Once XREAL
        // ships a Maven coordinate, add it here.
    }
}

rootProject.name = "codetalker-companion"
include(":app")
