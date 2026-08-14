package au.com.firstclassexpress.driver.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import au.com.firstclassexpress.driver.model.Location

enum class PreferredNavApp(val label: String) {
    GOOGLE_MAPS("Google Maps"),
    WAZE("Waze"),
    CHOOSER("Ask Every Time (System Chooser)")
}

object JobNavigation {
    fun destinationUri(location: Location): Uri? {
        val hasCoordinates = location.lat in -90.0..90.0 && location.lng in -180.0..180.0 &&
            !(location.lat == 0.0 && location.lng == 0.0)
        if (hasCoordinates) return Uri.parse("google.navigation:q=${location.lat},${location.lng}")
        val address = listOf(location.address, location.suburb).filter(String::isNotBlank).joinToString(", ")
        return address.takeIf(String::isNotBlank)?.let { Uri.parse("geo:0,0?q=${Uri.encode(it)}") }
    }

    fun wazeUri(location: Location): Uri? {
        val hasCoordinates = location.lat in -90.0..90.0 && location.lng in -180.0..180.0 &&
            !(location.lat == 0.0 && location.lng == 0.0)
        if (hasCoordinates) return Uri.parse("https://waze.com/ul?ll=${location.lat},${location.lng}&navigate=yes")
        val address = listOf(location.address, location.suburb).filter(String::isNotBlank).joinToString(", ")
        return address.takeIf(String::isNotBlank)?.let { Uri.parse("https://waze.com/ul?q=${Uri.encode(it)}&navigate=yes") }
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

    fun launchWaze(context: Context, location: Location): Boolean {
        val uri = wazeUri(location) ?: return false
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.waze")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            launch(context, location)
        }
    }

    fun launchChooser(context: Context, location: Location): Boolean {
        val uri = destinationUri(location) ?: return false
        val generic = Intent(Intent.ACTION_VIEW, uri)
        val chooser = Intent.createChooser(generic, "Navigate with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            launch(context, location)
        }
    }

    fun launchPreferred(
        context: Context,
        location: Location,
        preferredApp: PreferredNavApp = PreferredNavApp.CHOOSER
    ): Boolean = when (preferredApp) {
        PreferredNavApp.GOOGLE_MAPS -> launch(context, location)
        PreferredNavApp.WAZE -> launchWaze(context, location)
        PreferredNavApp.CHOOSER -> launchChooser(context, location)
    }

    fun dialPhone(context: Context, phone: String?): Boolean {
        if (phone.isNullOrBlank()) return false
        val uri = Uri.parse("tel:${phone.trim()}")
        val intent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
