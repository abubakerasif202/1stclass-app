package au.com.firstclassexpress.driver.data.repository

import androidx.room.withTransaction
import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import au.com.firstclassexpress.driver.domain.model.SyncOperation
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.sync.SyncQueue
import au.com.firstclassexpress.driver.domain.sync.SyncQueueCounts

/**
 * Room-backed [SyncQueue].
 *
 * The claim is a conditional update inside a transaction: `UPDATE … WHERE id = ? AND status =
 * 'PENDING'` reports one changed row for exactly one caller, so two workers — or a duplicate
 * WorkManager run — cannot process the same operation concurrently. The loser sees zero rows and
 * simply asks for the next one.
 */
class RoomSyncQueue(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) : SyncQueue {
    private val dao = database.syncOperationDao()

    override suspend fun claimNext(): SyncOperation? {
        // Bounded so a pathological race can never spin forever.
        repeat(MAX_CLAIM_ATTEMPTS) {
            val claimed = database.withTransaction {
                val candidate = dao.nextPending() ?: return@withTransaction null
                if (dao.claim(candidate.id, clock()) == 1) candidate else null
            }
            if (claimed != null) return claimed.toDomain()
            if (dao.nextPending() == null) return null
        }
        return null
    }

    override suspend fun releaseStale(staleBefore: Long): Int =
        dao.releaseStale(staleBefore = staleBefore, now = clock())

    override suspend fun markSynced(id: String) {
        dao.markSynced(id, clock())
    }

    override suspend fun markRetryable(id: String, retryCount: Int, error: String) {
        dao.markRetryable(id, retryCount, error.summarised(), clock())
    }

    override suspend fun markFailed(id: String, retryCount: Int, error: String) {
        dao.markFailed(id, retryCount, error.summarised(), clock())
    }

    override suspend fun release(id: String) {
        val existing = dao.getById(id) ?: return
        dao.markRetryable(id, existing.retryCount, existing.lastError.orEmpty(), clock())
    }

    override suspend fun requeueFailed(): Int = dao.requeueFailed(clock())

    override suspend fun counts(): SyncQueueCounts = SyncQueueCounts(
        pending = dao.countByStatus(SyncStatus.PENDING.name),
        inProgress = dao.countByStatus(SyncStatus.IN_PROGRESS.name),
        failed = dao.countByStatus(SyncStatus.FAILED.name),
        synced = dao.countByStatus(SyncStatus.SYNCED.name)
    )

    /** Error text is shown to drivers and support, so keep it short and free of payload data. */
    private fun String.summarised(): String = take(MAX_ERROR_LENGTH)

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

    private companion object {
        const val MAX_CLAIM_ATTEMPTS = 5
        const val MAX_ERROR_LENGTH = 200
    }
}
