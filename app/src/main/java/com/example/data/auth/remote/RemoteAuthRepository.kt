package com.example.data.auth.remote

import com.example.domain.model.AccessToken
import com.example.domain.model.AuthFailure
import com.example.domain.model.AuthenticatedDriver
import com.example.domain.model.AuthenticatedSession
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.TokenRepository

/**
 * Production authentication against the TMS.
 *
 * Wired only when a TMS base URL is configured for the build. Until the endpoint exists the app
 * selects [com.example.data.auth.LocalAuthRepository] instead — this class is the seam, not a
 * simulation of a server that is not there.
 *
 * The PIN is used once, for the request, and never persisted. Only the returned bearer token is
 * stored, and only in keystore-backed encrypted storage.
 */
class RemoteAuthRepository(
    private val api: TmsAuthApi,
    private val tokens: TokenRepository,
    private val clock: () -> Long = System::currentTimeMillis
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
                response.accessToken?.takeIf { it.isNotBlank() }?.let { token ->
                    tokens.save(
                        AuthenticatedSession(
                            driverId = response.driverId,
                            accessToken = AccessToken(
                                value = token,
                                expiresAtMillis = response.expiresInSeconds
                                    ?.let { clock() + it * 1_000L }
                            )
                        )
                    )
                }
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
                // The thrown message can carry a URL but never a credential — the request's
                // toString is redacted and bodies are not logged.
                Result.failure(
                    AuthFailure.Unavailable(error.message ?: "Unable to reach the TMS")
                )
            }
        )
    }
}
