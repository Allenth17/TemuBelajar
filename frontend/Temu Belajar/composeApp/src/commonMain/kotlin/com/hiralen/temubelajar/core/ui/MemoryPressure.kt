package com.hiralen.temubelajar.core.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Phase 1.20 — cross-platform memory pressure channel.
 *
 * Subscribers (currently `WebRtcManager`, but any retaining-heavy component
 * can subscribe) listen on [events] and dispose their long-lived resources
 * when [Level.CRITICAL] fires. Each platform actual is wired into an actual
 * OS signal rather than the simple `emit*()` here:
 *
 *   - **Android** — `TeBeApp.onTrimMemory(level)` forwards
 *     `TRIM_MEMORY_RUNNING_*` and `TRIM_MEMORY_COMPLETE` to [emitCritical],
 *     everything else to [emitBackground]. See `androidApp/.../TeBeApp.kt`.
 *
 *   - **WASM** — `document.addEventListener("visibilitychange")` reports
 *     `hidden` as BACKGROUND; `pagehide` (page-evicted-on-macOS-safari)
 *     as CRITICAL. See `wasmJsMain/.../core/ui/MemoryPressure.wasmJs.kt`.
 *
 *   - **Desktop (JVM)** — JVM heap pressure isn't directly observable; we
 *     rely on `java.lang.management.MemoryMXBean` low-memory notification or
 *     a configurable GC threshold. Not wired yet — the desktop app uses
 *     most of its RAM for the local renderer poll-loop, which itself runs
 *     inside the Compose `LaunchedEffect` lifecycle (already disposed on
 *     window close).
 *
 *   - **iOS** — `UIApplicationDidReceiveMemoryWarningNotification` should
 *     call [emitCritical]. NOT wired in this commit because the iOS KMP
 *     target is compile-disabled on this Linux host (GoogleWebRTC cinterop
 *     requires a macOS host); the wiring will be added by the next
 *     macOS-host session along with the 0.16 Keychain and 4.24 WebRTC-pod
 *     migrations.
 *
 * The flow is `SharedFlow` (replay = 0) so late subscribers don't see stale
 * events from before they subscribed — only fresh memory warnings fire. This
 * matches WebRTC manager semantics: dispose-on-critical is destructive and
 * reaching back into a stale warning would crash the engine.
 */
object MemoryPressure {

    enum class Level {
        /**
         * OS says memory is low and we should release everything that's not
         * currently powering an active user task. Subscribers that hold a
         * camera capture / video source / renderer chain should release it.
         */
        CRITICAL,

        /**
         * OS says the app went to background or got a soft pressure hint.
         * Cheaper cleanup: drop caches but keep long-lived resources.
         */
        BACKGROUND,
    }

    private val _events = MutableSharedFlow<Level>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<Level> = _events.asSharedFlow()

    /** Called from platform actuals — never from common code. */
    fun emitCritical() { _events.tryEmit(Level.CRITICAL) }

    /** Called from platform actuals — never from common code. */
    fun emitBackground() { _events.tryEmit(Level.BACKGROUND) }
}
