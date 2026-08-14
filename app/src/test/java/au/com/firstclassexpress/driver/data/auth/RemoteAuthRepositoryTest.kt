package au.com.firstclassexpress.driver.data.auth

import au.com.firstclassexpress.driver.data.auth.remote.RemoteAuthRepository
import au.com.firstclassexpress.driver.data.auth.remote.TmsAuthApi
import au.com.firstclassexpress.driver.data.auth.remote.TmsAuthRequest
import au.com.firstclassexpress.driver.data.auth.remote.TmsAuthResponse
import au.com.firstclassexpress.driver.data.auth.token.InMemoryTokenRepository
import au.com.firstclassexpress.driver.domain.model.AuthFailure
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the app keeps after a sign-in, and — more importantly — what it throws away. */
class RemoteAuthRepositoryTest {

    private class RecordingAuthApi(
        private val response: TmsAuthResponse? = null,
        private val error: Throwable? = null
    ) : TmsAuthApi {
        var lastRequest: TmsAuthRequest? = null

        override suspend fun authenticate(request: TmsAuthRequest): TmsAuthResponse {
            lastRequest = request
            error?.let { throw it }
            return requireNotNull(response)
        }
    }

    private fun response(token: String = "abc123", expiresAt: Long = 4_600_000L) = TmsAuthResponse(
        driverId = "DRV-8492",
        name = "James Miller",
        token = token,
        refreshToken = "refresh-token",
        expiresAt = expiresAt
    )

    @Test
    fun `a returned token is stored securely with its expiry`() = runTest {
        val tokens = InMemoryTokenRepository()
        val repository = RemoteAuthRepository(
            RecordingAuthApi(response(token = "abc123", expiresAt = 4_600_000L)),
            tokens,
            clock = { 1_000_000L }
        )

        val result = repository.authenticate("DRV-8492", "1234")

        assertTrue(result.isSuccess)
        val stored = tokens.load()!!
        assertEquals("abc123", stored.accessToken.value)
        assertEquals(1_000_000L + 3_600_000L, stored.accessToken.expiresAtMillis)
    }

    @Test
    fun `the PIN is never persisted`() = runTest {
        val tokens = InMemoryTokenRepository()
        val repository = RemoteAuthRepository(RecordingAuthApi(response(token = "abc123")), tokens)

        repository.authenticate("DRV-8492", "1234")

        val stored = tokens.load()!!
        assertFalse(stored.toString().contains("1234"))
        assertFalse(stored.accessToken.toString().contains("1234"))
        assertEquals("Only the token is kept", "abc123", stored.accessToken.value)
    }

    @Test
    fun `a request never prints the PIN`() = runTest {
        val api = RecordingAuthApi(response(token = "abc123"))

        RemoteAuthRepository(api, InMemoryTokenRepository()).authenticate("DRV-8492", "1234")

        assertFalse(
            "The PIN must be redacted in any log or crash report",
            api.lastRequest.toString().contains("1234")
        )
    }

    @Test
    fun `a blank server token does not get persisted`() = runTest {
        val tokens = InMemoryTokenRepository()

        val result = RemoteAuthRepository(RecordingAuthApi(response(token = "")), tokens)
            .authenticate("DRV-8492", "1234")

        assertTrue(result.isSuccess)
        assertNull(tokens.load())
    }

    @Test
    fun `an unreachable TMS reports unavailable and stores no token`() = runTest {
        val tokens = InMemoryTokenRepository()

        val result = RemoteAuthRepository(
            RecordingAuthApi(error = IOException("host unreachable")),
            tokens
        ).authenticate("DRV-8492", "1234")

        assertTrue(result.exceptionOrNull() is AuthFailure.Unavailable)
        assertNull(tokens.load())
    }

    @Test
    fun `blank credentials fail before any request is made`() = runTest {
        val api = RecordingAuthApi(response(token = "abc123"))

        val result = RemoteAuthRepository(api, InMemoryTokenRepository()).authenticate("", "")

        assertEquals(AuthFailure.MissingFields, result.exceptionOrNull())
        assertNull("No credential may leave the device", api.lastRequest)
    }
}
