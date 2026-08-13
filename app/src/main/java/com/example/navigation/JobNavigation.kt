package com.example.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.model.Location

object JobNavigation {
    fun destinationUri(location: Location): Uri? {
        val hasCoordinates = location.lat in -90.0..90.0 && location.lng in -180.0..180.0 &&
            !(location.lat == 0.0 && location.lng == 0.0)
        if (hasCoordinates) return Uri.parse("google.navigation:q=${location.lat},${location.lng}")
        val address = listOf(location.address, location.suburb).filter(String::isNotBlank).joinToString(", ")
        return address.takeIf(String::isNotBlank)?.let { Uri.parse("geo:0,0?q=${Uri.encode(it)}") }
    }

    fun launch(context: Context, location: Location): Boolean {
        val uri = destinationUri(location) ?: return false
        val generic = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(Intent(generic).setPackage("com.google.android.apps.maps"))
            true
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(generic)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}
