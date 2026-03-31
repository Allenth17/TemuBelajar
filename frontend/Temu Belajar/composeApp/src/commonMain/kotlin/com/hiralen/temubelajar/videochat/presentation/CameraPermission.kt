package com.hiralen.temubelajar.videochat.presentation

import androidx.compose.runtime.Composable

/**
 * Platform-specific composable that requests camera and microphone permissions
 * before showing the video call UI.
 *
 * - Android : accompanist-permissions (rememberMultiplePermissionsState)
 * - iOS     : AVFoundation AVCaptureDevice + AVAudioSession requestAccess
 * - Desktop : Always grants (JVM has no runtime camera/mic permission model)
 * - WASM    : Calls getUserMedia to trigger the browser permission dialog
 */
@Composable
expect fun CameraPermission(content: @Composable () -> Unit)
