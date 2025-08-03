package com.hiralen.temubelajar

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.hiralen.temubelajar.util.PermissionDenied
import com.hiralen.temubelajar.util.PermissionRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun CameraView() {
    val context = LocalContext.current
    val permissionState = rememberPermissionState(permission = android.Manifest.permission.CAMERA)

    var showRationale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (permissionState.status != PermissionStatus.Granted) {
            permissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(permissionState.status) {
        showRationale = when (permissionState.status) {
            PermissionStatus.Granted -> {
                false
            }

            is PermissionStatus.Denied -> {
                (permissionState.status as PermissionStatus.Denied)
                    .shouldShowRationale
            }
        }
    }

    when {
        permissionState.status == PermissionStatus.Granted -> {
            CameraPreview()
        }
        showRationale -> {
            PermissionRationale(
                onConfirm = {
                    permissionState.launchPermissionRequest()
                },
                onDismiss = {
                    showRationale = false
                }
            )
        }
        else -> {
            PermissionDenied {
                val intent = try {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                } catch (_: Exception) {
                    Intent(Settings.ACTION_SETTINGS)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open settings: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}