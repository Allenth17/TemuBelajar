package com.hiralen.temubelajar.videochat.component

import com.arkivanov.decompose.ComponentContext
import com.hiralen.temubelajar.core.domain.AccountRepository
import com.hiralen.temubelajar.core.presentation.BASE_WS_URL
import com.hiralen.temubelajar.videochat.model.ChatMessage
import com.hiralen.temubelajar.videochat.webrtc.WebRtcManager
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.koin.mp.KoinPlatform
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// ─── State ────────────────────────────────────────────────────────────────────

data class VideoChatState(
    val isConnected: Boolean = false,
    val isMicMuted: Boolean = false,
    val isCameraMuted: Boolean = false,
    val isFrontCamera: Boolean = true,
    val durationSeconds: Int = 0,
    val error: String? = null,
    val peerLeft: Boolean = false,
    // Chat
    val messages: List<ChatMessage> = emptyList(),
    val isChatOpen: Boolean = false,
    val isPeerTyping: Boolean = false,
    val isEmojiPickerOpen: Boolean = false,
    val unreadCount: Int = 0,
    // Peer info
    val peerName: String = "",
    val peerUniversity: String = ""
)

// ─── Component ────────────────────────────────────────────────────────────────

class VideoChatComponent(
    componentContext: ComponentContext,
    val pairId: String,
    val role: String,
    val peerEmail: String,
    val peerName: String = "",
    val peerUniversity: String = "",
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onViewProfile: (email: String) -> Unit = {}
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val httpClient: HttpClient = KoinPlatform.getKoin().get()
    private val repository: AccountRepository = KoinPlatform.getKoin().get()
    val webRtcManager: WebRtcManager = WebRtcManager()

    private val _state = MutableStateFlow(
        VideoChatState(peerName = peerName, peerUniversity = peerUniversity)
    )
    val state: StateFlow<VideoChatState> = _state.asStateFlow()

    // Typing debounce job
    private var typingClearJob: Job? = null

    // Separate WS sessions
    private var signalingSession: DefaultClientWebSocketSession? = null
    private var chatSession: DefaultClientWebSocketSession? = null
    private var timerJob: Job? = null
    // Ref counter — incremented from Main dispatcher only (both coroutines use Dispatchers.Main)
    private var ref = 1

    init {
        connectSignaling()
        connectChat()
    }

    // ── Signaling channel ───────────────────────────────────────────────────

    private fun connectSignaling() {
        scope.launch {
            val token = repository.getToken() ?: run {
                _state.value = _state.value.copy(error = "Token tidak ditemukan")
                return@launch
            }

            webRtcManager.initialize(
                isOffer = role == "caller",
                onLocalSdpReady = { type, sdp -> sendSignaling("signaling:$pairId", type, buildJsonObject { put("sdp", sdp) }) },
                onIceCandidateReady = { candidate, sdpMid, sdpMLineIndex ->
                    sendSignaling("signaling:$pairId", "ice_candidate", buildJsonObject {
                        put("candidate", candidate)
                        sdpMid?.let { put("sdp_mid", it) }
                        put("sdp_m_line_index", sdpMLineIndex)
                    })
                },
                onConnected = {
                    // Small delay so Compose finishes any in-progress recomposition
                    // before the large UI transition (connecting → connected) triggers.
                    // This prevents the WASM single-thread event loop from stalling.
                    scope.launch {
                        delay(16) // one 60fps frame
                        _state.value = _state.value.copy(isConnected = true)
                        startTimer()
                    }
                },
                onDisconnected = {
                    _state.value = _state.value.copy(isConnected = false)
                }
            )

            try {
                httpClient.webSocket("$BASE_WS_URL&token=$token") {
                    signalingSession = this
                    sendPhoenixMsg(this, "signaling:$pairId", "phx_join", buildJsonObject {})

                    // Offer is created only in response to "peer_joined" event (see handleSignalingMessage)
                    // This avoids a race condition where we send an offer before the peer has joined

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        handleSignalingMessage(frame.readText())
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _state.value = _state.value.copy(error = "Sinyal terputus: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleSignalingMessage(text: String) {
        try {
            // Phoenix sends frames as JSON arrays: [join_ref, ref, topic, event, payload]
            val arr = Json.parseToJsonElement(text).jsonArray
            val event   = arr[3].jsonPrimitive.content
            val payload = arr[4].jsonObject

            if (event == "phx_reply" || event == "phx_error" || event == "phx_close") return

            when (event) {
                "peer_joined" -> {
                    if (role == "caller") {
                        scope.launch {
                            webRtcManager.createOffer()
                            // Fallback: give real WebRTC 10s to negotiate ICE before forcing UI open
                            delay(5_000)
                            if (!_state.value.isConnected) {
                                println("[VideoChatComponent] Caller fallback: forcing isConnected=true after 5s")
                                _state.value = _state.value.copy(isConnected = true)
                                startTimer()
                            }
                        }
                    }
                }
                "ice_servers" -> {
                    // Server sent STUN config — no action needed, WebRtcManager already
                    // has hardcoded STUN servers baked in for each platform.
                }
                "offer" -> {
                    val sdp = payload["sdp"]?.jsonPrimitive?.content ?: return
                    println("[VideoChatComponent] Received offer, setting remote desc then creating answer")
                    scope.launch {
                        webRtcManager.setRemoteDescription("offer", sdp)
                        webRtcManager.createAnswer()
                        // Fallback: give real WebRTC 10s to reach CONNECTED state
                        delay(5_000)
                        if (!_state.value.isConnected) {
                            println("[VideoChatComponent] Receiver fallback: forcing isConnected=true after 5s")
                            _state.value = _state.value.copy(isConnected = true)
                            startTimer()
                        }
                    }
                }
                "answer" -> {
                    val sdp = payload["sdp"]?.jsonPrimitive?.content ?: return
                    println("[VideoChatComponent] Received answer")
                    scope.launch {
                        webRtcManager.setRemoteDescription("answer", sdp)
                    }
                }
                "ice_candidate" -> {
                    // Gateway relays with snake_case keys matching what sender used
                    val candidate     = payload["candidate"]?.jsonPrimitive?.content ?: return
                    val sdpMid        = payload["sdp_mid"]?.jsonPrimitive?.content
                                     ?: payload["sdpMid"]?.jsonPrimitive?.content
                    val sdpMLineIndex = payload["sdp_m_line_index"]?.jsonPrimitive?.int
                                     ?: payload["sdpMLineIndex"]?.jsonPrimitive?.int
                                     ?: 0
                    println("[VideoChatComponent] ICE candidate received")
                    webRtcManager.addIceCandidate(candidate, sdpMid, sdpMLineIndex)
                }
                "peer_left", "leave" -> {
                    _state.value = _state.value.copy(peerLeft = true)
                    stopTimer()
                }
            }
        } catch (e: Exception) {
            println("[VideoChatComponent] handleSignalingMessage error: ${e.message} | raw: $text")
        }
    }

    // ── Chat channel ────────────────────────────────────────────────────────

    private fun connectChat() {
        scope.launch {
            val token = repository.getToken() ?: return@launch
            try {
                httpClient.webSocket("$BASE_WS_URL&token=$token") {
                    chatSession = this
                    sendPhoenixMsg(this, "chat:$pairId", "phx_join", buildJsonObject {})

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        handleChatMessage(frame.readText())
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun handleChatMessage(text: String) {
        try {
            println("[VideoChatComponent] Chat frame: $text")
            val arr     = Json.parseToJsonElement(text).jsonArray
            val event   = arr[3].jsonPrimitive.content
            val payload = arr[4].jsonObject

            if (event == "phx_reply" || event == "phx_error" || event == "phx_close") return

            when (event) {
                "msg" -> {
                    val msgText = payload["text"]?.jsonPrimitive?.content ?: run {
                        println("[VideoChatComponent] msg missing 'text' key in: $payload")
                        return
                    }
                    val ts = payload["timestamp"]?.jsonPrimitive?.longOrNull ?: currentTimeMs()
                    addMessage(ChatMessage(text = msgText, fromSelf = false, timestampMs = ts))
                    cancelTypingIndicator()
                }
                "emoji" -> {
                    val emoji = payload["emoji"]?.jsonPrimitive?.content ?: return
                    val ts = payload["timestamp"]?.jsonPrimitive?.longOrNull ?: currentTimeMs()
                    addMessage(ChatMessage(text = emoji, emoji = emoji, fromSelf = false, timestampMs = ts, type = ChatMessage.Type.EMOJI))
                }
                "typing"     -> showTypingIndicator()
                "chat_reset" -> clearChat()
            }
        } catch (e: Exception) {
            println("[VideoChatComponent] handleChatMessage error: ${e.message} | raw: $text")
        }
    }

    private fun addMessage(msg: ChatMessage) {
        val current = _state.value
        val newMessages = current.messages + msg
        // Increment unread if chat panel is closed and message is from peer
        val unread = if (!current.isChatOpen && !msg.fromSelf) current.unreadCount + 1 else current.unreadCount
        _state.value = current.copy(messages = newMessages, unreadCount = unread)
    }

    private fun showTypingIndicator() {
        _state.value = _state.value.copy(isPeerTyping = true)
        typingClearJob?.cancel()
        typingClearJob = scope.launch {
            delay(3000)
            _state.value = _state.value.copy(isPeerTyping = false)
        }
    }

    private fun cancelTypingIndicator() {
        typingClearJob?.cancel()
        _state.value = _state.value.copy(isPeerTyping = false)
    }

    fun clearChat() {
        _state.value = _state.value.copy(
            messages = emptyList(),
            isPeerTyping = false,
            unreadCount = 0
        )
    }

    // ── Public chat actions ─────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val ts = currentTimeMs()
        // Add to own list immediately (optimistic)
        addMessage(ChatMessage(text = text, fromSelf = true, timestampMs = ts))
        scope.launch {
            sendPhoenixMsg(chatSession, "chat:$pairId", "msg", buildJsonObject {
                put("text", text)
                put("timestamp", ts)
            })
        }
    }

    fun sendEmoji(emoji: String) {
        val ts = currentTimeMs()
        addMessage(ChatMessage(text = emoji, emoji = emoji, fromSelf = true, timestampMs = ts, type = ChatMessage.Type.EMOJI))
        scope.launch {
            sendPhoenixMsg(chatSession, "chat:$pairId", "emoji", buildJsonObject {
                put("emoji", emoji)
                put("timestamp", ts)
            })
        }
    }

    fun notifyTyping() {
        scope.launch {
            sendPhoenixMsg(chatSession, "chat:$pairId", "typing", buildJsonObject {})
        }
    }

    fun toggleChatPanel() {
        val newIsOpen = !_state.value.isChatOpen
        _state.value = _state.value.copy(
            isChatOpen = newIsOpen,
            unreadCount = if (newIsOpen) 0 else _state.value.unreadCount
        )
    }

    fun toggleEmojiPicker() {
        _state.value = _state.value.copy(isEmojiPickerOpen = !_state.value.isEmojiPickerOpen)
    }

    fun closeEmojiPicker() {
        _state.value = _state.value.copy(isEmojiPickerOpen = false)
    }

    // ── WebRTC controls ─────────────────────────────────────────────────────

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

    // ── Session control ─────────────────────────────────────────────────────

    fun endSession() {
        scope.launch {
            sendSignaling("signaling:$pairId", "leave", buildJsonObject {})
            sendPhoenixMsg(chatSession, "chat:$pairId", "leave", buildJsonObject {})
            cleanUp()
            onBack()
        }
    }

    fun nextPerson() {
        scope.launch {
            // Tell backend to end this pair and reset chat
            sendSignaling("signaling:$pairId", "leave", buildJsonObject {})
            sendPhoenixMsg(chatSession, "chat:$pairId", "leave", buildJsonObject {})
            clearChat()
            cleanUp()
            onNext()
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun sendSignaling(topic: String, event: String, payload: JsonObject) {
        scope.launch {
            sendPhoenixMsg(signalingSession, topic, event, payload)
        }
    }

    private suspend fun sendPhoenixMsg(
        session: DefaultClientWebSocketSession?,
        topic: String,
        event: String,
        payload: JsonObject
    ) {
        val currentRef = (ref++).toString()
        val joinRef = if (event == "phx_join") currentRef else null
        val msg = buildJsonArray {
            if (joinRef != null) add(joinRef) else add(JsonNull)
            add(currentRef)
            add(topic)
            add(event)
            add(payload)
        }.toString()
        try { session?.send(msg) } catch (e: Exception) {
            println("[VideoChatComponent] sendPhoenixMsg error ($event): ${e.message}")
        }
    }

    private fun startTimer() {
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                _state.value = _state.value.copy(durationSeconds = _state.value.durationSeconds + 1)
            }
        }
    }

    private fun stopTimer() { timerJob?.cancel() }

    private suspend fun cleanUp() {
        stopTimer()
        try { signalingSession?.close() } catch (_: Exception) {}
        try { chatSession?.close() } catch (_: Exception) {}
        signalingSession = null
        chatSession = null
        webRtcManager.dispose()
    }

    fun onDestroy() {
        scope.launch { cleanUp() }
        scope.cancel()
    }

    @OptIn(ExperimentalTime::class)
    private fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()
}
