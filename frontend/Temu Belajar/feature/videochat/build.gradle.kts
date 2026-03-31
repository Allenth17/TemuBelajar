import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * :feature:videochat — Video call screen + WebRtcManager per-platform actuals.
 *
 * Android:  stream-webrtc-android  (real P2P, Camera2)
 * Desktop:  Sarxos webcam capture  (real camera preview, stub SDP relay)
 * iOS:      Google WebRTC XCFramework via CocoaPods (platform.WebRTC.*)
 * wasmJs:   Browser-native RTCPeerConnection via Kotlin/WASM JS interop (no lib)
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    id("org.jetbrains.kotlin.native.cocoapods")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "featureVideochat"; isStatic = true }
    }
    jvm("desktop")
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser(); binaries.executable() }

    // ── CocoaPods configuration for iOS ──────────────────────────────────────
    cocoapods {
        summary = "TemuBelajar video chat feature"
        homepage = "https://github.com/hiralen/temubelajar"
        version = "1.0"
        ios.deploymentTarget = "14.0"
        // Google WebRTC XCFramework — exposes platform.WebRTC.* to Kotlin/Native
        pod("GoogleWebRTC") {
            version = "~> 1.1"
            extraOpts += listOf("-compiler-option", "-fmodules")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":core:webrtc"))  // TBWebRtcEngine for all platforms
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.decompose)
            implementation(libs.decompose.extensionComposeJetbrains)
            implementation(libs.koin.compose)
            api(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.accompanist.permission)
            // SurfaceViewRenderer still needed for AndroidView in VideoViews.android.kt
            implementation(libs.stream.webrtc.android)
            implementation(libs.stream.webrtc.android.ui)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }

        val wasmJsMain by getting {
            // All WASM WebRTC interop is in :core:webrtc wasmJsMain
        }
    }
}

android {
    namespace = "com.hiralen.temubelajar.feature.videochat"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
