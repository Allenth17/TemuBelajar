package com.hiralen.temubelajar

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(context)
    }
    var currentCamera by remember {
        mutableIntStateOf(CameraSelector.LENS_FACING_FRONT)
    }

    val previewView = remember {
        PreviewView(context)
    }

    fun switchCamera() {
        when (currentCamera) {
            CameraSelector.LENS_FACING_FRONT -> {
                currentCamera = CameraSelector.LENS_FACING_BACK
            }
            CameraSelector.LENS_FACING_BACK -> {
                currentCamera = CameraSelector.LENS_FACING_FRONT
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderFuture
                    .get()
                    .unbindAll()
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error cleaning up camera", e)
            }
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AndroidView(
            factory = {
                previewView
            },
            modifier = Modifier
                .fillMaxSize(),
            update = {
                val cameraProvider = cameraProviderFuture
                    .get()

                val preview = Preview
                    .Builder()
                    .build()

                preview.surfaceProvider = previewView.surfaceProvider

                val cameraSelector = CameraSelector
                    .Builder()
                    .requireLensFacing(currentCamera)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider
                    .bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
            }
        )

        IconButton(
            onClick = {
                switchCamera()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.3f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.SwitchCamera,
                contentDescription = null
            )
        }
    }
}