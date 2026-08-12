package com.example.data.repository

import com.example.data.local.dao.SyncOperationDao
import com.example.data.local.entity.SyncOperationEntity
import com.example.domain.model.SyncOperation
import com.example.domain.model.SyncStatus
import com.example.domain.repository.SyncRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSyncRepository(
    private val dao: SyncOperationDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : SyncRepository {
    override fun observePending(): Flow<List<SyncOperation>> =
        dao.observePending().map { rows -> rows.map { it.toDomain() } }

    override suspend fun enqueue(
        entityType: String,
        entityId: String,
        operationType: String,
        payloadJson: String
    ): Result<String> = runCatching {
        require(entityType.isNotBlank()) { "Entity type is required" }
        require(entityId.isNotBlank()) { "Entity ID is required" }
        require(operationType.isNotBlank()) { "Operation type is required" }
        val id = idGenerator()
        dao.insert(
            SyncOperationEntity(
                id = id,
                entityType = entityType,
                entityId = entityId,
                operationType = operationType,
                payloadJson = payloadJson,
                createdAt = clock(),
                retryCount = 0,
                lastError = null,
                status = SyncStatus.PENDING.name
            )
        )
        id
    }

    override suspend fun markFailure(id: String, error: String): Result<Unit> = runCatching {
        val existing = requireNotNull(dao.getById(id)) { "Sync operation not found" }
        check(dao.updateFailure(id, existing.retryCount + 1, error) == 1) { "Failed to update sync operation" }
    }

    override suspend fun markSynced(id: String): Result<Unit> = runCatching {
        check(dao.updateStatus(id, SyncStatus.SYNCED.name) == 1) { "Sync operation not found" }
    }

    private fun SyncOperationEntity.toDomain() = SyncOperation(
        id = id,
        entityType = entityType,
        entityId = entityId,
        operationType = operationType,
        payloadJson = payloadJson,
        createdAt = createdAt,
        retryCount = retryCount,
        lastError = lastError,
        status = SyncStatus.valueOf(status)
    )
}
