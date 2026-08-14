package au.com.firstclassexpress.driver.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import au.com.firstclassexpress.driver.data.local.entity.InspectionEntity
import au.com.firstclassexpress.driver.data.local.entity.InspectionItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspection_items WHERE shiftId = :shiftId ORDER BY category, label")
    fun observeItems(shiftId: String): Flow<List<InspectionItemEntity>>

    @Query("SELECT * FROM inspection_items WHERE shiftId = :shiftId ORDER BY category, label")
    suspend fun getItems(shiftId: String): List<InspectionItemEntity>

    @Query("SELECT declarationAccepted FROM inspections WHERE shiftId = :shiftId LIMIT 1")
    fun observeDeclaration(shiftId: String): Flow<Boolean?>

    @Query("SELECT * FROM inspections WHERE shiftId = :shiftId LIMIT 1")
    suspend fun getInspectionForShift(shiftId: String): InspectionEntity?

    @Query("SELECT * FROM inspection_items WHERE id = :id LIMIT 1")
    suspend fun getItem(id: String): InspectionItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInspection(entity: InspectionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<InspectionItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InspectionItemEntity)

    @Query("UPDATE inspection_items SET status = :status, defectDescription = :description, defectSeverity = :severity WHERE id = :id")
    suspend fun updateItem(id: String, status: String, description: String?, severity: String?): Int

    @Query("UPDATE inspections SET declarationAccepted = :accepted WHERE shiftId = :shiftId")
    suspend fun updateDeclaration(shiftId: String, accepted: Boolean): Int

    @Query("UPDATE inspections SET validationState = :validationState, completedAt = :completedAt WHERE shiftId = :shiftId")
    suspend fun markCompleted(shiftId: String, validationState: String, completedAt: Long): Int
}
