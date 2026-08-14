package au.com.firstclassexpress.driver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import au.com.firstclassexpress.driver.data.local.entity.JobTimelineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobTimelineEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: JobTimelineEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<JobTimelineEventEntity>)

    @Query("SELECT * FROM job_timeline_events WHERE jobId = :jobId ORDER BY timestamp ASC")
    fun observeByJobId(jobId: String): Flow<List<JobTimelineEventEntity>>

    @Query("SELECT * FROM job_timeline_events WHERE jobId = :jobId ORDER BY timestamp ASC")
    suspend fun getByJobId(jobId: String): List<JobTimelineEventEntity>

    @Query("SELECT COUNT(*) FROM job_timeline_events WHERE jobId = :jobId")
    suspend fun countForJob(jobId: String): Int
}
