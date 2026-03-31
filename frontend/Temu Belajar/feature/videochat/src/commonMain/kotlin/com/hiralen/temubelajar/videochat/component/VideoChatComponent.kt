package com.hiralen.temubelajar.videochat.component

import com.arkivanov.decompose.ComponentContext
import com.hiralen.temubelajar.core.domain.AccountRepository
import com.hiralen.temubelajar.core.presentation.BASE_WS_URL
import com.hiralen.temubelajar.videochat.webrtc.WebRtcManager
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import org.koin.mp.KoinPlatform

// ─── Chat Models ──────────────────────────────────────────────────────────────

enum class ChatMessageTypeStub { TEXT, STICKER }

data class ChatMessageStub(
    val sender: String,            // email or "me"
    val content: String,           // text body or sticker id (emoji)
    val type: ChatMessageTypeStub,
    val timestamp: Long = currentTimeMillis()
)

/**
 * Sticker pack — simple emoji-based stickers.
 * Displayed in a bottom sheet when the user taps the sticker button.
 */
val STICKER_PACK = listOf(
    "👋", "😄", "🎉", "❤️", "😂",
    "👍", "🔥", "😍", "😭", "🤔",
    "💯", "🙏"
)

// ─── Video Chat State ─────────────────────────────────────────────────────────

data class VideoChatStateStub(
    // ── Connection / media ──────────────────────────────────────────────────
    val isConnected: Boolean = false,
    val isMicMuted: Boolean = false,
    val isCameraMuted: Boolean = false,
    val isFrontCamera: Boolean = true,
    val durationSeconds: Int = 0,
    val error: String? = null,
    val peerLeft: Boolean = false,

    // ── Chat panel ──────────────────────────────────────────────────────────
    val isChatOpen: Boolean = false,
    val chatMessages: List<ChatMessageStub> = emptyList(),
    val chatInput: String = "",

    // ── Sticker sheet ───────────────────────────────────────────────────────
    val isStickerSheetOpen: Boolean = false
)

// ─── Component ────────────────────────────────────────────────────────────────

class VideoChatComponentStub(
    componentContext: ComponentContext,
    val pairId: String,
    val role: String,
    val peerEmail: String,
    val onBack: () -> Unit,
    val onNext: () -> Unit
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val httpClient: HttpClient = KoinPlatform.getKoin().get()
    private val repository: AccountRepository = KoinPlatform.getKoin().get()

    val webRtcManager: WebRtcManager = WebRtcManager()

    private val _state = MutableStateFlow(VideoChatStateStub())
    val state: StateFlow<VideoChatStateStub> = _state.asStateFlow()

    private var wsSession: DefaultClientWebSocketSession? = null
    private var timerJob: Job? = null
    private var ref = 1

    init {
        connectAndInitialize()
    }

    // ─── Initialization ──────────────────────────────────────────────────────

    private fun connectAndInitialize() {
        scope.launch {
            val token = repository.getToken() ?: run {
                _state.value = _state.value.copy(error = "Token tidak ditemukan")
                return@launch
            }

            // Init WebRTC engine
            webRtcManager.initialize(
                isOffer = role == "caller",
                onLocalSdpReady = { type, sdp -> sendSdp(type, sdp) },
                onIceCandidateReady = { candidate, sdpMid, sdpMLineIndex ->
                    sendIce(candidate, sdpMid, sdpMLineIndex)
                },
                onConnected = {
                    _state.value = _state.value.copy(isConnected = true)
                    startTimer()
                },
                onDisconnected = {
                    _state.value = _state.value.copy(isConnected = false)
                }
            )

            // Connect signaling WebSocket
            try {
                httpClient.webSocket("$BASE_WS_URL&token=$token") {
                    wsSession = this

                    // Join signaling channel for this pair
                    sendPhoenixMsg("signaling:$pairId", "phx_join", buildJsonObject {})
                    // Join chat channel for this pair
                    sendPhoenixMsg("chat:$pairId", "phx_join", buildJsonObject {})

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        handleIncomingMessage(frame.readText())
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _state.value = _state.value.copy(error = "Sinyal terputus: ${e.message}")
                }
            }
        }
    }

    // ─── Incoming message handler ───────────────────────────────────────────

    private suspend fun handleIncomingMessage(text: String) {
        try {
            val obj = Json.parseToJsonElement(text).jsonObject
            val topic = obj["topic"]?.jsonPrimitive?.content ?: ""
            val event = obj["event"]?.jsonPrimitive?.content ?: ""
            val payload = obj["payload"]?.jsonObject ?: buildJsonObject {}
            
            if (event == "phx_reply" || event == "phx_error") return

            when {
                topic.startsWith("signaling:") -> handleSignalingEvent(event, payload)
                topic.startsWith("chat:") -> handleChatEvent(event, payload)
            }
        } catch (_: Exception) {}
    }

    private suspend fun handleSignalingEvent(event: String, payload: JsonObject) {
        when (event) {
            "ice_servers" -> {
                // Server-pushed STUN/TURN config — currently ignored (hardcoded in WebRtcManager)
            }

            "peer_joined" -> {
                    if (role == "caller") webRtcManager.createOffer()
                }

                "offer" -> {
                    val sdp = payload["sdp"]?.jsonPrimitive?.content ?: return
                    webRtcManager.setRemoteDescription("offer", sdp)
                    webRtcManager.createAnswer()
                }

                "answer" -> {
                    val sdp = payload["sdp"]?.jsonPrimitive?.content ?: return
                    webRtcManager.setRemoteDescription("answer", sdp)
                }

                "ice_candidate" -> {
                    val candidate = payload["candidate"]?.jsonPrimitive?.content ?: return
                    val sdpMid = payload["sdp_mid"]?.jsonPrimitive?.content
                    val sdpMLineIndex = payload["sdp_m_line_index"]?.jsonPrimitive?.int ?: 0
                    webRtcManager.addIceCandidate(candidate, sdpMid, sdpMLineIndex)
                }

                "renegotiate" -> {
                    val sdp = payload["sdp"]?.jsonPrimitive?.content ?: return
                    webRtcManager.setRemoteDescription("offer", sdp)
                    webRtcManager.createAnswer()
                }

            "peer_left" -> {
                _state.value = _state.value.copy(peerLeft = true)
                stopTimer()
            }
        }
    }

    private fun handleChatEvent(event: String, payload: JsonObject) { // ── Incoming chat messages from peer ──────────────────────
        when (event) {
            "chat_message" -> {
                val message = payload["message"]?.jsonPrimitive?.content ?: return
                appendIncomingChat(
                    ChatMessageStub(
                        sender = peerEmail,
                        content = message,
                        type = ChatMessageTypeStub.TEXT
                    )
                )
            }
            "sticker" -> {
                val stickerId = payload["sticker_id"]?.jsonPrimitive?.content ?: return
                appendIncomingChat(
                    ChatMessageStub(
                        sender = peerEmail,
                        content = stickerId,
                        type = ChatMessageTypeStub.STICKER
                    )
                )
            }
            "chat_reset" -> {
                _state.value = _state.value.copy(chatMessages = emptyList())
            }
        }
    }

    // ─── Chat actions ─────────────────────────────────────────────────────────

    /** Open / close the slide-in chat panel. */
    fun toggleChat() {
        _state.value = _state.value.copy(
            isChatOpen = !_state.value.isChatOpen,
            isStickerSheetOpen = false   // always close sticker sheet when toggling chat
        )
    }

    /** Open / close the sticker bottom-sheet. */
    fun toggleStickerSheet() {
        _state.value = _state.value.copy(
            isStickerSheetOpen = !_state.value.isStickerSheetOpen
        )
    }

    fun onChatInputChange(text: String) {
        _state.value = _state.value.copy(chatInput = text)
    }

    /** Send a plain-text chat message. */
    fun sendTextMessage() {
        val text = _state.value.chatInput.trim()
        if (text.isEmpty()) return

        scope.launch {
            sendPhoenixMsg(
                "chat:$pairId",
                "msg",
                buildJsonObject {
                    put("text", text)
                }
            )
        }

        _state.value = _state.value.copy(
            chatMessages = _state.value.chatMessages + ChatMessageStub(
                sender = "me",
                content = text,
                type = ChatMessageTypeStub.TEXT
            ),
            chatInput = ""
        )
    }

    /** Send an emoji sticker. stickerId is one of [STICKER_PACK]. */
    fun sendSticker(stickerId: String) {
        scope.launch {
            sendPhoenixMsg(
                "chat:$pairId",
                "emoji",
                buildJsonObject { put("emoji", stickerId) }
            )
        }

        _state.value = _state.value.copy(
            chatMessages = _state.value.chatMessages + ChatMessageStub(
                sender = "me",
                content = stickerId,
                type = ChatMessageTypeStub.STICKER
            ),
            isStickerSheetOpen = false
        )
    }

    private fun appendIncomingChat(message: ChatMessageStub) {
        _state.value = _state.value.copy(
            chatMessages = _state.value.chatMessages + message,
            // Auto-open chat panel if it's currently closed and the peer sends a message
            isChatOpen = if (_state.value.isChatOpen) true else true
        )
    }

    // ─── WebRTC control ──────────────────────────────────────────────────────

    fun toggleMic() {
        val muted = !_state.value.isMicMuted
        _state.value = _state.value.copy(isMicMuted = muted)
        webRtcManager.setMicEnabled(!muted)
    }

    fun toggleCamera() {
        val muted = !_state.value.isCameraMuted
        _state.value = _state.value.copy(isCameraMuted = muted)
        webRtcManager.setCameraEnabled(!muted)
    }

    fun switchCamera() {
        _state.value = _state.value.copy(isFrontCamera = !_state.value.isFrontCamera)
        webRtcManager.switchCamera()
    }

    // ─── Session management ──────────────────────────────────────────────────

    fun endSession() {
        scope.launch {
            sendPhoenixMsg("signaling:$pairId", "session_end", buildJsonObject {})
            cleanUp()
            onBack()
        }
    }

    fun nextPerson() {
        scope.launch {
            sendPhoenixMsg("signaling:$pairId", "session_end", buildJsonObject {})
            cleanUp()
            onNext()
        }
    }

    // ─── Signaling helpers ───────────────────────────────────────────────────

    private fun sendSdp(type: String, sdp: String) {
        scope.launch {
            sendPhoenixMsg(
                "signaling:$pairId",
                type,
                buildJsonObject { put("sdp", sdp) }
            )
        }
    }

    private fun sendIce(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        scope.launch {
            sendPhoenixMsg(
                "signaling:$pairId",
                "ice_candidate",
                buildJsonObject {
                    put("candidate", candidate)
                    sdpMid?.let { put("sdp_mid", it) }
                    put("sdp_m_line_index", sdpMLineIndex)
                }
            )
        }
    }

    private suspend fun sendPhoenixMsg(topic: String, event: String, payload: JsonObject) {
        val msg = buildJsonObject {
            put("topic", topic)
            put("event", event)
            put("payload", payload)
            put("ref", (ref++).toString())
        }.toString()
        try {
            wsSession?.send(msg)
        } catch (_: Exception) {
        }
    }

    // ─── Timer ───────────────────────────────────────────────────────────────

    private fun startTimer() {
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                _state.value = _state.value.copy(
                    durationSeconds = _state.value.durationSeconds + 1
                )
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    private suspend fun cleanUp() {
        stopTimer()
        try {
            wsSession?.close()
        } catch (_: Exception) {
        }
        wsSession = null
        webRtcManager.dispose()
    }

    fun onDestroy() {
        scope.launch { cleanUp() }
        scope.cancel()
    }
}

// ─── Platform time helper ─────────────────────────────────────────────────────

/** Returns current epoch-millis. Implemented differently per platform (expect/actual not needed
 *  here — kotlinx-datetime or a simple wrapper works). We use a simple inline approach. */
internal fun currentTimeMillis(): Long = 0L
