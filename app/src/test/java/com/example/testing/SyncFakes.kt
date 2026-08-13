package com.example.testing

import com.example.data.remote.dto.EvidenceMetadataSyncDto
import com.example.data.remote.dto.FreightExceptionSyncDto
import com.example.data.remote.dto.InspectionSyncDto
import com.example.data.remote.dto.JobStatusSyncDto
import com.example.data.remote.dto.LocationPointSyncDto
import com.example.data.remote.dto.ShiftEventSyncDto
import com.example.domain.model.SyncOperation
import com.example.domain.model.SyncStatus
import com.example.domain.sync.EvidenceUpload
import com.example.domain.sync.SyncOutcome
import com.example.domain.sync.SyncQueue
import com.example.domain.sync.SyncQueueCounts
import com.example.domain.sync.SyncTransport

/**
 * Test-only transport that returns whatever the test asks for.
 *
 * This exists *only* in the test source set. The runtime app has no fake that reports success —
 * with no endpoint configured the real code path is [com.example.domain.sync.UnconfiguredSyncTransport],
 * which refuses everything so queued work stays PENDING.
 */
class FakeSyncTransport(
    private var outcomes: MutableList<SyncOutcome> = mutableListOf(),
    private val default: SyncOutcome = SyncOutcome.Success
) : SyncTransport {

    /** Every idempotency key the transport was handed, in order. */
    val idempotencyKeys = mutableListOf<String>()
    val jobStatuses = mutableListOf<JobStatusSyncDto>()
    val shiftEvents = mutableListOf<ShiftEventSyncDto>()
    val inspections = mutableListOf<InspectionSyncDto>()
    val exceptions = mutableListOf<FreightExceptionSyncDto>()
    val locations = mutableListOf<List<LocationPointSyncDto>>()
    val evidenceMetadata = mutableListOf<EvidenceMetadataSyncDto>()
    val evidenceUploads = mutableListOf<EvidenceUpload>()
    val deletedEvidenceIds = mutableListOf<String>()

    fun willReturn(vararg next: SyncOutcome) {
        outcomes = next.toMutableList()
    }

    private fun next(key: String): SyncOutcome {
        idempotencyKeys += key
        return if (outcomes.isEmpty()) default else outcomes.removeAt(0)
    }

    override suspend fun sendJobStatus(idempotencyKey: String, payload: JobStatusSyncDto) =
        next(idempotencyKey).also { jobStatuses += payload }

    override suspend fun sendShiftEvent(idempotencyKey: String, payload: ShiftEventSyncDto) =
        next(idempotencyKey).also { shiftEvents += payload }

    override suspend fun sendInspection(idempotencyKey: String, payload: InspectionSyncDto) =
        next(idempotencyKey).also { inspections += payload }

    override suspend fun sendFreightException(
        idempotencyKey: String,
        payload: FreightExceptionSyncDto
    ) = next(idempotencyKey).also { exceptions += payload }

    override suspend fun sendLocationPoints(
        idempotencyKey: String,
        payload: List<LocationPointSyncDto>
    ) = next(idempotencyKey).also { locations += payload }

    override suspend fun uploadEvidence(
        idempotencyKey: String,
        metadata: EvidenceMetadataSyncDto,
        upload: EvidenceUpload
    ) = next(idempotencyKey).also {
        evidenceMetadata += metadata
        evidenceUploads += upload
    }

    override suspend fun deleteEvidence(
        idempotencyKey: String,
        evidenceId: String,
        jobId: String,
        deletedAt: Long
    ) = next(idempotencyKey).also { deletedEvidenceIds += evidenceId }
}

/** In-memory queue with the same claim semantics as the Room implementation. */
class FakeSyncQueue(
    operations: List<SyncOperation> = emptyList(),
    private val clock: () -> Long = { 0L }
) : SyncQueue {
    val rows = operations.toMutableList()
    var requeuedFailedCount = 0
        private set

    override suspend fun claimNext(): SyncOperation? {
        val index = rows.indexOfFirst { it.status == SyncStatus.PENDING }
        if (index < 0) return null
        val claimed = rows[index].copy(status = SyncStatus.IN_PROGRESS)
        rows[index] = claimed
        updatedAt[claimed.id] = clock()
        return claimed
    }

    /**
     * Mirrors the Room column: rows that predate the sync engine carry `updatedAt = 0`, so an
     * operation left IN_PROGRESS by a dead process is always eligible for recovery.
     */
    private val updatedAt = mutableMapOf<String, Long>()

    override suspend fun releaseStale(staleBefore: Long): Int {
        var released = 0
        rows.replaceAll { row ->
            if (row.status == SyncStatus.IN_PROGRESS &&
                updatedAt.getOrDefault(row.id, 0L) <= staleBefore
            ) {
                released++
                row.copy(status = SyncStatus.PENDING)
            } else {
                row
            }
        }
        return released
    }

    override suspend fun markSynced(id: String) = update(id) {
        it.copy(status = SyncStatus.SYNCED, lastError = null)
    }

    override suspend fun markRetryable(id: String, retryCount: Int, error: String) = update(id) {
        it.copy(status = SyncStatus.PENDING, retryCount = retryCount, lastError = error)
    }

    override suspend fun markFailed(id: String, retryCount: Int, error: String) = update(id) {
        it.copy(status = SyncStatus.FAILED, retryCount = retryCount, lastError = error)
    }

    override suspend fun release(id: String) = update(id) { it.copy(status = SyncStatus.PENDING) }

    override suspend fun requeueFailed(): Int {
        val failed = rows.count { it.status == SyncStatus.FAILED }
        rows.replaceAll { row ->
            if (row.status == SyncStatus.FAILED) row.copy(status = SyncStatus.PENDING, lastError = null)
            else row
        }
        requeuedFailedCount = failed
        return failed
    }

    override suspend fun counts(): SyncQueueCounts = SyncQueueCounts(
        pending = rows.count { it.status == SyncStatus.PENDING },
        inProgress = rows.count { it.status == SyncStatus.IN_PROGRESS },
        failed = rows.count { it.status == SyncStatus.FAILED },
        synced = rows.count { it.status == SyncStatus.SYNCED }
    )

    fun find(id: String): SyncOperation? = rows.find { it.id == id }

    private fun update(id: String, transform: (SyncOperation) -> SyncOperation) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = transform(rows[index])
    }
}

fun syncOperation(
    id: String,
    entityType: String = "JOB",
    entityId: String = "job-1",
    operationType: String = "STATUS_CHANGE",
    payloadJson: String = "{}",
    createdAt: Long = 1_000L,
    retryCount: Int = 0,
    status: SyncStatus = SyncStatus.PENDING
) = SyncOperation(
    id = id,
    entityType = entityType,
    entityId = entityId,
    operationType = operationType,
    payloadJson = payloadJson,
    createdAt = createdAt,
    retryCount = retryCount,
    lastError = null,
    status = status
)
