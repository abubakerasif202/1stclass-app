package au.com.firstclassexpress.driver.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "job_timeline_events",
    indices = [
        Index(value = ["jobId"]),
        Index(value = ["timestamp"])
    ]
)
data class JobTimelineEventEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val status: String,
    val title: String,
    val description: String?,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val syncStatus: String
)
