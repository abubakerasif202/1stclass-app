package com.example.viewmodel

import com.example.domain.model.EvidenceRecord
import com.example.domain.model.EvidenceStatus
import com.example.domain.model.EvidenceType
import com.example.domain.repository.EvidenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceViewModelTest {
    @Test
    fun cancelledPhotoDoesNotSatisfyRequirement() = runTest {
        val repository = FakeEvidenceRepository()
        val viewModel = EvidenceViewModel(repository)

        val id = viewModel.beginCapture("j1", EvidenceType.PICKUP_PHOTO).getOrThrow()
        viewModel.applyCaptureResult(id, CaptureResult.Cancelled).getOrThrow()

        assertFalse(viewModel.isRequirementSatisfied(repository.records, EvidenceType.PICKUP_PHOTO))
    }

    @Test
    fun savedPhotoSatisfiesRequirementOnlyAfterRepositorySave() = runTest {
        val repository = FakeEvidenceRepository()
        val viewModel = EvidenceViewModel(repository)

        val id = viewModel.beginCapture("j1", EvidenceType.PICKUP_PHOTO).getOrThrow()
        assertFalse(viewModel.isRequirementSatisfied(repository.records, EvidenceType.PICKUP_PHOTO))
        viewModel.applyCaptureResult(id, CaptureResult.Saved("prototype://j1/photo-1")).getOrThrow()

        assertTrue(viewModel.isRequirementSatisfied(repository.records, EvidenceType.PICKUP_PHOTO))
    }

    @Test
    fun cancelledSignatureDoesNotSatisfyRequirement() = runTest {
        val repository = FakeEvidenceRepository()
        val viewModel = EvidenceViewModel(repository)

        val id = viewModel.beginCapture("j1", EvidenceType.DELIVERY_SIGNATURE).getOrThrow()
        viewModel.applyCaptureResult(id, CaptureResult.Cancelled).getOrThrow()

        assertFalse(viewModel.isRequirementSatisfied(repository.records, EvidenceType.DELIVERY_SIGNATURE))
    }

    private class FakeEvidenceRepository : EvidenceRepository {
        private val flow = MutableStateFlow<List<EvidenceRecord>>(emptyList())
        val records: List<EvidenceRecord> get() = flow.value
        private var next = 0

        override fun observeForJob(jobId: String): Flow<List<EvidenceRecord>> = flow

        override suspend fun createPending(jobId: String, type: EvidenceType): Result<String> {
            val id = "e${next++}"
            flow.value = flow.value + EvidenceRecord(
                id = id,
                jobId = jobId,
                type = type,
                localUri = null,
                status = EvidenceStatus.PENDING_CAPTURE,
                createdAt = 1L
            )
            return Result.success(id)
        }

        override suspend fun markSavedLocal(id: String, localUri: String): Result<Unit> {
            flow.value = flow.value.map {
                if (it.id == id) it.copy(localUri = localUri, status = EvidenceStatus.SAVED_LOCAL) else it
            }
            return Result.success(Unit)
        }

        override suspend fun discardPending(id: String): Result<Unit> {
            flow.value = flow.value.filterNot { it.id == id && it.status == EvidenceStatus.PENDING_CAPTURE }
            return Result.success(Unit)
        }
    }
}
