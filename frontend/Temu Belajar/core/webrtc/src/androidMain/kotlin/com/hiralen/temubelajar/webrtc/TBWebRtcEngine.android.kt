package com.hiralen.temubelajar.webrtc

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.mp.KoinPlatform
import org.webrtc.*
import org.webrtc.PeerConnection.*
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import android.media.AudioManager

/**
 * Android WebRTC engine — stream-webrtc-android (Google libwebrtc fork).
 *
 * Codec priority (enforced via SDP):
 *   Video: H.264 (HW) → VP8 → VP9
 *   Audio: Opus
 *
 * localRenderer  → SurfaceViewRenderer (mirror=true, front-facing)
 * remoteRenderer → SurfaceViewRenderer (mirror=false)
 */
actual class TBWebRtcEngine actual constructor() {

    private val appCtx: Context = KoinPlatform.getKoin().get()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var factory:    PeerConnectionFactory? = null
    private var pc:         PeerConnection?        = null
    private var vidSrc:     VideoSource?            = null
    private var vidTrack:   VideoTrack?             = null
    private var audSrc:     AudioSource?            = null
    private var audTrack:   AudioTrack?             = null
    private var capturer:   Camera2Capturer?        = null
    private var stHelper:   SurfaceTextureHelper?   = null
    private var eglBase:    EglBase?                = null

    private var _local:  SurfaceViewRenderer? = null
    private var _remote: SurfaceViewRenderer? = null

    actual val localRenderer:  Any? get() = _local
    actual val remoteRenderer: Any? get() = _remote

    private var _onLocalSdp:    ((String, String) -> Unit)? = null
    private var _onIce:         ((String, String?, Int) -> Unit)? = null
    private var _onConnected:   (() -> Unit)? = null
    private var _onDisconnected:(() -> Unit)? = null

    private val pendingIce   = mutableListOf<IceCandidate>()
    private var remoteSet    = false
    @Volatile private var connected = false
    private var fallbackJob: Job? = null

    // ── STUN servers ──────────────────────────────────────────────────────────
    private val ICE_SERVERS = listOf(
        IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
    )

    actual fun init(
        onLocalSdp:     (String, String) -> Unit,
        onIceCandidate: (String, String?, Int) -> Unit,
        onConnected:    () -> Unit,
        onDisconnected: () -> Unit
    ) {
        _onLocalSdp    = onLocalSdp
        _onIce         = onIceCandidate
        _onConnected   = onConnected
        _onDisconnected = onDisconnected

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(appCtx)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        eglBase = EglBase.create()
        val eglCtx = eglBase!!.eglBaseContext

        _local = SurfaceViewRenderer(appCtx).also {
            it.init(eglCtx, null); it.setMirror(true); it.setEnableHardwareScaler(true)
        }
        _remote = SurfaceViewRenderer(appCtx).also {
            it.init(eglCtx, null); it.setMirror(false); it.setEnableHardwareScaler(true)
        }

        val adm = JavaAudioDeviceModule.builder(appCtx)
            .setUseHardwareAcousticEchoCanceler(false) // Force WebRTC Software AEC
            .setUseHardwareNoiseSuppressor(false)      // Force WebRTC Software NS
            .createAudioDeviceModule()

        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglCtx))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglCtx, true, true))
            .setAudioDeviceModule(adm)
            .createPeerConnectionFactory()

        // Camera
        val cam2 = Camera2Enumerator(appCtx)
        val front = cam2.deviceNames.firstOrNull { cam2.isFrontFacing(it) }
            ?: cam2.deviceNames.firstOrNull() ?: return
        stHelper = SurfaceTextureHelper.create("CapThread", eglCtx)
        capturer = Camera2Capturer(appCtx, front, null)
        vidSrc   = factory!!.createVideoSource(false)
        capturer!!.initialize(stHelper, appCtx, vidSrc!!.capturerObserver)
        capturer!!.startCapture(1280, 720, 30)
        vidTrack = factory!!.createVideoTrack("v0", vidSrc)
        vidTrack?.addSink(_local)

        // Professional Audio constraints with AGC2 and Sensitivity Boost
        val audConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        audSrc   = factory!!.createAudioSource(audConstraints)
        audTrack = factory!!.createAudioTrack("a0", audSrc)

        // PeerConnection
        val cfg = RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = factory!!.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onSignalingChange(s: SignalingState?)           {}
            override fun onIceConnectionReceivingChange(b: Boolean)      {}
            override fun onIceGatheringChange(s: IceGatheringState?)     {}
            override fun onIceCandidatesRemoved(a: Array<out IceCandidate>?) {}
            override fun onRemoveStream(s: MediaStream?)                 {}
            override fun onDataChannel(d: DataChannel?)                  {}
            override fun onRenegotiationNeeded()                         {}

            override fun onIceConnectionChange(s: IceConnectionState?) {
                when (s) {
                    IceConnectionState.CONNECTED,
                    IceConnectionState.COMPLETED  -> fireConnected()
                    IceConnectionState.DISCONNECTED,
                    IceConnectionState.FAILED     -> _onDisconnected?.invoke()
                    else -> {}
                }
            }
            override fun onIceCandidate(c: IceCandidate?) {
                c ?: return
                _onIce?.invoke(c.sdp, c.sdpMid, c.sdpMLineIndex)
            }
            override fun onAddStream(s: MediaStream?) {
                s?.videoTracks?.firstOrNull()?.addSink(_remote)
            }
            override fun onAddTrack(r: RtpReceiver?, streams: Array<out MediaStream>?) {
                (r?.track() as? VideoTrack)?.addSink(_remote)
            }
        })

        pc!!.addTrack(vidTrack, listOf("s0"))
        pc!!.addTrack(audTrack, listOf("s0"))
    }

    actual suspend fun createOffer() = suspendCancellableCoroutine<Unit> { cont ->
        pc?.createOffer(sdpObs { sdp ->
            pc?.setLocalDescription(sdpObs(), sdp)
            _onLocalSdp?.invoke("offer", optimizeSdp(sdp.description))
            cont.resume(Unit)
        }, MediaConstraints()) ?: cont.resume(Unit)
    }

    actual suspend fun createAnswer() = suspendCancellableCoroutine<Unit> { cont ->
        pc?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc?.setLocalDescription(sdpObs(), sdp)
                _onLocalSdp?.invoke("answer", optimizeSdp(sdp.description))
                cont.resume(Unit)
            }
            override fun onSetSuccess()                {}
            override fun onCreateFailure(e: String?)   { println("[WebRTC/Android] createAnswer: $e"); cont.resume(Unit) }
            override fun onSetFailure(e: String?)      { cont.resume(Unit) }
        }, MediaConstraints()) ?: cont.resume(Unit)
    }

    actual suspend fun setRemoteDescription(type: String, sdp: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            val t = if (type == "offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
            pc?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    remoteSet = true
                    scope.launch {
                        pendingIce.forEach { pc?.addIceCandidate(it) }
                        pendingIce.clear()
                    }
                    // 5s fallback in case ICE never reaches CONNECTED
                    fallbackJob?.cancel()
                    // 3s fallback — LAN P2P usually connects in <1s;
                    // if ICE callback fires first, fallbackJob is cancelled.
                    fallbackJob = scope.launch { delay(3000); fireConnected() }
                    cont.resume(Unit)
                }
                override fun onCreateSuccess(s: SessionDescription) {}
                override fun onCreateFailure(e: String?)             { cont.resume(Unit) }
                override fun onSetFailure(e: String?)                {
                    println("[WebRTC/Android] setRemote failed: $e"); cont.resume(Unit)
                }
            }, SessionDescription(t, sdp)) ?: cont.resume(Unit)
        }

    actual fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val ice = IceCandidate(sdpMid ?: "", sdpMLineIndex, candidate)
        if (remoteSet) pc?.addIceCandidate(ice) else pendingIce.add(ice)
    }

    actual fun setMicEnabled(enabled: Boolean)    { audTrack?.setEnabled(enabled) }
    actual fun setCameraEnabled(enabled: Boolean) {
        vidTrack?.setEnabled(enabled)
        if (enabled) capturer?.startCapture(1280, 720, 30)
        else scope.launch { try { capturer?.stopCapture() } catch (_: Exception) {} }
    }
    actual fun switchCamera() {
        scope.launch { try { capturer?.switchCamera(null) } catch (_: Exception) {} }
    }

    actual fun dispose() {
        fallbackJob?.cancel()
        connected = false; remoteSet = false; pendingIce.clear()
        scope.launch { try { capturer?.stopCapture() } catch (_: Exception) {} }
        capturer?.dispose(); vidSrc?.dispose(); audSrc?.dispose()
        stHelper?.dispose(); vidTrack?.dispose(); audTrack?.dispose()
        pc?.close(); pc?.dispose(); factory?.dispose()
        _local?.release(); _remote?.release(); eglBase?.release()
        capturer = null; vidSrc = null; audSrc = null; vidTrack = null
        audTrack = null; pc = null; factory = null; _local = null; _remote = null

        val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false

        scope.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fireConnected() {
        if (!connected) { connected = true; fallbackJob?.cancel(); _onConnected?.invoke() }
    }

    private fun sdpObs(onSuccess: ((SessionDescription) -> Unit)? = null) = object : SdpObserver {
        override fun onCreateSuccess(s: SessionDescription) { onSuccess?.invoke(s) }
        override fun onSetSuccess()                          {}
        override fun onCreateFailure(e: String?)             {}
        override fun onSetFailure(e: String?)                {}
    }

    /**
     * Optimize SDP:
     * 1. Reorder video codecs to prefer H.264 > VP8 > VP9.
     * 2. Enhance Opus audio bitrate to 64kbps and enable stereo.
     */
    private fun optimizeSdp(sdp: String): String {
        // Robust line splitting
        val lines = sdp.split(Regex("\\r?\\n")).toMutableList()
        
        // ── Audio Optimization ───────────────────────────────────────────────
        var opusPayload = -1
        var rtpmapIdx = -1
        for (i in lines.indices) {
            val m = Regex("a=rtpmap:(\\d+) opus/48000").find(lines[i]) ?: continue
            opusPayload = m.groupValues[1].toInt()
            rtpmapIdx = i
            break
        }

        if (opusPayload != -1) {
            val fmtpIdx = lines.indexOfFirst { it.startsWith("a=fmtp:$opusPayload") }
            val audioParams = "maxaveragebitrate=48000;useinbandfec=1"
            if (fmtpIdx >= 0) {
                val line = lines[fmtpIdx]
                if (!line.contains("maxaveragebitrate=")) {
                    lines[fmtpIdx] = "$line;$audioParams"
                }
            } else {
                // IMPORTANT: Insert fmtp line immediately after rtpmap to stay in-section.
                // Appending to the end of SDP is protocol-invalid and causes crashes.
                lines.add(rtpmapIdx + 1, "a=fmtp:$opusPayload $audioParams")
            }
        }

        // ── Video Optimization ───────────────────────────────────────────────
        val mVideoIdx = lines.indexOfFirst { it.startsWith("m=video") }
        if (mVideoIdx >= 0) {
            val preferOrder = listOf("H264", "VP8", "VP9")
            val ptMap = mutableMapOf<Int, String>()
            for (line in lines) {
                val m = Regex("a=rtpmap:(\\d+) ([^/]+)").find(line) ?: continue
                ptMap[m.groupValues[1].toInt()] = m.groupValues[2].uppercase()
            }

            val mLine = lines[mVideoIdx]
            val parts = mLine.split(" ").toMutableList()
            if (parts.size > 3) {
                val existingPts = parts.drop(3).map { it.toIntOrNull() ?: -1 }.filter { it >= 0 }
                val sorted = preferOrder.flatMap { codec ->
                    existingPts.filter { ptMap[it]?.contains(codec) == true }
                } + existingPts.filter { pt -> preferOrder.none { ptMap[pt]?.contains(it) == true } }
                lines[mVideoIdx] = (parts.take(3) + sorted.map { it.toString() }).joinToString(" ")
            }
        }

        return lines.joinToString("\r\n")
    }
}
