package com.hiralen.temubelajar.core.presentation

// ─── Backend URL Configuration ────────────────────────────────────────────────
// API Gateway (Phoenix/Elixir) listens on port 4000.
// All HTTP and WebSocket traffic is proxied through this single gateway.
//
// For development on emulator/simulator: use 10.0.2.2 (Android) or localhost (desktop/web)
// For development on physical device:    use your machine's LAN IP (e.g. 192.168.1.x)
// For production:                        replace with your real domain (e.g. https://api.temubelajar.id)
//
// HTTP endpoints routed by API Gateway:
//   POST   /api/register
//   POST   /api/verify-otp
//   POST   /api/resend-otp
//   POST   /api/login
//   POST   /api/logout
//   GET    /api/me
//   GET    /api/user/:email
//   PUT    /api/user/:email
//
// WebSocket topics routed by API Gateway:
//   matchmaking:lobby          — matchmaking channel
//   signaling:{pair_id}        — WebRTC signaling channel

// Phase 0.12 — URLs are no longer hardcoded to cleartext dev values at the
// source level. The active URL is resolved by [resolveBaseUrl] in order:
//   1. JVM system property `api.url` / `api.wsUrl` — set by desktop Main.kt
//      (via Gradle `-Papi.url=…`) and by androidApp via BuildConfig.
//   2. Dev fallbacks (HTTP) so a fresh checkout keeps working locally.
//
// Production builds MUST override these via Gradle properties at assembly
// time (see androidApp/build.gradle.kts + desktopApp Main.kt), and the
// network_security_config.xml rejects cleartext for release builds.

internal const val DEV_BASE_URL = "http://192.168.1.4:4000"
internal const val DEV_BASE_WS_URL = "ws://192.168.1.4:4000/socket/websocket?vsn=2.0.0"

val BASE_URL: String get() = resolveBaseUrl()
val BASE_WS_URL: String get() = resolveWsUrl()

// JVM-targeted resolve (Android BuildConfig injects via system property too).
// wasmJs has no System.getProperty so it uses the expect/actual below.
private fun resolveBaseUrl(): String =
    systemProperty("api.url") ?: DEV_BASE_URL

private fun resolveWsUrl(): String =
    systemProperty("api.wsUrl") ?: DEV_BASE_WS_URL

// `expect` per-platform — actual in androidMain/desktopMain/iosMain/wasmJsMain.
internal expect fun systemProperty(name: String): String?
