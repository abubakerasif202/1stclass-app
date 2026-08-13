package com.example.data.sync

import com.example.data.local.AppDatabase
import com.example.data.local.entity.EvidenceEntity
import com.example.data.local.entity.InspectionItemEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.remote.dto.EvidenceMetadataSyncDto
import com.example.data.remote.dto.FreightExceptionSyncDto
import com.example.data.remote.dto.InspectionItemSyncDto
import com.example.data.remote.dto.InspectionSyncDto
import com.example.data.remote.dto.JobStatusSyncDto
import com.example.data.remote.dto.LocationPointSyncDto
import com.example.data.remote.dto.ShiftEventSyncDto
import com.example.domain.model.EvidenceStatus
import com.example.domain.model.FreightExceptionReason
import com.example.domain.model.SyncOperation
import com.example.domain.model.SyncStatus
import com.example.domain.sync.EvidenceUpload
import com.example.domain.sync.SyncEntityTypes
import com.example.domain.sync.SyncOperationTypes
import com.example.domain.sync.SyncOutcome
import com.example.domain.sync.SyncTransport
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.File
import java.net.URI

/**
 * Routes a queued operation to the right remote call.
 *
 * Two rules shape this class:
 *
 *  1. **Payloads are hydrated from Room at send time**, not read out of `payloadJson`. The queued
 *     payload was only ever a small hint written by hand; the authoritative record is the row the
 *     driver's action actually wrote. Hydrating also means an operation queued by an older app
 *     version still produces a complete, current payload — which is why bumping `payloadVersion`
 *     does not strand anything.
 *  2. **Original timestamps are preserved.** An operation carries `createdAt` — when the driver
 *     did the thing — and that is what goes on the wire, never the time of the retry that finally
 *     got through.
 *
 * Unknown operation types return [SyncOutcome.Permanent] so they surface on the diagnostics screen
 * instead of crashing the worker or silently disappearing.
 */
class SyncOperationProcessor(
    private val database: AppDatabase,
    private val transport: SyncTransport,
    moshi: Moshi
) {
    private val jobStatusPayloadAdapter = moshi.adapter(LegacyJobStatusPayload::class.java)

    suspend fun process(operation: SyncOperation): SyncOutcome = when (operation.entityType) {
        SyncEntityTypes.JOB -> processJob(operation)
        SyncEntityTypes.SHIFT -> processShift(operation)
        SyncEntityTypes.INSPECTION -> processInspection(operation)
        SyncEntityTypes.INSPECTION_ITEM -> processInspectionItem(operation)
        SyncEntityTypes.EVIDENCE -> processEvidence(operation)
        SyncEntityTypes.FREIGHT_EXCEPTION -> processFreightException(operation)
        SyncEntityTypes.LOCATION_POINT -> processLocationPoint(operation)
        else -> unknown(operation)
    }

    // --- JOB ---------------------------------------------------------------------------------

    private suspend fun processJob(operation: SyncOperation): SyncOutcome {
        if (operation.operationType != SyncOperationTypes.STATUS_CHANGE) return unknown(operation)

        val job = database.jobDao().getById(operation.entityId)
            ?: return SyncOutcome.Permanent("Job no longer exists on device")

        // The transition that was recorded, not whatever the job looks like now.
        val recorded = runCatching { jobStatusPayloadAdapter.fromJson(operation.payloadJson) }
            .getOrNull()
        val shift = database.shiftDao().activeAt(operation.createdAt)

        return transport.sendJobStatus(
            idempotencyKey = operation.id,
            payload = JobStatusSyncDto(
                jobId = job.id,
                fromStatus = recorded?.from,
                toStatus = recorded?.to ?: job.status,
                driverId = shift?.driverId,
                shiftId = shift?.id,
                changedAt = operation.createdAt
            )
        )
    }

    // --- SHIFT -------------------------------------------------------------------------------

    private suspend fun processShift(operation: SyncOperation): SyncOutcome {
        val event = when (operation.operationType) {
            SyncOperationTypes.CREATE_DRAFT,
            SyncOperationTypes.READY_TO_START,
            SyncOperationTypes.START,
            SyncOperationTypes.END -> operation.operationType

            else -> return unknown(operation)
        }

        val shift = database.shiftDao().getById(operation.entityId)
            ?: return SyncOutcome.Permanent("Shift no longer exists on device")

        return transport.sendShiftEvent(
            idempotencyKey = operation.id,
            payload = shift.toEventDto(event, operation.createdAt)
        )
    }

    private fun ShiftEntity.toEventDto(event: String, occurredAt: Long) = ShiftEventSyncDto(
        shiftId = id,
        event = event,
        driverId = driverId,
        vehicleId = vehicleId,
        trailerId = trailerId,
        startOdometer = startOdometer,
        endOdometer = endOdometer,
        occurredAt = occurredAt,
        startedAt = startedAt,
        endedAt = endedAt
    )

    // --- INSPECTION --------------------------------------------------------------------------

    private suspend fun processInspection(operation: SyncOperation): SyncOutcome {
        if (operation.operationType != SyncOperationTypes.DECLARATION &&
            operation.operationType != SyncOperationTypes.COMPLETE
        ) {
            return unknown(operation)
        }
        // For INSPECTION the entityId is the shift id — that is how the repository queues it.
        return sendInspection(operation, shiftId = operation.entityId, singleItemId = null)
    }

    private suspend fun processInspectionItem(operation: SyncOperation): SyncOutcome {
        if (operation.operationType != SyncOperationTypes.ANSWER) return unknown(operation)

        val item = database.inspectionDao().getItem(operation.entityId)
            ?: return SyncOutcome.Permanent("Inspection item no longer exists on device")

        return sendInspection(operation, shiftId = item.shiftId, singleItemId = item.id)
    }

    private suspend fun sendInspection(
        operation: SyncOperation,
        shiftId: String,
        singleItemId: String?
    ): SyncOutcome {
        val inspection = database.inspectionDao().getInspectionForShift(shiftId)
            ?: return SyncOutcome.Permanent("Inspection no longer exists on device")
        val shift = database.shiftDao().getById(shiftId)
            ?: return SyncOutcome.Permanent("Shift no longer exists on device")

        val rows = database.inspectionDao().getItems(shiftId)
            .let { items -> singleItemId?.let { id -> items.filter { it.id == id } } ?: items }

        return transport.sendInspection(
            idempotencyKey = operation.id,
            payload = InspectionSyncDto(
                inspectionId = inspection.id,
                shiftId = shiftId,
                driverId = shift.driverId,
                vehicleId = shift.vehicleId,
                trailerId = shift.trailerId,
                declarationAccepted = inspection.declarationAccepted,
                validationState = inspection.validationState,
                completedAt = inspection.completedAt,
                occurredAt = operation.createdAt,
                items = rows.map { it.toDto(operation.createdAt) }
            )
        )
    }

    /**
     * Individual answer times are not stored per item, so the answering time is taken from the
     * queue entry that recorded it — which is the real moment the driver answered.
     */
    private fun InspectionItemEntity.toDto(answeredAt: Long) = InspectionItemSyncDto(
        itemId = id,
        shiftId = shiftId,
        code = code,
        label = label,
        category = category,
        mandatory = mandatory,
        status = status,
        defectDescription = defectDescription,
        defectSeverity = defectSeverity,
        answeredAt = answeredAt
    )

    // --- EVIDENCE ----------------------------------------------------------------------------

    private suspend fun processEvidence(operation: SyncOperation): SyncOutcome =
        when (operation.operationType) {
            SyncOperationTypes.UPSERT -> uploadEvidence(operation)
            SyncOperationTypes.DELETE -> deleteEvidence(operation)
            else -> unknown(operation)
        }

    private suspend fun uploadEvidence(operation: SyncOperation): SyncOutcome {
        val evidence = database.evidenceDao().getById(operation.entityId)
            ?: return SyncOutcome.Permanent("Evidence no longer exists on device")

        val uri = evidence.localUri
            ?: return SyncOutcome.Permanent("Evidence has no stored file")
        val file = resolveFile(uri)
            ?: return SyncOutcome.Permanent("Evidence file could not be resolved")
        if (!file.isFile || file.length() == 0L) {
            // The local record and the queue entry both survive; support can see the gap.
            return SyncOutcome.Permanent("Evidence file is missing on device")
        }

        val outcome = transport.uploadEvidence(
            idempotencyKey = operation.id,
            metadata = evidence.toMetadataDto(file),
            upload = EvidenceUpload(file, mimeTypeOf(file))
        )

        // The file stays on disk regardless — only an acknowledged upload changes local state.
        if (outcome is SyncOutcome.Success) {
            database.evidenceDao().updateSyncStatus(evidence.id, EvidenceStatus.SYNCED.name)
        }
        return outcome
    }

    private suspend fun deleteEvidence(operation: SyncOperation): SyncOutcome =
        transport.deleteEvidence(
            idempotencyKey = operation.id,
            evidenceId = operation.entityId,
            // The row is already gone locally, so the job id can only come from the queue entry.
            jobId = runCatching { jobStatusPayloadAdapter.fromJson(operation.payloadJson)?.jobId }
                .getOrNull()
                .orEmpty(),
            deletedAt = operation.createdAt
        )

    private fun EvidenceEntity.toMetadataDto(file: File) = EvidenceMetadataSyncDto(
        evidenceId = id,
        jobId = jobId,
        driverId = driverId,
        shiftId = shiftId,
        type = type,
        createdAt = createdAt,
        savedAt = savedAt,
        signerName = signerName,
        notes = notes,
        fileName = file.name,
        fileSizeBytes = fileSizeBytes ?: file.length(),
        mimeType = mimeTypeOf(file),
        // Absent GPS stays absent. A POD without a fix is not a POD at 0,0.
        latitude = latitude,
        longitude = longitude,
        locationAccuracyMeters = locationAccuracyMeters,
        locationRecordedAt = locationRecordedAt
    )

    // --- FREIGHT EXCEPTION -------------------------------------------------------------------

    private suspend fun processFreightException(operation: SyncOperation): SyncOutcome {
        if (operation.operationType != SyncOperationTypes.UPSERT) return unknown(operation)

        val record = database.freightExceptionDao().getById(operation.entityId)
            ?: return SyncOutcome.Permanent("Freight exception no longer exists on device")

        val jobEvidence = database.evidenceDao().getForJob(record.jobId)
        val acknowledged = jobEvidence.filter { it.status == EvidenceStatus.SYNCED.name }
        val stillUploading = jobEvidence.any {
            it.status == EvidenceStatus.SAVED_LOCAL.name || it.status == EvidenceStatus.PENDING_SYNC.name
        }

        // A damage claim must not reach the TMS pointing at photos the TMS has never seen.
        val needsPhoto = runCatching { FreightExceptionReason.valueOf(record.reason).requiresPhoto }
            .getOrDefault(false)
        if (needsPhoto && acknowledged.isEmpty() && stillUploading) {
            return SyncOutcome.Deferred("Waiting for the exception photo to upload first")
        }

        val outcome = transport.sendFreightException(
            idempotencyKey = operation.id,
            payload = FreightExceptionSyncDto(
                exceptionId = record.id,
                jobId = record.jobId,
                stage = record.stage,
                reason = record.reason,
                notes = record.notes,
                driverId = record.driverId,
                shiftId = record.shiftId,
                resolved = record.resolved,
                createdAt = record.createdAt,
                evidenceIds = acknowledged.map { it.id }
            )
        )
        if (outcome is SyncOutcome.Success) {
            database.freightExceptionDao().updateStatus(record.id, EvidenceStatus.SYNCED.name)
        }
        return outcome
    }

    // --- LOCATION ----------------------------------------------------------------------------

    private suspend fun processLocationPoint(operation: SyncOperation): SyncOutcome {
        if (operation.operationType != SyncOperationTypes.LOCATION_POINT_CREATED) {
            return unknown(operation)
        }
        val point = database.locationPointDao().getById(operation.entityId)
            ?: return SyncOutcome.Permanent("Location point no longer exists on device")

        val outcome = transport.sendLocationPoints(
            idempotencyKey = operation.id,
            payload = listOf(
                LocationPointSyncDto(
                    locationId = point.id,
                    driverId = point.driverId,
                    shiftId = point.shiftId,
                    jobId = point.jobId,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    accuracyMeters = point.accuracyMeters,
                    speedMetersPerSecond = point.speedMetersPerSecond,
                    bearingDegrees = point.bearingDegrees,
                    altitudeMeters = point.altitudeMeters,
                    recordedAt = point.recordedAt
                )
            )
        )
        if (outcome is SyncOutcome.Success) {
            database.locationPointDao().updateSyncStatus(point.id, SyncStatus.SYNCED.name)
        }
        return outcome
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun unknown(operation: SyncOperation) = SyncOutcome.Permanent(
        "Unsupported operation ${operation.entityType}/${operation.operationType}"
    )

    private fun resolveFile(uri: String): File? = runCatching {
        if (uri.startsWith("file:")) File(URI(uri)) else File(uri)
    }.getOrNull()

    private fun mimeTypeOf(file: File): String =
        if (file.extension.equals("png", ignoreCase = true)) "image/png" else "image/jpeg"

    /**
     * The hand-built JSON the repositories have always written. Parsed leniently — every field is
     * optional, because operations queued by earlier app versions must keep working after upgrade.
     */
    @JsonClass(generateAdapter = true)
    internal data class LegacyJobStatusPayload(
        val from: String? = null,
        val to: String? = null,
        val jobId: String? = null,
        val type: String? = null
    )
}
