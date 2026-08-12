package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE phase != 'OFF_DUTY' ORDER BY createdAt DESC LIMIT 1")
    fun observeCurrent(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ShiftEntity)

    @Query("UPDATE shifts SET phase = :phase, startedAt = :startedAt WHERE id = :id")
    suspend fun updatePhase(id: String, phase: String, startedAt: Long?): Int

    @Query("UPDATE shifts SET phase = 'OFF_DUTY', endOdometer = :endOdometer, endedAt = :endedAt WHERE id = :id")
    suspend fun endShift(id: String, endOdometer: Long, endedAt: Long): Int
}
