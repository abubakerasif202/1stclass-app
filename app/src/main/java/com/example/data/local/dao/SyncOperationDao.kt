package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SyncOperationEntity
import com.example.data.local.entity.SyncStatusCount
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOperationDao {
    @Query("SELECT * FROM sync_operations WHERE status != 'SYNCED' ORDER BY createdAt, id")
    fun observePending(): Flow<List<SyncOperationEntity>>

    @Query("SELECT status, COUNT(*) AS count FROM sync_operations GROUP BY status")
    fun observeStatusCounts(): Flow<List<SyncStatusCount>>

    @Query("SELECT * FROM sync_operations WHERE status != 'SYNCED' ORDER BY createdAt, id LIMIT :limit")
    suspend fun outstanding(limit: Int): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SyncOperationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SyncOperationEntity)

    @Query("UPDATE sync_operations SET retryCount = :retryCount, lastError = :error, status = 'FAILED' WHERE id = :id")
    suspend fun updateFailure(id: String, retryCount: Int, error: String): Int

    @Query("UPDATE sync_operations SET status = :status, lastError = CASE WHEN :status = 'SYNCED' THEN NULL ELSE lastError END WHERE id = :id")
    suspend fun updateStatus(id: String, status: String): Int

    @Query("SELECT COUNT(*) FROM sync_operations WHERE entityType = :entityType AND entityId = :entityId AND operationType = :operationType AND status = 'PENDING'")
    suspend fun pendingCountFor(entityType: String, entityId: String, operationType: String): Int

    // --- Worker queue mechanics -------------------------------------------------------------
    // Claiming is a two-step select-then-conditional-update rather than a single statement so the
    // engine can see *which* row it won. The `AND status = 'PENDING'` guard is what makes it safe:
    // if two workers race for the same row, exactly one UPDATE reports a changed row.

    @Query("SELECT * FROM sync_operations WHERE status = 'PENDING' ORDER BY createdAt, id LIMIT 1")
    suspend fun nextPending(): SyncOperationEntity?

    @Query(
        "UPDATE sync_operations SET status = 'IN_PROGRESS', updatedAt = :now " +
            "WHERE id = :id AND status = 'PENDING'"
    )
    suspend fun claim(id: String, now: Long): Int

    @Query("UPDATE sync_operations SET status = 'SYNCED', lastError = NULL, updatedAt = :now WHERE id = :id")
    suspend fun markSynced(id: String, now: Long): Int

    /** Back to the queue for another attempt; the operation and its data are preserved. */
    @Query(
        "UPDATE sync_operations SET status = 'PENDING', retryCount = :retryCount, " +
            "lastError = :error, updatedAt = :now WHERE id = :id"
    )
    suspend fun markRetryable(id: String, retryCount: Int, error: String, now: Long): Int

    /** Rejected by the server. Kept, visible and inspectable — never deleted. */
    @Query(
        "UPDATE sync_operations SET status = 'FAILED', retryCount = :retryCount, " +
            "lastError = :error, updatedAt = :now WHERE id = :id"
    )
    suspend fun markFailed(id: String, retryCount: Int, error: String, now: Long): Int

    /**
     * Returns operations abandoned `IN_PROGRESS` to the queue. Called at the start of every run so
     * a process killed mid-upload cannot strand work. The idempotency key is unchanged, so a
     * request the server *did* receive before we died is deduplicated server-side on replay.
     */
    @Query(
        "UPDATE sync_operations SET status = 'PENDING', updatedAt = :now " +
            "WHERE status = 'IN_PROGRESS' AND updatedAt <= :staleBefore"
    )
    suspend fun releaseStale(staleBefore: Long, now: Long): Int

    /** Manual "retry failed sync" from the diagnostics screen. */
    @Query(
        "UPDATE sync_operations SET status = 'PENDING', lastError = NULL, updatedAt = :now " +
            "WHERE status = 'FAILED'"
    )
    suspend fun requeueFailed(now: Long): Int

    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = :status")
    suspend fun countByStatus(status: String): Int
}
