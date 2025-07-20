package com.hiralen.temubelajar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import temubelajar.composeapp.generated.resources.Res
import temubelajar.composeapp.generated.resources.compose_multiplatform

@Composable
@Preview
fun App(platformContext : PlatformContext) {
    MaterialTheme {
        val cameraManager = remember { CameraManager(platformContext) }

        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = {
                    cameraManager.startCamera(CameraType.FRONT)
                }
            ) {
                Text("Start Front Camera")
            }

            Button(
                onClick = {
                    cameraManager.stopCamera()
                }
            ) {
                Text("Stop Camera")
            }

            CameraPreview(cameraManager)
        }

    }
}