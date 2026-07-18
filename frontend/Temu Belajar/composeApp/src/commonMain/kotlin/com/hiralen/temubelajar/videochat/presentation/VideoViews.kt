package com.hiralen.temubelajar.videochat.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Platform-specific local video preview (PiP) */
@Composable
expect fun LocalVideoView(renderer: Any?, isMuted: Boolean, modifier: Modifier = Modifier)

/** Platform-specific full-screen remote video */
@Composable
expect fun RemoteVideoView(renderer: Any?, modifier: Modifier = Modifier)
