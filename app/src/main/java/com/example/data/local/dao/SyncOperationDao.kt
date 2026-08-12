package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOperationDao {
    @Query("SELECT * FROM sync_operations WHERE status != 'SYNCED' ORDER BY createdAt, id")
    fun observePending(): Flow<List<SyncOperationEntity>>

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
}
