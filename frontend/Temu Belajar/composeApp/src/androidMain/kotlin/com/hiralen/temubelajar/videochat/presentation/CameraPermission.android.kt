package com.hiralen.temubelajar.videochat.presentation

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.hiralen.temubelajar.core.ui.TBColors
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
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(32.dp)
                .background(Color(0xFF1A1A2E), RoundedCornerShape(20.dp))
                .padding(28.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(TablerIcons.Camera, contentDescription = null, tint = TBColors.Primary, modifier = Modifier.size(32.dp))
                Icon(TablerIcons.Microphone, contentDescription = null, tint = TBColors.Primary, modifier = Modifier.size(32.dp))
            }
            Text("Izin Kamera & Mikrofon", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "TemuBelajar membutuhkan akses kamera dan mikrofon untuk melakukan video call dengan sesama mahasiswa.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = TBColors.Primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Izinkan Akses", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
