package com.hiralen.temubelajar.videochat.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
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
import kotlin.time.Instant

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
    // Phase 5.19 — chat input lives in component state (not screen-local) so
    // `nextPerson()` can clear it atomically with the message list. Otherwise
    // the previous peer's drafted text survives into the new call's first Enter.
    val chatInput: String = "",
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
    // Phase 1.1 / 1.2 — a separate scope used during destruction so WebSocket
    // `.close()` (which suspends) completes even after the main `scope` is
    // cancelled. runBlocking can't be used because wasmJs is single-threaded.
    private val cleanupScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val httpClient: HttpClient = KoinPlatform.getKoin().get()
    private val repository: AccountRepository = KoinPlatform.getKoin().get()
    // Phase 1.4 — share the WebRtcManager with HomeComponent via Koin so the
    // engine + camera capture aren't double-initialized when Home → VideoChat
    // transition happens. The Koin `single { WebRtcManager() }` declaration
    // lives in `coreModule`. The idempotent initializer (1.4) on the manager
    // also means that even if Home ever DID initialize it, VideoChat's call
    // to `initialize()` here is a no-op.
    val webRtcManager: WebRtcManager = KoinPlatform.getKoin().get()

    private val _state = MutableStateFlow(
        VideoChatState(peerName = peerName, peerUniversity = peerUniversity)
    )
    val state: StateFlow<VideoChatState> = _state.asStateFlow()

    // Typing debounce job (auto-clears local "peer is typing..." indicator)
    private var typingClearJob: Job? = null
    // Phase 5.22 — debounce for OUTGOING typing notifications. Only forward
    // the next "typing" frame to the chatSession if at least 250ms have
    // passed since the previous send; otherwise a fresh keystroke resets the
    // window so the chat channel never sees one frame per character.
    private var typingSendDebounceJob: Job? = null
    private var lastTypingSendMs: Long = 0L

    // Separate WS sessions
    private var signalingSession: DefaultClientWebSocketSession? = null
    private var chatSession: DefaultClientWebSocketSession? = null
    private var timerJob: Job? = null
    // Phase 5.7 — replace the 5s force-isConnected fallback with an 8s
    // watchdog that surfaces a real error if the engine never fires its
    // `onConnected` callback. Cancelled as soon as `onConnected` toggles
    // `isConnected = true`.
    private var connectionWatchdogJob: Job? = null
    // Ref counter — incremented from Main dispatcher only (both coroutines use Dispatchers.Main)
    private var ref = 1

    init {
        connectSignaling()
        connectChat()

        // Phase 1.1 / 1.2 — generate the destroy event via Decompose instead of
        // relying on someone to call onDestroy(). The cleanup runs SYNCHRONOUSLY
        // (on the lifecycle owner's thread) before the coroutine scope is
        // cancelled; otherwise the launched coroutine gets cancelled before
        // `cleanUp()` ever executes — leaking `webRtcManager`, both websockets,
        // and AudioManager state.
        lifecycle.doOnDestroy {
            // Run the suspend cleanup on a coroutine whose scope is *separate*
            // from `scope` so cancelling `scope` after this block returns does
            // not abort the WS close handshake. We then cancel `cleanupScope`
            // so the process-level coroutine goes away too.
            cleanupScope.launch {
                try { cleanUp() } catch (_: Throwable) {}
            }
            // Tear down the main scope immediately so signalling/chat frame
            // loops exit even if cleanup itself is still flushing.
            scope.cancel()
        }
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
                    // Phase 5.7 — cancel the 8s connect watchdog as soon as the
                    // engine confirms ICE/SDP completed.
                    connectionWatchdogJob?.cancel()
                    connectionWatchdogJob = null
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
                            // Phase 5.7 — do NOT force isConnected=true after a
                            // short delay. The real `onConnected` engine callback
                            // (above, in `webRtcManager.initialize` → onConnected)
                            // is the only legitimate way to flip this UI state.
                            // Instead, set up an 8s watchdog: if ICE/SDP never
                            // hits CONNECTED by then, surface an explicit error
                            // so the user is told "no peer video" rather than
                            // seeing a hung black screen.
                            connectionWatchdog()
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
                        // Phase 5.7 — see peer_joined branch: replace the force-
                        // isConnected fallback with an 8s watchdog that emits
                        // an explicit error if the engine never fires onConnected.
                        connectionWatchdog()
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
                    val ts = parseTimestamp(payload) ?: currentTimeMs()
                    addMessage(ChatMessage(text = msgText, fromSelf = false, timestampMs = ts))
                    cancelTypingIndicator()
                }
                "emoji" -> {
                    val emoji = payload["emoji"]?.jsonPrimitive?.content ?: return
                    val ts = parseTimestamp(payload) ?: currentTimeMs()
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
            unreadCount = 0,
            // Phase 5.19 — also wipe any drafted text so it can't leak into
            // the next peer's conversation after `nextPerson()`.
            chatInput = ""
        )
        // Cancel any in-flight typing debounce so we don't emit a frame into
        // the freshly-reset chatSession after clear.
        typingSendDebounceJob?.cancel()
        typingSendDebounceJob = null
    }

    // ── Public chat actions ─────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val ts = currentTimeMs()
        // Add to own list immediately (optimistic)
        addMessage(ChatMessage(text = text, fromSelf = true, timestampMs = ts))
        scope.launch {
            // Phase 3.17 — wire timestamp is ISO 8601 UTC (consistent with
            // Ecto `:utc_datetime`); internal state keeps epoch millis.
            sendPhoenixMsg(chatSession, "chat:$pairId", "msg", buildJsonObject {
                put("text", text)
                put("timestamp", iso8601Now())
            })
        }
    }

    /**
     * Phase 5.19 — bound to the chat field's `onValueChange`. Persists the
     * current draft into [VideoChatState.chatInput] so it survives screen
     * recompositions and is reset atomically when `nextPerson()`/`clearChat()`
     * runs. Each keystroke also triggers the debounced typing notification.
     */
    fun onChatInputChange(text: String) {
        _state.value = _state.value.copy(chatInput = text)
        notifyTyping()
    }

    /**
     * Phase 5.19 — send the current draft and clear the field. Replaces the
     * screen-local `chatInput = ""` reset that previously leaked across peers.
     */
    fun sendCurrentChat() {
        val text = _state.value.chatInput
        if (text.isBlank()) return
        sendMessage(text)
        _state.value = _state.value.copy(chatInput = "")
    }

    /**
     * Phase 5.38 — was: every emoji-tap sent the emoji as a standalone
     * `type=EMOJI` chat message, dismissing the picker immediately. That
     * made it impossible to type a sentence that contained an emoji (you'd
     * tap the picker, your "👍 great work" arrived in two messages: "👍"
     * as a big-emoji bubble, then "great work" as text after you re-opened
     * the keyboard). Tapping an emoji now APPENDS it to `chatInput` so the
     * user can keep typing, send combined text+emoji, or — if the input is
     * empty and that's the only intended content — just hit send to fire a
     * standalone emoji (preserving the big-emoji UI affordance that the
     * wire format + `ChatMessage.Type.EMOJI` rendering exists to support).
     *
     * The picker is no longer auto-closed so the user can pick several emojis
     * in sequence; closing is left to the toggle button / send action.
     */
    fun sendEmoji(emoji: String) {
        _state.value = _state.value.copy(chatInput = _state.value.chatInput + emoji)
    }

    /**
     * Phase 5.22 — debounced outgoing "typing" notification. Honours a 250ms
     * minimum spacing between successive `typing` frames so the chat channel
     * is never flooded with one frame per keystroke. Calls within the window
     * reset the pending send (the gap restarts), preserving the "user is
     * actively typing" signal without a per-keystroke frame blast.
     */
    fun notifyTyping() {
        val now = currentTimeMs()
        val remaining = TYPING_DEBOUNCE_MS - (now - lastTypingSendMs)
        if (remaining <= 0L) {
            // Window already elapsed — fire immediately and stamp the last send.
            lastTypingSendMs = now
            typingSendDebounceJob?.cancel()
            typingSendDebounceJob = null
            scope.launch {
                sendPhoenixMsg(chatSession, "chat:$pairId", "typing", buildJsonObject {})
            }
        } else {
            // Inside the debounce window — reschedule a single send for the
            // remainder so a fresh keystroke resets the gap.
            typingSendDebounceJob?.cancel()
            typingSendDebounceJob = scope.launch {
                delay(remaining)
                lastTypingSendMs = currentTimeMs()
                sendPhoenixMsg(chatSession, "chat:$pairId", "typing", buildJsonObject {})
            }
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
        // Phase 5.36 — Soft haptic on rapid-toggle controls so a
        // mic/camera/speaker sequence doesn't buzz into a single drone.
        com.hiralen.temubelajar.core.ui.platformHapticSoft()
    }

    fun toggleCamera() {
        val muted = !_state.value.isCameraMuted
        _state.value = _state.value.copy(isCameraMuted = muted)
        webRtcManager.setCameraEnabled(!muted)
        com.hiralen.temubelajar.core.ui.platformHapticSoft()
    }

    fun switchCamera() {
        _state.value = _state.value.copy(isFrontCamera = !_state.value.isFrontCamera)
        webRtcManager.switchCamera()
        com.hiralen.temubelajar.core.ui.platformHapticClick()
    }

    // ── Session control ─────────────────────────────────────────────────────

    fun endSession() {
        // Phase 5.36 — warning haptic for the destructive end-call action.
        com.hiralen.temubelajar.core.ui.platformHapticWarning()
        scope.launch {
            sendSignaling("signaling:$pairId", "leave", buildJsonObject {})
            sendPhoenixMsg(chatSession, "chat:$pairId", "leave", buildJsonObject {})
            cleanUp()
            onBack()
        }
    }

    fun nextPerson() {
        // Phase 5.36 — click haptic; skipping to a new peer is meaningful
        // but not destructive.
        com.hiralen.temubelajar.core.ui.platformHapticClick()
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

    /**
     * Phase 5.7 — fire-and-forget 8-second watchdog that emits the in-call
     * "Tidak ada video dari lawan" error if the WebRTC engine never reports
     * `onConnected`. Kills the prior watchdog first so SDP-offer-then-answer
     * flows don't stack duplicate timers. Cleared by `onConnected` (above).
     */
    private fun connectionWatchdog() {
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (!_state.value.isConnected) {
                _state.value = _state.value.copy(error = "Tidak ada video dari lawan")
            }
        }
    }

    private suspend fun cleanUp() {
        stopTimer()
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = null
        typingSendDebounceJob?.cancel()
        typingSendDebounceJob = null
        typingClearJob?.cancel()
        typingClearJob = null
        try { signalingSession?.close() } catch (_: Exception) {}
        try { chatSession?.close() } catch (_: Exception) {}
        signalingSession = null
        chatSession = null
        webRtcManager.dispose()
    }

    @OptIn(ExperimentalTime::class)
    private fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()

    /**
     * Phase 3.17 — parse a `timestamp` field that may be either an ISO 8601
     * string (the new format; matches Ecto's `:utc_datetime` columns) or a
     * raw epoch-millis Long (the old format emitted by pre-3.17 backends).
     * Returns `null` for missing/blank/unparseable values so callers can
     * fall back to [currentTimeMs].
     */
    private fun parseTimestamp(payload: JsonObject): Long? {
        val el = payload["timestamp"] ?: return null
        if (el !is JsonPrimitive) return null
        // New format: ISO 8601 string ("2024-12-31T15:30:00.123Z").
        if (el.isString) {
            val s = el.content
            if (s.isBlank()) return null
            return runCatching {
                Instant.parse(s).toEpochMilliseconds()
            }.getOrNull()
        }
        // Old format: epoch millis Long.
        return el.longOrNull
    }

    /**
     * Phase 3.17 — emit an ISO 8601 UTC timestamp (millisecond-precise) for
     * outgoing chat/emoji frames, matching the format the backend now
     * broadcasts. Internal state keeps the existing Long `ChatMessage.timestampMs`
     * (used for LazyList keys / monotonic ordering); only the wire format
     * changes.
     */
    private fun iso8601Now(): String =
        Instant.fromEpochMilliseconds(currentTimeMs()).toString()

    private companion object {
        // Phase 5.22 — minimum spacing between consecutive outgoing `typing`
        // frames (milliseconds). 250ms per the audit spec.
        const val TYPING_DEBOUNCE_MS = 250L
        // Phase 5.7 — how long we wait for the engine's `onConnected` callback
        // before reporting an explicit "no peer video" error.
        const val CONNECTION_TIMEOUT_MS = 8_000L
    }
}
