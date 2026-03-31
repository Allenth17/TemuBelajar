package com.hiralen.temubelajar.webrtc

import kotlinx.coroutines.*
import platform.AVFoundation.*
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

    actual val localRenderer:  Any? get() = localVidTrack
    actual val remoteRenderer: Any? get() = remoteVidTrack

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
        onLocalSdp:     (String, String) -> Unit,
        onIceCandidate: (String, String?, Int) -> Unit,
        onConnected:    () -> Unit,
        onDisconnected: () -> Unit
    ) {
        _onLocalSdp  = onLocalSdp
        _onIce       = onIceCandidate
        _onConnected = onConnected
        _onDisc      = onDisconnected

        RTCPeerConnectionFactory.initialize(RTCInitializeSSL())

        factory = RTCPeerConnectionFactory(
            encoderFactory = RTCDefaultVideoEncoderFactory(),
            decoderFactory = RTCDefaultVideoDecoderFactory()
        )

        // Video
        val vidSrc  = factory!!.videoSource()
        localVidTrack = factory!!.videoTrackWithSource(vidSrc, trackId = "v0")

        // Camera
        capturer = RTCCameraVideoCapturer(delegate = vidSrc)
        val front = RTCCameraVideoCapturer.captureDevices()
            .filterIsInstance<AVCaptureDevice>()
            .firstOrNull { it.position == AVCaptureDevicePositionFront }
        if (front != null) {
            val fmt = RTCCameraVideoCapturer.supportedFormatsForDevice(front).lastOrNull()
            val fps = (fmt?.let {
                RTCCameraVideoCapturer.supportedFrameRateRangeForFormat(it)
            }?.maxFrameRate ?: 30.0).toInt()
            if (fmt != null) {
                capturer!!.startCaptureWithDevice(front, format = fmt, fps = fps.toLong()) {}
            }
        }

        // Audio
        val audSrc  = factory!!.audioSourceWithConstraints(null)
        localAudTrack = factory!!.audioTrackWithSource(audSrc, trackId = "a0")

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
                if (track is RTCVideoTrack) remoteVidTrack = track
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
        pc = null; factory = null; localVidTrack = null; remoteVidTrack = null
        localAudTrack = null; capturer = null
        connected = false; remoteSet = false; pendingIce.clear()
        scope.cancel()
    }

    private fun fireConnected() {
        if (!connected) { connected = true; fallbackJob?.cancel(); _onConnected?.invoke() }
    }
}
