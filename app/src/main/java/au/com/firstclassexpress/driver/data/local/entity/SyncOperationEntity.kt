package au.com.firstclassexpress.driver.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One durable, replayable remote mutation.
 *
 * [id] doubles as the idempotency key sent to the TMS. It is generated once, when the driver's
 * action is persisted, and is reused verbatim on every retry — so a response lost on the wire can
 * never become a duplicate job status or a duplicate POD.
 */
@Entity(
    tableName = "sync_operations",
    indices = [Index("status"), Index(value = ["entityType", "entityId", "operationType"])]
)
data class SyncOperationEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int,
    val lastError: String?,
    val status: String,
    /**
     * Schema version of [payloadJson]. Operations queued before this column existed default to 1,
     * so an app upgrade can still understand work that is already sitting in the queue.
     */
    @ColumnInfo(defaultValue = "1") val payloadVersion: Int = CURRENT_PAYLOAD_VERSION,
    /**
     * When the row last changed state. Used to recover operations left `IN_PROGRESS` by a process
     * that died mid-upload, so nothing can be stuck forever.
     */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L
) {
    companion object {
        const val CURRENT_PAYLOAD_VERSION = 1
    }
}

/** Row count per queue status, for the sync status UI and diagnostics screen. */
data class SyncStatusCount(val status: String, val count: Int)
