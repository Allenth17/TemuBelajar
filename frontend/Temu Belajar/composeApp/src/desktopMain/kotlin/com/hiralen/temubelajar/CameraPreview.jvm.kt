package com.hiralen.temubelajar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.hiralen.temubelajar.util.flipHorizontal
import compose.icons.FeatherIcons
import compose.icons.feathericons.Camera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameGrabber
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun CameraPreview() {
    var currentCameraIndex by remember {
        mutableStateOf(0)
    }

    val frameBitmap = remember {
        mutableStateOf<ImageBitmap?>(null)
    }

    val isRunning = remember {
        AtomicBoolean(true)
    }

    val nativeResolution = remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }

    LaunchedEffect(currentCameraIndex) {
        isRunning.set(true)

        withContext(Dispatchers.IO) {
            var grabber: OpenCVFrameGrabber? = null
            val converter = Java2DFrameConverter()

            try {
                grabber = OpenCVFrameGrabber.createDefault(currentCameraIndex)
                grabber.start()

                val width = grabber.imageWidth
                val height = grabber.imageHeight
                nativeResolution.value = Pair(width, height)

                grabber.imageWidth = width
                grabber.imageHeight = height

                grabber.stop()
                grabber.start()

                while (isRunning.get()) {
                    val frame = grabber.grab() ?: continue
                    val bufferedImage = converter.convert(frame)
                    if (bufferedImage != null) {
                        val mirroredImage = bufferedImage.flipHorizontal()
                        val imageBitmap = mirroredImage.toComposeImageBitmap()
                        withContext(Dispatchers.Main) {
                            frameBitmap.value = imageBitmap
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    grabber?.stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                converter.close()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isRunning.set(false)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        frameBitmap.value?.let { bitmap ->
            Image(
                painter = BitmapPainter(bitmap),
                contentDescription = "Camera Preview",
                modifier = Modifier.fillMaxSize()
            )
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FeatherIcons.Camera,
                    contentDescription = "Loading Camera",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}