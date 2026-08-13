package com.example.domain.repository

import com.example.domain.model.LocationPoint
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeLatest(): Flow<LocationPoint?>
    suspend fun save(point: LocationPoint): Result<Unit>
    suspend fun latestRecent(maxAgeMillis: Long): LocationPoint?
}
