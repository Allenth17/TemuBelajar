package com.hiralen.temubelajar.core.ui

import androidx.compose.runtime.Composable

/**
 * iOS actual — no-op. iOS provides no equivalent global back gesture inside a
 * ComposeUIViewController; users rely on the in-call "End" / "Next" controls.
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no global back gesture inside ComposeUIViewController — deliberately unimplemented.
}
