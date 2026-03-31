package com.hiralen.temubelajar.home.component

import com.arkivanov.decompose.ComponentContext
import com.hiralen.temubelajar.core.domain.AccountRepository
import com.hiralen.temubelajar.core.presentation.BASE_WS_URL
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.koin.mp.KoinPlatform

enum class MatchingStatus { IDLE, SEARCHING, FOUND, ERROR }

data class HomeState(
    val status: MatchingStatus = MatchingStatus.IDLE,
    val queueSize: Int = 0,
    val queuePosition: Int = 0,
    val error: String? = null,
    val userEmail: String = "",
    val userUniversity: String? = null,
    val isCameraReady: Boolean = false
)

class HomeComponent(
    componentContext: ComponentContext,
    val onMatchFound: (pairId: String, role: String, peerEmail: String, peerUniversity: String) -> Unit,
    val onLogout: () -> Unit
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val httpClient: HttpClient = KoinPlatform.getKoin().get()
    private val repository: AccountRepository = KoinPlatform.getKoin().get()

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var wsSession: DefaultClientWebSocketSession? = null
    private var ref = 1

    val webRtcManager = com.hiralen.temubelajar.videochat.webrtc.WebRtcManager()

    init {
        loadUserInfo()
        initCamera()
    }

    private fun initCamera() {
        scope.launch {
            webRtcManager.initialize(
                isOffer = true,
                onLocalSdpReady = { _, _ -> },
                onIceCandidateReady = { _, _, _ -> },
                onConnected = {},
                onDisconnected = {}
            )
            _state.value = _state.value.copy(isCameraReady = true)
        }
    }

    private fun loadUserInfo() {
        scope.launch {
            val token = repository.getToken() ?: return@launch
            when (val result = repository.me(token)) {
                is com.hiralen.temubelajar.core.domain.Result.Success<*> -> {
                    val me = (result as com.hiralen.temubelajar.core.domain.Result.Success<com.hiralen.temubelajar.core.domain.MeResponse>).data
                    _state.value = _state.value.copy(
                        userEmail = me.email,
                        userUniversity = me.university
                    )
                }
                else -> {}
            }
        }
    }

    fun startMatching() {
        scope.launch {
            val token = repository.getToken() ?: run {
                _state.value = _state.value.copy(error = "Silakan login ulang")
                return@launch
            }
            val university = _state.value.userUniversity

            _state.value = _state.value.copy(status = MatchingStatus.SEARCHING, error = null)

            try {
                println("[HomeComponent] Connecting to WebSocket: $BASE_WS_URL&token=$token")
                httpClient.webSocket("$BASE_WS_URL&token=$token") {
                    wsSession = this
                    println("[HomeComponent] WebSocket connected!")

                    // Step 1: Join the Phoenix channel (required before sending any events)
                    println("[HomeComponent] Joining matchmaking lobby...")
                    sendPhoenixMsg("matchmaking:lobby", "phx_join", buildJsonObject {})

                    // Step 2: Send join_queue with university for smart matching
                    sendPhoenixMsg("matchmaking:lobby", "join_queue", buildJsonObject {
                        if (university != null) put("university", university)
                    })

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        handleMessage(text)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _state.value = _state.value.copy(
                        status = MatchingStatus.ERROR,
                        error = "Koneksi terputus: ${e.message}"
                    )
                }
            }
        }
    }

    fun stopMatching() {
        scope.launch {
            try {
                wsSession?.let {
                    sendPhoenixMsg("matchmaking:lobby", "leave_queue", buildJsonObject {})
                    it.close()
                }
            } catch (_: Exception) {}
            wsSession = null
            _state.value = _state.value.copy(
                status = MatchingStatus.IDLE,
                queuePosition = 0
            )
        }
    }

    private suspend fun handleMessage(text: String) {
        println("[HomeComponent] Received message: $text")
        try {
            // Phoenix sends frames as JSON arrays: [join_ref, ref, topic, event, payload]
            val arr = Json.parseToJsonElement(text).jsonArray
            val event   = arr[3].jsonPrimitive.content
            val payload = arr[4].jsonObject

            when (event) {
                "match_found" -> {
                    val pairId    = payload["pair_id"]?.jsonPrimitive?.content ?: return
                    val role      = payload["role"]?.jsonPrimitive?.content ?: "caller"
                    val peerEmail = payload["peer_email"]?.jsonPrimitive?.content ?: ""
                    val peerUni   = payload["peer_university"]?.jsonPrimitive?.content ?: ""
                    _state.value  = _state.value.copy(status = MatchingStatus.FOUND)

                    delay(500)
                    wsSession?.close()
                    wsSession = null
                    withContext(Dispatchers.Main) {
                        onMatchFound(pairId, role, peerEmail, peerUni)
                    }
                }
                "queue_stats", "update" -> {
                    val size = payload["queue_size"]?.jsonPrimitive?.int ?: 0
                    _state.value = _state.value.copy(queueSize = size)
                }
                "phx_reply" -> {
                    val response = payload["response"]?.jsonObject
                    val status = payload["status"]?.jsonPrimitive?.content
                    if (status == "ok") {
                        val position = response?.get("position")?.jsonPrimitive?.int
                        if (position != null) {
                            _state.value = _state.value.copy(queuePosition = position)
                        }
                    }
                }
                "queue_timeout" -> {
                    _state.value = _state.value.copy(
                        status = MatchingStatus.IDLE,
                        error = "Waktu mencari habis. Coba lagi."
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun sendPhoenixMsg(topic: String, event: String, payload: JsonObject) {
        val currentRef = (ref++).toString()
        // Phoenix protocol: send as array [join_ref, ref, topic, event, payload]
        // join_ref is the same as ref for join events, null for others
        val joinRef = if (event == "phx_join") currentRef else null
        val msg = buildJsonArray {
            if (joinRef != null) add(joinRef) else add(JsonNull)
            add(currentRef)
            add(topic)
            add(event)
            add(payload)
        }.toString()
        wsSession?.send(msg)
    }

    fun logout() {
        scope.launch {
            stopMatching()
            val token = repository.getToken()
            if (token != null) {
                repository.logout(token)
            }
            repository.clearToken()
            onLogout()
        }
    }

    fun onDestroy() {
        scope.cancel()
    }
}
