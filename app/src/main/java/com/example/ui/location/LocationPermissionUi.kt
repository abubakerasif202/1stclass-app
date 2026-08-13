package com.example.ui.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
fun LocationPermissionCoordinator(onDuty: Boolean, onPermissionChanged: () -> Unit) {
    val context = LocalContext.current
    var explain by rememberSaveable { mutableStateOf(false) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        requested = true
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        permanentlyDenied = !granted && !ActivityCompat.shouldShowRequestPermissionRationale(
            context.findActivity(), Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (!granted) explain = true
        onPermissionChanged()
    }
    LaunchedEffect(onDuty) {
        if (onDuty && !context.hasLocationPermission() && !requested) explain = true
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text("Allow shift location") },
            text = { Text("Location is used while you are on shift to support dispatch, job operations and navigation. You can continue working if location is unavailable.") },
            confirmButton = {
                TextButton(onClick = {
                    explain = false
                    if (permanentlyDenied) context.openAppSettings() else launcher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }) { Text(if (permanentlyDenied) "Open App Settings" else "Continue") }
            },
            dismissButton = { TextButton(onClick = { explain = false; requested = true }) { Text("Not now") } }
        )
    }
}

fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> error("Activity context is required")
}
