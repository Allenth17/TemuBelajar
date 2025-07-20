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
    private var surfaceProvider: Preview.SurfaceProvider? = null

    @SuppressLint("RestrictedApi", "VisibleForTests")
    actual fun startCamera(cameraType: CameraType) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context.context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelector = when (cameraType) {
                    CameraType.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                    CameraType.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                }

                // 1. Buat preview
                preview = Preview.Builder().build().apply {
                    // Gunakan surface provider jika sudah tersedia
                    surfaceProvider?.let { surfaceProvider = it }
                }

                // 2. Setup use case
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    context.context as LifecycleOwner,
                    cameraSelector,
                    preview
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context.context))
    }

    actual fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    fun setSurfaceProvider(provider: Preview.SurfaceProvider) {
        surfaceProvider = provider
        // Update preview jika sudah diinisialisasi
        preview?.surfaceProvider = provider
    }
}