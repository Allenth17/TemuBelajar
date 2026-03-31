rootProject.name = "TemuBelajar"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Jitpack for Sarxos webcam-capture
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// ─── Modules ───────────────────────────────────────────────────────────────────
include(":core")
include(":core:webrtc")          // platform-native WebRTC engine (VP8 / H.264 / VP9)
include(":feature:auth")
include(":feature:home")
include(":feature:videochat")
include(":composeApp")
