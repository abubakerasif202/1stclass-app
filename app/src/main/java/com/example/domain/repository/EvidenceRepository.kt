package com.example.domain.repository

import com.example.domain.model.EvidenceRecord
import com.example.domain.model.EvidenceType
import kotlinx.coroutines.flow.Flow

interface EvidenceRepository {
    fun observeForJob(jobId: String): Flow<List<EvidenceRecord>>
    suspend fun createPending(jobId: String, type: EvidenceType): Result<String>
    suspend fun markSavedLocal(id: String, localUri: String): Result<Unit>
    suspend fun discardPending(id: String): Result<Unit>
}
