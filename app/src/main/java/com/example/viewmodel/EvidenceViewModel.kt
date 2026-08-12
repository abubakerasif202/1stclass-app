package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.domain.evidence.EvidenceCaptureService
import com.example.domain.evidence.PendingCapture
import com.example.domain.evidence.SignatureDrawing
import com.example.domain.model.EvidenceCaptureRequest
import com.example.domain.model.EvidenceRecord
import com.example.domain.model.EvidenceType
import com.example.domain.repository.EvidenceRepository
import com.example.domain.rules.EvidenceRules

/**
 * Thin presentation wrapper over [EvidenceCaptureService].
 *
 * Capture screens never touch files or the database directly; they hand raw results here and the
 * service decides whether anything counts as evidence.
 */
class EvidenceViewModel(
    private val captureService: EvidenceCaptureService,
    private val repository: EvidenceRepository
) : ViewModel() {

    fun observeForJob(jobId: String) = repository.observeForJob(jobId)

    suspend fun beginCapture(request: EvidenceCaptureRequest): Result<PendingCapture> =
        captureService.begin(request)

    suspend fun resumeCapture(evidenceId: String): Result<PendingCapture> =
        captureService.resume(evidenceId)

    fun stagingPathFor(pending: PendingCapture): String = captureService.stagingPathFor(pending)

    suspend fun completePhoto(pending: PendingCapture, stagingPath: String): Result<EvidenceRecord> =
        captureService.completePhoto(pending, stagingPath)

    suspend fun completeSignature(
        pending: PendingCapture,
        drawing: SignatureDrawing,
        signerName: String?
    ): Result<EvidenceRecord> = captureService.completeSignature(pending, drawing, signerName)

    suspend fun cancelCapture(pending: PendingCapture): Result<Unit> = captureService.cancel(pending)

    suspend fun deleteEvidence(evidenceId: String): Result<Unit> =
        captureService.deleteSaved(evidenceId)

    fun isRequirementSatisfied(
        records: List<EvidenceRecord>,
        type: EvidenceType
    ): Boolean = records.any { it.type == type && EvidenceRules.isSatisfied(it.status) }
}
