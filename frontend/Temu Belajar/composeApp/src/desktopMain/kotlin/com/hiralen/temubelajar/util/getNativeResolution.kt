package com.hiralen.temubelajar.util

import org.bytedeco.javacv.OpenCVFrameGrabber

internal fun getNativeResolution(cameraIndex: Int): Pair<Int, Int>? {
    return try {
        val grabber = OpenCVFrameGrabber.createDefault(cameraIndex)
        grabber.start()
        val width = grabber.imageWidth
        val height = grabber.imageHeight
        grabber.stop()
        Pair(width, height)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}