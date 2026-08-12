package com.example.domain.repository

import com.example.domain.model.FreightExceptionDraft
import com.example.domain.model.FreightExceptionRecord
import kotlinx.coroutines.flow.Flow

interface FreightExceptionRepository {
    fun observeForJob(jobId: String): Flow<List<FreightExceptionRecord>>
    suspend fun getForJob(jobId: String): List<FreightExceptionRecord>
    suspend fun record(draft: FreightExceptionDraft): Result<String>
    suspend fun markResolved(id: String, resolved: Boolean): Result<Unit>
}
