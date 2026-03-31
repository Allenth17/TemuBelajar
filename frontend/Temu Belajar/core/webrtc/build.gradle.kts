import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * :core:webrtc — Platform-native WebRTC engine for all targets.
 *
 * Provides a single unified interface [TBWebRtcEngine] backed by:
 *   Android  → stream-webrtc-android  (Google libwebrtc, VP8/H.264/VP9, Camera2)
 *   Desktop  → webrtc-java 0.14.0     (Chromium libwebrtc JNI, VP8/H.264, Sarxos cam)
 *   iOS      → GoogleWebRTC CocoaPod  (RTCPeerConnectionFactory, RTCMTLVideoView)
 *   WASM     → Browser RTCPeerConnection (VP8/H.264/VP9, getUserMedia — zero libs)
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    id("org.jetbrains.kotlin.native.cocoapods")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "coreWebrtc"; isStatic = true }
    }
    jvm("desktop")
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    cocoapods {
        summary   = "TemuBelajar WebRTC engine"
        homepage  = "https://github.com/hiralen/temubelajar"
        version   = "1.0"
        ios.deploymentTarget = "14.0"
        pod("GoogleWebRTC") {
            version  = "~> 1.1"
            extraOpts += listOf("-compiler-option", "-fmodules")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.stream.webrtc.android)
            implementation(libs.koin.core)   // KoinPlatform.getKoin().get<Context>()
            implementation(libs.koin.android)
        }

        val desktopMain by getting {
            dependencies {
                // webrtc-java: Chromium libwebrtc JNI wrapper
                implementation(libs.webrtc.java)
                runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtcJava.get()}:linux-x86_64")
                runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtcJava.get()}:macos-x86_64")
                runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtcJava.get()}:macos-aarch64")
                runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtcJava.get()}:windows-x86_64")
                // Sarxos webcam for local camera capture
                implementation(libs.webcam.capture)
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }

        // iOS: platform.WebRTC.* from GoogleWebRTC CocoaPod (declared above)
        // WASM: browser RTCPeerConnection — zero deps
    }
}

android {
    namespace   = "com.hiralen.temubelajar.core.webrtc"
    compileSdk  = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
