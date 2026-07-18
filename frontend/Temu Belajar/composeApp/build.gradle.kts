import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

/**
 * :composeApp — Compose Multiplatform shared library.
 *
 * Targets: Android, Desktop (jvm), iOS (Arm64 + SimulatorArm64), wasmJs (browser).
 * Includes the Linear-styled UI, Decompose navigation, networking, and inlined
 * platform-native WebRTC engine (TBWebRtcEngine) + per-target VideoViews.
 *
 * Application entry points live in :androidApp and :desktopApp (and MainViewController
 * here for iOS, main.wasmJs.kt for wasmJs).
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.kotlin.native.cocoapods")
}

kotlin {
    android {
        namespace = "com.hiralen.temubelajar.shared"
        compileSdk = 37
        minSdk = 26
        androidResources.enable = true

        // Phase 8.22 — the AGP plugin in use here is
        // `com.android.kotlin.multiplatform.library` (libs.versions.toml
        // `android-kmp-library`), not the older `com.android.library`. The
        // new plugin auto-creates matching release/debug variants for the
        // app module's release BuildType — verified empirically: `./gradlew
        // :androidApp:assembleRelease` invokes `:androidApp:minifyReleaseWithR8`
        // which pulls in `:composeApp`'s release variant automatically. No
        // explicit `buildTypes { release { } }` block is needed here, and
        // the new DSL doesn't actually expose that setter (the older
        // `com.android.library` plugin is what the audit was written
        // against).

        // Phase 8.23 — silence the "android host tests are not enabled"
        // warning the KMP Android target emits whenever `commonTest` runs
        // on a non-Android device. `withHostTest { }` enables local unit
        // tests on the Android target's JVM host so `:composeApp:testDebug`
        // picks up `commonTest` source. Same plugin docs as above; the API
        // is on the `android { }` KMP-target block itself, not the top
        // Kotlin DSL.
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        withHostTest { }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    // iosX64 (Intel Mac simulator) is excluded — Compose Multiplatform + Miuix-style
    // multiplatform libs do not publish iosX64 artifacts; modern simulators use
    // iosSimulatorArm64 (Apple Silicon).
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export(libs.decompose)
            export(libs.decompose.extensions.compose)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("temubelajar")
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "temubelajar.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static(rootDirPath)
                    static(projectDirPath)
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }

        commonMain.dependencies {
            // Compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            // Navigation + lifecycle
            api(libs.decompose)
            api(libs.decompose.extensions.compose)
            api(libs.essenty.lifecycle)

            // Networking
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Serialization / coroutines / datetime
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            // DI
            api(libs.koin.core)
            implementation(libs.koin.compose)

            // Image loading (used in social/profile screens for avatars)
            implementation(libs.coil.compose)
            implementation(libs.coil.compose.core)
            implementation(libs.coil.network.ktor3)

            // Lottie (used for auth loading animations)
            implementation(libs.lottie.compose)

            // Icons (Feather + Tabler + Cupertino extended)
            implementation(libs.compose.icons.feather)
            implementation(libs.compose.icons.tablerIcons)
            implementation(libs.compose.cupertino.icons.extended)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.accompanist.permission)
            // Encrypted token storage (Phase 0.15) — wraps SharedPreferences with
            // AES-256 via Android Keystore. Renders backup-extraction useless.
            implementation(libs.androidx.security.crypto)
            // BackHandler support for in-call confirm-leave dialog (Phase 5.17).
            implementation(libs.androidx.activity.compose)
            // WebRTC — stream-webrtc-android (Google libwebrtc fork)
            implementation(libs.stream.webrtc.android)
            // Phase 4.23 — `stream-webrtc-android-ui` removed. Previous code
            // declared it but no source file imports `io.getstream.webrtc.ui`,
            // and the leak-canary-style SurfaceViewRenderer helpers it
            // adds aren't needed — we render via Compose `AndroidView`.
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.kotlinx.coroutines.swing)
                // WebRTC — webrtc-java (Chromium libwebrtc JNI) + per-OS native binaries
                implementation(libs.webrtc.java)
                runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtcJava.get()}:linux-x86_64")
                runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtcJava.get()}:macos-x86_64")
                runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtcJava.get()}:macos-aarch64")
                runtimeOnly("dev.onvoid.webrtc:webrtc-java:${libs.versions.webrtcJava.get()}:windows-x86_64")
                // Desktop camera (Sarxos)
                implementation(libs.webcam.capture)
            }
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // WebRTC on iOS — provided by GoogleWebRTC CocoaPod (declared in cocoapods {} block below)
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
                // WebRTC on wasmJs — uses the browser-native RTCPeerConnection via JS interop (zero deps)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }

    cocoapods {
        summary = "TemuBelajar shared KMP library"
        homepage = "https://github.com/hiralen/temubelajar"
        version = "1.0"
        ios.deploymentTarget = "14.0"
        // Phase 4.24 — TODO: replace `GoogleWebRTC ~> 1.1` (abandoned 2019,
        // crashes on iOS 17+ during `RTCPeerConnection.initialize` because
        // of removed bits in the iOS 17 C++ runtime) with a maintained
        // drop-in:
        //   - `pod("WebRTC", "~> 1.1.29075")` (livekit fork) — same Google
        //     binary surface, rebuilt against modern toolchains.
        //   - `pod("LiveKitWebRTC", "~> 1.3.4")` if we want livekit-style
        //     RTP/RTCC extra surface (not needed atm).
        // Cannot be changed from a Linux dev host — `:composeApp:podInstall`
        // requires a macOS host with Xcode installed; the iOS target is
        // compile-disabled off-mac (see GoogleWebRTC cinterop), and any
        // silent swap here would leave the next dev-to-Mac handoff unbuildable
        // until they re-install pods. So this stays as 1.1 with the 4.24
        // note here instead of a speculative pod name swap.
        pod("GoogleWebRTC") {
            version = "~> 1.1"
            extraOpts += listOf("-compiler-option", "-fmodules")
        }
    }
}
