package com.hiralen.temubelajar.videochat.webrtc

import com.hiralen.temubelajar.webrtc.TBWebRtcEngine

/**
 * WebRtcManager — thin wrapper around [TBWebRtcEngine] kept for backwards
 * compatibility with VideoChatComponent.
 *
 * All real WebRTC logic lives in :core:webrtc/TBWebRtcEngine.
 * Per-platform actuals just delegate to TBWebRtcEngine.
 */
class WebRtcManager {

    private val engine = TBWebRtcEngine()

    val localVideoRenderer:  Any? get() = engine.localRenderer
    val remoteVideoRenderer: Any? get() = engine.remoteRenderer

    fun initialize(
        isOffer: Boolean,
        onLocalSdpReady: (type: String, sdp: String) -> Unit,
        onIceCandidateReady: (candidate: String, sdpMid: String?, sdpMLineIndex: Int) -> Unit,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit
    ) = engine.init(onLocalSdpReady, onIceCandidateReady, onConnected, onDisconnected)

    suspend fun createOffer()                                            = engine.createOffer()
    suspend fun createAnswer()                                           = engine.createAnswer()
    suspend fun setRemoteDescription(type: String, sdp: String)         = engine.setRemoteDescription(type, sdp)
    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) =
        engine.addIceCandidate(candidate, sdpMid, sdpMLineIndex)
    fun setMicEnabled(enabled: Boolean)    = engine.setMicEnabled(enabled)
    fun setCameraEnabled(enabled: Boolean) = engine.setCameraEnabled(enabled)
    fun switchCamera()                     = engine.switchCamera()
    fun dispose()                          = engine.dispose()
}
