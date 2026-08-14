package au.com.firstclassexpress.driver.domain.repository

import au.com.firstclassexpress.driver.domain.model.SyncOperation
import au.com.firstclassexpress.driver.domain.sync.SyncQueueCounts
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun observePending(): Flow<List<SyncOperation>>

    /** Live queue totals per state, for the sync status UI and the diagnostics screen. */
    fun observeCounts(): Flow<SyncQueueCounts>
    suspend fun enqueue(
        entityType: String,
        entityId: String,
        operationType: String,
        payloadJson: String
    ): Result<String>
    suspend fun markFailure(id: String, error: String): Result<Unit>
    suspend fun markSynced(id: String): Result<Unit>
}
