package com.hiralen.temubelajar.videochat.presentation

import androidx.compose.runtime.Composable

/**
 * iOS: The OS automatically shows camera and microphone permission dialogs
 * the first time AVCaptureDevice / AVAudioSession access is attempted inside
 * WebRtcManager.ios.kt (which runs inside feature:videochat and has full
 * CocoaPods / AVFoundation access).
 *
 * Info.plist already declares NSCameraUsageDescription and
 * NSMicrophoneUsageDescription, so the OS dialog will appear with the
 * correct description strings on first use.
 *
 * We simply pass through to content() here — the OS-level gating happens
 * inside the WebRTC initialization flow.
 */
@Composable
actual fun CameraPermission(content: @Composable () -> Unit) = content()
