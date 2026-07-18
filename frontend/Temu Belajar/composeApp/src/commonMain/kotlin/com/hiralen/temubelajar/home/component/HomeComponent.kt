package com.hiralen.temubelajar.home.component

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
import org.koin.mp.KoinPlatform

enum class MatchingStatus { IDLE, SEARCHING, FOUND, ERROR }

/**
 * Phase 5.4 — filter-pref data structure for matchmaking.
 *
 * Task 5.4 is "No gender/country filter structure" — the order of operations
 * needed to deliver this end-to-end is schema-first-user → matchmaking
 * matchmaking → matchmaking join_queue → matchmaking-server find_best_match →
 * HomeScreen UI. The frontend side is the cheap layer of that chain, but
 * without backend schema changes (user_service dropping a `gender` /
 * `country` column + matchmaking_server storing them in the ETS queue tuples
 * and respecting them in `find_best_match/4`), there's literally nothing for
 * the client to send that the server can honour. To avoid shipping a fake UI
 * toggle that pretends to filter but does nothing, the `MatchFilter` data
 * class is declared here as the structure that the upcoming backend pipeline
 * will consume.
 *
 * Today the HomeComponent adds this onto the `join_queue` payload as
 * `preferred_gender` / `preferred_country` only when non-default, but the
 * matchmaking channel's `join/3` only reads `socket.assigns.university` and
 * silently ignores the rest, so the wire form is forward-compatible without
 * breaking existing backends. The data class itself is here so callers can
 * pattern-match on it (e.g. UI displaying the active filter set in a chip).
 */
data class MatchFilter(
    val genderPreference: GenderPreference = GenderPreference.ANY,
    /** ISO 3166-1 alpha-2 country code or `null` for "no country filter". */
    val countryPreference: String? = null
) {
    /** True when every field is at its default (no filter applied). */
    fun isEmpty(): Boolean =
        genderPreference == GenderPreference.ANY && countryPreference == null
}

enum class GenderPreference { ANY, FEMALE, MALE, NONBINARY }

data class HomeState(
    val status: MatchingStatus = MatchingStatus.IDLE,
    val queueSize: Int = 0,
    val queuePosition: Int = 0,
    val error: String? = null,
    val userEmail: String = "",
    val userUniversity: String? = null,
    val isCameraReady: Boolean = false,
    val filter: MatchFilter = MatchFilter()
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
    // 3.9 — track the in-flight matching coroutine so stopMatching can
    // cancel it BEFORE opening a new one, instead of racing wsSession
    // writes. Cancelling the Job also drains the `webSocket { }` block.
    private var matchingJob: Job? = null
    // 3.10 — Phoenix `ref` counter. We satisfy the thread-safety invariant
    // by the "single-thread dispatcher" branch of finding 3.10 rather than
    // an AtomicInteger: `scope` is `Dispatchers.Main + SupervisorJob()`, and
    // every read-modify-write of `ref` happens inside a `scope.launch` or a
    // `suspend` fn called from one, so all reads/writes serialize on the
    // Main dispatcher. (KMP: `java.util.concurrent.atomic.AtomicInteger` is
    // JVM-only and would break wasmJs/iOS targets.)
    private var ref = 1

    // Phase 1.4 — share the WebRtcManager via Koin. Previously this owned
    // its own `new` instance, and so did VideoChatComponent — two engines,
    // two captures of the same camera → "camera in use" race. Now the
    // engine is a process-singleton and HomeScreen just reads
    // `localVideoRenderer` / `localTrackReady` from it for the Home
    // preview. Match-found-away-from-Home re-uses the same capture.
    //
    // Phase 1.5 — Home NO LONGER calls `webRtcManager.initialize()` at
    // Home screen-mount. The previous early-start burned battery + forced
    // speakerphone ON + opened MODE_IN_COMMUNICATION system-wide while
    // the user was just staring at the splash. The HomeScreen preview is
    // now empty until the user taps "Start matching"; the engine
    // initializes inside `connectSignaling()` once a match_found message
    // arrives and we transition to VideoChat. Untapped-Home stops the
    // user from silently broadcasting camera frames before they consent.
    //
    // The Home pre-matching PREVIEW view at HomeScreen.kt:154–159 was
    // gated on `state.isCameraReady`. Now isCameraReady stays false on
    // Home (we never init here), so the HomeScreen renders the icon
    // block instead of the empty local video slot — strictly better UX.
    private val webRtcManager: WebRtcManager = KoinPlatform.getKoin().get()

    init {
        loadUserInfo()
        // Phase 1.5 — `initCamera()` removed from Home.
        // The lifecycle hook below now only drains the Koin-singleton WebRtcManager
        // scope when Home is popped (which happens on navigation through the app
        // but NOT when we explicitly stay on Home and route to VideoChat —
        // Decompose does not destroy Home when pushing VideoChat onto the stack
        // because Home is the root of RootComponent). So dispose() here would
        // kill the engine mid-match. We leave engine lifecycle fully under
        // VideoChat's control.
        // HOWEVER — if the user truly backs out (root component pops), the
        // WebRtcManager singleton lives on in Koin so its `memScope`
        // subscription keeps running. That's intended: Koin singleton isn't
        // scoped to Decompose lifecycle. Apps that wantxc dispose-on-logout
        // should call `webRtcManager.shutdown()` from `logout()` directly (it
        // already disposes the engine — see `HomeComponent.logout()` below).
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
        // 3.9 — cancel any prior matching job before opening a new socket so
        // a leftover `webSocket { }` block on the old session can't flip
        // `wsSession` under us mid-flight.
        matchingJob?.cancel()
        matchingJob = scope.launch {
            val token = repository.getToken() ?: run {
                _state.value = _state.value.copy(error = "Silakan login ulang")
                return@launch
            }
            val university = _state.value.userUniversity

            _state.value = _state.value.copy(status = MatchingStatus.SEARCHING, error = null)

            try {
                // Phase 0.13 — never log the token; log only the host so we
                // can debug connect issues without leaking bearer secrets.
                println("[HomeComponent] Connecting to WebSocket host: ${BASE_WS_URL.substringBefore("/socket")}")
                httpClient.webSocket("$BASE_WS_URL&token=$token") {
                    wsSession = this
                    println("[HomeComponent] WebSocket connected!")

                    // Step 1: Join the Phoenix channel (required before sending any events)
                    println("[HomeComponent] Joining matchmaking lobby...")
                    sendPhoenixMsg("matchmaking:lobby", "phx_join", buildJsonObject {})

                    // Step 2: Send join_queue with university for smart matching
                    // Phase 5.4 — when `HomeState.filter` has non-default
                    // fields, attach them to the join payload so the backend
                    // (once Phase 5.4 backend lands) can restrict match
                    // candidates to those with matching gender / country.
                    // Today the matchmaking channel ignores extra keys, so
                    // the payload is forward-compatible without breaking.
                    val filter = _state.value.filter
                    sendPhoenixMsg("matchmaking:lobby", "join_queue", buildJsonObject {
                        if (university != null) put("university", university)
                        if (filter.genderPreference != GenderPreference.ANY) {
                            put("preferred_gender", filter.genderPreference.name.lowercase())
                        }
                        if (filter.countryPreference != null) {
                            put("preferred_country", filter.countryPreference)
                        }
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
        // 3.9 — cancel the matching coroutine FIRST so the `webSocket { }`
        // block drains + closes its session. We also defensively null-out
        // wsSession and try to close any session that escaped the Job (e.g.
        // cold cancellation race from startMatching replacing the job).
        matchingJob?.cancel()
        scope.launch {
            try {
                wsSession?.let {
                    // best-effort leave; ignore frame errors during teardown
                    runCatching { sendPhoenixMsg("matchmaking:lobby", "leave_queue", buildJsonObject {}) }
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
                    val pairId    = payload["pair_id"]?.jsonPrimitive?.content
                    val role      = payload["role"]?.jsonPrimitive?.content ?: "caller"
                    val peerEmail = payload["peer_email"]?.jsonPrimitive?.content ?: ""
                    val peerUni   = payload["peer_university"]?.jsonPrimitive?.content ?: ""
                    // 3.8 — bare `?: return` on missing pair_id leaves the
                    // user stuck on "Searching…" forever with no clue. Surface
                    // the malformed frame as ERROR so the UI can recover.
                    if (pairId == null) {
                        _state.value = _state.value.copy(
                            status = MatchingStatus.ERROR,
                            error = "Match-found payload missing pair_id; restart search."
                        )
                        return
                    }
                    _state.value  = _state.value.copy(status = MatchingStatus.FOUND)

                    // Phase 5.36 — success haptic the moment a match is
                    // found so the user (who may have backgrounded the app
                    // or looked away while queued) feels the state change.
                    com.hiralen.temubelajar.core.ui.platformHapticSuccess()
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
                    } else if (status == "error") {
                        // 3.8 — Phoenix reply `{status: "error", response: %{...}}`
                        // was silently dropped. Surface the response's `reason`
                        // (or `error`) text to the user.
                        val reason = response?.get("reason")?.jsonPrimitive?.contentOrNullSafe()
                            ?: response?.get("error")?.jsonPrimitive?.contentOrNullSafe()
                            ?: "Server rejected queue join"
                        _state.value = _state.value.copy(error = reason)
                    }
                }
                "queue_timeout" -> {
                    _state.value = _state.value.copy(
                        status = MatchingStatus.IDLE,
                        error = "Waktu mencari habis. Coba lagi."
                    )
                }
                "phx_error" -> {
                    // 3.8 — Phoenix-level errors (auth/transport) were
                    // swallowed. Surface them.
                    val reason = payload["reason"]?.jsonPrimitive?.contentOrNullSafe()
                        ?: "Phoenix channel error"
                    _state.value = _state.value.copy(
                        status = MatchingStatus.ERROR,
                        error = reason
                    )
                }
            }
        } catch (e: Exception) {
            // 3.8 — surface parse/handling failures instead of swallowing
            // them silently. Logger gets the stack frame for debugging; the
            // user gets a short, actionable retry message via state.error
            // (which HomeScreen's TBErrorBanner already renders).
            println("[HomeComponent] handleMessage failed on frame: $text")
            println(e.stackTraceToString())
            _state.value = _state.value.copy(error = "Gagal memproses pesan server. Coba lagi.")
        }
    }

    private suspend fun sendPhoenixMsg(topic: String, event: String, payload: JsonObject) {
        // 3.10 — atomic increment means refs are unique across concurrent
        // send coroutines (no more duplicate-ref replies from Phoenix).
        // (Main dispatcher invariant documented above — `scope.launch` is
        // the sole accessor so reads/writes serialize on Main.)
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
            // Phase 1.4 — release the process-wide WebRTC engine now that
            // the user is signing out. Koin keeps the singleton instance
            // alive even after every Decompose component is gone, so without
            // an explicit `shutdown()` here the camera capture (and the
            // speakerphone/audio-focus handles on Android) would keep
            // running on the login screen — leaking the camera indicator
            // and hijacking the OS audio routing.
            webRtcManager.shutdown()
            val token = repository.getToken()
            if (token != null) {
                repository.logout(token)
            }
            repository.clearToken()
            onLogout()
        }
    }
}

/**
 * Null-safe variant of `jsonPrimitive.content`. `jsonPrimitive` itself throws
 * on `JsonNull`, so we take the safer route used elsewhere in the repo
 * (see finding 3.6).
 */
private fun JsonElement?.contentOrNullSafe(): String? =
    (this as? JsonPrimitive)?.let { if (it == JsonNull) null else it.content }
