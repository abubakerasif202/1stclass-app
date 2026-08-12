package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.JobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY updatedAt DESC, id")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT COUNT(*) FROM jobs")
    suspend fun count(): Int

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): JobEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jobs: List<JobEntity>)

    @Query("UPDATE jobs SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long): Int
}
