package au.com.firstclassexpress.driver.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single piece of proof captured against a job (photo, signature, defect image).
 *
 * [createdAt] is when capture was requested; [savedAt] is only set once a file has actually been
 * written to disk, which is also the only point at which the status becomes SAVED_LOCAL.
 */
@Entity(
    tableName = "evidence",
    indices = [Index("jobId"), Index("status")]
)
data class EvidenceEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val type: String,
    val localUri: String?,
    val status: String,
    val createdAt: Long,
    val driverId: String? = null,
    val shiftId: String? = null,
    val signerName: String? = null,
    val notes: String? = null,
    val fileSizeBytes: Long? = null,
    val savedAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val locationRecordedAt: Long? = null
)
