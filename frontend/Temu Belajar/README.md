# TemuBelajar — Compose Multiplatform frontend

A Kotlin Multiplatform video-call companion UI built on a Linear-flavored dark design system. Targets Android, Desktop (JVM), iOS, and wasmJs (browser).

## Project layout

Mirrors the PresensiQ architecture: a single shared `composeApp` library plus thin per-platform entry modules.

```
Temu Belajar/
├── settings.gradle.kts           # :composeApp + :androidApp + :desktopApp
├── build.gradle.kts              # root plugin declarations
├── gradle.properties             # JVM 17, app.version, KMP source-set layout v2
├── gradle/
│   ├── libs.versions.toml        # version catalog (single source of truth)
│   └── wrapper/gradle-wrapper.properties   # Gradle 9.4.1
│
├── composeApp/                   # KMP library: Compose UI, Decompose, Ktor, WebRTC engine
│   └── src/
│       ├── commonMain/           # screens, theme, DI, networking, WebRtcManager
│       ├── androidMain/          # stream-webrtc-android engine + platform VideoViews
│       ├── desktopMain/          # webrtc-java (Chromium JNI) + Sarxos camera
│       ├── iosMain/              # MainViewController.kt + GoogleWebRTC CocoaPod actual
│       └── wasmJsMain/           # browser RTCPeerConnection via JS interop + Main.wasm.kt
│
├── androidApp/                   # :com.android.application shell (MainActivity, TeBeApp, res/)
├── desktopApp/                   # jvm app (Main.kt → Compose Window)
└── iosApp/                       # Xcode project (UIKit host for ComposeUIViewController)
```

## Toolchain

| Component                    | Version   |
|------------------------------|-----------|
| Gradle                       | 9.4.1     |
| Android Gradp Plugin (AGP)   | 9.2.1     |
| Kotlin                       | 2.3.21    |
| Compose Multiplatform        | 1.11.0    |
| Decompose                    | 3.5.0     |
| Essenty                      | 2.5.0     |
| Ktor                         | 3.1.3     |
| Koin                         | 4.0.0     |
| Coil3                        | 3.1.0     |
| Compottie (Lottie for CMP)   | 2.1.0     |
| stream-webrtc-android        | 1.3.10    |
| webrtc-java                  | 0.14.0    |
| GoogleWebRTC (iOS CocoaPod)  | ~> 1.1    |
| JVM target                   | 17        |
| Android compileSdk           | 37        |
| Android minSdk               | 26        |

All library versions live in `gradle/libs.versions.toml`.

## Design system — Linear dark canvas

The reusable theme is `composeApp/src/commonMain/.../core/ui/Theme.kt`:
`LinearColors` (canvas `#010102`, four-step surface ladder, lavender `#5e6ad2`
single accent, ink/hairline tiers, success green) and `TBTypography` (display /
text / mono families with Linear's negative tracking spec). MaterialTheme is
applied through `TemuBelajarTheme` and the surface ladder carries depth — no
shadows, no atmospheric gradients.

Reusable components (`Components.kt`) build on the surface ladder:
`TBPrimaryButton` (lavender CTA, 8px corners, hover/focus tints), `TBSecondaryButton`
(surface-1 + hairline), `TBTextField` (surface-1 + lavender focus ring), `TBCard`
(surface-1 + hairline, 12px corners), `TBErrorBanner` / `TBSuccessBanner` (surface-2
tint), `TBLogoHeader`, `TBLottie` (resource-path based Lottie loader).

## Building

```sh
./gradlew :androidApp:assembleDebug                       # Android debug APK
./gradlew :desktopApp:assemble                            # Desktop JVM jar
./gradlew :composeApp:compileDevelopmentExecutableKotlinWasmJs   # wasmJs bundle
./gradlew :composeApp:compileCommonMainKotlinMetadata     # commonMain kotlin metadata
```

iOS targets (`iosArm64` / `iosSimulatorArm64`) build only on macOS hosts because
of the `GoogleWebRTC` CocoaPod cinterop — they're disabled by the Kotlin/Native
plugin on Linux/Windows hosts.

## Backend dependency

Talks to the Phoenix/Elixir microservices in `../../backend_elixir/` (api_gateway,
auth_service, user_service, signaling_service, matchmaking_service, social_service,
email_service). The frontend's `BaseUrl.kt` points at the api_gateway; the
signaling WebSocket is consumed by `WebRtcManager` directly.
