package com.hiralen.temubelajar.core.data

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpMethod
import kotlin.time.Duration.Companion.seconds

/**
 * HttpClientFactory — creates a configured Ktor HttpClient.
 *
 * ─── SSL Pinning ────────────────────────────────────────────────────────────
 * SSL pinning is implemented per-platform because each engine uses a
 * different TLS stack:
 *
 *   Android / Desktop (OkHttp):
 *     Use OkHttpClient.Builder().certificatePinner(CertificatePinner.Builder()
 *         .add(API_HOST, CERT_PIN_1)
 *         .build())
 *     Then wrap: OkHttpEngine(OkHttpConfig().apply { preconfigured = okClient })
 *
 *   iOS (Darwin / NSURLSession):
 *     Implement URLSessionDelegate.urlSession(_:didReceive:completionHandler:)
 *     and verify the server certificate's public key hash against CERT_PIN_1.
 *
 *   wasmJs (browser):
 *     The browser enforces certificate validity natively. Custom pinning is
 *     not supported via JS fetch/WebSocket APIs. Rely on HSTS + CAA DNS records
 *     for production hardening instead.
 *
 * ─── How to enable in production ────────────────────────────────────────────
 * 1. Obtain your production certificate's SHA-256 public-key hash:
 *      openssl s_client -connect api.temubelajar.id:443 < /dev/null 2>/dev/null \
 *        | openssl x509 -pubkey -noout \
 *        | openssl pkey -pubin -outform DER 2>/dev/null \
 *        | openssl dgst -sha256 -binary \
 *        | base64
 * 2. Replace CERT_PIN_1 with the real value.
 * 3. Pass enablePinning = true when calling HttpClientFactory.create() in the
 *    platform DI module (CorePlatformModule).
 * 4. Provide a platform-specific engine with pinning configured (see above).
 *
 * For local development / emulator, leave enablePinning = false so self-signed
 * or plain HTTP connections to localhost:4000 work without issues.
 */
object HttpClientFactory {

    /** Production API host — used as the pin hostname pattern. */
    const val API_HOST = "temubelajar.id"

    /**
     * SHA-256 public-key pin for the production API Gateway TLS certificate.
     * Format: "sha256/<base64-encoded-hash>"
     * Replace this placeholder with the real value before shipping to production.
     */
    const val CERT_PIN_1 = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    /**
     * Create a Ktor HttpClient tuned for short-lived HTTP REST calls.
     *
     * - `HttpRequestRetry` (3.13): retries once on transient network
     *   (`IOException`) errors for idempotent verbs only (GET/HEAD), so a
     *   flaky cell handoff doesn't drop a one-shot `GET /api/me`. POST
     *   (register/login/verifyOtp/logout) is NOT retried to avoid
     *   double-charging / double-OTP-issue side effects.
     * - `HttpTimeout`: bounded `requestTimeoutMillis` + `connectTimeoutMillis`
     *   so a wedged server can't hang the UI forever. `socketTimeoutMillis`
     *   is intentionally left default (very large / effectively no cap) — see
     *   3.14 below for why per-call WS timeouts are deferred to the WS
     *   client / per-request overrides instead.
     * - `Logging` (3.26): `LogLevel.INFO` only — `HEADERS` was leaking the
     *   `Authorization: Bearer ...` header to the logger in plaintext.
     */
    fun create(
        engine: HttpClientEngine,
        enablePinning: Boolean = false   // ← flip to true once cert pin is real
    ): HttpClient = HttpClient(engine) {

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            // 3.26 — INFO only. HEADERS leaked the Authorization header.
            level = if (enablePinning) LogLevel.NONE else LogLevel.INFO
            logger = Logger.DEFAULT
        }
        install(WebSockets) {
            // Ping every 30 s to keep the connection alive through NAT/proxies
            pingInterval = 30.seconds
        }

        // 3.13 — basic one-shot retry for idempotent verbs on network errors.
        // Non-network exceptions (HTTP 4xx/5xx) are NOT retried; those flow to
        // the repository's typed Result.Error path (3.1/3.2).
        install(HttpRequestRetry) {
            retryOnException(maxRetries = 1, retryOnTimeout = false)
            retryIf { request, _ ->
                // Only retry idempotent methods (per task 3.13).
                // POST/PUT/DELETE/PATCH are excluded so login/register/verifyOtp
                // don't fire twice.
                request.method.run {
                    this == HttpMethod.Get || this == HttpMethod.Head
                }
            }
            exponentialDelay()
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 15_000L
            // 3.14 — Leave socketTimeoutMillis unset (Ktor default = none).
            // The matchmaking WebSocket sits idle for >60s waiting for a
            // match_found frame; a 60s socket timeout killed it. Long-lived
            // WS keepalive relies on the 30s ping above + per-call WS client
            // override (see `createWebSocket`). Bounded HTTP requests still
            // get `requestTimeoutMillis` above.
        }

        defaultRequest {
            // Nothing forced here — each call sets its own Content-Type / auth header
        }

    }

    /**
     * Create a dedicated Ktor HttpClient for long-lived WebSocket sessions
     * (matchmaking lobby, in-call chat/signaling).
     *
     * 3.14 — separates the WS client from the REST client so that:
     *   - Long-lived matchmaking waits (>60s idle, >30s request) don't trip
     *     the HTTP `requestTimeoutMillis` cap, AND
     *   - HTTP REST calls still get a tight `requestTimeoutMillis` so chat
     *     WS holding mid-call HTTP requests don't drop silently.
     *
     * `requestTimeoutMillis = 0` disables the overall request timer for the
     * WS upgrade handshake + drain. The 30s ping keeps the connection alive;
     * reads/writes block until the server closes.
     *
     * NOTE (3.14): wiring this client into Koin (e.g. a `wsHttpClient` binding
     * in `CoreModule.kt`) is out of scope for this agent (CoreModule.kt is
     * owned by another agent). For now, the REST client retains the
     * `WebSockets` plugin so existing `httpClient.webSocket { }` call sites
     * keep working; migrate HomeComponent's WS use to this client in a
     * follow-up when CoreModule.kt is available.
     */
    fun createWebSocket(
        engine: HttpClientEngine,
        enablePinning: Boolean = false
    ): HttpClient = HttpClient(engine) {

        install(Logging) {
            level = if (enablePinning) LogLevel.NONE else LogLevel.INFO
            logger = Logger.DEFAULT
        }
        install(WebSockets) {
            pingInterval = 30.seconds
        }
        install(HttpTimeout) {
            // 0 = disabled. WS sessions may sit idle for the entire match
            // queue duration (multi-minute). The 30s pingloop is the only
            // liveness signal that matters here.
            requestTimeoutMillis = 0L
            socketTimeoutMillis = 0L
            connectTimeoutMillis = 15_000L
        }

        defaultRequest { }
    }
}
