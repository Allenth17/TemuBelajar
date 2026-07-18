package com.hiralen.temubelajar.webrtc

import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.WebRTC.*
import kotlin.coroutines.resume

/**
 * iOS WebRTC engine — Google WebRTC XCFramework via CocoaPods (platform.WebRTC.*).
 *
 * localRenderer  → RTCVideoTrack  (render via RTCMTLVideoView inside UIKitView)
 * remoteRenderer → RTCVideoTrack  (render via RTCMTLVideoView inside UIKitView)
 *
 * Codecs: VP8 / H.264 (negotiated via SDP, hardware H.264 preferred on Apple Silicon)
 */
actual class TBWebRtcEngine actual constructor() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var factory:    RTCPeerConnectionFactory? = null
    private var pc:         RTCPeerConnection?        = null
    private var localVidTrack:  RTCVideoTrack? = null
    private var remoteVidTrack: RTCVideoTrack? = null
    private var localAudTrack:  RTCAudioTrack? = null
    private var capturer:   RTCCameraVideoCapturer? = null
    // Phase 1.14 — `vidSrc`/`audSrc` were local vals, not retained. The
    // underlying RTCVideoSource / RTCAudioSource objects MUST be held as
    // named instance fields so the Kotlin GC doesn't release them while
    // libwebrtc still references them via the tracks. SAM closures over a
    // local val do not extend its lifetime on Kotlin/Native — the source
    // gets freed when init() returns, then `addTrack` later segfaults.
    private var vidSrc:     RTCVideoSource? = null
    private var audSrc:     RTCAudioSource? = null

    actual val localRenderer:  Any? get() = localVidTrack
    actual val remoteRenderer: Any? get() = remoteVidTrack
    // Phase 1.7 — Compose-observable remote renderer. Mirrors
    // `remoteVidTrack` so `UIRemoteVideoView` (collects this flow) mounts
    // the `RTCMTLVideoView` only after `didAddReceiver` reports the track —
    // no polling, immediate recomposition. Opaque handle is the same
    // `RTCVideoTrack?` as `remoteRenderer`.
    private val _remoteRendererFlow = kotlinx.coroutines.flow.MutableStateFlow<Any?>(null)
    actual val remoteRendererFlow: kotlinx.coroutines.flow.StateFlow<Any?> = _remoteRendererFlow
    // Phase 5.21 — match the cross-platform readiness gate. `localVidTrack`
    // stays null when `RTCCameraVideoCapturer.captureDevices()` returned
    // empty or the front device's `startCaptureWithDevice` failed.
    actual val localTrackReady: Boolean get() = localVidTrack != null

    private var _onLocalSdp:    ((String, String) -> Unit)? = null
    private var _onIce:         ((String, String?, Int) -> Unit)? = null
    private var _onConnected:   (() -> Unit)? = null
    private var _onDisc:        (() -> Unit)? = null

    private val pendingIce  = mutableListOf<RTCIceCandidate>()
    private var remoteSet   = false
    @Volatile private var connected = false
    private var fallbackJob: Job? = null

    private val ICE_SERVERS = listOf(
        RTCIceServer(urlStrings = listOf("stun:stun.l.google.com:19302")),
        RTCIceServer(urlStrings = listOf("stun:stun1.l.google.com:19302")),
        RTCIceServer(urlStrings = listOf("stun:stun.cloudflare.com:3478"))
    )

    actual fun init(
        onLocalSdp:     (type: String, sdp: String) -> Unit,
        onIceCandidate: (candidate: String, sdpMid: String?, sdpMLineIndex: Int) -> Unit,
        onConnected:    () -> Unit,
        onDisconnected: () -> Unit
    ) {
        _onLocalSdp  = onLocalSdp
        _onIce       = onIceCandidate
        _onConnected = onConnected
        _onDisc      = onDisconnected

        RTCPeerConnectionFactory.initialize(RTCInitializeSSL())

        // Phase 4.11 — Configure AVAudioSession for VoIP before the peer
        // connection starts pumping audio. Without this iOS plays audio
        // through the receiver (quiet) and ignores the user's Bluetooth
        // headset. We pick the speaker as the default route + enable
        // voice chat mode so iOS routes audio correctly during a call.
        try {
            val session = AVAudioSession.sharedAudioSession()
            session.setCategory(
                category = AVAudioSessionCategoryPlayAndRecord,
                mode = AVAudioSessionModeVoiceChat,
                options = AVAudioSessionCategoryOptionDefaultToSpeaker
                    or AVAudioSessionCategoryOptionAllowBluetooth
                    or AVAudioSessionCategoryOptionAllowBluetoothA2DP,
                error = null
            )
            session.setActive(true, error = null)
        } catch (t: Throwable) {
            println("[TBWebRtc/iOS] AVAudioSession config failed: ${t.message}")
        }

        factory = RTCPeerConnectionFactory(
            encoderFactory = RTCDefaultVideoEncoderFactory(),
            decoderFactory = RTCDefaultVideoDecoderFactory()
        )

        // Video — Phase 1.14: retain vidSrc as instance field
        vidSrc = factory!!.videoSource()
        localVidTrack = factory!!.videoTrackWithSource(vidSrc!!, trackId = "v0")

        // Camera
        capturer = RTCCameraVideoCapturer(delegate = vidSrc)
        val front = RTCCameraVideoCapturer.captureDevices()
            .filterIsInstance<AVCaptureDevice>()
            .firstOrNull { it.position == AVCaptureDevicePositionFront }
        if (front != null) {
            // Phase 4.5 — `supportedFormatsForDevice(front).lastOrNull()` used
            // to pick the highest-supported format which on modern iPhones is
            // 4K — kills encode bandwidth, melts the battery, and saturates
            // uplink. Filter to ≤1280x720 and pick the highest fps / largest
            // under that cap. If nothing qualifies, fall back to the first
            // supported format (VGA-class on every device we've seen).
            val fmts = RTCCameraVideoCapturer.supportedFormatsForDevice(front)
            val capped = fmts
                .filterIsInstance<AVCaptureDeviceFormat>()
                .filter { fmt ->
                    val desc = fmt.formatDescription
                    val dims = CMVideoFormatDescriptionGetDimensions(desc)
                    dims.width <= 1280 && dims.height <= 720
                }
            val fmt = capped.lastOrNull() ?: fmts.firstOrNull()
            val fps = (fmt?.let {
                RTCCameraVideoCapturer.supportedFrameRateRangeForFormat(it)
            }?.maxFrameRate ?: 30.0).toInt()
            if (fmt != null) {
                capturer!!.startCaptureWithDevice(front, format = fmt, fps = fps.toLong()) {}
            }
        }

        // Audio — Phase 1.14: retain audSrc as instance field
        audSrc = factory!!.audioSourceWithConstraints(null)
        localAudTrack = factory!!.audioTrackWithSource(audSrc!!, trackId = "a0")

        // PeerConnection
        val cfg = RTCConfiguration(iceServers = ICE_SERVERS).apply {
            sdpSemantics = RTCSdpSemantics.RTCSdpSemanticsUnifiedPlan
            continualGatheringPolicy =
                RTCContinualGatheringPolicy.RTCContinualGatheringPolicyGatherContinually
        }

        val observer = object : NSObject(), RTCPeerConnectionDelegateProtocol {
            override fun peerConnection(
                peerConnection: RTCPeerConnection,
                didChangeIceConnectionState: RTCIceConnectionState
            ) {
                when (didChangeIceConnectionState) {
                    RTCIceConnectionState.RTCIceConnectionStateConnected,
                    RTCIceConnectionState.RTCIceConnectionStateCompleted -> fireConnected()
                    RTCIceConnectionState.RTCIceConnectionStateDisconnected,
                    RTCIceConnectionState.RTCIceConnectionStateFailed    -> _onDisc?.invoke()
                    else -> {}
                }
            }
            override fun peerConnection(
                peerConnection: RTCPeerConnection,
                didGenerateIceCandidate: RTCIceCandidate
            ) {
                _onIce?.invoke(
                    didGenerateIceCandidate.sdp,
                    didGenerateIceCandidate.sdpMid,
                    didGenerateIceCandidate.sdpMLineIndex.toInt()
                )
            }
            override fun peerConnection(
                peerConnection: RTCPeerConnection,
                didAddReceiver: RTCRtpReceiver,
                streams: List<*>
            ) {
                val track = didAddReceiver.track
                if (track is RTCVideoTrack) {
                    remoteVidTrack = track
                    // Phase 1.7 — emit on the Compose-observable flow so the
                    // remote video view recomposes + mounts its UIKit view.
                    _remoteRendererFlow.value = track
                }
            }
            override fun peerConnectionShouldNegotiate(peerConnection: RTCPeerConnection)             {}
            override fun peerConnection(peerConnection: RTCPeerConnection,
                                        didChangeSignalingState: RTCSignalingState)                   {}
            override fun peerConnection(peerConnection: RTCPeerConnection,
                                        didChangeIceGatheringState: RTCIceGatheringState)             {}
            override fun peerConnection(peerConnection: RTCPeerConnection,
                                        didRemoveIceCandidates: List<*>)                             {}
            override fun peerConnection(peerConnection: RTCPeerConnection,
                                        didOpenDataChannel: RTCDataChannel)                          {}
        }

        val constraints = RTCMediaConstraints(
            mandatoryConstraints = null, optionalConstraints = null
        )
        pc = factory!!.peerConnectionWithConfiguration(cfg, constraints = constraints, delegate = observer)

        val streamId = "s0"
        pc!!.addTrack(localVidTrack!!, streamIds = listOf(streamId))
        pc!!.addTrack(localAudTrack!!, streamIds = listOf(streamId))
    }

    actual suspend fun createOffer() = suspendCancellableCoroutine<Unit> { cont ->
        val constraints = RTCMediaConstraints(mandatoryConstraints = null, optionalConstraints = null)
        pc?.offerForConstraints(constraints) { sdp, err ->
            if (sdp != null) {
                pc?.setLocalDescription(sdp) { _ ->
                    _onLocalSdp?.invoke("offer", sdp.sdp)
                    cont.resume(Unit)
                }
            } else {
                println("[TBWebRtc/iOS] createOffer: $err"); cont.resume(Unit)
            }
        } ?: cont.resume(Unit)
    }

    actual suspend fun createAnswer() = suspendCancellableCoroutine<Unit> { cont ->
        val constraints = RTCMediaConstraints(mandatoryConstraints = null, optionalConstraints = null)
        pc?.answerForConstraints(constraints) { sdp, err ->
            if (sdp != null) {
                pc?.setLocalDescription(sdp) { _ ->
                    _onLocalSdp?.invoke("answer", sdp.sdp)
                    cont.resume(Unit)
                }
            } else {
                println("[TBWebRtc/iOS] createAnswer: $err"); cont.resume(Unit)
            }
        } ?: cont.resume(Unit)
    }

    actual suspend fun setRemoteDescription(type: String, sdp: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            val sdpType = if (type == "offer") RTCSdpType.RTCSdpTypeOffer else RTCSdpType.RTCSdpTypeAnswer
            val sessionDesc = RTCSessionDescription(type = sdpType, sdp = sdp)
            pc?.setRemoteDescription(sessionDesc) { err ->
                if (err == null) {
                    remoteSet = true
                    pendingIce.forEach { pc?.add(it) }
                    pendingIce.clear()
                    fallbackJob?.cancel()
                    fallbackJob = scope.launch { delay(5000); fireConnected() }
                } else {
                    println("[TBWebRtc/iOS] setRemote: $err")
                }
                cont.resume(Unit)
            } ?: cont.resume(Unit)
        }

    actual fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val ice = RTCIceCandidate(sdp = candidate, sdpMid = sdpMid ?: "", sdpMLineIndex = sdpMLineIndex.toLong())
        if (remoteSet) pc?.add(ice) else pendingIce.add(ice)
    }

    actual fun setMicEnabled(enabled: Boolean)    { localAudTrack?.isEnabled = enabled }

    /**
     * Phase 4.9 — toggle iOS speakerphone output.
     * We re-map iOS's AVAudioSessionPortOverride into a single boolean.
     * `setCategory:.speaker` controls whether the route is forced to the
     * built-in speaker vs. the default receiver / Bluetooth.
     */
    actual fun setSpeakerphoneOn(enabled: Boolean) {
        try {
            val session = platform.AVFAudio.AVAudioSession.Companion.sharedInstance()
            if (enabled) {
                session.overrideOutputAudioPort(platform.AVFAudio.AVAudioSessionPortOverride.AVAudioSessionPortOverrideSpeaker)
            } else {
                session.overrideOutputAudioPort(platform.AVFAudio.AVAudioSessionPortOverride.AVAudioSessionPortOverrideNone)
            }
        } catch (_: Throwable) {
            // AVAudioSession override is best-effort; the next call
            // setup will re-set the right route.
        }
    }

    /**
     * Phase 4.10 — iOS has no per-app audio focus concept equivalent to
     * Android's; AVAudioSession.setActive(true/false) handles the
     * ambient mix policy. We activate the session (with options that
     * allow interruption of background music). Already done in init()
     * per Phase 4.11 — `AVAudioSession configured for VoIP`. So this
     * is a no-op for iOS.
     */
    actual fun requestAudioFocus() {}

    actual fun setCameraEnabled(enabled: Boolean) { localVidTrack?.isEnabled = enabled }
    actual fun switchCamera() {
        scope.launch {
            val devices = RTCCameraVideoCapturer.captureDevices()
                .filterIsInstance<AVCaptureDevice>()
            val next = devices.firstOrNull { it.position != AVCaptureDevicePositionFront } ?: return@launch
            val fmt  = RTCCameraVideoCapturer.supportedFormatsForDevice(next).lastOrNull() ?: return@launch
            val fps  = RTCCameraVideoCapturer.supportedFrameRateRangeForFormat(fmt).maxFrameRate.toInt()
            capturer?.startCaptureWithDevice(next, format = fmt, fps = fps.toLong()) {}
        }
    }

    actual fun dispose() {
        fallbackJob?.cancel()
        capturer?.stopCapture {}
        pc?.close()
        // Phase 1.15 — `pc?.close()` releases the libwebrtc transport but
        // the underlying peer-connection object itself needs `dispose()` to
        // free the C++ handles. Without it the OS XCFramework handle leak
        // accumulates across calls. Same for the factory.
        pc?.dispose()
        factory?.dispose()
        // Phase 1.15 — Pair-init `RTCInitializeSSL()` with `RTCCleanupSSL()`
        // so the OpenSSL state isn't leaked. Must be called once per
        // init/cleanup pair on iOS (libwebrtc uses OpenSSL on iOS).
        RTCCleanupSSL()
        // Phase 1.14 — release the retained sources so they don't leak the
        // underlying libwebrtc source objects.
        vidSrc = null; audSrc = null
        pc = null; factory = null; localVidTrack = null; remoteVidTrack = null
        localAudTrack = null; capturer = null
        // Phase 1.7 — flip the Compose-observable remote flow back to null.
        _remoteRendererFlow.value = null
        connected = false; remoteSet = false; pendingIce.clear()
        scope.cancel()
    }

    private fun fireConnected() {
        if (!connected) { connected = true; fallbackJob?.cancel(); _onConnected?.invoke() }
    }
}
