package com.hiralen.temubelajar.home.component

import com.arkivanov.decompose.ComponentContext
import com.hiralen.temubelajar.core.domain.AccountRepository
import com.hiralen.temubelajar.core.domain.Result
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
    val userUniversity: String? = null
)

class HomeComponent(
    componentContext: ComponentContext,
    val onMatchFound: (pairId: String, role: String, peerEmail: String) -> Unit,
    val onLogout: () -> Unit
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val httpClient: HttpClient = KoinPlatform.getKoin().get()
    private val repository: AccountRepository = KoinPlatform.getKoin().get()

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var wsSession: DefaultClientWebSocketSession? = null
    private var ref = 1

    init {
        loadUserInfo()
    }

    private fun loadUserInfo() {
        scope.launch {
            val token = repository.getToken() ?: return@launch
            when (val result = repository.me(token)) {
                is Result.Success<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val me = (result as Result.Success<com.hiralen.temubelajar.core.domain.MeResponse>).data
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
                httpClient.webSocket("$BASE_WS_URL&token=$token") {
                    wsSession = this

                    // Join Phoenix matchmaking channel, send university for smart matching
                    sendPhoenixMsg("matchmaking:lobby", "join_queue", buildJsonObject {
                        if (university != null) put("university", university)
                    })

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        handleMessage(frame.readText())
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
                wsSession?.let { ws ->
                    sendPhoenixMsgTo(ws, "matchmaking:lobby", "leave_queue", buildJsonObject {})
                    ws.close()
                }
            } catch (_: Exception) {
            }
            wsSession = null
            _state.value = HomeState(
                userEmail = _state.value.userEmail,
                userUniversity = _state.value.userUniversity
            )
        }
    }

    private suspend fun handleMessage(text: String) {
        try {
            val arr = Json.parseToJsonElement(text).jsonArray
            val event = arr[3].jsonPrimitive.content
            val payload = arr[4].jsonObject

            when (event) {
                "match_found" -> {
                    val pairId = payload["pair_id"]?.jsonPrimitive?.content ?: return
                    val role = payload["role"]?.jsonPrimitive?.content ?: "caller"
                    val peerEmail = payload["peer_email"]?.jsonPrimitive?.content ?: ""
                    _state.value = _state.value.copy(status = MatchingStatus.FOUND)
                    wsSession?.close()
                    wsSession = null
                    withContext(Dispatchers.Main) {
                        onMatchFound(pairId, role, peerEmail)
                    }
                }

                "queue_stats" -> {
                    val size = payload["queue_size"]?.jsonPrimitive?.int ?: 0
                    _state.value = _state.value.copy(queueSize = size)
                }

                "phx_reply" -> {
                    // Reply from join_queue may contain current queue position
                    val response = payload["response"]?.jsonObject
                    val position = response?.get("position")?.jsonPrimitive?.int
                    if (position != null) {
                        _state.value = _state.value.copy(queuePosition = position)
                    }
                }

                "queue_timeout" -> {
                    _state.value = _state.value.copy(
                        status = MatchingStatus.IDLE,
                        error = "Waktu mencari habis. Coba lagi."
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun sendPhoenixMsg(topic: String, event: String, payload: JsonObject) {
        wsSession?.also { ws -> sendPhoenixMsgTo(ws, topic, event, payload) }
    }

    private suspend fun sendPhoenixMsgTo(
        ws: DefaultClientWebSocketSession,
        topic: String,
        event: String,
        payload: JsonObject
    ) {
        val msg = buildJsonArray {
            add((ref++).toString())
            add(ref.toString())
            add(topic)
            add(event)
            add(payload)
        }.toString()
        ws.send(msg)
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
