package com.example.domain.repository

import com.example.domain.model.AuthenticatedDriver
import com.example.domain.model.DriverSession
import kotlinx.coroutines.flow.Flow

/**
 * Stores who is currently signed in on this device.
 *
 * Clearing a session signs the driver out. It must never remove operational data — completed jobs,
 * inspection history and queued sync operations all outlive a session.
 */
interface SessionRepository {
    fun observeSession(): Flow<DriverSession?>
    suspend fun currentSession(): DriverSession?
    suspend fun startSession(driver: AuthenticatedDriver): Result<DriverSession>
    suspend fun clearSession(): Result<Unit>
}
