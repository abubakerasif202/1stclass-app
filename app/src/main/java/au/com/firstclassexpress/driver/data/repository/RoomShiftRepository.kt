package au.com.firstclassexpress.driver.data.repository

import androidx.room.withTransaction
import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.entity.ShiftEntity
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import au.com.firstclassexpress.driver.domain.model.ShiftPhase
import au.com.firstclassexpress.driver.domain.model.ShiftRecord
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.model.ValidationResult
import au.com.firstclassexpress.driver.domain.repository.InspectionRepository
import au.com.firstclassexpress.driver.domain.repository.ShiftRepository
import au.com.firstclassexpress.driver.domain.rules.ShiftRules
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomShiftRepository(
    private val database: AppDatabase,
    private val inspectionRepository: InspectionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : ShiftRepository {
    private val shiftDao = database.shiftDao()
    private val syncDao = database.syncOperationDao()

    override fun observeCurrentShift(): Flow<ShiftRecord?> =
        shiftDao.observeCurrent().map { it?.toDomain() }

    override suspend fun createPreStartDraft(
        driverId: String,
        vehicleId: String,
        trailerId: String?,
        startOdometer: Long
    ): Result<String> = runCatching {
        require(driverId.isNotBlank()) { "Driver ID is required" }
        require(vehicleId.isNotBlank()) { "Vehicle ID is required" }
        require(startOdometer >= 0L) { "Odometer cannot be negative" }

        database.withTransaction {
            val id = idGenerator()
            val createdAt = clock()
            shiftDao.insert(
                ShiftEntity(
                    id = id,
                    driverId = driverId,
                    vehicleId = vehicleId.trim().uppercase(),
                    trailerId = trailerId?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
                    startOdometer = startOdometer,
                    endOdometer = null,
                    phase = ShiftPhase.PRESTART_REQUIRED.name,
                    createdAt = createdAt,
                    startedAt = null,
                    endedAt = null
                )
            )
            enqueue(
                entityId = id,
                operationType = "CREATE_DRAFT",
                payloadJson = "{\"vehicleId\":\"${vehicleId.trim().uppercase()}\",\"startOdometer\":$startOdometer}",
                createdAt = createdAt
            )
            id
        }
    }

    override suspend fun markReadyToStart(shiftId: String): Result<Unit> = runCatching {
        val validation = inspectionRepository.currentValidation(shiftId)
        require(ShiftRules.canMarkReady(validation)) { validation.message("Inspection is not ready") }
        database.withTransaction {
            val shift = requireNotNull(shiftDao.getById(shiftId)) { "Shift not found" }
            require(shift.phase == ShiftPhase.PRESTART_REQUIRED.name) { "Shift is not awaiting pre-start" }
            check(shiftDao.updatePhase(shiftId, ShiftPhase.READY_TO_START.name, null) == 1) {
                "Failed to mark shift ready"
            }
            enqueue(shiftId, "READY_TO_START", "{}")
        }
    }

    override suspend fun activateShift(shiftId: String): Result<Unit> = runCatching {
        val validation = inspectionRepository.currentValidation(shiftId)
        database.withTransaction {
            val shift = requireNotNull(shiftDao.getById(shiftId)) { "Shift not found" }
            val phase = ShiftPhase.valueOf(shift.phase)
            require(ShiftRules.canActivate(phase, validation)) { validation.message("Shift cannot start") }
            val startedAt = clock()
            check(shiftDao.updatePhase(shiftId, ShiftPhase.ON_DUTY.name, startedAt) == 1) {
                "Failed to start shift"
            }
            enqueue(shiftId, "START", "{}", startedAt)
        }
    }

    override suspend fun endShift(shiftId: String, endOdometer: Long): Result<Unit> = runCatching {
        database.withTransaction {
            val shift = requireNotNull(shiftDao.getById(shiftId)) { "Shift not found" }
            val phase = ShiftPhase.valueOf(shift.phase)
            require(phase == ShiftPhase.ON_DUTY || phase == ShiftPhase.ON_BREAK) { "Shift is not active" }
            require(endOdometer >= shift.startOdometer) { "End odometer cannot be less than start odometer" }
            val endedAt = clock()
            check(shiftDao.endShift(shiftId, endOdometer, endedAt) == 1) { "Failed to end shift" }
            enqueue(
                entityId = shiftId,
                operationType = "END",
                payloadJson = "{\"endOdometer\":$endOdometer}",
                createdAt = endedAt
            )
        }
    }

    private suspend fun enqueue(
        entityId: String,
        operationType: String,
        payloadJson: String,
        createdAt: Long = clock()
    ) {
        syncDao.insert(
            SyncOperationEntity(
                id = idGenerator(),
                entityType = "SHIFT",
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

    private fun ShiftEntity.toDomain() = ShiftRecord(
        id = id,
        driverId = driverId,
        vehicleId = vehicleId,
        trailerId = trailerId,
        startOdometer = startOdometer,
        endOdometer = endOdometer,
        phase = ShiftPhase.valueOf(phase),
        createdAt = createdAt,
        startedAt = startedAt,
        endedAt = endedAt
    )

    private fun ValidationResult.message(fallback: String): String = when (this) {
        ValidationResult.Valid -> fallback
        is ValidationResult.Invalid -> reasons.joinToString("; ")
        is ValidationResult.Blocked -> reasons.joinToString("; ")
    }
}
