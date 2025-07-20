package com.hiralen.temubelajar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Composable
actual fun CameraPreview(cameraManager: CameraManager) {
    val frame = remember { mutableStateOf<ImageBitmap?>(null) }

    val image by cameraManager.imageFlow.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                frame.value = cameraManager.getCurrentFrame()
            }
        }
    }


    Canvas(modifier = Modifier.fillMaxSize()) {
        image?.let { bitmap ->
            drawImage(
                bitmap,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        }
    }
}