package com.example.domain.repository

import com.example.domain.model.ShiftRecord
import kotlinx.coroutines.flow.Flow

interface ShiftRepository {
    fun observeCurrentShift(): Flow<ShiftRecord?>
    suspend fun createPreStartDraft(
        driverId: String,
        vehicleId: String,
        trailerId: String?,
        startOdometer: Long
    ): Result<String>
    suspend fun markReadyToStart(shiftId: String): Result<Unit>
    suspend fun activateShift(shiftId: String): Result<Unit>
    suspend fun endShift(shiftId: String, endOdometer: Long): Result<Unit>
}
