package au.com.firstclassexpress.driver.domain.repository

import au.com.firstclassexpress.driver.domain.model.LocationPoint
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeLatest(): Flow<LocationPoint?>
    fun observePendingCount(): Flow<Int>
    suspend fun save(point: LocationPoint): Result<Unit>
    suspend fun getPendingPoints(limit: Int): List<LocationPoint>
    suspend fun markPointsSynced(ids: List<String>): Result<Unit>
    suspend fun latestRecent(maxAgeMillis: Long): LocationPoint?
    suspend fun pruneOldSynced(maxAgeMillis: Long): Int
}
