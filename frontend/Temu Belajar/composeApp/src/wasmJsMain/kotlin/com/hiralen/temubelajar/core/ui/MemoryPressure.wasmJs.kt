package com.hiralen.temubelajar.core.ui

// Phase 1.20 — wasmJs platform actual for `MemoryPressure`. See the
// commonMain `MemoryPressure.kt` for design rationale. We use the
// `@JsFun` interop pattern that wasmJs now prefers over the legacy
// `org.w3c.dom.*` typed DOM bindings (which the new wasm target no longer
// ships complete types for; see WasmRtcInterop.kt for prior-art).
//
//   - visibilitychange → "hidden"  : emit BACKGROUND
//   - pagehide         → event.persisted true (BFCache hit): emit CRITICAL
//     (the page is about to be fully evicted and the user will re-spawn
//     us cold on next restore; releasing the camera now frees the
//     MediaStream + releases the getUserMedia permission bar)
//
// Emissions are idempotent: the engine `dispose()` call tree is a no-op when
// nothing is initialized, and `MemoryPressure.emit*()` is just a `tryEmit`
// on a `SharedFlow` with replay=0 — missed events don't accumulate.
//
// We register the listeners at process start via `initOnce` below. The JS
// runtime keeps the closure references alive independent of GC root.

@JsFun("() => document.visibilityState")
private external fun documentVisibilityState(): String

@JsFun("(cb) => document.addEventListener('visibilitychange', cb)")
private external fun onVisibilityChange(cb: () -> Unit)

@JsFun("(cb) => window.addEventListener('pagehide', cb)")
private external fun onPageHide(cb: (JsAny) -> Unit)

@JsFun("(e) => e.persisted")
private external fun eventPersisted(e: JsAny): Boolean

@Suppress("unused")
private val wasmMemoryPressureBootstrap: Unit = run {
    onVisibilityChange {
        if (documentVisibilityState() == "hidden") {
            MemoryPressure.emitBackground()
        }
    }
    onPageHide { ev ->
        if (eventPersisted(ev)) {
            MemoryPressure.emitCritical()
        } else {
            MemoryPressure.emitBackground()
        }
    }
    Unit
}
