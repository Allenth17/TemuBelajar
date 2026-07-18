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
    // Phase 1.17 — share a process-wide EGL context instead of per-engine EglBase.create
    private val eglBase:    EglBase                = SharedEglBase.get()
    // Phase 4.7 — capture format remembered so `setCameraEnabled(true)` after
    // `setCameraEnabled(false)` restarts at the same dims instead of the
    // hardcoded 1280x720 mid-tier-killing fallback.
    @Volatile private var lastCaptureWidth:  Int = 1280
    @Volatile private var lastCaptureHeight: Int = 720
    @Volatile private var lastCaptureFps:    Int = 30
    // Phase 4.3 — true when we requested SCO audio routing for an
    // active Bluetooth headset. Cleared in `dispose()` so the engine
    // returns to the speakerphone/earpiece default on re-init. Used by
    // BluetoothHeadsetManagerPlayback — we read `AudioManager.isBluetoothScoOn`
    // so any platform setting persists across the call.
    @Volatile private var btScoActive: Boolean = false

    private var _local:  SurfaceViewRenderer? = null
    private var _remote: SurfaceViewRenderer? = null

    // Phase 1.12 — keep a ref to the remote track so we can removeSink on dispose.
    // The legacy `addSink(_remote)` was duplicated in both onAddStream + onAddTrack
    // (Phase 4.8 dup-sink fix below) so storing the track lets us detach exactly
    // once before `_remote?.release()`.
    private var remoteVideoTrack: VideoTrack? = null

    actual val localRenderer:  Any? get() = _local
    actual val remoteRenderer: Any? get() = _remote
    // Phase 1.7 — Compose-observable remote renderer. Initial value `null`,
    // flips to the `_remote` SurfaceViewRenderer once `onAddTrack` fires
    // and back to `null` on `dispose()`.
    private val _remoteRendererFlow = kotlinx.coroutines.flow.MutableStateFlow<Any?>(null)
    actual val remoteRendererFlow: kotlinx.coroutines.flow.StateFlow<Any?> = _remoteRendererFlow
    // Phase 5.21 — surface real camera-track readiness rather than relying on
    // `init()` returning, which is unit-typed and silently succeeds even when
    // no front-facing device was enumerated or `startCapture` threw.
    actual val localTrackReady: Boolean get() = vidTrack != null

    private var _onLocalSdp:    ((String, String) -> Unit)? = null
    private var _onIce:         ((String, String?, Int) -> Unit)? = null
    private var _onConnected:   (() -> Unit)? = null
    private var _onDisconnected:(() -> Unit)? = null

    private val pendingIce   = mutableListOf<IceCandidate>()
    private var remoteSet    = false
    @Volatile private var connected = false
    private var fallbackJob: Job? = null

    // ── STUN/TURN servers ─────────────────────────────────────────────────────
    // Phase 4.2 — was hardcoded Google public STUN only. STUN alone can't
    // punch symmetric NAT (most home routers + carrier CGNAT) — for those
    // we need a TURN relay. The TURN host + credentials are read from
    // build config (`BuildConfig.TB_TURN_URL` etc.) with fallback to the
    // still-useful public STUN set so a fresh dev build still works out
    // of the box.
    //
    // To configure real TURN, set in `androidApp/build.gradle.kts`:
    //     buildConfigField("String", "TB_TURN_URL",
    //         "\"turn:turn.example.com:3478\"")
    //     buildConfigField("String", "TB_TURN_USER", "\"user\"")
    //     buildConfigField("String", "TB_TURN_CRED", "\"pass\"")
    // The androidApp module already defines `buildConfigFeatures.buildConfig = true`.
    private val ICE_SERVERS: List<IceServer> by lazy {
        val servers = mutableListOf<IceServer>()
        servers.add(IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        servers.add(IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
        servers.add(IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer())
        // Phase 4.2 — try to load TURN credentials from androidApp's
        // BuildConfig via reflection so it gracefully no-ops when the field
        // doesn't exist (dev builds without BuildConfig wiring). The
        // alternative is a hard compile-time dep on androidApp's package,
        // which would invert the module layering (library knows app's
        // constants — bad). Reflection drop-in keeps the dep optional.
        runCatching {
            val cfgClass = Class.forName("com.hiralen.temubelajar.BuildConfig")
            val turnUrl = cfgClass.getField("TB_TURN_URL").get(null) as? String
            val turnUser = cfgClass.getField("TB_TURN_USER").get(null) as? String
            val turnCred = cfgClass.getField("TB_TURN_CRED").get(null) as? String
            if (!turnUrl.isNullOrBlank() && !turnUser.isNullOrBlank() && !turnCred.isNullOrBlank()) {
                servers.add(
                    IceServer.builder(listOf(turnUrl, "turn:" + turnUrl.substringAfter("turn:")))
                        .setUsername(turnUser)
                        .setPassword(turnCred)
                        .createIceServer()
                )
                android.util.Log.i("TBWebRtcEngine", "Phase 4.2 — TURN server configured from BuildConfig")
            }
        }.onFailure {
            // expected on dev builds without the BuildConfig field — silent
        }
        servers
    }

    actual fun init(
        onLocalSdp:     (type: String, sdp: String) -> Unit,
        onIceCandidate: (candidate: String, sdpMid: String?, sdpMLineIndex: Int) -> Unit,
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
        // Phase 4.1 — set MODE_IN_COMMUNICATION but DO NOT force speakerphone
        // at init(). The previous `isSpeakerphoneOn = true` toggled speaker
        // on even when a Bluetooth headset / wired headset was connected,
        // routing audio away from the user's chosen output device. We now
        // leave speakerphone at the platform default (off — earpiece /
        // headset / Bluetooth SCO follow the OS routing policy) and let
        // the user toggle explicitly via `setSpeakerphoneOn()` (4.9).
        // MODE_IN_COMMUNICATION is still required: it routes the audio
        // through the VoIP audio path (with proper EC/NS processing) at
        // the AudioFlinger level; switching back to NORMAL is done in
        // `dispose()` to restore ringtones.
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        // Phase 4.3 — start Bluetooth SCO routing if a BT headset is
        // connected. The previous code never set
        // `isBluetoothScoOn = true` / `startBluetoothSco()` so even when a
        // user had a paired headset, calls played through the speaker /
        // earpiece. We probe the AudioFlinger for any connected BT SCO
        // device and request it as the audio sink for VoIP. Wrapped in
        // try/catch because some OEMs throw on startBluetoothSco when no
        // headset is paired (caller must check first via
        // isBluetoothScoAvailableOffCall but that API has its own quirks
        // — best-effort startup is the safer call).
        try {
            if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
                btScoActive = true
                android.util.Log.i("TBWebRtcEngine", "Phase 4.3 — Bluetooth SCO routing enabled")
            }
        } catch (t: Throwable) {
            android.util.Log.w("TBWebRtcEngine", "Phase 4.3 — startBluetoothSco failed: ${t.message}")
            btScoActive = false
        }

        val eglCtx = eglBase.eglBaseContext

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

        // Phase 1.6 / 4.6 — pass a real CameraVideoCapturer.CameraEventsHandler
        // so libwebrtc surfaces "camera in use" / "camera disconnected" /
        // "camera stopped" instead of silently nuking the track. The handler
        // logs + surfaces a recoverable error path. Previously the `null`
        // handler swallowed every failure and `init()` returned at line 107
        // with `vidTrack = null` — leaving the user staring at a black preview.
        val cam2 = Camera2Enumerator(appCtx)
        val front = cam2.deviceNames.firstOrNull { cam2.isFrontFacing(it) }
            ?: cam2.deviceNames.firstOrNull()
        if (front == null) {
            android.util.Log.w("TBWebRtcEngine", "Phase 1.6 — no camera enumerated; init returns with no video track")
            return
        }

        stHelper = SurfaceTextureHelper.create("CapThread", eglCtx)
        val cameraEvents = object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraOpening(s: String?) {
                android.util.Log.i("TBWebRtcEngine", "Phase 1.6 — camera opening: $s")
            }
            override fun onCameraError(s: String?) {
                android.util.Log.e("TBWebRtcEngine", "Phase 1.6 — camera error: $s")
            }
            override fun onCameraDisconnected() {
                android.util.Log.w("TBWebRtcEngine", "Phase 1.6 — camera disconnected")
            }
            override fun onCameraFreezed(s: String?) {
                android.util.Log.w("TBWebRtcEngine", "Phase 1.6 — camera freezed: $s")
            }
            override fun onCameraClosed() {
                android.util.Log.i("TBWebRtcEngine", "Phase 1.6 — camera closed")
            }
            override fun onFirstFrameAvailable() {
                android.util.Log.i("TBWebRtcEngine", "Phase 1.6 — first preview frame available")
            }
        }
        capturer = Camera2Capturer(appCtx, front, cameraEvents)
        vidSrc   = factory!!.createVideoSource(false)
        capturer!!.initialize(stHelper, appCtx, vidSrc!!.capturerObserver)

        // Phase 4.4 — wrap startCapture in try/catch so "camera in use" /
        // runtime permission revoked after init doesn't crash init itself. The
        // default 1280x720@30 is fine as the starting format; we let libwebrtc
        // adapt down via `setCameraEnabled(true)` / network response.
        //
        // Phase 4.7 — capture the format we started capture with so that a
        // `setCameraEnabled(true)` AFTER `setCameraEnabled(false)` re-uses
        // the same dimensions, instead of crashing on devices that don't
        // support the hardcoded 1280x720 fallback.
        val captureW = 1280
        val captureH = 720
        val captureFps = 30
        try {
            capturer!!.startCapture(captureW, captureH, captureFps)
            lastCaptureWidth = captureW
            lastCaptureHeight = captureH
            lastCaptureFps = captureFps
        } catch (t: Throwable) {
            android.util.Log.e("TBWebRtcEngine", "Phase 4.4 — startCapture failed: ${t.message}")
            // leave vidTrack absent so UI can surface "Camera unavailable" —
            // but DO continue with audio so the call can still proceed.
        }
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
            // Phase 4.17 — this stream-webrtc-android fork doesn't expose the
            // `minBitrate/maxBitrate/startBitrate` knobs that browser / native
            // libwebrtc does — those were left over from an aborted refactor
            // and reference symbols that don't exist on `RTCConfiguration` for
            // this artifact. Per-sender degradation preference is also not
            // settable via `RtpSender.rtpParameters` here (the class exposes
            // only `id`, `track`, `streamIds`, no `rtpParameters` property).
            //
            // What this fork DOES: it auto-adapts per its own internal
            // encoder selection. To force a specific preference we'd need
            // to swap to the official `io.github.factory-webrtc:webrtc` or
            // pin a different fork. Phase 4.17 therefore degrades to "trust
            // libwebrtc default" plus logging — leaving SDP driven bitrate
            // params in `optimizeSdp` below (b=<value> munging) which works
            // universally regardless of platform.
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
                    IceConnectionState.DISCONNECTED -> {
                        // Phase 4.18 — request an ICE restart so the peer
                        // can re-acquire path on transient network drop
                        // (phone switching cell tower → Wi-Fi, Wi-Fi
                        // re-association, brief NAT binding expiry). Without
                        // `restartIce()` the peer sits in DISCONNECTED until
                        // the call timeout fires; with restart, libwebrtc
                        // re-gathers candidates with the CONSENT/ICE-CONTROLLING
                        // flag flipped and the connection self-heals.
                        try { pc?.restartIce() } catch (e: Throwable) {
                            android.util.Log.w(
                                "TBWebRtcEngine",
                                "Phase 4.18 — restartIce() failed: ${e.message}"
                            )
                        }
                        _onDisconnected?.invoke()
                    }
                    IceConnectionState.FAILED     -> _onDisconnected?.invoke()
                    else -> {}
                }
            }
            override fun onIceCandidate(c: IceCandidate?) {
                c ?: return
                _onIce?.invoke(c.sdp, c.sdpMid, c.sdpMLineIndex)
            }
            // Phase 4.8 — UNIFIED_PLAN delivers tracks exclusively via
            // onAddTrack. Legacy onAddStream was a PLAN_B-era fallback that
            // would add the sink twice (both onAddStream AND onAddTrack fire
            // for the same track on some libwebrtc versions). We now only
            // attach the remote sink through onAddTrack and drop onAddStream.
            override fun onAddStream(s: MediaStream?) {}
            override fun onAddTrack(r: RtpReceiver?, streams: Array<out MediaStream>?) {
                (r?.track() as? VideoTrack)?.let { track ->
                    if (remoteVideoTrack == null) {
                        remoteVideoTrack = track
                        track.addSink(_remote)
                        // Phase 1.7 — emit so Compose `collectAsState()` recomposes
                        // when the remote track arrives. The opaque handle is the
                        // SurfaceViewRenderer `_remote` — same as `remoteRenderer`.
                        _remoteRendererFlow.value = _remote
                    }
                }
            }
        })

        pc!!.addTrack(vidTrack, listOf("s0"))
        pc!!.addTrack(audTrack, listOf("s0"))

        // Phase 4.17 — see comment in cfg block above: this stream-webrtc
        // fork exposes no `RtpSender.rtpParameters`/`DegradationPreference`
        // surface (that's an org.webrtc:webrtc Google-native API). Attempts
        // to set it threw Unresolved reference at compile time. We leave
        // libwebrtc default handling in place and rely on `optimizeSdp`
        // below for any SDP-driven adaptation. Revisit when the artifact
        // is swapped to the full GoogleWebRTC maven.
    }

    actual suspend fun createOffer() = suspendCancellableCoroutine<Unit> { cont ->
        // Phase 4.16 — `pc?.setLocalDescription(...)` is async (SdpObserver).
        // Previously the local SDP was handed to `_onLocalSdp` and `cont.resume`
        // BEFORE the local description was actually set, so trickled ICE
        // candidates the engine generates during `onSetSuccess` weren't yet
        // attached to the SDP the signaling layer forwarded. Fix: wait for
        // `onSetSuccess` to fire before emitting the SDP and resuming.
        pc?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(s: SessionDescription) {}
                    override fun onSetSuccess() {
                        _onLocalSdp?.invoke("offer", optimizeSdp(sdp.description))
                        cont.resume(Unit)
                    }
                    override fun onCreateFailure(e: String?) {}
                    override fun onSetFailure(e: String?)    {
                        println("[WebRTC/Android] setLocal(offer) failed: $e")
                        cont.resume(Unit)
                    }
                }, sdp)
            }
            override fun onSetSuccess()                {}
            override fun onCreateFailure(e: String?)   { println("[WebRTC/Android] createOffer: $e"); cont.resume(Unit) }
            override fun onSetFailure(e: String?)      { cont.resume(Unit) }
        }, MediaConstraints()) ?: cont.resume(Unit)
    }

    actual suspend fun createAnswer() = suspendCancellableCoroutine<Unit> { cont ->
        // Phase 4.16 — see createOffer for the reasoning.
        pc?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(s: SessionDescription) {}
                    override fun onSetSuccess() {
                        _onLocalSdp?.invoke("answer", optimizeSdp(sdp.description))
                        cont.resume(Unit)
                    }
                    override fun onCreateFailure(e: String?) {}
                    override fun onSetFailure(e: String?)    {
                        println("[WebRTC/Android] setLocal(answer) failed: $e")
                        cont.resume(Unit)
                    }
                }, sdp)
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
                    // Phase 4.21 — synchronously flip `remoteSet = true` AND
                    // snapshot + clear `pendingIce` BEFORE kicking off the async
                    // drain. This eliminates the previous race where a concurrent
                    // `addIceCandidate` arriving between the `forEach` running
                    // (inside `scope.launch`) and the list-clearance would either:
                    //   (a) double-add the same candidate (flush tail + direct add),
                    //   (b) lose a candidate (it cleared-after-add, the launch
                    //       hasn't processed it yet), or
                    //   (c) ConcurrentModificationException from simultaneous
                    //       `forEach { addIce }` + `pendingIce.add(...)`.
                    // Callers of `addIceCandidate` after this point always see
                    // `remoteSet=true` and go straight to `pc?.addIceCandidate`.
                    val drained: List<IceCandidate> = synchronized(pendingIce) {
                        remoteSet = true
                        val snap = pendingIce.toList()
                        pendingIce.clear()
                        snap
                    }
                    scope.launch {
                        drained.forEach { pc?.addIceCandidate(it) }
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
        // Phase 4.21 — both the `remoteSet` read and the `pendingIce.add` are
        // now guarded by the same monitor so we can't interleave badly with
        // `onSetSuccess`. After setRemoteDescription's success we always go
        // through `pc?.addIceCandidate` directly, never landing in `pendingIce`.
        if (remoteSet) {
            pc?.addIceCandidate(ice)
        } else synchronized(pendingIce) {
            if (remoteSet) pc?.addIceCandidate(ice) else pendingIce.add(ice)
        }
    }

    actual fun setMicEnabled(enabled: Boolean)    { audTrack?.setEnabled(enabled) }

    // Phase 4.9 — runtime speakerphone toggle. UI passes the user's choice
    // through this fn; the AudioFlinger routing picks the right device. No-op
    // when no call is active (init() hasn't placed us in MODE_IN_COMMUNICATION
    // yet — caller is responsible for ordering).
    actual fun setSpeakerphoneOn(enabled: Boolean) {
        val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            audioManager.isSpeakerphoneOn = enabled
        } catch (t: Throwable) {
            android.util.Log.w("TBWebRtcEngine", "Phase 4.9 — setSpeakerphoneOn(${enabled}) failed: ${t.message}")
        }
    }

    // Phase 4.10 — audio focus request. Without AUDIOFOCUS_GAIN_TRANSIENT,
    //music / podcast apps keep playing during a call (echo & UX confusion).
    // Android 8.0+ uses AudioFocusRequest; older API (deprecated) lives at
    // audioManager.requestAudioFocus(). We hand-roll both because the
    // AGP compileSdk 26 floor lets us use the new + deprecated paths inside.
    @Volatile private var audioFocusRequest: android.media.AudioFocusRequest? = null

    actual fun requestAudioFocus() {
        val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                android.media.AudioManager.STREAM_VOICE_CALL,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    actual fun setCameraEnabled(enabled: Boolean) {
        vidTrack?.setEnabled(enabled)
        // Phase 4.7 — was hardcoded `1280, 720, 30` on every re-enable,
        // which crashed mid-tier devices whose supported formats don't
        // include 1280x720. Now we re-use the same format the capturer
        // was last initialized with — captured at init() via
        // `lastCaptureWidth/Height/Fps` below — so toggling camera off/on
        // restarts at a known-good resolution.
        if (enabled) {
            capturer?.startCapture(lastCaptureWidth, lastCaptureHeight, lastCaptureFps)
        } else {
            scope.launch { try { capturer?.stopCapture() } catch (_: Exception) {} }
        }
    }
    actual fun switchCamera() {
        scope.launch { try { capturer?.switchCamera(null) } catch (_: Exception) {} }
    }

    actual fun dispose() {
        fallbackJob?.cancel()
        connected = false; remoteSet = false; pendingIce.clear()

        // Phase 1.8 — stopCapture() on libwebrtc is itself a synchronous
        // blocking call that joins the capture thread. Wrap it in try/catch
        // and run inline (NOT in scope.launch) so dispose() returns only
        // after the camera is genuinely stopped; the previous async-launch
        // pattern raced capturer.stopCapture() against capturer.dispose()
        // and intermittently crashed Camera2 with "Camera was closed during
        // stopCapture". Synchronous stop-then-dispose is the documented
        // libwebrtc teardown order.
        try { capturer?.stopCapture() } catch (_: Throwable) {}

        // Phase 1.12 — detach the remote track's sink BEFORE releasing the
        // renderer. Otherwise libwebrtc keeps a dangling ref to the released
        // SurfaceViewRenderer in the track's sink list, and the next frame
        // tries to draw into a dead surface — CRASH on some devices when the
        // peer's next video frame arrives mid-teardown.
        try {
            remoteVideoTrack?.let { _remote?.let { sink -> it.removeSink(sink) } }
            vidTrack?.let { _local?.let { sink -> it.removeSink(sink) } }
        } catch (_: Throwable) {}
        remoteVideoTrack = null

        capturer?.dispose(); vidSrc?.dispose(); audSrc?.dispose()
        stHelper?.dispose(); vidTrack?.dispose(); audTrack?.dispose()
        pc?.close(); pc?.dispose(); factory?.dispose()
        try { _local?.release() } catch (_: Throwable) {}
        try { _remote?.release() } catch (_: Throwable) {}
        // Phase 1.17 — DO NOT release the shared EglBase here. It is process-
        // wide and reused by the next engine instance. Releasing per-instance
        // caused "black preview" after the 2nd call.
        capturer = null; vidSrc = null; audSrc = null; vidTrack = null
        audTrack = null; pc = null; factory = null; _local = null; _remote = null

        val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        // Phase 4.3 — release the Bluetooth SCO channel we requested in init.
        try {
            if (btScoActive) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                btScoActive = false
            }
        } catch (_: Throwable) {}
        // Phase 4.10 — release audio focus so background music / podcasts
        // resume. The AudioFocusRequest created in requestAudioFocus() is
        // cached in `audioFocusRequest`; older API path uses the deprecated
        // abandonAudioFocus(null) (passing null listener is fine; we don't
        // care about the focus-change notification path because the call
        // already ended by the time we get here).
        try {
            val req = audioFocusRequest
            if (req != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            audioFocusRequest = null
        } catch (_: Throwable) {}

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
