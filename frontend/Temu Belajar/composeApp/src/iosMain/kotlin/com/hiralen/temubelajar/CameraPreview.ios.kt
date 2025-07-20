package com.hiralen.temubelajar

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import kotlinx.cinterop.ExperimentalForeignApi
import androidx.compose.ui.viewinterop.UIKitView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    cameraManager: CameraManager
) {
    UIKitView(
        factory = {
            cameraManager.context.view
        },
        modifier = Modifier,
        update = { view ->
            // Update view jika diperlukan
            cameraManager.videoPreviewLayer?.frame = view.bounds
        },
        properties = UIKitInteropProperties(
            isInteractive = true,
            isNativeAccessibilityEnabled = true
        )
    )

    LaunchedEffect(Unit) {
        cameraManager.startCamera(CameraType.BACK)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.stopCamera()
        }
    }
}