package com.hiralen.temubelajar.core.ui

import androidx.compose.runtime.Composable

/**
 * Android actual — delegate to Jetpack Activity's `BackHandler` so the OS
 * back press / predictive-back gesture triggers [onBack] only while [enabled]
 * is true. `androidx.activity:activity-compose` is declared as an `androidMain`
 * implementation dependency of :composeApp.
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
