package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.EvidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence WHERE jobId = :jobId ORDER BY createdAt, id")
    fun observeForJob(jobId: String): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EvidenceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: EvidenceEntity)

    @Query("UPDATE evidence SET localUri = :uri, status = :status WHERE id = :id")
    suspend fun updateSaved(id: String, uri: String, status: String): Int

    @Query("DELETE FROM evidence WHERE id = :id AND status = 'PENDING_CAPTURE'")
    suspend fun deletePending(id: String): Int
}
