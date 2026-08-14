package au.com.firstclassexpress.driver.data.repository

import androidx.room.withTransaction
import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.entity.FreightExceptionEntity
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import au.com.firstclassexpress.driver.domain.model.EvidenceStatus
import au.com.firstclassexpress.driver.domain.model.ExceptionStage
import au.com.firstclassexpress.driver.domain.model.FreightExceptionDraft
import au.com.firstclassexpress.driver.domain.model.FreightExceptionReason
import au.com.firstclassexpress.driver.domain.model.FreightExceptionRecord
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.repository.FreightExceptionRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists freight exceptions locally and queues them for the TMS through the existing durable
 * sync queue — the same path job status changes and evidence already take.
 */
class RoomFreightExceptionRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : FreightExceptionRepository {
    private val exceptionDao = database.freightExceptionDao()
    private val syncDao = database.syncOperationDao()

    override fun observeForJob(jobId: String): Flow<List<FreightExceptionRecord>> =
        exceptionDao.observeForJob(jobId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getForJob(jobId: String): List<FreightExceptionRecord> =
        exceptionDao.getForJob(jobId).map { it.toDomain() }

    override suspend fun record(draft: FreightExceptionDraft): Result<String> = runCatching {
        require(draft.jobId.isNotBlank()) { "Job ID is required" }
        require(draft.driverId.isNotBlank()) { "Driver ID is required" }
        val notes = draft.notes.trim()
        require(notes.isNotEmpty()) { "Notes are required for a freight exception" }

        val id = idGenerator()
        val timestamp = clock()
        database.withTransaction {
            exceptionDao.insert(
                FreightExceptionEntity(
                    id = id,
                    jobId = draft.jobId,
                    stage = draft.stage.name,
                    reason = draft.reason.name,
                    notes = notes,
                    driverId = draft.driverId,
                    shiftId = draft.shiftId,
                    resolved = false,
                    createdAt = timestamp,
                    status = EvidenceStatus.SAVED_LOCAL.name
                )
            )
            syncDao.insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_TYPE,
                    entityId = id,
                    operationType = "UPSERT",
                    payloadJson =
                        "{\"jobId\":\"${draft.jobId}\",\"reason\":\"${draft.reason.name}\"}",
                    createdAt = timestamp,
                    retryCount = 0,
                    lastError = null,
                    status = SyncStatus.PENDING.name
                )
            )
            database.jobTimelineEventDao().insert(
                au.com.firstclassexpress.driver.data.local.entity.JobTimelineEventEntity(
                    id = idGenerator(),
                    jobId = draft.jobId,
                    status = "EXCEPTION",
                    title = "Exception: ${draft.reason.label} (${draft.stage.name})",
                    description = notes,
                    timestamp = timestamp,
                    latitude = null,
                    longitude = null,
                    syncStatus = SyncStatus.PENDING.name
                )
            )
        }
        id
    }

    override suspend fun markResolved(id: String, resolved: Boolean): Result<Unit> = runCatching {
        requireNotNull(exceptionDao.getById(id)) { "Freight exception not found" }
        check(exceptionDao.updateResolved(id, resolved) == 1) {
            "Failed to update freight exception"
        }
    }

    private fun FreightExceptionEntity.toDomain() = FreightExceptionRecord(
        id = id,
        jobId = jobId,
        stage = ExceptionStage.valueOf(stage),
        reason = FreightExceptionReason.valueOf(reason),
        notes = notes,
        driverId = driverId,
        shiftId = shiftId,
        resolved = resolved,
        createdAt = createdAt,
        status = EvidenceStatus.valueOf(status)
    )

    private companion object {
        const val ENTITY_TYPE = "FREIGHT_EXCEPTION"
    }
}
