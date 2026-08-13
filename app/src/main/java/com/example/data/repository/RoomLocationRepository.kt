package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.LocationPointEntity
import com.example.data.local.entity.SyncOperationEntity
import com.example.domain.model.LocationPoint
import com.example.domain.model.SyncStatus
import com.example.domain.repository.LocationRepository
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

    override suspend fun save(point: LocationPoint): Result<Unit> = runCatching {
        database.withTransaction {
            database.locationPointDao().insert(point.toEntity())
            database.syncOperationDao().insert(
                SyncOperationEntity(
                    id = idGenerator(), entityType = "LOCATION_POINT", entityId = point.id,
                    operationType = "LOCATION_POINT_CREATED",
                    payloadJson = "{\"driverId\":\"${point.driverId}\",\"shiftId\":\"${point.shiftId}\",\"recordedAt\":${point.recordedAt}}",
                    createdAt = point.createdAt, retryCount = 0, lastError = null,
                    status = SyncStatus.PENDING.name
                )
            )
        }
    }

    override suspend fun latestRecent(maxAgeMillis: Long): LocationPoint? =
        database.locationPointDao().latestSince(clock() - maxAgeMillis)?.toDomain()

    private fun LocationPoint.toEntity() = LocationPointEntity(
        id, driverId, shiftId, jobId, latitude, longitude, accuracyMeters,
        speedMetersPerSecond, bearingDegrees, altitudeMeters, recordedAt, createdAt, syncStatus.name
    )

    private fun LocationPointEntity.toDomain() = LocationPoint(
        id, driverId, shiftId, jobId, latitude, longitude, accuracyMeters,
        speedMetersPerSecond, bearingDegrees, altitudeMeters, recordedAt, createdAt,
        SyncStatus.valueOf(syncStatus)
    )
}
