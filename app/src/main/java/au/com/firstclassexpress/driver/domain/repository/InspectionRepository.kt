package au.com.firstclassexpress.driver.domain.repository

import au.com.firstclassexpress.driver.domain.model.InspectionAnswer
import au.com.firstclassexpress.driver.domain.model.InspectionItemRecord
import au.com.firstclassexpress.driver.domain.model.ValidationResult
import kotlinx.coroutines.flow.Flow

interface InspectionRepository {
    fun observeItems(shiftId: String): Flow<List<InspectionItemRecord>>
    fun observeDeclaration(shiftId: String): Flow<Boolean>
    suspend fun ensureForShift(shiftId: String, hasTrailer: Boolean): Result<Unit>
    suspend fun saveAnswer(itemId: String, answer: InspectionAnswer): Result<Unit>
    suspend fun setDeclaration(shiftId: String, accepted: Boolean): Result<Unit>
    suspend fun complete(shiftId: String): ValidationResult
    suspend fun currentValidation(shiftId: String): ValidationResult
}
