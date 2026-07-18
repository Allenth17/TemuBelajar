package com.hiralen.temubelajar.videochat.webrtc

import com.hiralen.temubelajar.core.ui.MemoryPressure
import com.hiralen.temubelajar.webrtc.TBWebRtcEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * WebRtcManager — thin wrapper around [TBWebRtcEngine] kept for backwards
 * compatibility with VideoChatComponent.
 *
 * All real WebRTC logic lives in :core:webrtc/TBWebRtcEngine.
 * Per-platform actuals just delegate to TBWebRtcEngine.
 *
 * Phase 1.4 — instances should be obtained via Koin `single { WebRtcManager() }`
 * so Home + VideoChat share the same engine. The `ensureInitialized` guard
 * makes `init()` idempotent — a second `init()` call (e.g. when VideoChat
 * opens after Home already started the preview) is a no-op rather than
 * tearing down + re-initializing the PeerConnection.
 */
class WebRtcManager {

    private val engine = TBWebRtcEngine()

    private var initialized = false

    /**
     * Phase 1.20 — long-lived coroutine scope to subscribe to [MemoryPressure]
     * events. A separate scope (NOT shared with `videochat`'s lifecycle-bound
     * scope) so it survives Component destroy events that we should respond
     * to. Cancelled in `dispose()`.
     */
    private val memScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Phase 1.20 — subscribe to OS-emitted memory pressure so we can
        // tear down the camera capture if the OS asks us to. Subscribers
        // are expected to be silent if they're not initialized — `dispose()`
        // itself is idempotent via engine.dispose()-per-platform.
        memScope.launch {
            MemoryPressure.events.collect { level ->
                if (level == MemoryPressure.Level.CRITICAL && initialized) {
                    println("[WebRtcManager] MemoryPressure CRITICAL — disposing engine")
                    dispose()
                }
            }
        }
    }

    val localVideoRenderer:  Any? get() = engine.localRenderer
    val remoteVideoRenderer: Any? get() = engine.remoteRenderer
    /**
     * Phase 5.21 — does the engine actually have a local video track? The
     * Home screen uses this instead of "`initialize()` returned" to decide
     * whether the "Start matching" button is enabled. Direct call-through
     * to [TBWebRtcEngine.localTrackReady] so platform-specific failure paths
     * (no camera enumerated, getUserMedia denied audio-only fallback, etc.)
     * surface here without us having to re-implement them per platform.
     */
    val localTrackReady: Boolean get() = engine.localTrackReady

    /**
     * Phase 1.4 — idempotent initializer. If `init` has already been called
     * on this instance (e.g. Home started the camera preview), subsequent
     * calls from VideoChat are no-ops instead of destroying the existing
     * PeerConnection + re-initializing the camera. The callbacks passed by
     * the second caller are silently dropped — the originals from the first
     * caller keep firing (Home's onConnected handler just updates the
     * preview state, which is benign when VideoChat is foregrounded).
     *
     * For a full re-init (new call after `dispose()`), call `dispose()`
     * first, which clears `initialized`.
     *
     * Phase 4.14 — the platform `engine.init()` actuals do heavy synchronous
     * work (Android: `PeerConnectionFactory.initialize` + `Camera2Enumerator.deviceNames`
     * + `startCapture(1280x720@30)`; ~100-500ms on mid-tier device). Previously
     * that ran on `Dispatchers.Main` because the call-sites wrap this fn in
     * `scope.launch { }` where `scope = Dispatchers.Main + SupervisorJob()`,
     * freezing the UI + Compose recompositions for the duration. Offload to
     * `Dispatchers.Default` so the heavy lifting happens off the main thread;
     * the libwebrtc-internal callbacks (`_onLocalSdp` / `_onConnected` / …)
     * already hop back to libwebrtc's signal thread internally, and the
     * Kotlin-side `StateFlow.value =` writes the callers do are safe from
     * any dispatcher (they use `MutableStateFlow` which is thread-safe).
     */
    suspend fun initialize(
        isOffer: Boolean,
        onLocalSdpReady: (type: String, sdp: String) -> Unit,
        onIceCandidateReady: (candidate: String, sdpMid: String?, sdpMLineIndex: Int) -> Unit,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit
    ) {
        if (initialized) return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            initialized = true
            engine.init(onLocalSdpReady, onIceCandidateReady, onConnected, onDisconnected)
        }
    }

    suspend fun createOffer()                                            = engine.createOffer()
    suspend fun createAnswer()                                           = engine.createAnswer()
    suspend fun setRemoteDescription(type: String, sdp: String)         = engine.setRemoteDescription(type, sdp)
    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) =
        engine.addIceCandidate(candidate, sdpMid, sdpMLineIndex)
    fun setMicEnabled(enabled: Boolean)    = engine.setMicEnabled(enabled)
    fun setCameraEnabled(enabled: Boolean) = engine.setCameraEnabled(enabled)
    fun switchCamera()                     = engine.switchCamera()
    fun dispose() {
        engine.dispose()
        initialized = false
    }

    /**
     * Phase 1.20 — cancels the [MemoryPressure] subscription scope + engine.
     * Called when the owning Decompose component (`HomeComponent` /
     * `VideoChatComponent`) is destroyed — also implicitly on memory-critical
     * OS events (the subscription above).
     */
    fun shutdown() {
        dispose()
        memScope.cancel()
    }

    companion object {
        /** Convenience for code paths not using Koin DI yet. */
        fun fromKoin(): WebRtcManager = KoinPlatform.getKoin().get()
    }
}
