package com.hiralen.temubelajar.core.ui

import androidx.compose.runtime.Composable

/**
 * Desktop actual — no-op. JVM/Desktop has no system back button; the in-call
 * screen always exposes explicit "End" / "Next" actions, so leaving this
 * callback unreachable on desktop is intentional and avoids wiring a fake
 * keyboard back-shortcut the host window doesn't know about.
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop has no system back gesture — deliberately unimplemented.
}
