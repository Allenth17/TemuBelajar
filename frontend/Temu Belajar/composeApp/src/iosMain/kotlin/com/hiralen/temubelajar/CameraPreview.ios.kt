package com.hiralen.temubelajar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.CValue
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.position
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureStillImageOutput
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecJPEG
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.UIKit.UIView
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.QuartzCore.CATransaction
import platform.CoreGraphics.CGRect
import platform.QuartzCore.kCATransactionDisableActions

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraView() {
    val device = platform.AVFoundation.AVCaptureDevice
        .devicesWithMediaType(platform.AVFoundation.AVMediaTypeVideo).firstOrNull { device ->
            (device as platform.AVFoundation.AVCaptureDevice)
                .position == AVCaptureDevicePositionFront
        }!! as AVCaptureDevice

    val input = AVCaptureDeviceInput
        .deviceInputWithDevice(device, null)
            as AVCaptureDeviceInput

    val output = AVCaptureStillImageOutput()
    output
        .outputSettings = mapOf(
            AVVideoCodecKey to AVVideoCodecJPEG
        )

    val session = AVCaptureSession()
    session
        .sessionPreset = AVCaptureSessionPresetPhoto

    val cameraPreviewLayer = remember {
        AVCaptureVideoPreviewLayer(session = session)
    }

    UIKitView(
        modifier = Modifier.fillMaxSize(),
        background = Color.Black,
        factory = {
            val container = UIView()

            container
                .layer
                .addSublayer(cameraPreviewLayer)

            cameraPreviewLayer
                .videoGravity = AVLayerVideoGravityResizeAspectFill

            session
                .startRunning()

            container
        },
        onResize = {
            container: UIView,
            rect: CValue<CGRect> ->

                CATransaction
                    .begin()

                CATransaction
                    .setValue(true, kCATransactionDisableActions)

                container
                    .layer
                    .setFrame(rect)

                cameraPreviewLayer
                    .setFrame(rect)

                CATransaction
                    .commit()
        }
    )
}