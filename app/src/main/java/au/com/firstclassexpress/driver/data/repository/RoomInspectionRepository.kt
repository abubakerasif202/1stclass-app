package au.com.firstclassexpress.driver.data.repository

import androidx.room.withTransaction
import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.entity.InspectionEntity
import au.com.firstclassexpress.driver.data.local.entity.InspectionItemEntity
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import au.com.firstclassexpress.driver.domain.model.DefectSeverity
import au.com.firstclassexpress.driver.domain.model.InspectionAnswer
import au.com.firstclassexpress.driver.domain.model.InspectionChecklist
import au.com.firstclassexpress.driver.domain.model.InspectionItemRecord
import au.com.firstclassexpress.driver.domain.model.InspectionItemStatus
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.model.ValidationResult
import au.com.firstclassexpress.driver.domain.repository.InspectionRepository
import au.com.firstclassexpress.driver.domain.rules.InspectionRules
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomInspectionRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : InspectionRepository {
    private val dao = database.inspectionDao()
    private val syncDao = database.syncOperationDao()

    override fun observeItems(shiftId: String): Flow<List<InspectionItemRecord>> =
        dao.observeItems(shiftId).map { rows -> rows.map { it.toDomain() } }

    override fun observeDeclaration(shiftId: String): Flow<Boolean> =
        dao.observeDeclaration(shiftId).map { it ?: false }

    override suspend fun ensureForShift(shiftId: String, hasTrailer: Boolean): Result<Unit> = runCatching {
        database.withTransaction {
            requireNotNull(database.shiftDao().getById(shiftId)) { "Shift not found" }
            if (dao.getInspectionForShift(shiftId) != null) return@withTransaction

            val inspectionId = idGenerator()
            dao.insertInspection(
                InspectionEntity(
                    id = inspectionId,
                    shiftId = shiftId,
                    declarationAccepted = false,
                    validationState = null,
                    completedAt = null
                )
            )
            dao.insertItems(
                InspectionChecklist.items(hasTrailer).map { checklist ->
                    InspectionItemEntity(
                        id = idGenerator(),
                        inspectionId = inspectionId,
                        shiftId = shiftId,
                        code = checklist.code,
                        label = checklist.label,
                        category = checklist.category,
                        mandatory = checklist.mandatory,
                        status = InspectionItemStatus.UNANSWERED.name,
                        defectDescription = null,
                        defectSeverity = null
                    )
                }
            )
        }
    }

    override suspend fun saveAnswer(itemId: String, answer: InspectionAnswer): Result<Unit> = runCatching {
        database.withTransaction {
            val existing = requireNotNull(dao.getItem(itemId)) { "Inspection item not found" }
            require(existing.code == answer.itemCode) { "Inspection answer does not match item" }
            check(
                dao.updateItem(
                    id = itemId,
                    status = answer.status.name,
                    description = answer.defectDescription?.trim()?.takeIf { it.isNotEmpty() },
                    severity = answer.defectSeverity?.name
                ) == 1
            ) { "Failed to save inspection answer" }
            enqueue(
                entityType = "INSPECTION_ITEM",
                entityId = itemId,
                operationType = "ANSWER",
                payloadJson = "{\"status\":\"${answer.status.name}\"}"
            )
        }
    }

    override suspend fun setDeclaration(shiftId: String, accepted: Boolean): Result<Unit> = runCatching {
        database.withTransaction {
            check(dao.updateDeclaration(shiftId, accepted) == 1) { "Inspection not found" }
            enqueue(
                entityType = "INSPECTION",
                entityId = shiftId,
                operationType = "DECLARATION",
                payloadJson = "{\"accepted\":$accepted}"
            )
        }
    }

    override suspend fun complete(shiftId: String): ValidationResult {
        val validation = currentValidation(shiftId)
        if (validation is ValidationResult.Invalid) return validation

        return runCatching {
            database.withTransaction {
                val state = when (validation) {
                    ValidationResult.Valid -> "VALID"
                    is ValidationResult.Blocked -> "BLOCKED"
                    is ValidationResult.Invalid -> error("Invalid inspections cannot complete")
                }
                val completedAt = clock()
                check(dao.markCompleted(shiftId, state, completedAt) == 1) { "Inspection not found" }
                enqueue(
                    entityType = "INSPECTION",
                    entityId = shiftId,
                    operationType = "COMPLETE",
                    payloadJson = "{\"validationState\":\"$state\"}",
                    createdAt = completedAt
                )
                validation
            }
        }.getOrElse { ValidationResult.Invalid(listOf(it.message ?: "Failed to complete inspection")) }
    }

    override suspend fun currentValidation(shiftId: String): ValidationResult {
        val inspection = dao.getInspectionForShift(shiftId)
            ?: return ValidationResult.Invalid(listOf("Inspection has not been created"))
        val rows = dao.getItems(shiftId)
        if (rows.isEmpty()) return ValidationResult.Invalid(listOf("Inspection checklist is missing"))
        val answers = rows.map { row ->
            InspectionAnswer(
                itemCode = row.code,
                mandatory = row.mandatory,
                status = InspectionItemStatus.valueOf(row.status),
                defectDescription = row.defectDescription,
                defectSeverity = row.defectSeverity?.let(DefectSeverity::valueOf)
            )
        }
        return InspectionRules.validate(answers, inspection.declarationAccepted)
    }

    private suspend fun enqueue(
        entityType: String,
        entityId: String,
        operationType: String,
        payloadJson: String,
        createdAt: Long = clock()
    ) {
        syncDao.insert(
            SyncOperationEntity(
                id = idGenerator(),
                entityType = entityType,
                entityId = entityId,
                operationType = operationType,
                payloadJson = payloadJson,
                createdAt = createdAt,
                retryCount = 0,
                lastError = null,
                status = SyncStatus.PENDING.name
            )
        )
    }

    private fun InspectionItemEntity.toDomain() = InspectionItemRecord(
        id = id,
        shiftId = shiftId,
        code = code,
        label = label,
        category = category,
        mandatory = mandatory,
        status = InspectionItemStatus.valueOf(status),
        defectDescription = defectDescription,
        defectSeverity = defectSeverity?.let(DefectSeverity::valueOf)
    )
}
