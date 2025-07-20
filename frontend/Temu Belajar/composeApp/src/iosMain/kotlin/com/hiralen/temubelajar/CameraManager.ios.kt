package com.hiralen.temubelajar
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlinx.cinterop.ObjCObjectBase
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.QuartzCore.CATransaction

actual class CameraManager actual constructor(
    val context: PlatformContext
) {
    private var captureSession: AVCaptureSession? = null
    var videoPreviewLayer: AVCaptureVideoPreviewLayer? = null
    private lateinit var backCamera: AVCaptureDevice
    private lateinit var frontCamera: AVCaptureDevice
    private lateinit var currentCamera: AVCaptureDevice

    @OptIn(ExperimentalForeignApi::class)
    actual fun startCamera(cameraType: CameraType) {
        val session = AVCaptureSession().apply {
            sessionPreset = AVCaptureSessionPresetPhoto
        }

        val discoverySession = AVCaptureDeviceDiscoverySession()

        val devices = discoverySession.devices
        backCamera = devices.firstOrNull { it == AVCaptureDevicePositionBack } as AVCaptureDevice
        frontCamera = devices.firstOrNull { it == AVCaptureDevicePositionFront } as AVCaptureDevice

        currentCamera = when (cameraType) {
            CameraType.BACK -> backCamera
            CameraType.FRONT -> frontCamera
        }
        try {
            // Setup input
            val input = AVCaptureDeviceInput.deviceInputWithDevice(currentCamera, null)
            if (input != null && session.canAddInput(input)) {
                session.addInput(input)
            }

            // Setup preview layer
            val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
                videoGravity = AVLayerVideoGravityResizeAspectFill
                frame = context.view.bounds
            }

            CATransaction.begin()
            CATransaction.setDisableActions(true)
            context.view.layer.addSublayer(previewLayer)
            CATransaction.commit()

            videoPreviewLayer = previewLayer

            session.startRunning()
            captureSession = session

        } catch (e: Exception) {
            println("Error starting camera: ${e.message}")
        }
    }

    actual fun stopCamera() {
        captureSession?.stopRunning()
        videoPreviewLayer?.removeFromSuperlayer()
        captureSession = null
        videoPreviewLayer = null
    }
}