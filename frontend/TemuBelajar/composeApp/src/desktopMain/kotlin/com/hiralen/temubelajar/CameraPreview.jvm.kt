package com.hiralen.temubelajar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize

@Composable
actual fun CameraPreview(cameraManager: CameraManager) {
    val frame = remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                frame.value = cameraManager.getCurrentFrame()
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        frame.value?.let {
            drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }
    }
}