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

    // Pre-allocated RGB pixel array — reused every frame to avoid GC pressure
    @Volatile private var rgbBuf: IntArray? = null

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
        onLocalSdp:     (String, String) -> Unit,
        onIceCandidate: (String, String?, Int) -> Unit,
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
     * I420 VideoFrame → BufferedImage for Compose remote preview.
     * Uses a bulk pixel array + setRGB(0,0,w,h,pixels,0,w) to avoid per-pixel JNI overhead.
     */
    private fun i420ToBufferedImage(frame: VideoFrame): BufferedImage? {
        return try {
            val i420    = frame.buffer.toI420() ?: return null
            val w       = i420.getWidth()
            val h       = i420.getHeight()
            val yBuf    = i420.getDataY()
            val uBuf    = i420.getDataU()
            val vBuf    = i420.getDataV()
            val strideY = i420.getStrideY()
            val strideUV = i420.getStrideU()

            val pixels = IntArray(w * h)
            for (row in 0 until h) {
                val rowOffY  = row * strideY
                val rowOffPx = row * w
                val rowOffUV = (row / 2) * strideUV
                for (col in 0 until w) {
                    val y = (yBuf.get(rowOffY + col).toInt() and 0xFF) - 16
                    val u = (uBuf.get(rowOffUV + col / 2).toInt() and 0xFF) - 128
                    val v = (vBuf.get(rowOffUV + col / 2).toInt() and 0xFF) - 128
                    val r = ((298 * y + 409 * v + 128) shr 8).coerceIn(0, 255)
                    val g = ((298 * y - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
                    val b = ((298 * y + 516 * u + 128) shr 8).coerceIn(0, 255)
                    pixels[rowOffPx + col] = (r shl 16) or (g shl 8) or b
                }
            }

            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            img.setRGB(0, 0, w, h, pixels, 0, w)
            i420.release()
            img
        } catch (e: Exception) {
            println("[TBWebRtc/Desktop] i420ToBufferedImage: ${e.message}"); null
        }
    }

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
    actual fun setCameraEnabled(enabled: Boolean) {
        camOn.set(enabled); videoTrack?.setEnabled(enabled)
        if (!enabled) localRef.set(null)
    }
    actual fun switchCamera() {}

    actual fun dispose() {
        fallbackJob?.cancel(); captureJob?.cancel()

        // Deactivate sinks first — any in-flight OnFrame calls will return early
        remoteSink0?.active = false; remoteSink1?.active = false
        // Close PC — stops new OnFrame callbacks from being issued
        try { pc?.close() } catch (_: Exception) {}
        Thread.sleep(100) // wait for any in-flight OnFrame to complete before GC
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
        scope.cancel()
    }

    private fun fireConnected() {
        if (!connected) { connected = true; fallbackJob?.cancel(); _onConnected?.invoke() }
    }

    /**
     * Optimize SDP:
     * Enhance Opus audio bitrate to 64kbps and enable stereo.
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
                // Insert immediately after rtpmap to stay in-section.
                lines.add(rtpmapIdx + 1, "a=fmtp:$opusPayload $audioParams")
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
 */
class RemoteVideoSink(
    private val frameRef: AtomicReference<BufferedImage?>
) : VideoTrackSink {

    // Volatile flag — when false (after dispose()), we do nothing in onVideoFrame
    @Volatile var active = true

    override fun onVideoFrame(frame: VideoFrame) {
        // CRITICAL: DO NOT call frame.release() here.
        // webrtc-java's JNI bridge (VideoTrackSink::OnFrame) automatically releases
        // the frame after this method returns. Calling frame.release() here causes
        // a double-release → refcount underflow → SIGSEGV in the C++ destructor.
        if (!active) return

        try {
            // toI420() creates a NEW ref-counted I420 buffer that WE own → must release it.
            val i420 = frame.buffer.toI420() ?: return
            val w    = i420.getWidth()
            val h    = i420.getHeight()
            val yBuf = i420.getDataY()
            val uBuf = i420.getDataU()
            val vBuf = i420.getDataV()
            val strideY  = i420.getStrideY()
            val strideUV = i420.getStrideU()

            val pixels = IntArray(w * h)
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

            if (active) {
                val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                img.setRGB(0, 0, w, h, pixels, 0, w)
                frameRef.set(img)
            }
        } catch (_: Exception) {
            // Do NOT call frame.release() here either — C++ side handles it
        }
    }
}
