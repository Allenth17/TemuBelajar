package com.hiralen.temubelajar.videochat.presentation

import androidx.compose.runtime.Composable

/**
 * WASM: Camera permission is handled entirely in index.html BEFORE the Kotlin
 * WASM bundle loads. By the time this composable runs, the user has either:
 *   - Granted permission (window.__tbStream is set, cachedLocalStream is populated)
 *   - Denied permission (window.__tbStream is null, app runs without camera)
 *
 * No getUserMedia() call here — that was causing the freeze.
 */
@Composable
actual fun CameraPermission(content: @Composable () -> Unit) {
    content()
}
