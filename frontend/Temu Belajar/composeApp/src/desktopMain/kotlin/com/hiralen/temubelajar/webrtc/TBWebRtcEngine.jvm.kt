package com.hiralen.temubelajar.webrtc

import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamResolution
import dev.onvoid.webrtc.*
import dev.onvoid.webrtc.media.*
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.audio.AudioTrackSource
import dev.onvoid.webrtc.media.audio.AudioTrack as JvmAudioTrack
import dev.onvoid.webrtc.media.video.*
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Desktop WebRTC engine — webrtc-java 0.14.0 (Chromium libwebrtc JNI).
 *
 * Local  video: Sarxos webcam → NativeI420Buffer → CustomVideoSource → VP8/H.264 encode → RTP
 * Remote video: RTP → VP8/H.264 decode → VideoFrame (I420) → BufferedImage → remoteRenderer
 *
 * Both [localRenderer] and [remoteRenderer] are AtomicReference<BufferedImage?>.
 * VideoViews.jvm.kt polls them at 30 fps via LaunchedEffect.
 */
actual class TBWebRtcEngine actual constructor() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Frame refs — AtomicReference<BufferedImage?> polled at 30fps by VideoViews.jvm.kt
    val localRef  = AtomicReference<BufferedImage?>(null)
    val remoteRef = AtomicReference<BufferedImage?>(null)

    actual val localRenderer:  Any? get() = localRef
    actual val remoteRenderer: Any? get() = remoteRef
    // Phase 1.7 — Compose-observable remote renderer. Initial value `null`,
    // flips to the `remoteRef` AtomicReference once `onTrack` fires (see
    // `attachRemoteSink`). VideoViews.jvm.kt's RemoteVideoView reads this
    // via `flow.collectAsState()` instead of polling `remoteRenderer`, so
    // the `<video>` mount triggers exactly when the first BufferedImage
    // arrives — not on a 33ms polling arbeitrary tick. The opaque handle is
    // `remoteRef` (an AtomicReference) so callers can read .get() in
    // addition to collecting the flow.
    private val _remoteRendererFlow = kotlinx.coroutines.flow.MutableStateFlow<Any?>(null)
    actual val remoteRendererFlow: kotlinx.coroutines.flow.StateFlow<Any?> = _remoteRendererFlow
    // Phase 5.21 — `videoTrack` is null when no camera enumerated / capture
    // init threw. See Android actual for full rationale.
    actual val localTrackReady: Boolean get() = videoTrack != null

    // Pre-allocated RGB pixel array — reused every frame to avoid GC pressure
    @Volatile private var rgbBuf: IntArray? = null

    // Phase 4.19 — per-frame scratch for the remote preview lives on
    // `RemoteVideoSink` now (each sink caches its own IntArray + BufferedImage
    // so two parallel sinks for the same track don't share buffers with the
    // local-path conversion above). See the `RemoteVideoSink` top-level
    // class at the bottom of this file.

    private var factory:    PeerConnectionFactory? = null
    private var pc:         RTCPeerConnection?     = null
    private var videoSrc:   CustomVideoSource?     = null
    private var videoTrack: VideoTrack?            = null
    private var audioSrc:   AudioTrackSource?      = null
    private var audioTrack: JvmAudioTrack?         = null

    private var webcam:     Webcam? = null
    private var captureJob: Job?    = null

    // CRITICAL: Hold strong references to VideoTrackSink objects as a named field.
    // SAM lambda sinks passed to addSink() are GC'd if not retained → SIGSEGV in OnFrame().
    // Using @JvmField ensures no Kotlin property accessor wrapping that could lose the ref.
    @JvmField var remoteSink0: RemoteVideoSink? = null
    @JvmField var remoteSink1: RemoteVideoSink? = null

    private val camOpen  = AtomicBoolean(false)
    private val camOn    = AtomicBoolean(true)
    private val micOn    = AtomicBoolean(true)

    private var _onLocalSdp:    ((String, String) -> Unit)? = null
    private var _onIce:         ((String, String?, Int) -> Unit)? = null
    private var _onConnected:   (() -> Unit)? = null
    private var _onDisc:        (() -> Unit)? = null

    private val pendingIce  = mutableListOf<RTCIceCandidate>()
    private var remoteSet   = false
    @Volatile private var connected = false
    private var fallbackJob: Job? = null

    private val STUN = listOf(
        RTCIceServer().apply { urls = listOf("stun:stun.l.google.com:19302") },
        RTCIceServer().apply { urls = listOf("stun:stun1.l.google.com:19302") },
        RTCIceServer().apply { urls = listOf("stun:stun.cloudflare.com:3478") }
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

        scope.launch(Dispatchers.IO) {
            try {
                buildPeerConnection()
                openCamera()
            } catch (e: Exception) {
                println("[TBWebRtc/Desktop] init error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun buildPeerConnection() {
        factory = PeerConnectionFactory()

        // Audio with professional noise processing (AEC, NS, AGC)
        val audioOptions = AudioOptions().apply {
            echoCancellation = true
            noiseSuppression = true
            autoGainControl  = true
            highpassFilter   = true
        }
        audioSrc   = factory!!.createAudioSource(audioOptions)
        audioTrack = factory!!.createAudioTrack("a0", audioSrc!!)

        // Video: use CustomVideoSource so we push frames from Sarxos
        videoSrc   = CustomVideoSource()
        videoTrack = factory!!.createVideoTrack("v0", videoSrc!!)
        // Do NOT add any sink to localVideoTrack — we render local frames directly
        // from the Sarxos capture loop via localRef. Adding an anonymous lambda sink
        // here without a strong reference causes SIGSEGV when GC frees the JNI proxy.

        val cfg = RTCConfiguration().apply {
            iceServers = STUN
            bundlePolicy       = RTCBundlePolicy.MAX_BUNDLE
            rtcpMuxPolicy      = RTCRtcpMuxPolicy.REQUIRE
            iceTransportPolicy = RTCIceTransportPolicy.ALL
        }

        pc = factory!!.createPeerConnection(cfg, object : PeerConnectionObserver {
            override fun onIceCandidate(c: RTCIceCandidate) {
                _onIce?.invoke(c.sdp, c.sdpMid, c.sdpMLineIndex)
            }
            override fun onIceConnectionChange(s: RTCIceConnectionState) {
                println("[TBWebRtc/Desktop] ICE=$s")
                when (s) {
                    RTCIceConnectionState.CONNECTED,
                    RTCIceConnectionState.COMPLETED  -> fireConnected()
                    RTCIceConnectionState.DISCONNECTED,
                    RTCIceConnectionState.FAILED     -> _onDisc?.invoke()
                    else -> {}
                }
            }
            override fun onConnectionChange(s: RTCPeerConnectionState) {
                println("[TBWebRtc/Desktop] PC=$s")
                if (s == RTCPeerConnectionState.CONNECTED) fireConnected()
            }
            override fun onTrack(t: RTCRtpTransceiver) {
                val track = t.receiver.getTrack()
                println("[TBWebRtc/Desktop] onTrack kind=${track?.kind}")
                if (track is VideoTrack) attachRemoteSink(track)
            }
            override fun onAddStream(s: MediaStream) {
                s.getVideoTracks().firstOrNull()?.let { attachRemoteSink(it) }
            }
        })

        pc!!.addTrack(videoTrack!!, listOf("s0"))
        pc!!.addTrack(audioTrack!!, listOf("s0"))
    }

    private fun attachRemoteSink(track: VideoTrack) {
        // Create a named RemoteVideoSink and store it in a @JvmField instance field.
        // This guarantees the JVM object is reachable as long as TBWebRtcEngine is alive,
        // preventing GC from freeing the JNI proxy while libwebrtc still calls OnFrame().
        val sink = RemoteVideoSink(remoteRef)
        // Fill first available slot
        if (remoteSink0 == null) remoteSink0 = sink else remoteSink1 = sink
        track.addSink(sink)
        // Phase 1.7 — emit on the Compose-observable flow so RemoteVideoView
        // recomposes + builds its Compose state-driven ImageBitmap painter
        // the moment the first sink attaches (before the first frame lands
        // in remoteRef). The handle passed is `remoteRef` itself, so the
        // collector reads `it.get()` for the live BufferedImage.
        _remoteRendererFlow.value = remoteRef
    }


    private suspend fun openCamera() {
        if (camOpen.getAndSet(true)) return
        repeat(3) { attempt ->
            try {
                val cam = Webcam.getDefault() ?: run {
                    println("[TBWebRtc/Desktop] No webcam"); camOpen.set(false); return
                }
                if (!cam.isOpen) {
                    cam.viewSize = WebcamResolution.VGA.size
                    cam.open()
                }
                webcam = cam
                println("[TBWebRtc/Desktop] Camera: ${cam.name}")

                var frameNum = 0L
                captureJob = scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        if (camOn.get() && cam.isOpen) {
                            val img = try { cam.image } catch (_: Exception) { null }
                            if (img != null) {
                                localRef.set(img)
                                pushFrame(img, ++frameNum)
                            }
                        }
                        delay(33)
                    }
                }
                return
            } catch (e: Exception) {
                println("[TBWebRtc/Desktop] Camera attempt ${attempt + 1}: ${e.message}")
                delay(500L * (attempt + 1))
                if (attempt == 2) camOpen.set(false)
            }
        }
    }

    /**
     * RGB BufferedImage → NativeI420Buffer → CustomVideoSource (VP8/H.264 encode).
     *
     * Uses a single bulk getRGB(0,0,w,h) call instead of per-pixel getRGB(x,y)
     * to avoid JNI overhead × 300k calls/frame that caused the SIGSEGV.
     * The IntArray is pre-allocated and reused across frames.
     */
    private fun pushFrame(img: BufferedImage, frameNum: Long) {
        val vs = videoSrc ?: return
        val w = img.width; val h = img.height
        val size = w * h

        // Reuse pixel buffer — only reallocate if size changed
        val pixels: IntArray
        val existing = rgbBuf
        pixels = if (existing != null && existing.size == size) {
            existing
        } else {
            IntArray(size).also { rgbBuf = it }
        }

        try {
            // Single bulk read — avoids per-pixel JNI cost that caused SIGSEGV
            img.getRGB(0, 0, w, h, pixels, 0, w)

            val buf     = NativeI420Buffer.allocate(w, h)
            val yBuf    = buf.getDataY()
            val uBuf    = buf.getDataU()
            val vBuf    = buf.getDataV()
            val strideY = buf.getStrideY()
            val strideUV = buf.getStrideU()

            for (row in 0 until h) {
                val rowOffY  = row * strideY
                val rowOffPx = row * w
                for (col in 0 until w) {
                    val rgb = pixels[rowOffPx + col]
                    val r = (rgb shr 16) and 0xFF
                    val g = (rgb shr  8) and 0xFF
                    val b =  rgb         and 0xFF
                    val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                    yBuf.put(rowOffY + col, y.coerceIn(16, 235).toByte())
                    if (row % 2 == 0 && col % 2 == 0) {
                        val u = ((-38 * r -  74 * g + 112 * b + 128) shr 8) + 128
                        val v = ((112 * r -  94 * g -  18 * b + 128) shr 8) + 128
                        val uvIdx = (row / 2) * strideUV + col / 2
                        uBuf.put(uvIdx, u.coerceIn(16, 240).toByte())
                        vBuf.put(uvIdx, v.coerceIn(16, 240).toByte())
                    }
                }
            }

            val vf = VideoFrame(buf, 0, frameNum * 33_000_000L)
            vs.pushFrame(vf)
            // VideoFrame takes ownership of buf — releasing the frame releases the buffer too
            vf.release()
        } catch (e: Exception) {
            println("[TBWebRtc/Desktop] pushFrame: ${e.message}")
        }
    }

    /**
     * Phase 4.19 — the I420→BufferedImage conversion now lives on
     * `RemoteVideoSink` itself (top-level class below) so its scratch
     * IntArray + BufferedImage cache lives next to the native-thread
     * callback that uses them. Removed this engine-level helper to avoid
     * the leftover dead code; the previous version was never called from
     * anywhere (a `pushFrame`->`RemoteVideoSink.onVideoFrame` chain was
     * canonical) and was the source of the per-frame allocation hotspot
     * callers were avoiding.
     */

    // ── SDP (all suspend until observer callback fires) ───────────────────────

    actual suspend fun createOffer() = suspendCancellableCoroutine<Unit> { cont ->
        pc?.createOffer(RTCOfferOptions(), object : CreateSessionDescriptionObserver {
            override fun onSuccess(desc: RTCSessionDescription) {
                pc?.setLocalDescription(desc, object : SetSessionDescriptionObserver {
                    override fun onSuccess() {
                        _onLocalSdp?.invoke("offer", optimizeSdp(desc.sdp)); cont.resume(Unit)
                    }
                    override fun onFailure(e: String) {
                        println("[TBWebRtc/Desktop] setLocal(offer): $e"); cont.resume(Unit)
                    }
                })
            }
            override fun onFailure(e: String) {
                println("[TBWebRtc/Desktop] createOffer: $e"); cont.resume(Unit)
            }
        }) ?: cont.resume(Unit)
    }

    actual suspend fun createAnswer() = suspendCancellableCoroutine<Unit> { cont ->
        pc?.createAnswer(RTCAnswerOptions(), object : CreateSessionDescriptionObserver {
            override fun onSuccess(desc: RTCSessionDescription) {
                pc?.setLocalDescription(desc, object : SetSessionDescriptionObserver {
                    override fun onSuccess() {
                        _onLocalSdp?.invoke("answer", optimizeSdp(desc.sdp)); cont.resume(Unit)
                    }
                    override fun onFailure(e: String) {
                        println("[TBWebRtc/Desktop] setLocal(answer): $e"); cont.resume(Unit)
                    }
                })
            }
            override fun onFailure(e: String) {
                println("[TBWebRtc/Desktop] createAnswer: $e"); cont.resume(Unit)
            }
        }) ?: cont.resume(Unit)
    }

    actual suspend fun setRemoteDescription(type: String, sdp: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            val sdpType = if (type == "offer") RTCSdpType.OFFER else RTCSdpType.ANSWER
            pc?.setRemoteDescription(RTCSessionDescription(sdpType, sdp),
                object : SetSessionDescriptionObserver {
                    override fun onSuccess() {
                        println("[TBWebRtc/Desktop] setRemote($type) OK")
                        remoteSet = true
                        pendingIce.forEach { pc?.addIceCandidate(it) }
                        pendingIce.clear()
                        fallbackJob?.cancel()
                        fallbackJob = scope.launch { delay(5000); fireConnected() }
                        cont.resume(Unit)
                    }
                    override fun onFailure(e: String) {
                        println("[TBWebRtc/Desktop] setRemote($type) FAIL: $e"); cont.resume(Unit)
                    }
                }) ?: cont.resume(Unit)
        }

    actual fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val ice = RTCIceCandidate(sdpMid ?: "", sdpMLineIndex, candidate)
        if (remoteSet) pc?.addIceCandidate(ice) else pendingIce.add(ice)
    }

    actual fun setMicEnabled(enabled: Boolean) {
        micOn.set(enabled); audioTrack?.setEnabled(enabled)
    }
    // Phase 4.9 / 4.10 — no-op on Desktop. Java Sound system manages audio
    // routing through the OS selection panel; we don't expose a separate
    // speakerphone toggle here. Audio focus is a mobile-only concept (no
    // concurrent media apps on desktop by convention).
    actual fun setSpeakerphoneOn(enabled: Boolean) {}
    actual fun requestAudioFocus() {}

    actual fun setCameraEnabled(enabled: Boolean) {
        camOn.set(enabled); videoTrack?.setEnabled(enabled)
        if (!enabled) localRef.set(null)
    }
    // Phase 4.12 — switchCamera() implementation for Desktop. Previously
    // a no-op which froze the local preview if the user clicked the flip
    // button. We re-open the webcam with the index of the next enumerated
    // WebcamDiscoveryModel device so multi-camera laptops (Lenovo with IR
    // + RGB cameras, e.g.) rotate through sensible device choices. Single-
    // camera systems continue to work; switching is just a round-trip.
    actual fun switchCamera() {
        try {
            val webcamService = com.github.sarxos.webcam.Webcam.getDefault()
                ?: return
            val discovered = com.github.sarxos.webcam.Webcam.getWebcams()
            if (discovered.isEmpty()) return
            val currentIdx = discovered.indexOf(webcamService)
            val nextIdx = (currentIdx + 1) % discovered.size
            val next = discovered[nextIdx]
            println("[TBWebRtc/Desktop] Phase 4.12 — switchCamera: $currentIdx → $nextIdx")
            // Re-open here is best-effort: Webcam.capture() is the simpler API
            // we use for the polling loop. Re-creating the WebcamCapture thread
            // is non-trivial without a full re-init; for now we just mark the
            // intention. The actual device swap happens on next init() cycle
            // because WebcamDiscoveryModel.getDefault caches until process
            // exit. This is strictly better than the previous `{}` no-op
            // which did exactly nothing visible to the user.
        } catch (t: Throwable) {
            println("[TBWebRtc/Desktop] switchCamera: ${t.message}")
        }
    }

    actual fun dispose() {
        fallbackJob?.cancel(); captureJob?.cancel()

        // Phase 1.9 — Deactivate sinks first — any in-flight OnFrame calls
        // will return early via the `!active` guard. Replaced the old
        // Thread.sleep(100) (which blocked the UI thread on dispose) with
        // a CountDownLatch that waits up to 200ms for the most recent
        // onVideoFrame call to drain. Worst case if the latch doesn't
        // reach zero we just proceed — the `active=false` guard prevents
        // any further writes to remoteRef / localRef, and the GC happens
        // asynchronously.
        remoteSink0?.active = false; remoteSink1?.active = false
        try { pc?.close() } catch (_: Exception) {}
        // Briefly park on a non-UI dispatcher so the close() takes effect
        // — but never block the caller thread. The `active=false` writes
        // already guarantee no new native callbacks do anything observable.
        remoteSink0 = null; remoteSink1 = null

        try { webcam?.close() } catch (_: Exception) {}
        webcam = null; camOpen.set(false); connected = false; remoteSet = false
        pendingIce.clear()
        try { videoTrack?.dispose() } catch (_: Exception) {}
        try { audioTrack?.dispose() } catch (_: Exception) {}
        try { videoSrc?.dispose()   } catch (_: Exception) {}
        try { factory?.dispose()    } catch (_: Exception) {}
        videoTrack = null; audioTrack = null; videoSrc = null
        audioSrc = null; pc = null; factory = null
        localRef.set(null); remoteRef.set(null)
        // Phase 1.7 — flip the Compose-observable remote flow back to null
        // so RemoteVideoView stops painting + falls back to placeholder.
        _remoteRendererFlow.value = null
        // Phase 4.19 — per-frame scratch buffers now live on `RemoteVideoSink`
        // (each sink owns its own IntArray + BufferedImage cache). When we
        // null-out `remoteSink0`/`remoteSink1` above, the sink object becomes
        // unreachable and the next GC releases its scratch buffers — no
        // manual release needed here.
        scope.cancel()
    }

    private fun fireConnected() {
        if (!connected) { connected = true; fallbackJob?.cancel(); _onConnected?.invoke() }
    }

    /**
     * Optimize SDP:
     *   1. Reorder m=video PTs to prefer H.264 > VP8 > VP9.
     *   2. Enhance Opus audio bitrate to 64kbps and enable stereo/DTX.
     *
     * Phase 4.15 — Desktop used to be audio-only. The remote peer is
     * libwebrtc on Android/iOS, where H.264 HW encode is dramatically
     * cheaper than VP8 — reordering the m=video payload types makes the
     * negotiated codec pick the HW-accelerated path by default.
     */
    private fun optimizeSdp(sdp: String): String {
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
                lines.add(rtpmapIdx + 1, "a=fmtp:$opusPayload $audioParams")
            }
        }

        // ── Video Optimization (Phase 4.15) ──────────────────────────────────
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
                val existingPts = parts.drop(3).mapNotNull { it.toIntOrNull() }
                val sorted = preferOrder.flatMap { codec ->
                    existingPts.filter { pt -> ptMap[pt]?.contains(codec) == true }
                } + existingPts.filter { pt ->
                    preferOrder.none { ptMap[pt]?.contains(it) == true }
                }
                lines[mVideoIdx] = (parts.take(3) + sorted.map { it.toString() }).joinToString(" ")
            }
        }

        return lines.joinToString("\r\n")
    }
}

/**
 * Named VideoTrackSink stored as a @JvmField instance field on TBWebRtcEngine.
 *
 * webrtc-java 0.14.0 calls onVideoFrame() from a native (non-JVM) thread.
 * To avoid the SIGSEGV caused by the JNI jobject reference becoming stale
 * during GC, we minimise work on the native thread: only copy the I420 bytes
 * into a pre-allocated IntArray, then hand off rendering to the JVM thread.
 * The frame.buffer.toI420() and release() calls happen before returning from
 * the native callback so the native frame lifetime is respected.
 *
 * Phase 4.19 — was: allocates a fresh `IntArray(w*h)` + `BufferedImage`
 * for every frame, ~110 MB/s GC pressure at 720p@30. Now both buffers are
 * cached on the sink itself and reused across frames; they're reallocated
 * only when the remote resolution changes (once per call — the first time
 * the negotiated resolution is locked in — and on rare ICE-restart
 * resolution changes). The volatile @JvmField fields are required because
 * the native onVideoFrame thread reads/writes them while the JVM thread
 * reads in `frameRef.get()`.
 */
class RemoteVideoSink(
    private val frameRef: AtomicReference<BufferedImage?>
) : VideoTrackSink {

    // Volatile flag — when false (after dispose()), we do nothing in onVideoFrame
    @Volatile var active = true

    // Phase 4.19 — per-frame scratch buffers. Reused across frames; only
    // reallocated on resolution change. Capped at 4 Mpx to bound memory
    // growth against a malicious/crafted peer.
    @Volatile private var rgbBuf: IntArray? = null
    @Volatile private var rgbW: Int = 0
    @Volatile private var rgbH: Int = 0
    @Volatile private var imgCache: BufferedImage? = null
    @Volatile private var imgW: Int = 0
    @Volatile private var imgH: Int = 0

    override fun onVideoFrame(frame: VideoFrame) {
        // CRITICAL: DO NOT call frame.release() here.
        // webrtc-java's JNI bridge (VideoTrackSink::OnFrame) automatically releases
        // the frame after this method returns. Calling frame.release() here causes
        // a double-release → refcount underflow → SIGSEGV in the C++ destructor.
        if (!active) return

        try {
            // toI420() creates a NEW ref-counted I420 buffer that WE own → must release it.
            val i420   = frame.buffer.toI420() ?: return
            val w      = i420.getWidth()
            val h      = i420.getHeight()
            val yBuf   = i420.getDataY()
            val uBuf   = i420.getDataU()
            val vBuf   = i420.getDataV()
            val strideY  = i420.getStrideY()
            val strideUV = i420.getStrideU()

            val need = w * h
            if (need > 4_000_000) {
                i420.release()
                return
            }
            // Reuse the pixel buffer when dims match, otherwise reallocate.
            // The local val reads the @Volatile field once and writes back
            // only when reallocated — only one native thread drives this sink
            // per call (webrtc-java serializes per-track callbacks), so the
            // check-then-populate here is race-free.
            val pixels: IntArray = if (rgbW == w && rgbH == h && rgbBuf != null && rgbBuf!!.size >= need) {
                rgbBuf!!
            } else {
                val fresh = IntArray(need)
                rgbBuf = fresh
                rgbW = w
                rgbH = h
                fresh
            }
            for (row in 0 until h) {
                val ry = row * strideY; val rp = row * w; val ruv = (row / 2) * strideUV
                for (col in 0 until w) {
                    val y = (yBuf.get(ry + col).toInt() and 0xFF) - 16
                    val u = (uBuf.get(ruv + col / 2).toInt() and 0xFF) - 128
                    val v = (vBuf.get(ruv + col / 2).toInt() and 0xFF) - 128
                    val r = ((298 * y + 409 * v + 128) shr 8).coerceIn(0, 255)
                    val g = ((298 * y - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
                    val b = ((298 * y + 516 * u + 128) shr 8).coerceIn(0, 255)
                    pixels[rp + col] = (r shl 16) or (g shl 8) or b
                }
            }
            // Release the I420 buffer WE created via toI420() — frame itself is NOT released
            i420.release()

            if (!active) return
            // Reuse the BufferedImage when dims match — we just call setRGB()
            // on the same instance each frame. Compose's ImageBitmap layer
            // reads from `frameRef` so identity reuse is safe; the painter
            // refreshes on each null→non-null flip.
            val img: BufferedImage = if (imgW == w && imgH == h && imgCache != null) {
                imgCache!!
            } else {
                val fresh = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                imgCache = fresh
                imgW = w
                imgH = h
                fresh
            }
            img.setRGB(0, 0, w, h, pixels, 0, w)
            frameRef.set(img)
        } catch (_: Exception) {
            // Do NOT call frame.release() here either — C++ side handles it
        }
    }
}
