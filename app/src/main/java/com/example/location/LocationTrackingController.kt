package com.example.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class LocationTrackingController(private val context: Context) {
    fun reconcile(onDuty: Boolean, loggedIn: Boolean) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (onDuty && loggedIn && hasPermission) start() else stop()
    }

    fun start() {
        ContextCompat.startForegroundService(
            context, Intent(context, LocationTrackingService::class.java).setAction(ACTION_START)
        )
    }

    fun stop() {
        context.stopService(Intent(context, LocationTrackingService::class.java))
    }

    companion object {
        const val ACTION_START = "com.example.location.START"
        const val ACTION_STOP = "com.example.location.STOP"
    }
}
