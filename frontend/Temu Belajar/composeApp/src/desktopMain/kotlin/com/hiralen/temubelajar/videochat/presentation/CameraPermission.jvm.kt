package com.hiralen.temubelajar.videochat.presentation

import androidx.compose.runtime.Composable

/** Desktop (JVM): no runtime permission model — grant immediately. */
@Composable
actual fun CameraPermission(content: @Composable () -> Unit) = content()
