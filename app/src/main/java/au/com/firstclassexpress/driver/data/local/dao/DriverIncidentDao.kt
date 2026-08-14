package au.com.firstclassexpress.driver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import au.com.firstclassexpress.driver.data.local.entity.DriverIncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverIncidentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incident: DriverIncidentEntity)

    @Query("SELECT * FROM driver_incidents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DriverIncidentEntity>>

    @Query("SELECT * FROM driver_incidents WHERE jobId = :jobId ORDER BY createdAt DESC")
    fun observeByJobId(jobId: String): Flow<List<DriverIncidentEntity>>

    @Query("SELECT * FROM driver_incidents WHERE id = :id")
    suspend fun getById(id: String): DriverIncidentEntity?

    @Query("UPDATE driver_incidents SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String): Int
}
