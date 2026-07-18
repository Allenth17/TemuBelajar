package com.hiralen.temubelajar.core.ui

/**
 * Phase 5.36 — WASM haptic implementation via the Web Vibration API
 * (`navigator.vibrate`). Chromium-family browsers support it on Android
 * and Desktop; Firefox/Safari silently no-op. The patterns map to the
 * generic VibrationEffect "milliseconds" form (no amplitude control on the
 * Web API — just durations, which the phone maps onto intensity internally).
 *
 * Uses the `@JsFun` interop pattern (preferred in wasmJs over the legacy
 * `org.w3c.dom.*` typed bindings — see MemoryPressure.wasmJs.kt for
 * prior-art). One `@JsFun` per duration because Kotlin/Wasm JS interop
 * forbids `IntArray` as a value parameter on JS-interop functions (only
 * primitive / string / JsAny / function types are supported).
 *
 * Calls from a non-main JS thread — Compose WASM uses main thread anyway —
 * so no thread marshalling needed. Calls on Safari throw at the JS layer
 * and the try/catch swallows them; no per-platform pre-feature-detect is
 * required.
 */
@JsFun("() => typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function' ? navigator.vibrate(15) : false")
private external fun vibrate15(): Boolean

@JsFun("() => typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function' ? navigator.vibrate(8) : false")
private external fun vibrate8(): Boolean

@JsFun("() => typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function' ? navigator.vibrate(35) : false")
private external fun vibrate35(): Boolean

@JsFun("() => typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function' ? navigator.vibrate(60) : false")
private external fun vibrate60(): Boolean

private inline fun safeVibrate(call: () -> Boolean) {
    try { call() } catch (_: Throwable) { /* platform without Vibration API */ }
}

actual fun platformHapticClick()    { safeVibrate { vibrate15() } }
actual fun platformHapticSoft()     { safeVibrate { vibrate8() } }
actual fun platformHapticSuccess()  { safeVibrate { vibrate35() } }
actual fun platformHapticWarning()  { safeVibrate { vibrate60() } }
