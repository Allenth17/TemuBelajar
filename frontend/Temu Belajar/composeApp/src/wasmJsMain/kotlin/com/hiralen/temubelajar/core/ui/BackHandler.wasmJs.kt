package com.hiralen.temubelajar.core.ui

import androidx.compose.runtime.Composable

/**
 * WasmJs actual — no-op. Browser back navigation would abandon the page; we
 * do not intercept it. Users rely on the in-call "End" / "Next" controls.
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Browser back exits the tab/app — deliberately unimplemented.
}
