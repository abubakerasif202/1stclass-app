package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.EvidenceEntity
import com.example.data.local.entity.SyncOperationEntity
import com.example.domain.evidence.StoredEvidenceFile
import com.example.domain.model.EvidenceCaptureRequest
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
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val recentLocationMaxAgeMillis: Long = 5 * 60 * 1000L
) : EvidenceRepository {
    private val evidenceDao = database.evidenceDao()
    private val syncDao = database.syncOperationDao()

    override fun observeForJob(jobId: String): Flow<List<EvidenceRecord>> =
        evidenceDao.observeForJob(jobId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getForJob(jobId: String): List<EvidenceRecord> =
        evidenceDao.getForJob(jobId).map { it.toDomain() }

    override suspend fun getById(id: String): EvidenceRecord? =
        evidenceDao.getById(id)?.toDomain()

    override suspend fun createPending(request: EvidenceCaptureRequest): Result<String> =
        runCatching {
            require(request.jobId.isNotBlank()) { "Job ID is required" }
            require(request.driverId.isNotBlank()) { "Driver ID is required" }
            val id = idGenerator()
            evidenceDao.insert(
                EvidenceEntity(
                    id = id,
                    jobId = request.jobId,
                    type = request.type.name,
                    localUri = null,
                    status = EvidenceStatus.PENDING_CAPTURE.name,
                    createdAt = clock(),
                    driverId = request.driverId,
                    shiftId = request.shiftId
                )
            )
            id
        }

    override suspend fun markSavedLocal(
        id: String,
        file: StoredEvidenceFile,
        signerName: String?,
        notes: String?
    ): Result<EvidenceRecord> = runCatching {
        require(file.uri.isNotBlank()) { "Evidence URI is required" }
        require(file.sizeBytes > 0L) { "Evidence file is empty" }
        database.withTransaction {
            val existing = requireNotNull(evidenceDao.getById(id)) { "Evidence not found" }
            require(existing.status == EvidenceStatus.PENDING_CAPTURE.name) {
                "Evidence is not awaiting capture"
            }
            val location = database.locationPointDao().latestSince(clock() - recentLocationMaxAgeMillis)
            check(
                evidenceDao.updateSaved(
                    id = id,
                    uri = file.uri,
                    status = EvidenceStatus.SAVED_LOCAL.name,
                    signerName = signerName?.trim()?.takeIf { it.isNotEmpty() },
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    fileSizeBytes = file.sizeBytes,
                    savedAt = file.savedAt,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    locationAccuracyMeters = location?.accuracyMeters,
                    locationRecordedAt = location?.recordedAt
                ) == 1
            ) { "Failed to save evidence" }

            syncDao.insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_TYPE,
                    entityId = id,
                    operationType = "UPSERT",
                    payloadJson = "{\"jobId\":\"${existing.jobId}\",\"type\":\"${existing.type}\"}",
                    createdAt = clock(),
                    retryCount = 0,
                    lastError = null,
                    status = SyncStatus.PENDING.name
                )
            )
            requireNotNull(evidenceDao.getById(id)) { "Evidence not found" }.toDomain()
        }
    }

    override suspend fun discardPending(id: String): Result<Unit> = runCatching {
        val existing = requireNotNull(evidenceDao.getById(id)) { "Evidence not found" }
        require(existing.status == EvidenceStatus.PENDING_CAPTURE.name) {
            "Only pending capture can be discarded"
        }
        check(evidenceDao.deletePending(id) == 1) { "Failed to discard pending evidence" }
    }

    override suspend fun deleteSaved(id: String): Result<EvidenceRecord> = runCatching {
        database.withTransaction {
            val existing = requireNotNull(evidenceDao.getById(id)) { "Evidence not found" }
            require(existing.status != EvidenceStatus.SYNCED.name) {
                "Evidence already sent to the TMS cannot be deleted on device"
            }
            check(evidenceDao.deleteById(id) == 1) { "Failed to delete evidence" }
            syncDao.insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_TYPE,
                    entityId = id,
                    operationType = "DELETE",
                    payloadJson = "{\"jobId\":\"${existing.jobId}\",\"type\":\"${existing.type}\"}",
                    createdAt = clock(),
                    retryCount = 0,
                    lastError = null,
                    status = SyncStatus.PENDING.name
                )
            )
            existing.toDomain()
        }
    }

    private fun EvidenceEntity.toDomain() = EvidenceRecord(
        id = id,
        jobId = jobId,
        type = EvidenceType.valueOf(type),
        localUri = localUri,
        status = EvidenceStatus.valueOf(status),
        createdAt = createdAt,
        driverId = driverId,
        shiftId = shiftId,
        signerName = signerName,
        notes = notes,
        fileSizeBytes = fileSizeBytes,
        savedAt = savedAt,
        latitude = latitude,
        longitude = longitude,
        locationAccuracyMeters = locationAccuracyMeters,
        locationRecordedAt = locationRecordedAt
    )

    private companion object {
        const val ENTITY_TYPE = "EVIDENCE"
    }
}
