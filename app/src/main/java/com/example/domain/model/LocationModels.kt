package com.example.domain.model

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
    val syncStatus: SyncStatus = SyncStatus.PENDING
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
        require(accuracyMeters >= 0f) { "Accuracy cannot be negative" }
        require(driverId.isNotBlank()) { "Driver ID is required" }
        require(shiftId.isNotBlank()) { "Shift ID is required" }
    }
}

enum class GpsStatus {
    ACTIVE,
    WAITING_FOR_FIX,
    LIMITED,
    GPS_OFF,
    PERMISSION_REQUIRED,
    OFF
}

data class LocationTrackingState(
    val status: GpsStatus = GpsStatus.OFF,
    val lastPoint: LocationPoint? = null,
    val isShiftActive: Boolean = false
)

fun trackingStatusFor(
    onDuty: Boolean,
    hasCoarsePermission: Boolean,
    hasFinePermission: Boolean,
    locationServicesEnabled: Boolean,
    hasFix: Boolean
): GpsStatus = when {
    !onDuty -> GpsStatus.OFF
    !hasCoarsePermission -> GpsStatus.PERMISSION_REQUIRED
    !locationServicesEnabled -> GpsStatus.GPS_OFF
    hasFix && hasFinePermission -> GpsStatus.ACTIVE
    hasFix -> GpsStatus.LIMITED
    else -> GpsStatus.WAITING_FOR_FIX
}
