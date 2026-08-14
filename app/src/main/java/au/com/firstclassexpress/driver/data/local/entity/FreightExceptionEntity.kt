package au.com.firstclassexpress.driver.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A freight discrepancy recorded against a job (damage, shortage, refusal, access problem, …).
 *
 * Exceptions are persisted locally so they can later be pushed to the TMS through the existing
 * durable sync queue.
 */
@Entity(
    tableName = "freight_exceptions",
    indices = [Index("jobId"), Index("status")]
)
data class FreightExceptionEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val stage: String,
    val reason: String,
    val notes: String,
    val driverId: String,
    val shiftId: String?,
    val resolved: Boolean,
    val createdAt: Long,
    val status: String
)
