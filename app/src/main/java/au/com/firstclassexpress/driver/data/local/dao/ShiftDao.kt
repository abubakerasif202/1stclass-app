package au.com.firstclassexpress.driver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import au.com.firstclassexpress.driver.data.local.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE phase != 'OFF_DUTY' ORDER BY createdAt DESC LIMIT 1")
    fun observeCurrent(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE phase IN ('ON_DUTY', 'ON_BREAK') ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActive(): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ShiftEntity?

    /**
     * The shift that was running at [at]. Lets the sync engine attribute a queued job status
     * change to the shift it actually happened on, rather than to whatever shift happens to be
     * active when the network finally comes back.
     */
    @Query(
        "SELECT * FROM shifts WHERE startedAt IS NOT NULL AND startedAt <= :at " +
            "AND (endedAt IS NULL OR endedAt >= :at) ORDER BY startedAt DESC LIMIT 1"
    )
    suspend fun activeAt(at: Long): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ShiftEntity)

    @Query("UPDATE shifts SET phase = :phase, startedAt = :startedAt WHERE id = :id")
    suspend fun updatePhase(id: String, phase: String, startedAt: Long?): Int

    @Query("UPDATE shifts SET phase = 'OFF_DUTY', endOdometer = :endOdometer, endedAt = :endedAt WHERE id = :id")
    suspend fun endShift(id: String, endOdometer: Long, endedAt: Long): Int
}
