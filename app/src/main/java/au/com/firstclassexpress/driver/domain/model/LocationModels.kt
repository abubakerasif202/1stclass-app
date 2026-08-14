package au.com.firstclassexpress.driver.domain.model

data class LocationPoint(
    val id: String,
    val driverId: String,
    val shiftId: String,
    val jobId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    val altitudeMeters: Double? = null,
    val recordedAt: Long,
    val createdAt: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val vehicleId: String? = null,
    val batteryLevel: Int? = null,
    val networkState: String? = null,
    val source: String = "FUSED_LOCATION"
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
        require(accuracyMeters >= 0f) { "Accuracy cannot be negative" }
        require(driverId.isNotBlank()) { "Driver ID is required" }
        require(shiftId.isNotBlank()) { "Shift ID is required" }
    }
}

enum class GpsStatus(val label: String) {
    ACTIVE("Tracking Active"),
    WAITING_FOR_FIX("GPS Searching"),
    LIMITED("GPS Limited"),
    GPS_OFF("Location Services Disabled"),
    PERMISSION_REQUIRED("Location Permission Required"),
    OFFLINE_QUEUED("Offline — Locations Queued"),
    SYNCING("Syncing Locations"),
    PAUSED("Tracking Paused"),
    OFF("Tracking Off")
}

data class LocationTrackingState(
    val status: GpsStatus = GpsStatus.OFF,
    val lastPoint: LocationPoint? = null,
    val isShiftActive: Boolean = false,
    val queuedCount: Int = 0
)

fun trackingStatusFor(
    onDuty: Boolean,
    hasCoarsePermission: Boolean,
    hasFinePermission: Boolean,
    locationServicesEnabled: Boolean,
    hasFix: Boolean,
    isOffline: Boolean = false,
    isSyncing: Boolean = false,
    isPaused: Boolean = false
): GpsStatus = when {
    !onDuty -> GpsStatus.OFF
    !hasCoarsePermission -> GpsStatus.PERMISSION_REQUIRED
    !locationServicesEnabled -> GpsStatus.GPS_OFF
    isPaused -> GpsStatus.PAUSED
    isSyncing -> GpsStatus.SYNCING
    isOffline -> GpsStatus.OFFLINE_QUEUED
    hasFix && hasFinePermission -> GpsStatus.ACTIVE
    hasFix -> GpsStatus.LIMITED
    else -> GpsStatus.WAITING_FOR_FIX
}
