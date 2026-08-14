package au.com.firstclassexpress.driver.location

import au.com.firstclassexpress.driver.domain.model.LocationPoint
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import com.google.android.gms.location.Priority
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationTrackingConfig(
    val stationaryIntervalMillis: Long = 60_000L,
    val enRouteIntervalMillis: Long = 15_000L,
    val nearStopIntervalMillis: Long = 5_000L,
    val minDistanceMeters: Float = 10f,
    val nearStopDistanceMeters: Double = 500.0
)

data class AdaptiveLocationInterval(
    val priority: Int,
    val intervalMillis: Long,
    val minUpdateIntervalMillis: Long,
    val minDistanceMeters: Float,
    val modeDescription: String
)

object AdaptiveLocationFrequency {

    fun calculate(
        isShiftActive: Boolean,
        activeJob: Job?,
        lastPoint: LocationPoint?,
        config: LocationTrackingConfig = LocationTrackingConfig()
    ): AdaptiveLocationInterval? {
        if (!isShiftActive) return null

        if (activeJob == null || activeJob.status.isTerminal) {
            return AdaptiveLocationInterval(
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                intervalMillis = config.stationaryIntervalMillis,
                minUpdateIntervalMillis = config.stationaryIntervalMillis / 2,
                minDistanceMeters = config.minDistanceMeters * 2,
                modeDescription = "On Duty — Stationary / Standby"
            )
        }

        val targetLocation = when (activeJob.status) {
            JobStatus.ASSIGNED, JobStatus.ACCEPTED, JobStatus.IN_PROGRESS -> activeJob.pickup
            JobStatus.AT_PICKUP -> activeJob.pickup
            JobStatus.PICKED_UP, JobStatus.EN_ROUTE_DELIVERY -> activeJob.delivery
            JobStatus.AT_DELIVERY -> activeJob.delivery
            else -> null
        }

        val isNearStop = if (targetLocation != null && lastPoint != null && targetLocation.lat != 0.0 && targetLocation.lng != 0.0) {
            val dist = distanceMeters(
                lastPoint.latitude, lastPoint.longitude,
                targetLocation.lat, targetLocation.lng
            )
            dist <= config.nearStopDistanceMeters
        } else false

        return when {
            isNearStop || activeJob.status == JobStatus.AT_PICKUP || activeJob.status == JobStatus.AT_DELIVERY -> {
                AdaptiveLocationInterval(
                    priority = Priority.PRIORITY_HIGH_ACCURACY,
                    intervalMillis = config.nearStopIntervalMillis,
                    minUpdateIntervalMillis = config.nearStopIntervalMillis / 2,
                    minDistanceMeters = 5f,
                    modeDescription = "Near Site Stop — High Precision"
                )
            }
            activeJob.status == JobStatus.IN_PROGRESS || activeJob.status == JobStatus.EN_ROUTE_DELIVERY -> {
                AdaptiveLocationInterval(
                    priority = Priority.PRIORITY_HIGH_ACCURACY,
                    intervalMillis = config.enRouteIntervalMillis,
                    minUpdateIntervalMillis = config.enRouteIntervalMillis / 2,
                    minDistanceMeters = config.minDistanceMeters,
                    modeDescription = "En Route In Transit — Active Tracking"
                )
            }
            else -> {
                AdaptiveLocationInterval(
                    priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    intervalMillis = config.stationaryIntervalMillis,
                    minUpdateIntervalMillis = config.stationaryIntervalMillis / 2,
                    minDistanceMeters = config.minDistanceMeters,
                    modeDescription = "Active Job — Low Power"
                )
            }
        }
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
