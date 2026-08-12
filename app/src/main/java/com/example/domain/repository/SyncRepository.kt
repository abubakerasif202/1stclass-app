package com.example.domain.repository

import com.example.domain.model.SyncOperation
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun observePending(): Flow<List<SyncOperation>>
    suspend fun enqueue(
        entityType: String,
        entityId: String,
        operationType: String,
        payloadJson: String
    ): Result<String>
    suspend fun markFailure(id: String, error: String): Result<Unit>
    suspend fun markSynced(id: String): Result<Unit>
}
