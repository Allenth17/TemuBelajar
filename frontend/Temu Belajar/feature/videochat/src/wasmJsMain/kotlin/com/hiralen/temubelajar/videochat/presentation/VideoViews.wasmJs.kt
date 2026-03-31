package com.hiralen.temubelajar.videochat.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiralen.temubelajar.core.ui.TBColors
import com.hiralen.temubelajar.webrtc.*
import compose.icons.TablerIcons
import compose.icons.tablericons.Video
import compose.icons.tablericons.VideoOff
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * All WASM video rendering is handled by [mountStream] which uses setTimeout(0)
 * to mount a <video> element AFTER the current JS task/Compose frame completes.
 * This prevents any freeze during Compose recomposition.
 *
 * We use LaunchedEffect (not DisposableEffect) so the side effect runs
 * after composition is committed, not during it.
 */

@Composable
actual fun LocalVideoView(renderer: Any?, isMuted: Boolean, modifier: Modifier) {
    val stream = renderer as? JsMediaStream

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (stream == null || isMuted) {
            Icon(TablerIcons.VideoOff, contentDescription = null, tint = TBColors.TextSecondary)
            return@Box
        }

        // Determine role from measured size
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val role = if (maxWidth < 150.dp) "preview" else "local"
            WasmVideoView(stream = stream, muted = true, role = role)
        }
    }
}

@Composable
actual fun RemoteVideoView(renderer: Any?, modifier: Modifier) {
    val stream = renderer as? JsMediaStream

    Box(modifier = modifier.background(Color(0xFF050510)), contentAlignment = Alignment.Center) {
        if (stream != null) {
            WasmVideoView(stream = stream, muted = false, role = "remote")
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(TablerIcons.Video, contentDescription = null, tint = TBColors.TextMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("Menunggu video...", color = TBColors.TextMuted, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Renders video frames to a Compose Canvas using Skia.
 * No more <video> elements appended to document.body.
 * Everything is contained within the Compose hierarchy.
 */
@Composable
private fun WasmVideoView(stream: JsMediaStream, muted: Boolean, role: String) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    // We use stream as key so we recreate capturer if stream changes
    LaunchedEffect(stream) {
        val capturer = createVideoCapturer(stream, muted)
        var buffer: IntArray? = null
        var lastWidth = 0
        var lastHeight = 0
        
        try {
            while (true) {
                // Throttle to ~30fps to reduce CPU load and JS/WASM interop overhead.
                // 30fps is the standard for video calls and ensures consistent smoothness.
                delay(33)

                withFrameNanos {
                    val dims = getVideoDimensions(capturer)
                    val w = getDimWidth(dims)
                    val h = getDimHeight(dims)
                    
                    if (w > 0 && h > 0) {
                        val jsBuffer = captureVideoFrameInts(capturer)
                        if (jsBuffer != null) {
                            val bufferLen = getJsBufferLength(jsBuffer)
                            if (buffer == null || buffer!!.size != bufferLen) {
                                buffer = IntArray(bufferLen)
                                lastWidth = w
                                lastHeight = h
                            }
                            
                            // Skia needs ByteArray for installPixels
                            val skBuffer = ByteArray(bufferLen * 4)
                            
                            val b = buffer!!
                            for (i in 0 until bufferLen) {
                                val pixel = getJsBufferInt(jsBuffer, i)
                                b[i] = pixel
                                
                                // Write to ByteArray for Skia
                                val offset = i * 4
                                skBuffer[offset + 0] = (pixel and 0xFF).toByte()
                                skBuffer[offset + 1] = ((pixel ushr 8) and 0xFF).toByte()
                                skBuffer[offset + 2] = ((pixel ushr 16) and 0xFF).toByte()
                                skBuffer[offset + 3] = ((pixel ushr 24) and 0xFF).toByte()
                            }
                            
                            val skiaBitmap = Bitmap()
                            skiaBitmap.allocPixels(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
                            skiaBitmap.installPixels(skBuffer)
                            bitmap = skiaBitmap.asComposeImageBitmap()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Cancellation - fine.
        } finally {
            stopVideoCapturer(capturer)
        }
    }

    val bmp = bitmap
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Video stream",
                modifier = Modifier.fillMaxSize(),
                contentScale = if (role == "preview") ContentScale.Crop else ContentScale.Fit
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = TBColors.Primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text("Memuat video...", color = TBColors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}
