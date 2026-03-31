package com.hiralen.temubelajar.webrtc

import kotlin.js.Promise

// ── RTCPeerConnection ─────────────────────────────────────────────────────────

@JsName("RTCPeerConnection")
external class JsPeerConnection(config: JsAny) : JsAny {
    var onicecandidate:          ((JsAny) -> Unit)?
    var ontrack:                 ((JsAny) -> Unit)?
    var onconnectionstatechange: (() -> Unit)?
    fun close()
}

@JsName("MediaStream")
external class JsMediaStream : JsAny

@JsName("MediaStreamTrack")
external class JsMediaStreamTrack : JsAny

// ── RTCPeerConnection operations ──────────────────────────────────────────────

@JsFun("(pc) => pc.createOffer()")
external fun pcCreateOffer(pc: JsPeerConnection): Promise<JsAny>

@JsFun("(pc) => pc.createAnswer()")
external fun pcCreateAnswer(pc: JsPeerConnection): Promise<JsAny>

@JsFun("(pc, d) => pc.setLocalDescription(d).then(() => null)")
external fun pcSetLocal(pc: JsPeerConnection, desc: JsAny): Promise<JsAny?>

@JsFun("(pc, d) => pc.setRemoteDescription(d).then(() => null)")
external fun pcSetRemote(pc: JsPeerConnection, desc: JsAny): Promise<JsAny?>

@JsFun("(pc, ice) => pc.addIceCandidate(ice).then(() => null)")
external fun pcAddIce(pc: JsPeerConnection, ice: JsAny): Promise<JsAny?>

@JsFun("(pc, track, stream) => pc.addTrack(track, stream)")
external fun pcAddTrack(pc: JsPeerConnection, track: JsAny, stream: JsAny)

@JsFun("(pc) => pc.connectionState")
external fun pcConnState(pc: JsPeerConnection): String

// ── Object builders ───────────────────────────────────────────────────────────

@JsFun("() => ({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }, { urls: 'stun:stun1.l.google.com:19302' }, { urls: 'stun:stun.cloudflare.com:3478' }] })")
external fun buildRtcConfig(): JsAny

@JsFun("(type, sdp) => ({ type, sdp })")
external fun buildSdpInit(type: String, sdp: String): JsAny

@JsFun("(c, mid, idx) => ({ candidate: c, sdpMid: mid, sdpMLineIndex: idx })")
external fun buildIceInit(c: String, mid: String, idx: Int): JsAny

// ── SDP / ICE accessors ───────────────────────────────────────────────────────

@JsFun("(d) => d.sdp")
external fun getSdp(d: JsAny): String

@JsFun("(e) => e.candidate ? e.candidate.candidate : null")
external fun getIceSdp(e: JsAny): String?

@JsFun("(e) => e.candidate ? e.candidate.sdpMid : null")
external fun getIceMid(e: JsAny): String?

@JsFun("(e) => e.candidate ? (e.candidate.sdpMLineIndex || 0) : 0")
external fun getIceIdx(e: JsAny): Int

@JsFun("(e) => e.track")
external fun getEventTrack(e: JsAny): JsMediaStreamTrack?

@JsFun("(t) => t.kind")
external fun getTrackKind(t: JsMediaStreamTrack): String

// ── MediaStream helpers ───────────────────────────────────────────────────────

@JsFun("() => new MediaStream()")
external fun newMediaStream(): JsMediaStream

@JsFun("(s, t) => s.addTrack(t)")
external fun streamAddTrack(s: JsMediaStream, t: JsAny)

@JsFun("(s) => s.getTracks()")
external fun streamTracks(s: JsMediaStream): JsAny

@JsFun("(s) => s.getVideoTracks()")
external fun streamVideoTracks(s: JsMediaStream): JsAny

@JsFun("(s) => s.getAudioTracks()")
external fun streamAudioTracks(s: JsMediaStream): JsAny

@JsFun("(arr) => arr.length")
external fun arrLen(arr: JsAny): Int

@JsFun("(arr, i) => arr[i]")
external fun arrGet(arr: JsAny, i: Int): JsMediaStreamTrack?

@JsFun("(t, en) => { t.enabled = en; }")
external fun trackSetEnabled(t: JsAny, en: Boolean)

@JsFun("(t) => t.stop()")
external fun trackStop(t: JsAny)

// ── getUserMedia ──────────────────────────────────────────────────────────────

@JsFun("""() => navigator.mediaDevices.getUserMedia({ 
    video: { width: { ideal: 640 }, height: { ideal: 480 }, frameRate: { ideal: 30 } }, 
    audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
        googAutoGainControl2: true,
        googEchoCancellation: true,
        googAutoGainControl: true,
        googNoiseSuppression: true,
        googHighpassFilter: true,
        channelCount: 1
    } 
})""")
external fun getUserMedia(): Promise<JsMediaStream>

/**
 * Read window.__tbStream synchronously — set by index.html before Kotlin loads.
 * No await, no coroutine, no freeze.
 */
@JsFun("() => window.__tbStream || null")
external fun getPreAcquiredStream(): JsMediaStream?

// ── Global stream cache ───────────────────────────────────────────────────────
var cachedLocalStream: JsMediaStream? = null


@JsFun("(fn) => fn()")
external fun callFn(fn: JsAny)

// ── New Canvas-based Video Capturer ───────────────────────────────────────────

@JsFun("""(stream, muted) => {
    const video = document.createElement('video');
    video.autoplay    = true;
    video.playsInline = true;
    video.muted       = !!muted;
    
    // Use visibility:hidden; some browsers pause/throttle video elements with display:none.
    video.style.position = 'fixed';
    video.style.pointerEvents = 'none';
    video.style.visibility = 'hidden';
    video.style.width = '1px';
    video.style.height = '1px';
    
    video.srcObject   = stream;
    document.body.appendChild(video);
    video.play().catch(() => {});
    
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    
    return { video, canvas, ctx };
}""")
external fun createVideoCapturer(stream: JsMediaStream, muted: Boolean): JsAny

@JsFun("""(capturer) => {
    const { video, canvas, ctx } = capturer;
    if (video.videoWidth === 0 || video.readyState < 2) return null;
    
    if (canvas.width !== video.videoWidth || canvas.height !== video.videoHeight) {
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
    }
    
    ctx.drawImage(video, 0, 0);
    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    // Return Int32Array (each element is one ARGB pixel)
    return new Int32Array(imageData.data.buffer);
}""")
external fun captureVideoFrameInts(capturer: JsAny): JsAny?

@JsFun("(buffer, i) => buffer[i]")
external fun getJsBufferInt(buffer: JsAny, i: Int): Int

@JsFun("(buffer) => buffer.length")
external fun getJsBufferLength(buffer: JsAny): Int

@JsFun("""(capturer) => ({ width: capturer.video.videoWidth, height: capturer.video.videoHeight })""")
external fun getVideoDimensions(capturer: JsAny): JsAny

@JsFun("(dim) => dim.width")
external fun getDimWidth(dim: JsAny): Int

@JsFun("(dim) => dim.height")
external fun getDimHeight(dim: JsAny): Int

@JsFun("""(capturer) => {
    if (!capturer) return;
    try {
        capturer.video.srcObject = null;
        capturer.video.pause();
        capturer.video.remove();
    } catch(e) {}
}""")
external fun stopVideoCapturer(capturer: JsAny)
