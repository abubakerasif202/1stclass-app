package au.com.firstclassexpress.driver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import au.com.firstclassexpress.driver.data.local.entity.LocationPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(point: LocationPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<LocationPointEntity>)

    @Query("SELECT * FROM location_points WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocationPointEntity?

    /** Set only after the TMS has acknowledged the point. */
    @Query("UPDATE location_points SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String): Int

    @Query("UPDATE location_points SET syncStatus = :status WHERE id IN (:ids)")
    suspend fun updateSyncStatusForIds(ids: List<String>, status: String): Int

    @Query("SELECT * FROM location_points WHERE syncStatus = 'PENDING' ORDER BY recordedAt ASC LIMIT :limit")
    suspend fun getPendingPoints(limit: Int): List<LocationPointEntity>

    @Query("SELECT COUNT(*) FROM location_points WHERE syncStatus = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM location_points WHERE syncStatus = 'PENDING'")
    suspend fun countPending(): Int

    @Query("SELECT * FROM location_points ORDER BY recordedAt DESC LIMIT 1")
    fun observeLatest(): Flow<LocationPointEntity?>

    @Query("SELECT * FROM location_points WHERE recordedAt >= :notBefore ORDER BY recordedAt DESC LIMIT 1")
    suspend fun latestSince(notBefore: Long): LocationPointEntity?

    @Query("DELETE FROM location_points WHERE syncStatus = 'SYNCED' AND recordedAt < :beforeTimestamp")
    suspend fun pruneSyncedBefore(beforeTimestamp: Long): Int
}
