package com.example.data.remote

import com.example.domain.sync.SyncOutcome
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which HTTP answers are worth another try, and which need a human. */
class NetworkResultMapperTest {

    @Test
    fun `only a 2xx counts as a server acknowledgement`() {
        assertEquals(SyncOutcome.Success, NetworkResultMapper.fromHttpCode(200))
        assertEquals(SyncOutcome.Success, NetworkResultMapper.fromHttpCode(201))
        assertEquals(SyncOutcome.Success, NetworkResultMapper.fromHttpCode(204))
        assertFalse(NetworkResultMapper.fromHttpCode(302) is SyncOutcome.Success)
    }

    @Test
    fun `401 and 403 are session problems, not operation problems`() {
        assertEquals(SyncOutcome.Unauthorized, NetworkResultMapper.fromHttpCode(401))
        assertEquals(SyncOutcome.Unauthorized, NetworkResultMapper.fromHttpCode(403))
    }

    @Test
    fun `timeouts, throttling and server faults are retryable`() {
        listOf(408, 425, 429, 500, 502, 503, 504).forEach { code ->
            assertTrue(
                "HTTP $code should be retryable",
                NetworkResultMapper.fromHttpCode(code) is SyncOutcome.Retryable
            )
        }
    }

    @Test
    fun `a malformed request is permanent and will not be retried forever`() {
        listOf(400, 404, 409, 422).forEach { code ->
            assertTrue(
                "HTTP $code should be permanent",
                NetworkResultMapper.fromHttpCode(code) is SyncOutcome.Permanent
            )
        }
    }

    @Test
    fun `network faults are retryable rather than data loss`() {
        assertTrue(NetworkResultMapper.fromThrowable(IOException()) is SyncOutcome.Retryable)
        assertTrue(
            NetworkResultMapper.fromThrowable(SocketTimeoutException()) is SyncOutcome.Retryable
        )
        assertTrue(
            NetworkResultMapper.fromThrowable(UnknownHostException()) is SyncOutcome.Retryable
        )
        assertTrue(
            NetworkResultMapper.fromThrowable(SSLHandshakeException("handshake")) is SyncOutcome.Retryable
        )
    }

    @Test
    fun `stored reasons never carry a response body or a credential`() {
        val reason = (NetworkResultMapper.fromHttpCode(500) as SyncOutcome.Retryable).reason
        assertEquals("HTTP 500 from TMS", reason)

        // The mapper is only ever given a code, so a body cannot leak through it by construction.
        val permanent = (NetworkResultMapper.fromHttpCode(400) as SyncOutcome.Permanent).reason
        assertFalse(permanent.contains("Bearer"))
        assertFalse(permanent.contains("pin"))
    }
}
