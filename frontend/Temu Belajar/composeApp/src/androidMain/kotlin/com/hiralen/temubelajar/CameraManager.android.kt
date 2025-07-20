package com.hiralen.temubelajar

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

actual class CameraManager actual constructor(
    private val context: PlatformContext
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null

    actual fun startCamera(cameraType: CameraType) {
        val cameraProviderFuture = ProcessCameraProvider.Companion.getInstance(context.context)

        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelector = when (cameraType) {
                    CameraType.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                    CameraType.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                }
                preview = Preview.Builder().build()

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    context.context as LifecycleOwner,
                    cameraSelector,
                    preview
                )
            },
            ContextCompat.getMainExecutor(context.context)
        )
    }

    actual fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    @SuppressLint("RestrictedApi", "VisibleForTests")
    fun getSurfaceProvider() : Preview.SurfaceProvider? {
        return preview?.surfaceProvider
    }

    fun setSurfaceProvider(provider: Preview.SurfaceProvider) {
        preview?.surfaceProvider = provider
    }
}
