# TemuBelajar frontend — agent notes

## Working directory
`frontend/Temu Belajar/` (the Kotlin Multiplatform project). All commands below are run from there.

## Build / compile commands
```sh
# Configure + metadata
./gradlew help
./gradlew :composeApp:compileCommonMainKotlinMetadata

# Compile each KMP target
./gradlew :composeApp:compileAndroidMain
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:compileDevelopmentExecutableKotlinWasmJs

# Assemble the per-platform app shell
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:assemble

# Full sweep (commonMain + Android + Desktop + wasmJs)
./gradlew :androidApp:assembleDebug :desktopApp:assemble \
         :composeApp:compileDevelopmentExecutableKotlinWasmJs \
         :composeApp:compileCommonMainKotlinMetadata
```

iOS targets (`iosArm64`, `iosSimulatorArm64`) only build on a macOS host — Kotlin/Native disables them on Linux/Windows because of the `GoogleWebRTC` cinterop.

## Lint / typecheck
There's no separate lint or typecheck task. Compile tasks above act as the typecheck (compiler errors fail the build). Dependencies are declared in `gradle/libs.versions.toml`.

## Structure (PresensiQ-style flat layout)
- `:composeApp` — KMP library: shared Compose UI, Decompose navigation, Ktor client, WebRTC engine (inlined per-target), screens, theme.
- `:androidApp` — `com.android.application` shell (`MainActivity`, `TeBeApp`, AndroidManifest, res/).
- `:desktopApp` — jvm app (`Main.kt` → Compose `Window`).
- `iosApp/` — Xcode project hosting `ComposeUIViewController`.

## Toolchain
Gradle 9.4.1 · AGP 9.2.1 · Kotlin 2.3.21 · Compose Multiplatform 1.11.0 · JVM 17 · Android compileSdk 37 / minSdk 26.

## Design system
`composeApp/src/commonMain/.../core/ui/{Theme,Components}.kt` — Linear dark canvas (#010102), four-step surface ladder, lavender `#5e6ad2` single accent, MaterialTheme bridge via `TemuBelajarTheme`. Public back-compat aliases (`TBColors`, `TBShapes`, `TBElevation`, `TBFonts`) keep old screens compiling while delegating to `LinearColors` / `TBTypography` / `TBSpace`.

## Backend dependency
The frontend talks to `backend_elixir/` (parent dir of `frontend/`). Don't edit unless the task explicitly says so — frontend is the active surface here.
