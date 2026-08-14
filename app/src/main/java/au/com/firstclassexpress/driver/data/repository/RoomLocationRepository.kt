package au.com.firstclassexpress.driver.data.repository

import androidx.room.withTransaction
import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.entity.LocationPointEntity
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import au.com.firstclassexpress.driver.domain.model.LocationPoint
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.repository.LocationRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLocationRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : LocationRepository {
    override fun observeLatest(): Flow<LocationPoint?> =
        database.locationPointDao().observeLatest().map { it?.toDomain() }

    override fun observePendingCount(): Flow<Int> =
        database.locationPointDao().observePendingCount()

    override suspend fun save(point: LocationPoint): Result<Unit> = runCatching {
        database.withTransaction {
            database.locationPointDao().insert(point.toEntity())
            database.syncOperationDao().insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = "LOCATION_POINT",
                    entityId = point.id,
                    operationType = "LOCATION_POINT_CREATED",
                    payloadJson = "{\"driverId\":\"${point.driverId}\",\"shiftId\":\"${point.shiftId}\",\"recordedAt\":${point.recordedAt}}",
                    createdAt = point.createdAt,
                    retryCount = 0,
                    lastError = null,
                    status = SyncStatus.PENDING.name
                )
            )
        }
    }

    override suspend fun getPendingPoints(limit: Int): List<LocationPoint> =
        database.locationPointDao().getPendingPoints(limit).map { it.toDomain() }

    override suspend fun markPointsSynced(ids: List<String>): Result<Unit> = runCatching {
        if (ids.isNotEmpty()) {
            database.locationPointDao().updateSyncStatusForIds(ids, SyncStatus.SYNCED.name)
        }
    }

    override suspend fun latestRecent(maxAgeMillis: Long): LocationPoint? =
        database.locationPointDao().latestSince(clock() - maxAgeMillis)?.toDomain()

    override suspend fun pruneOldSynced(maxAgeMillis: Long): Int =
        database.locationPointDao().pruneSyncedBefore(clock() - maxAgeMillis)

    private fun LocationPoint.toEntity() = LocationPointEntity(
        id = id,
        driverId = driverId,
        shiftId = shiftId,
        jobId = jobId,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
        altitudeMeters = altitudeMeters,
        recordedAt = recordedAt,
        createdAt = createdAt,
        syncStatus = syncStatus.name,
        vehicleId = vehicleId,
        batteryLevel = batteryLevel,
        networkState = networkState,
        source = source
    )

    private fun LocationPointEntity.toDomain() = LocationPoint(
        id = id,
        driverId = driverId,
        shiftId = shiftId,
        jobId = jobId,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
        altitudeMeters = altitudeMeters,
        recordedAt = recordedAt,
        createdAt = createdAt,
        syncStatus = SyncStatus.valueOf(syncStatus),
        vehicleId = vehicleId,
        batteryLevel = batteryLevel,
        networkState = networkState,
        source = source
    )
}
