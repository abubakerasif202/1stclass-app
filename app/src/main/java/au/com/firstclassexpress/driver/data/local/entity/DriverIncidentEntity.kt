package au.com.firstclassexpress.driver.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "driver_incidents",
    indices = [
        Index(value = ["driverId"]),
        Index(value = ["jobId"]),
        Index(value = ["createdAt"])
    ]
)
data class DriverIncidentEntity(
    @PrimaryKey val id: String,
    val driverId: String,
    val shiftId: String?,
    val jobId: String?,
    val category: String,
    val severity: String,
    val description: String,
    val photoUri: String?,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: Long,
    val syncStatus: String
)
