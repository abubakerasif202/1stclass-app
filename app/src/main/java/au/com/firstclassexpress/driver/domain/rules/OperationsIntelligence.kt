package au.com.firstclassexpress.driver.domain.rules

import au.com.firstclassexpress.driver.domain.model.LocationPoint
import au.com.firstclassexpress.driver.location.AdaptiveLocationFrequency
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DriverEtaInfo(
    val jobId: String,
    val estimatedArrivalMillis: Long,
    val distanceRemainingMeters: Long,
    val travelTimeRemainingSeconds: Long,
    val routeUpdatedAt: Long,
    val source: String = "TMS_ROUTING"
) {
    val formattedEtaTime: String
        get() = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(estimatedArrivalMillis))

    val formattedDistance: String
        get() = if (distanceRemainingMeters >= 1000) {
            "%.1f km".format(distanceRemainingMeters / 1000.0)
        } else {
            "$distanceRemainingMeters m"
        }
}

enum class DelayReason(val label: String) {
    RUNNING_LATE("Running Late"),
    TRAFFIC("Heavy Traffic"),
    LOADING_DELAY("Loading / Dock Delay"),
    CUSTOMER_DELAY("Customer Delay / Not Ready"),
    VEHICLE_ISSUE("Vehicle / Mechanical Problem"),
    WEATHER("Severe Weather / Road Hazard"),
    OTHER("Other Operational Delay")
}

data class DelayDetectionResult(
    val isDelayed: Boolean,
    val reasonSuggestion: String?,
    val suggestedPrompt: String?
)

object DelayDetectionEngine {

    /**
     * Non-punitive operational delay detection.
     */
    fun evaluate(
        job: Job,
        lastPoint: LocationPoint?,
        stationarySinceMillis: Long?,
        currentTime: Long = System.currentTimeMillis()
    ): DelayDetectionResult {
        if (job.status.isTerminal || job.status.isException) {
            return DelayDetectionResult(isDelayed = false, null, null)
        }

        // 1. Stationary during active transit (> 10 mins stationary while in transit)
        val isEnRoute = job.status == JobStatus.IN_PROGRESS || job.status == JobStatus.EN_ROUTE_DELIVERY
        if (isEnRoute && stationarySinceMillis != null && (currentTime - stationarySinceMillis) > 10 * 60 * 1000L) {
            return DelayDetectionResult(
                isDelayed = true,
                reasonSuggestion = "Vehicle stationary in transit",
                suggestedPrompt = "You appear stationary on your route. Is there traffic or an operational delay?"
            )
        }

        return DelayDetectionResult(isDelayed = false, null, null)
    }
}

data class GeofenceArrivalSuggestion(
    val isNearDestination: Boolean,
    val destinationName: String,
    val distanceMeters: Double,
    val targetStatus: JobStatus,
    val promptText: String
)

object GeofenceArrivalEngine {
    private const val GEOFENCE_RADIUS_METERS = 150.0

    fun checkArrival(
        job: Job,
        currentPoint: LocationPoint?
    ): GeofenceArrivalSuggestion? {
        if (currentPoint == null) return null

        val (targetLocation, targetStatus, stopLabel) = when (job.status) {
            JobStatus.IN_PROGRESS -> Triple(job.pickup, JobStatus.AT_PICKUP, "Pickup (${job.pickup.companyName})")
            JobStatus.EN_ROUTE_DELIVERY -> Triple(job.delivery, JobStatus.AT_DELIVERY, "Delivery (${job.delivery.companyName})")
            else -> return null
        }

        if (targetLocation.lat == 0.0 && targetLocation.lng == 0.0) return null

        val distance = AdaptiveLocationFrequency.distanceMeters(
            currentPoint.latitude, currentPoint.longitude,
            targetLocation.lat, targetLocation.lng
        )

        if (distance <= GEOFENCE_RADIUS_METERS) {
            return GeofenceArrivalSuggestion(
                isNearDestination = true,
                destinationName = targetLocation.companyName,
                distanceMeters = distance,
                targetStatus = targetStatus,
                promptText = "You have arrived at $stopLabel. Would you like to mark as arrived?"
            )
        }

        return null
    }
}
