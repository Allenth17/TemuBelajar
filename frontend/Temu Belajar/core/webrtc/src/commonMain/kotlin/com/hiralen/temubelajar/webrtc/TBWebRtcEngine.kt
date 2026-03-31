package com.hiralen.temubelajar.webrtc

/**
 * TBWebRtcEngine — unified WebRTC interface for all platforms.
 *
 * Lifecycle:
 *   1. init(callbacks)
 *   2. caller  → createOffer()  ; receiver → wait for setRemoteDescription() then createAnswer()
 *   3. exchange ICE candidates via addIceCandidate()
 *   4. onConnected fires → real-time video/audio P2P is live
 *   5. dispose() when done
 *
 * [localRenderer] and [remoteRenderer] are opaque platform handles:
 *   Android : SurfaceViewRenderer  (attach to AndroidView in Compose)
 *   Desktop : AtomicReference<BufferedImage?>  (polled in LaunchedEffect at 30fps)
 *   iOS     : RTCVideoTrack  (wrapped in UIKitView with RTCMTLVideoView)
 *   WASM    : JsMediaStream  (attached to <video> element via DOM portal)
 *
 * Codecs used per platform:
 *   Android : VP8 / H.264 (HW encoder via DefaultVideoEncoderFactory)
 *   Desktop : VP8 / H.264 (libwebrtc, negotiated via SDP)
 *   iOS     : VP8 / H.264 (GoogleWebRTC XCFramework)
 *   WASM    : VP8 / H.264 / VP9 (browser-native, negotiated via SDP)
 */
expect class TBWebRtcEngine() {

    /** Opaque local video renderer (platform-specific) */
    val localRenderer:  Any?

    /** Opaque remote video renderer (platform-specific) */
    val remoteRenderer: Any?

    /**
     * Initialise the engine: open camera/mic, create PeerConnection.
     * Must be called before createOffer() / setRemoteDescription().
     */
    fun init(
        onLocalSdp:      (type: String, sdp: String) -> Unit,
        onIceCandidate:  (candidate: String, sdpMid: String?, sdpMLineIndex: Int) -> Unit,
        onConnected:     () -> Unit,
        onDisconnected:  () -> Unit
    )

    /** Create and send an SDP offer (caller side). Suspends until local description is set. */
    suspend fun createOffer()

    /** Create and send an SDP answer (receiver side). Suspends until local description is set. */
    suspend fun createAnswer()

    /**
     * Apply a remote SDP offer or answer.
     * Suspends until setRemoteDescription completes so createAnswer() can be called directly after.
     */
    suspend fun setRemoteDescription(type: String, sdp: String)

    /** Add a trickled ICE candidate from the remote peer. */
    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)

    /** Mute / unmute microphone. */
    fun setMicEnabled(enabled: Boolean)

    /** Enable / disable camera (shows black frame when disabled). */
    fun setCameraEnabled(enabled: Boolean)

    /** Switch between front and back camera (no-op on Desktop/WASM). */
    fun switchCamera()

    /** Release all resources: PeerConnection, camera, microphone. */
    fun dispose()
}
