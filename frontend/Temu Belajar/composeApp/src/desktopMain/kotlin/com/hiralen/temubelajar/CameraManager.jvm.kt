package com.hiralen.temubelajar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.sarxos.webcam.Webcam
import com.github.sarxos.webcam.WebcamEvent
import com.github.sarxos.webcam.WebcamListener
import com.github.sarxos.webcam.WebcamResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.image.BufferedImage

actual class CameraManager actual constructor(
    private val context: PlatformContext
) {
    private var webcam: Webcam? = null
    private var frame: BufferedImage? = null
    private val _imageFlow = MutableStateFlow<ImageBitmap?>(null)
    val imageFlow: StateFlow<ImageBitmap?> = _imageFlow

    actual fun startCamera(cameraType: CameraType) {
        val devices = Webcam.getWebcams()
        println("Devices found: ${devices.size}")
        val deviceIndex = if (cameraType == CameraType.FRONT) devices.size - 1 else 0
        if (devices.isEmpty()) return
        webcam = devices[deviceIndex]
        webcam?.apply {
            viewSize = WebcamResolution.VGA.size
            addWebcamListener(
                object : WebcamListener {
                    override fun webcamOpen(we: WebcamEvent) {
                        frame = we.image
                        _imageFlow.value = frame?.toComposeImageBitmap()

                    }
                    override fun webcamClosed(we: WebcamEvent) {

                    }
                    override fun webcamDisposed(we: WebcamEvent) {

                    }
                    override fun webcamImageObtained(we: WebcamEvent) {
                        frame = we.image
                    }
                }
            )
            open()
        }
    }

    actual fun stopCamera() {
        webcam?.close()
    }

    fun getCurrentFrame(): ImageBitmap? = _imageFlow.value
}