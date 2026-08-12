package com.example.domain.evidence

import com.example.domain.model.EvidenceCaptureRequest
import com.example.domain.model.EvidenceRecord
import com.example.domain.model.EvidenceType
import com.example.domain.repository.EvidenceRepository

/** A reserved evidence row plus everything needed to name the file it will produce. */
data class PendingCapture(
    val evidenceId: String,
    val descriptor: EvidenceFileDescriptor
)

/**
 * Coordinates the capture lifecycle: reserve, write the file, then — and only then — record the
 * evidence as saved.
 *
 * Ordering matters. If the file write fails nothing is promoted; if the database write fails the
 * file is removed again so no orphan is left behind. Cancelling simply drops the reservation.
 */
class EvidenceCaptureService(
    private val evidenceRepository: EvidenceRepository,
    private val fileStore: EvidenceFileStore,
    private val signatureRenderer: SignatureRenderer
) {

    suspend fun begin(request: EvidenceCaptureRequest): Result<PendingCapture> =
        evidenceRepository.createPending(request).map { evidenceId ->
            PendingCapture(
                evidenceId = evidenceId,
                descriptor = EvidenceFileDescriptor(
                    jobId = request.jobId,
                    evidenceId = evidenceId,
                    type = request.type,
                    driverId = request.driverId,
                    shiftId = request.shiftId
                )
            )
        }

    /**
     * Rebuilds a pending capture from the database so a capture screen still works after process
     * death. Fails if the evidence is missing or is no longer awaiting capture.
     */
    suspend fun resume(evidenceId: String): Result<PendingCapture> = runCatching {
        val record = requireNotNull(evidenceRepository.getById(evidenceId)) {
            "Evidence not found"
        }
        require(record.status == com.example.domain.model.EvidenceStatus.PENDING_CAPTURE) {
            "Evidence is not awaiting capture"
        }
        PendingCapture(
            evidenceId = record.id,
            descriptor = EvidenceFileDescriptor(
                jobId = record.jobId,
                evidenceId = record.id,
                type = record.type,
                driverId = record.driverId.orEmpty(),
                shiftId = record.shiftId
            )
        )
    }

    fun stagingPathFor(pending: PendingCapture): String =
        fileStore.createStagingPhotoPath(pending.descriptor)

    suspend fun completePhoto(
        pending: PendingCapture,
        stagingPath: String,
        notes: String? = null
    ): Result<EvidenceRecord> {
        val stored = fileStore.storePhoto(pending.descriptor, stagingPath)
            .getOrElse { return Result.failure(it) }
        return evidenceRepository.markSavedLocal(pending.evidenceId, stored, notes = notes)
            .onFailure { fileStore.delete(stored.uri) }
    }

    suspend fun completeSignature(
        pending: PendingCapture,
        drawing: SignatureDrawing,
        signerName: String?
    ): Result<EvidenceRecord> {
        val requiresSigner = pending.descriptor.type == EvidenceType.DELIVERY_SIGNATURE
        val cleanedSigner = signerName?.trim()?.takeIf { it.isNotEmpty() }
        if (requiresSigner && cleanedSigner == null) {
            return Result.failure(IllegalArgumentException("Enter the name of the person signing."))
        }
        if (!drawing.hasInk) {
            return Result.failure(IllegalArgumentException("Sign in the box before saving."))
        }

        val pngBytes = signatureRenderer.renderPng(drawing).getOrElse { return Result.failure(it) }
        val stored = fileStore.storeSignature(pending.descriptor, pngBytes)
            .getOrElse { return Result.failure(it) }
        return evidenceRepository
            .markSavedLocal(pending.evidenceId, stored, signerName = cleanedSigner)
            .onFailure { fileStore.delete(stored.uri) }
    }

    /** Back/cancel. Removes the reservation so an abandoned screen never counts as evidence. */
    suspend fun cancel(pending: PendingCapture): Result<Unit> =
        evidenceRepository.discardPending(pending.evidenceId)

    /** Retake support: drop the saved record and its file. */
    suspend fun deleteSaved(evidenceId: String): Result<Unit> =
        evidenceRepository.deleteSaved(evidenceId).mapCatching { record ->
            record.localUri?.let { fileStore.delete(it) }
            Unit
        }
}
