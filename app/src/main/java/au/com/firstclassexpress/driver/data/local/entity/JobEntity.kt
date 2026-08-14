package au.com.firstclassexpress.driver.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val payloadJson: String,
    val status: String,
    val updatedAt: Long,
    val revision: Long = 1L,
    val serverUpdatedAt: Long? = null
)
