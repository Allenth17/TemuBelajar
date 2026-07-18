package com.hiralen.temubelajar.videochat.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.hiralen.temubelajar.core.ui.TBColors
import com.hiralen.temubelajar.core.ui.TBTypography
import compose.icons.TablerIcons
import compose.icons.tablericons.Video
import compose.icons.tablericons.VideoOff
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Renders the local webcam preview.
 * Uses ContentScale.Fit so the actual camera aspect ratio is preserved —
 * portrait cameras show tall/narrow, landscape show wide.
 */
@Composable
actual fun LocalVideoView(renderer: Any?, isMuted: Boolean, modifier: Modifier) {
    Box(modifier = modifier.background(Color(0xFF0A0A1A)), contentAlignment = Alignment.Center) {
        if (isMuted || renderer == null) {
            Icon(TablerIcons.VideoOff, contentDescription = null,
                tint = TBColors.TextSecondary, modifier = Modifier.size(28.dp))
            return@Box
        }

        @Suppress("UNCHECKED_CAST")
        val frameRef = renderer as? AtomicReference<java.awt.image.BufferedImage?> ?: return@Box
        var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

        LaunchedEffect(frameRef) {
            // Phase 1.11 — gate on `isActive` so the polling loop exits
            // gracefully when the LaunchedEffect cancels on dispose / key
            // change. The previous `while (true)` leaked a coroutine that
            // kept polling the ref even after the composition went away.
            while (isActive) {
                val img = frameRef.get()
                if (img != null) bitmap = img.toComposeImageBitmap()
                delay(33)
            }
        }

        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Local camera",
                // Phase 4.13 — mirror the local preview so the user's
                // movements match a physical mirror. Android mirrors at
                // the libwebrtc SurfaceViewRenderer level (setMirror(true)
                // in TBWebRtcEngine.android.kt); Desktop has no such knob
                // on the webrtc-java side, so we mirror in the Compose
                // transform layer instead. Same behaviour.
                modifier = Modifier.fillMaxSize().scale(scaleX = -1f, scaleY = 1f),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TBColors.Primary, strokeWidth = 2.dp)
                // Phase 6.7 — was raw `fontSize = 10.sp`. Now TBTypography.Badge
                // (10sp Medium) — same visual size, sourced from the design system.
                Text("Kamera...", color = Color.White.copy(alpha = 0.6f), style = TBTypography.Badge)
            }
        }
    }
}

/**
 * Renders the remote peer's video.
 * Phase 5.16 — switched to ContentScale.Crop (full-bleed) per OmeTV spec;
 * the remote stream is the dominant visual element of the call screen
 * and Fit's letterboxing looked broken next to the active call HUD.
 */
@Composable
actual fun RemoteVideoView(renderer: Any?, modifier: Modifier) {
    Box(modifier = modifier.background(Color(0xFF050510)), contentAlignment = Alignment.Center) {
        @Suppress("UNCHECKED_CAST")
        val frameRef = renderer as? AtomicReference<java.awt.image.BufferedImage?>

        if (frameRef == null) {
            RemotePlaceholder()
            return@Box
        }

        var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

        LaunchedEffect(frameRef) {
            // Phase 1.11 — gate on `isActive` so the loop cancels with the
            // composition rather than leaking.
            while (isActive) {
                val img = frameRef.get()
                if (img != null) bitmap = img.toComposeImageBitmap()
                delay(33)
            }
        }

        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Remote video",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop   // Phase 5.16 — OmeTV full-bleed
            )
        } else {
            RemotePlaceholder()
        }
    }
}

@Composable
private fun RemotePlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(TablerIcons.Video, contentDescription = null,
            tint = TBColors.TextMuted, modifier = Modifier.size(48.dp))
        // Phase 6.7 — was `fontSize = 13.sp`. Now TBTypography.Eyebrow (13sp
        // Medium) — exact same size on the type ladder.
        Text("Menunggu video...", color = TBColors.TextMuted, style = TBTypography.Eyebrow)
    }
}
