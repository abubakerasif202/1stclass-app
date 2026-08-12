package com.example.data.auth

import com.example.data.local.dao.DriverCredentialDao
import com.example.domain.model.AuthFailure
import com.example.domain.model.AuthenticatedDriver
import com.example.domain.repository.AuthRepository

/**
 * Development/offline authentication backed by salted PIN hashes in Room.
 *
 * Driver IDs and emails are matched case-insensitively after trimming; the PIN must match exactly.
 * Unknown logins and wrong PINs return the same failure so the response cannot be used to probe
 * which driver IDs exist.
 */
class LocalAuthRepository(
    private val credentialDao: DriverCredentialDao,
    private val hasher: PinHasher = PinHasher()
) : AuthRepository {

    override suspend fun authenticate(loginId: String, pin: String): Result<AuthenticatedDriver> {
        val normalisedLogin = loginId.trim().lowercase()
        val normalisedPin = pin.trim()
        if (normalisedLogin.isEmpty() || normalisedPin.isEmpty()) {
            return Result.failure(AuthFailure.MissingFields)
        }

        val credential = try {
            credentialDao.findByLogin(normalisedLogin)
        } catch (error: Exception) {
            return Result.failure(
                AuthFailure.Unavailable(error.message ?: "Unable to read local credentials")
            )
        } ?: return Result.failure(AuthFailure.InvalidCredentials)

        if (!hasher.verify(normalisedPin, credential.pinSalt, credential.pinHash)) {
            return Result.failure(AuthFailure.InvalidCredentials)
        }

        return Result.success(
            AuthenticatedDriver(
                driverId = credential.driverId,
                name = credential.displayName,
                email = credential.email,
                phone = credential.phone
            )
        )
    }
}
