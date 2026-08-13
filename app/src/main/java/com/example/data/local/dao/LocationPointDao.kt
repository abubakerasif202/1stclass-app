package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.LocationPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(point: LocationPointEntity)

    @Query("SELECT * FROM location_points WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocationPointEntity?

    /** Set only after the TMS has acknowledged the point. */
    @Query("UPDATE location_points SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String): Int

    @Query("SELECT * FROM location_points ORDER BY recordedAt DESC LIMIT 1")
    fun observeLatest(): Flow<LocationPointEntity?>

    @Query("SELECT * FROM location_points WHERE recordedAt >= :notBefore ORDER BY recordedAt DESC LIMIT 1")
    suspend fun latestSince(notBefore: Long): LocationPointEntity?
}
