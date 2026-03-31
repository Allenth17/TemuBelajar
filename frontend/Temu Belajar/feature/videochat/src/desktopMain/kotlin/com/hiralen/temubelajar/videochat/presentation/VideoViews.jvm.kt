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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiralen.temubelajar.core.ui.TBColors
import compose.icons.TablerIcons
import compose.icons.tablericons.Video
import compose.icons.tablericons.VideoOff
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay

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
            while (true) {
                val img = frameRef.get()
                if (img != null) bitmap = img.toComposeImageBitmap()
                delay(33)
            }
        }

        val bmp = bitmap
        if (bmp != null) {
            // ContentScale.Fit: shows whole image with letterbox/pillarbox — preserves aspect ratio
            Image(
                bitmap = bmp,
                contentDescription = "Local camera",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TBColors.Primary, strokeWidth = 2.dp)
                Text("Kamera...", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
    }
}

/**
 * Renders the remote peer's video.
 * Uses ContentScale.Fit so portrait/landscape is shown correctly with bars.
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
            while (true) {
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
                contentScale = ContentScale.Fit   // preserve actual stream aspect ratio
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
        Text("Menunggu video...", color = TBColors.TextMuted, fontSize = 13.sp)
    }
}
