package au.com.firstclassexpress.driver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import au.com.firstclassexpress.driver.data.local.entity.DriverMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: DriverMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<DriverMessageEntity>)

    @Query("SELECT * FROM driver_messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<DriverMessageEntity>>

    @Query("SELECT COUNT(*) FROM driver_messages WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE driver_messages SET isRead = 1 WHERE id = :messageId")
    suspend fun markAsRead(messageId: String): Int

    @Query("SELECT COUNT(*) FROM driver_messages")
    suspend fun count(): Int
}
