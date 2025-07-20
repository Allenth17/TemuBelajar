package com.hiralen.temubelajar

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun CameraPreview(cameraManager: CameraManager) {
    AndroidView(
        factory = {
            ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                previewStreamState.observeForever { streamState ->
                    if (streamState == PreviewView.StreamState.STREAMING) {
                        cameraManager.setSurfaceProvider(surfaceProvider)
                    }
                }
            }
        }
    )
}