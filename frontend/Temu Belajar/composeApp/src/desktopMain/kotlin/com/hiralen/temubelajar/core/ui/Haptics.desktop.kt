package com.hiralen.temubelajar.core.ui

import java.awt.Toolkit
import java.awt.event.KeyEvent

/**
 * Phase 5.36 — Desktop JVM haptic implementation. There is no built-in
 * vibrator; the closest "tactile" response is a brief Toolkit beep (the
 * system-default alert sound) for warning/success, and a no-op for click
 * (we don't beep on every primary-button click — that crosses from
 * responsive into annoying within three button presses).
 *
 * Click / Soft thus no-op. Warning + Success emit a single short `beep()`
 * so the user hears confirmation for rare destructive / affirmative
 * events. On platforms without an audio Toolkit (rare headless CI), the
 * `beep()` call is wrapped in a try/catch.
 */
private fun beep() {
    try {
        Toolkit.getDefaultToolkit().beep()
    } catch (_: Throwable) {}
}

actual fun platformHapticClick()   { /* no-op: click-rate beeping is too noisy */ }
actual fun platformHapticSoft()    { /* no-op */ }
actual fun platformHapticSuccess() { beep() }
actual fun platformHapticWarning() { beep() }
