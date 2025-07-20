package com.hiralen.temubelajar

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
actual fun CameraPreview(cameraManager: CameraManager) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                // Langsung berikan surface provider ke camera manager
                cameraManager.setSurfaceProvider(surfaceProvider)
            }
        },
        update = { view ->
            // Pastikan surface provider selalu terupdate
            cameraManager.setSurfaceProvider(view.surfaceProvider)
        }
    )

    LaunchedEffect(lifecycleOwner) {
        cameraManager.startCamera(CameraType.FRONT)
    }
}