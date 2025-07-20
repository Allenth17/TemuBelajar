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
import java.awt.image.BufferedImage

actual class CameraManager actual constructor(

    // constructor
    private val context: PlatformContext

) {
    private var webcam: Webcam? = null
    private var frame: BufferedImage? by mutableStateOf(null)

    actual fun startCamera(cameraType: CameraType) {
        val devices = Webcam.getWebcams()
        val deviceIndex = if (cameraType == CameraType.FRONT) devices.size - 1 else 0

        if (devices.isEmpty()) return

        webcam = devices[deviceIndex]

        webcam?.apply {
            viewSize = WebcamResolution.VGA.size

            addWebcamListener(
                object : WebcamListener {
                    override fun webcamOpen(we: WebcamEvent) {
                        TODO("Not yet implemented")
                    }

                    override fun webcamClosed(we: WebcamEvent) {
                        TODO("Not yet implemented")
                    }

                    override fun webcamDisposed(we: WebcamEvent) {
                        TODO("Not yet implemented")
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

    fun getCurrentFrame(): ImageBitmap? {
        return frame?.toComposeImageBitmap()
    }
}