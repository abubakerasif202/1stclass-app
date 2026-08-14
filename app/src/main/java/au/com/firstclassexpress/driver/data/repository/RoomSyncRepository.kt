package au.com.firstclassexpress.driver.data.repository

import au.com.firstclassexpress.driver.data.local.dao.SyncOperationDao
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import au.com.firstclassexpress.driver.domain.model.SyncOperation
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.repository.SyncRepository
import au.com.firstclassexpress.driver.domain.sync.SyncQueueCounts
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

    override fun observeCounts(): Flow<SyncQueueCounts> =
        dao.observeStatusCounts().map { rows ->
            val byStatus = rows.associate { it.status to it.count }
            SyncQueueCounts(
                pending = byStatus[SyncStatus.PENDING.name] ?: 0,
                inProgress = byStatus[SyncStatus.IN_PROGRESS.name] ?: 0,
                failed = byStatus[SyncStatus.FAILED.name] ?: 0,
                synced = byStatus[SyncStatus.SYNCED.name] ?: 0
            )
        }

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
