package com.example.data.auth.remote

import com.example.domain.model.AuthFailure
import com.example.domain.model.AuthenticatedDriver
import com.example.domain.repository.AuthRepository

/**
 * Production authentication against the TMS.
 *
 * Wired only when a TMS base URL is configured for the build. Until the endpoint exists the app
 * selects [com.example.data.auth.LocalAuthRepository] instead — this class is the seam, not a
 * simulation of a server that is not there.
 */
class RemoteAuthRepository(
    private val api: TmsAuthApi
) : AuthRepository {

    override suspend fun authenticate(loginId: String, pin: String): Result<AuthenticatedDriver> {
        val normalisedLogin = loginId.trim()
        val normalisedPin = pin.trim()
        if (normalisedLogin.isEmpty() || normalisedPin.isEmpty()) {
            return Result.failure(AuthFailure.MissingFields)
        }

        return runCatching {
            api.authenticate(TmsAuthRequest(loginId = normalisedLogin, pin = normalisedPin))
        }.fold(
            onSuccess = { response ->
                Result.success(
                    AuthenticatedDriver(
                        driverId = response.driverId,
                        name = response.name,
                        email = response.email,
                        phone = response.phone
                    )
                )
            },
            onFailure = { error ->
                Result.failure(
                    AuthFailure.Unavailable(error.message ?: "Unable to reach the TMS")
                )
            }
        )
    }
}
