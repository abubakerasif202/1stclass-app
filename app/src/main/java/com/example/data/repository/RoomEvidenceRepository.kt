package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.EvidenceEntity
import com.example.data.local.entity.SyncOperationEntity
import com.example.domain.model.EvidenceRecord
import com.example.domain.model.EvidenceStatus
import com.example.domain.model.EvidenceType
import com.example.domain.model.SyncStatus
import com.example.domain.repository.EvidenceRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEvidenceRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : EvidenceRepository {
    private val evidenceDao = database.evidenceDao()
    private val syncDao = database.syncOperationDao()

    override fun observeForJob(jobId: String): Flow<List<EvidenceRecord>> =
        evidenceDao.observeForJob(jobId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun createPending(jobId: String, type: EvidenceType): Result<String> = runCatching {
        require(jobId.isNotBlank()) { "Job ID is required" }
        val id = idGenerator()
        evidenceDao.insert(
            EvidenceEntity(
                id = id,
                jobId = jobId,
                type = type.name,
                localUri = null,
                status = EvidenceStatus.PENDING_CAPTURE.name,
                createdAt = clock()
            )
        )
        id
    }

    override suspend fun markSavedLocal(id: String, localUri: String): Result<Unit> = runCatching {
        require(localUri.isNotBlank()) { "Evidence URI is required" }
        database.withTransaction {
            val existing = requireNotNull(evidenceDao.getById(id)) { "Evidence not found" }
            require(existing.status == EvidenceStatus.PENDING_CAPTURE.name) { "Evidence is not awaiting capture" }
            check(evidenceDao.updateSaved(id, localUri, EvidenceStatus.SAVED_LOCAL.name) == 1) {
                "Failed to save evidence"
            }
            syncDao.insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = "EVIDENCE",
                    entityId = id,
                    operationType = "UPSERT",
                    payloadJson = "{\"jobId\":\"${existing.jobId}\",\"type\":\"${existing.type}\"}",
                    createdAt = clock(),
                    retryCount = 0,
                    lastError = null,
                    status = SyncStatus.PENDING.name
                )
            )
        }
    }

    override suspend fun discardPending(id: String): Result<Unit> = runCatching {
        val existing = requireNotNull(evidenceDao.getById(id)) { "Evidence not found" }
        require(existing.status == EvidenceStatus.PENDING_CAPTURE.name) { "Only pending capture can be discarded" }
        check(evidenceDao.deletePending(id) == 1) { "Failed to discard pending evidence" }
    }

    private fun EvidenceEntity.toDomain() = EvidenceRecord(
        id = id,
        jobId = jobId,
        type = EvidenceType.valueOf(type),
        localUri = localUri,
        status = EvidenceStatus.valueOf(status),
        createdAt = createdAt
    )
}
