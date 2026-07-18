package com.hiralen.temubelajar.videochat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglRenderer
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * Local camera preview — respects actual camera aspect ratio.
 *
 * SurfaceViewRenderer by default stretches to fill its container.
 * We let it fill the container but set scalingType to SCALE_ASPECT_FIT
 * so it letterboxes/pillarboxes to preserve the true aspect ratio.
 *
 * Portrait camera (e.g. 720×1280): fills height, pillarboxes width.
 * Landscape camera (e.g. 1280×720): fills width, letterboxes height.
 */
@Composable
actual fun LocalVideoView(renderer: Any?, isMuted: Boolean, modifier: Modifier) {
    if (isMuted || renderer == null) {
        Box(modifier = modifier.background(Color(0xFF0A0A1A)))
        return
    }
    val view = renderer as? SurfaceViewRenderer ?: return

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                view.apply {
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                }
            },
            update = { v ->
                v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Remote video view — Phase 5.16 OmeTV-style full-bleed.
 *
 * Previously used SCALE_ASPECT_FIT which letterboxed remote feeds with
 * black bars. The call screen's dominant visual is the remote peer —
 * using SCALE_ASPECT_FILL makes the remote stream fill the screen edge
 * to edge (cropping overflow on mismatched ratios), matching OmeTV and
 * the desktop/iOS view in this same fix.
 */
@Composable
actual fun RemoteVideoView(renderer: Any?, modifier: Modifier) {
    if (renderer == null) {
        Box(modifier = modifier.background(Color(0xFF050510)))
        return
    }
    val view = renderer as? SurfaceViewRenderer ?: return

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                view.apply {
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                }
            },
            update = { v ->
                v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
