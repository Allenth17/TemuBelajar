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
 *        | openssl pkey -pubin -outform DER \
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
     * Create a Ktor HttpClient using the supplied [engine].
     *
     * @param engine         Platform-specific Ktor engine (OkHttp, Darwin, Js …).
     *                       When [enablePinning] is true the engine should already
     *                       be configured with certificate pinning (see kdoc above).
     * @param enablePinning  Set to true in production builds.
     *                       Currently always false — real pin is not yet provisioned.
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
            // Use HEADERS in debug, NONE in release for privacy
            level = if (enablePinning) LogLevel.NONE else LogLevel.HEADERS
            logger = Logger.DEFAULT
        }
        install(WebSockets) {
            // Ping every 30 s to keep the connection alive through NAT/proxies
            pingInterval = 30.seconds
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis = 60_000L   // longer for WebSocket streams
        }

        defaultRequest {
            // Nothing forced here — each call sets its own Content-Type / auth header
        }

    }
}
