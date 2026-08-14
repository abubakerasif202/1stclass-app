package au.com.firstclassexpress.driver.data.remote

import au.com.firstclassexpress.driver.data.auth.token.InMemoryTokenRepository
import au.com.firstclassexpress.driver.domain.model.AccessToken
import au.com.firstclassexpress.driver.domain.model.AuthenticatedSession
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** When the bearer token is attached, when it is not, and that it never reaches a log. */
class AuthInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun clientWith(tokens: InMemoryTokenRepository, now: Long = 1_000L) =
        OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens) { now }).build()

    private fun session(token: String, expiresAt: Long? = null) = AuthenticatedSession(
        driverId = "DRV-1",
        accessToken = AccessToken(token, expiresAt)
    )

    @Test
    fun `a live token is sent as a bearer header`() = runTest {
        val tokens = InMemoryTokenRepository(session("secret-token"))
        server.enqueue(MockResponse().setResponseCode(200))

        clientWith(tokens).newCall(
            Request.Builder().url(server.url("/v1/driver/jobs")).build()
        ).execute().close()

        assertEquals("Bearer secret-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `no token means no header rather than a guess`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        clientWith(InMemoryTokenRepository()).newCall(
            Request.Builder().url(server.url("/v1/driver/jobs")).build()
        ).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `an expired token is not sent`() = runTest {
        val tokens = InMemoryTokenRepository(session("stale-token", expiresAt = 500L))
        server.enqueue(MockResponse().setResponseCode(200))

        clientWith(tokens, now = 1_000L).newCall(
            Request.Builder().url(server.url("/v1/driver/jobs")).build()
        ).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `the login endpoint is never given a bearer token`() = runTest {
        val tokens = InMemoryTokenRepository(session("secret-token"))
        server.enqueue(MockResponse().setResponseCode(200))

        clientWith(tokens).newCall(
            Request.Builder().url(server.url("/v1/driver/auth")).build()
        ).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `logging the client never prints the token`() {
        val tokens = InMemoryTokenRepository(session("super-secret-token"))
        val client = TmsApiClient.okHttp(tokens, isDebugBuild = true)

        val captured = ByteArrayOutputStream()
        val original = System.out
        try {
            System.setOut(PrintStream(captured))
            // Both the client's own description and the token holder's toString are safe.
            println(client.interceptors.joinToString())
            println(tokens.peek(1_000L).toString())
            println(session("super-secret-token").accessToken.toString())
        } finally {
            System.setOut(original)
        }

        assertFalse(
            "A token must never be printable",
            captured.toString().contains("super-secret-token")
        )
    }
}
