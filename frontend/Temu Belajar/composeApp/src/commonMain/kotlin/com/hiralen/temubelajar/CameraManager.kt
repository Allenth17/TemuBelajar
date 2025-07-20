package com.hiralen.temubelajar

expect class CameraManager(context: PlatformContext) {
    fun startCamera(cameraType: CameraType)
    fun stopCamera()
}