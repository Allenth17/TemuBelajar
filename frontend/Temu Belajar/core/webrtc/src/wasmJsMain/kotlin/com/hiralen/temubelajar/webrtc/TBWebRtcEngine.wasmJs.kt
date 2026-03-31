package com.hiralen.temubelajar.webrtc

import kotlinx.coroutines.*
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * WASM WebRTC engine — browser-native RTCPeerConnection (VP8/H.264/VP9).
 *
 * Camera is acquired in index.html BEFORE Kotlin loads (pure JS, no coroutines).
 * Stream is stored in window.__tbStream and read synchronously via getPreAcquiredStream().
 * Zero getUserMedia() calls inside Kotlin — zero freezes.
 */
actual class TBWebRtcEngine actual constructor() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var pc:          JsPeerConnection? = null
    private var localStream: JsMediaStream?    = null

    // remoteStream is null until ontrack fires — null triggers placeholder UI,
    // non-null triggers video mount. Using a separate stream-per-call object
    // so the key() in WasmVideoView correctly detects "new stream arrived".
    private val _remoteStream = MutableStateFlow<JsMediaStream?>(null)

    actual val localRenderer:  Any? get() = localStream
    actual val remoteRenderer: Any? get() = _remoteStream.value

    private var _onLocalSdp: ((String, String) -> Unit)? = null
    private var _onIce:      ((String, String?, Int) -> Unit)? = null
    private var _onConn:     (() -> Unit)? = null
    private var _onDisc:     (() -> Unit)? = null

    private val pendingIce = mutableListOf<Triple<String, String?, Int>>()
    private var remoteSet  = false
    private var connected  = false
    private var fallbackJob: Job? = null

    actual fun init(
        onLocalSdp:     (String, String) -> Unit,
        onIceCandidate: (String, String?, Int) -> Unit,
        onConnected:    () -> Unit,
        onDisconnected: () -> Unit
    ) {
        _onLocalSdp = onLocalSdp
        _onIce      = onIceCandidate
        _onConn     = onConnected
        _onDisc     = onDisconnected

        // ── 1. Read pre-acquired stream (SYNCHRONOUS — no await, no freeze) ────
        val stream = cachedLocalStream ?: getPreAcquiredStream()?.also { cachedLocalStream = it }
        localStream = stream
        println("[TBWebRtc/WASM] localStream=${if (stream != null) "ready" else "none (no camera)"}")

        // ── 2. Create RTCPeerConnection (synchronous JS constructor) ───────────
        val conn = JsPeerConnection(buildRtcConfig())
        pc = conn

        conn.onicecandidate = { e ->
            val sdp = getIceSdp(e)
            if (sdp != null) _onIce?.invoke(sdp, getIceMid(e), getIceIdx(e))
        }
        conn.onconnectionstatechange = {
            when (pcConnState(conn)) {
                "connected"                        -> fireConnected()
                "disconnected", "failed", "closed" -> _onDisc?.invoke()
            }
        }

        // ── 3. Wire ontrack — create remoteStream lazily when first track arrives ─
        // remoteStream starts null → RemoteVideoView shows placeholder.
        // When ontrack fires, we create a stream, add all tracks, then set it —
        // this triggers recomposition which mounts the <video> with a live stream.
        conn.ontrack = { e ->
            val t = getEventTrack(e)
            if (t != null) {
                val rs = _remoteStream.value ?: newMediaStream().also { _remoteStream.value = it }
                streamAddTrack(rs, t)
                println("[TBWebRtc/WASM] Remote track added: kind=${getTrackKind(t)}")
            }
        }

        if (stream != null) {
            val tracks = streamTracks(stream)
            for (i in 0 until arrLen(tracks)) {
                val t = arrGet(tracks, i) ?: continue
                pcAddTrack(conn, t, stream)
            }
            println("[TBWebRtc/WASM] ${arrLen(tracks)} tracks added to PC synchronously")
        }
    }

    // ── SDP — these are the ONLY async operations (pure WebRTC protocol, unavoidable) ──

    actual suspend fun createOffer() {
        val conn = pc ?: return
        try {
            val offer: JsAny = pcCreateOffer(conn).await<JsAny>()
            pcSetLocal(conn, offer).await<JsAny?>()
            _onLocalSdp?.invoke("offer", optimizeSdp(getSdp(offer)))
        } catch (e: Throwable) { println("[TBWebRtc/WASM] createOffer: ${e.message}") }
    }

    actual suspend fun createAnswer() {
        val conn = pc ?: return
        try {
            val ans: JsAny = pcCreateAnswer(conn).await<JsAny>()
            pcSetLocal(conn, ans).await<JsAny?>()
            _onLocalSdp?.invoke("answer", optimizeSdp(getSdp(ans)))
        } catch (e: Throwable) { println("[TBWebRtc/WASM] createAnswer: ${e.message}") }
    }

    actual suspend fun setRemoteDescription(type: String, sdp: String) {
        val conn = pc ?: return
        try {
            pcSetRemote(conn, buildSdpInit(type, sdp)).await<JsAny?>()
            remoteSet = true
            pendingIce.forEach { (c, mid, idx) ->
                pcAddIce(conn, buildIceInit(c, mid ?: "", idx)).await<JsAny?>()
            }
            pendingIce.clear()
            fallbackJob?.cancel()
            fallbackJob = scope.launch { delay(5000); fireConnected() }
        } catch (e: Throwable) { println("[TBWebRtc/WASM] setRemote: ${e.message}") }
    }

    actual fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        if (!remoteSet) { pendingIce.add(Triple(candidate, sdpMid, sdpMLineIndex)); return }
        scope.launch {
            try {
                pc?.let { pcAddIce(it, buildIceInit(candidate, sdpMid ?: "", sdpMLineIndex)).await<JsAny?>() }
            } catch (e: Throwable) { println("[TBWebRtc/WASM] addIce: ${e.message}") }
        }
    }

    actual fun setMicEnabled(enabled: Boolean) {
        val tracks = localStream?.let { streamAudioTracks(it) } ?: return
        for (i in 0 until arrLen(tracks)) arrGet(tracks, i)?.let { trackSetEnabled(it, enabled) }
    }

    actual fun setCameraEnabled(enabled: Boolean) {
        val tracks = localStream?.let { streamVideoTracks(it) } ?: return
        for (i in 0 until arrLen(tracks)) arrGet(tracks, i)?.let { trackSetEnabled(it, enabled) }
    }

    actual fun switchCamera() {}

    actual fun dispose() {
        fallbackJob?.cancel()
        pc?.close()
        pc = null; localStream = null; _remoteStream.value = null
        connected = false; remoteSet = false; pendingIce.clear()
        scope.cancel()
    }

    private fun fireConnected() {
        if (!connected) { connected = true; fallbackJob?.cancel(); _onConn?.invoke() }
    }

    /**
     * Optimize SDP:
     * Enhance Opus audio bitrate to 64kbps and enable stereo.
     */
    private fun optimizeSdp(sdp: String): String {
        // Robust line splitting — DO NOT filter blank lines as they are structural markers.
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
