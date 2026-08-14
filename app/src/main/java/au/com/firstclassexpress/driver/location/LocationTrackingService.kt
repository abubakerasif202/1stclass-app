package au.com.firstclassexpress.driver.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import au.com.firstclassexpress.driver.FirstClassExpressApplication
import au.com.firstclassexpress.driver.R
import au.com.firstclassexpress.driver.domain.model.GpsStatus
import au.com.firstclassexpress.driver.domain.model.LocationPoint
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container by lazy { (application as FirstClassExpressApplication).container }
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var callback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == LocationTrackingController.ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        scope.launch { beginIfAllowed() }
        return START_NOT_STICKY
    }

    private suspend fun beginIfAllowed() {
        val shift = container.database.shiftDao().getActive()
        if (shift == null) {
            stopTracking()
            return
        }
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!coarse) {
            container.locationStateStore.updateStatus(GpsStatus.PERMISSION_REQUIRED, true)
            stopTracking()
            return
        }
        val manager = getSystemService(LocationManager::class.java)
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
        if (!locationEnabled) {
            container.locationStateStore.updateStatus(GpsStatus.GPS_OFF, true)
            stopTracking()
            return
        }
        if (callback != null) return
        container.locationStateStore.updateStatus(GpsStatus.WAITING_FOR_FIX, true)

        val activeCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val battery = getBatteryPercentage(this@LocationTrackingService)
                val network = getNetworkType(this@LocationTrackingService)

                result.locations.forEach { location ->
                    if (!location.hasAccuracy() || location.accuracy < 0f) return@forEach
                    val point = LocationPoint(
                        id = UUID.randomUUID().toString(),
                        driverId = shift.driverId,
                        shiftId = shift.id,
                        vehicleId = shift.vehicleId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy,
                        speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
                        bearingDegrees = location.bearing.takeIf { location.hasBearing() },
                        altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
                        batteryLevel = battery,
                        networkState = network,
                        source = "FUSED_LOCATION",
                        recordedAt = location.time,
                        createdAt = System.currentTimeMillis()
                    )
                    scope.launch {
                        container.locationRepository.save(point).onSuccess {
                            container.locationStateStore.updatePoint(point, fine)
                        }
                    }
                }
            }
        }
        callback = activeCallback

        val request = LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 15_000L)
            .setMinUpdateIntervalMillis(10_000L)
            .setMaxUpdateDelayMillis(25_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()
        client.requestLocationUpdates(request, activeCallback, mainLooper)
    }

    private fun stopTracking() {
        callback?.let(client::removeLocationUpdates)
        callback = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        callback?.let(client::removeLocationUpdates)
        callback = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Shift Live Tracking", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Active driver location telemetry transmission to dispatch"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle("1st Class Express Fleet Tracking")
        .setContentText("Location sharing active — transmitting to Operations")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private companion object {
        const val CHANNEL_ID = "shift_tracking"
        const val NOTIFICATION_ID = 18492

        fun getBatteryPercentage(context: Context): Int? {
            return try {
                val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) (level * 100) / scale else null
            } catch (_: Exception) {
                null
            }
        }

        fun getNetworkType(context: Context): String {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "UNKNOWN"
                val activeNetwork = cm.activeNetwork ?: return "OFFLINE"
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "OFFLINE"
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                    else -> "CONNECTED"
                }
            } catch (_: Exception) {
                "UNKNOWN"
            }
        }
    }
}
