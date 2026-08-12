package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.FreightExceptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FreightExceptionDao {
    @Query("SELECT * FROM freight_exceptions WHERE jobId = :jobId ORDER BY createdAt, id")
    fun observeForJob(jobId: String): Flow<List<FreightExceptionEntity>>

    @Query("SELECT * FROM freight_exceptions WHERE jobId = :jobId ORDER BY createdAt, id")
    suspend fun getForJob(jobId: String): List<FreightExceptionEntity>

    @Query("SELECT * FROM freight_exceptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FreightExceptionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: FreightExceptionEntity)

    @Query("UPDATE freight_exceptions SET resolved = :resolved WHERE id = :id")
    suspend fun updateResolved(id: String, resolved: Boolean): Int
}
