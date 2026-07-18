package com.hiralen.temubelajar.videochat.presentation

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.hiralen.temubelajar.core.ui.LinearColors
import com.hiralen.temubelajar.core.ui.TBShapes
import com.hiralen.temubelajar.core.ui.TBSpace
import com.hiralen.temubelajar.core.ui.TBTypography
import compose.icons.TablerIcons
import compose.icons.tablericons.Camera
import compose.icons.tablericons.Microphone

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun CameraPermission(content: @Composable () -> Unit) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    when {
        permissionsState.allPermissionsGranted -> content()

        permissionsState.shouldShowRationale -> {
            PermissionRationaleScreen(
                onRequest = { permissionsState.launchMultiplePermissionRequest() }
            )
        }

        else -> {
            LaunchedEffect(Unit) {
                permissionsState.launchMultiplePermissionRequest()
            }
            PermissionRationaleScreen(
                onRequest = { permissionsState.launchMultiplePermissionRequest() }
            )
        }
    }
}

@Composable
private fun PermissionRationaleScreen(onRequest: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(LinearColors.Canvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TBSpace.MD),
            modifier = Modifier
                .padding(TBSpace.XL)
                .clip(TBShapes.XL)
                .background(LinearColors.Surface2)
                .border(1.dp, LinearColors.Hairline, TBShapes.XL)
                .padding(28.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TBSpace.SM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(TablerIcons.Camera, contentDescription = null, tint = LinearColors.Primary, modifier = Modifier.size(32.dp))
                Icon(TablerIcons.Microphone, contentDescription = null, tint = LinearColors.Primary, modifier = Modifier.size(32.dp))
            }
            Text(
                "Izin Kamera & Mikrofon",
                color = LinearColors.Ink,
                style = TBTypography.Headline,
                textAlign = TextAlign.Center
            )
            Text(
                "TemuBelajar membutuhkan akses kamera dan mikrofon untuk melakukan video call dengan sesama mahasiswa.",
                color = LinearColors.InkMuted,
                style = TBTypography.Body,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = LinearColors.Primary),
                shape = TBShapes.MD,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Izinkan Akses", style = TBTypography.Button)
            }
        }
    }
}
