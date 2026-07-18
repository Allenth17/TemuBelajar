package com.hiralen.temubelajar.core.ui

import androidx.compose.runtime.Composable

/**
 * Cross-platform `BackHandler` shim.
 *
 * Compose Multiplatform 1.11 does not ship Jetpack's `androidx.activity.compose
 * .BackHandler` for commonMain (that artifact is published as an Android-only
 * `.aar`). This expect/actual pair gives common code a single call site:
 *
 *  - androidMain — delegates to the real `androidx.activity.compose.BackHandler`
 *    so the system back gesture triggers the lambda (activity-compose is wired
 *    in as an `androidMain` implementation dependency for this library).
 *  - desktopMain / iosMain / wasmJsMain — no platform back button fires the
 *    callback; VideoChatScreen always exposes explicit on-screen "End" and
 *    "Next" actions, so leaving the hook as a no-op on non-Android targets is
 *    acceptable and keeps the common call site single-voiced.
 *
 * Mirrors the Jetpack Activity signature: `enabled` controls whether the
 * handler is installed; the call happens from the Composition only while
 * `enabled == true`.
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
