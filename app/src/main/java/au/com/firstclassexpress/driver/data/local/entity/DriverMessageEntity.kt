package au.com.firstclassexpress.driver.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "driver_messages",
    indices = [
        Index(value = ["category"]),
        Index(value = ["timestamp"]),
        Index(value = ["isRead"])
    ]
)
data class DriverMessageEntity(
    @PrimaryKey val id: String,
    val category: String,
    val title: String,
    val body: String,
    val jobId: String?,
    val timestamp: Long,
    val isRead: Boolean,
    val urgency: String
)
