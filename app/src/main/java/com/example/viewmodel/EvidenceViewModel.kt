package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.domain.model.EvidenceRecord
import com.example.domain.model.EvidenceType
import com.example.domain.repository.EvidenceRepository
import com.example.domain.rules.EvidenceRules

sealed interface CaptureResult {
    data class Saved(val localUri: String) : CaptureResult
    data object Cancelled : CaptureResult
}

class EvidenceViewModel(
    private val repository: EvidenceRepository
) : ViewModel() {
    fun observeForJob(jobId: String) = repository.observeForJob(jobId)

    suspend fun beginCapture(jobId: String, type: EvidenceType): Result<String> =
        repository.createPending(jobId, type)

    suspend fun applyCaptureResult(
        evidenceId: String,
        result: CaptureResult
    ): Result<Unit> = when (result) {
        is CaptureResult.Saved -> repository.markSavedLocal(evidenceId, result.localUri)
        CaptureResult.Cancelled -> repository.discardPending(evidenceId)
    }

    fun isRequirementSatisfied(
        records: List<EvidenceRecord>,
        type: EvidenceType
    ): Boolean = records.any { it.type == type && EvidenceRules.isSatisfied(it.status) }
}
