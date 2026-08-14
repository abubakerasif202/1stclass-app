package au.com.firstclassexpress.driver.data.auth.remote

import au.com.firstclassexpress.driver.domain.model.AccessToken
import au.com.firstclassexpress.driver.domain.model.AuthFailure
import au.com.firstclassexpress.driver.domain.model.AuthenticatedDriver
import au.com.firstclassexpress.driver.domain.model.AuthenticatedSession
import au.com.firstclassexpress.driver.domain.repository.AuthRepository
import au.com.firstclassexpress.driver.domain.repository.TokenRepository

/**
 * Production authentication against the TMS.
 *
 * Wired only when a TMS base URL is configured for the build. Until the endpoint exists the app
 * selects [au.com.firstclassexpress.driver.data.auth.LocalAuthRepository] instead — this class is the seam, not a
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
            api.authenticate(TmsAuthRequest(driverId = normalisedLogin, pin = normalisedPin))
        }.fold(
            onSuccess = { response ->
                response.token.takeIf { it.isNotBlank() }?.let { token ->
                    tokens.save(
                        AuthenticatedSession(
                            driverId = response.driverId,
                            accessToken = AccessToken(
                                value = token,
                                expiresAtMillis = response.expiresAt
                            )
                        )
                    )
                }
                Result.success(
                    AuthenticatedDriver(
                        driverId = response.driverId,
                        name = response.name,
                        email = "",
                        phone = null
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
