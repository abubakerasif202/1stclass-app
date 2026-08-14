package au.com.firstclassexpress.driver.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_points",
    indices = [
        Index("driverId"),
        Index("shiftId"),
        Index("jobId"),
        Index("recordedAt"),
        Index("syncStatus")
    ]
)
data class LocationPointEntity(
    @PrimaryKey val id: String,
    val driverId: String,
    val shiftId: String,
    val jobId: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val altitudeMeters: Double?,
    val recordedAt: Long,
    val createdAt: Long,
    val syncStatus: String,
    val vehicleId: String? = null,
    val batteryLevel: Int? = null,
    val networkState: String? = null,
    val source: String = "FUSED_LOCATION"
)
