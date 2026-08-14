package au.com.firstclassexpress.driver.domain.sync

import au.com.firstclassexpress.driver.data.remote.TmsEnvironment
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.testing.FakeSyncQueue
import au.com.firstclassexpress.driver.testing.syncOperation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the queue must obey. The single most important one is the last test in this file:
 * with no TMS configured, nothing is ever marked SYNCED.
 */
class SyncEngineTest {

    /** Far enough past epoch that "now minus the stale lease" is still a sane instant. */
    private val NOW = 10_000_000L

    private val configured = TmsEnvironment.Configured("https://tms.example.com/")

    private fun engine(
        queue: FakeSyncQueue,
        outcomes: MutableList<SyncOutcome>,
        environment: TmsEnvironment = configured,
        onUnauthorized: suspend () -> Unit = {},
        maxRetries: Int = SyncEngine.DEFAULT_MAX_RETRIES
    ) = SyncEngine(
        queue = queue,
        process = { if (outcomes.isEmpty()) SyncOutcome.Success else outcomes.removeAt(0) },
        environment = { environment },
        onUnauthorized = onUnauthorized,
        maxRetriesBeforeFailing = maxRetries,
        clock = { NOW }
    )

    @Test
    fun `pending operation is picked up and acknowledged work becomes synced`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1")))

        val result = engine(queue, mutableListOf(SyncOutcome.Success)).run()

        assertEquals(SyncRunResult.Completed(synced = 1, failed = 0, deferred = 0), result)
        assertEquals(SyncStatus.SYNCED, queue.find("op-1")!!.status)
    }

    @Test
    fun `a timeout leaves the operation retryable and keeps it queued`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1")))

        val result = engine(queue, mutableListOf(SyncOutcome.Retryable("TMS request timed out"))).run()

        assertTrue(result is SyncRunResult.RetryLater)
        val operation = queue.find("op-1")!!
        assertEquals(SyncStatus.PENDING, operation.status)
        assertEquals(1, operation.retryCount)
    }

    @Test
    fun `a permanent rejection fails the operation but preserves it`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1")))

        val result = engine(queue, mutableListOf(SyncOutcome.Permanent("HTTP 400 rejected by TMS"))).run()

        assertEquals(SyncRunResult.Completed(synced = 0, failed = 1, deferred = 0), result)
        val operation = queue.find("op-1")!!
        assertEquals(SyncStatus.FAILED, operation.status)
        assertEquals("HTTP 400 rejected by TMS", operation.lastError)
        assertNotNull("The operation must not be deleted", queue.find("op-1"))
    }

    @Test
    fun `a permanent failure does not block the rest of the queue`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1"), syncOperation("op-2")))

        val result = engine(
            queue,
            mutableListOf(SyncOutcome.Permanent("rejected"), SyncOutcome.Success)
        ).run()

        assertEquals(SyncRunResult.Completed(synced = 1, failed = 1, deferred = 0), result)
        assertEquals(SyncStatus.FAILED, queue.find("op-1")!!.status)
        assertEquals(SyncStatus.SYNCED, queue.find("op-2")!!.status)
    }

    @Test
    fun `a retryable operation is failed once it exhausts its attempts`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1", retryCount = 2)))

        engine(queue, mutableListOf(SyncOutcome.Retryable("HTTP 500 from TMS")), maxRetries = 3).run()

        val operation = queue.find("op-1")!!
        assertEquals(SyncStatus.FAILED, operation.status)
        assertTrue(operation.lastError!!.contains("gave up after 3 attempts"))
    }

    @Test
    fun `unauthorized stops the run, preserves the queue and signals re-authentication`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1"), syncOperation("op-2")))
        var clearedTokens = false

        val result = engine(
            queue,
            mutableListOf(SyncOutcome.Unauthorized),
            onUnauthorized = { clearedTokens = true }
        ).run()

        assertEquals(SyncRunResult.AuthenticationRequired(synced = 0), result)
        assertTrue("A 401 must clear the dead token", clearedTokens)
        assertEquals(SyncStatus.PENDING, queue.find("op-1")!!.status)
        assertEquals(SyncStatus.PENDING, queue.find("op-2")!!.status)
        // The failed attempt must not be charged against the operation's retry budget.
        assertEquals(0, queue.find("op-1")!!.retryCount)
    }

    @Test
    fun `a deferred operation is released without being counted as an attempt`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1"), syncOperation("op-2")))

        val result = engine(
            queue,
            mutableListOf(SyncOutcome.Deferred("waiting for the photo"), SyncOutcome.Success)
        ).run()

        assertEquals(SyncRunResult.Completed(synced = 1, failed = 0, deferred = 1), result)
        val deferredOperation = queue.find("op-1")!!
        assertEquals(SyncStatus.PENDING, deferredOperation.status)
        assertEquals(0, deferredOperation.retryCount)
        assertEquals(SyncStatus.SYNCED, queue.find("op-2")!!.status)
    }

    @Test
    fun `an unknown operation type fails safely instead of crashing the run`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1", entityType = "MARTIAN")))

        val result = engine(
            queue,
            mutableListOf(SyncOutcome.Permanent("Unsupported operation MARTIAN/STATUS_CHANGE"))
        ).run()

        assertEquals(SyncRunResult.Completed(synced = 0, failed = 1, deferred = 0), result)
        assertEquals(SyncStatus.FAILED, queue.find("op-1")!!.status)
    }

    @Test
    fun `stale in-progress work is recovered on the next run`() = runTest {
        val queue = FakeSyncQueue(
            listOf(syncOperation("op-1", createdAt = 1L, status = SyncStatus.IN_PROGRESS))
        )

        val result = engine(queue, mutableListOf(SyncOutcome.Success)).run()

        assertEquals(SyncRunResult.Completed(synced = 1, failed = 0, deferred = 0), result)
        assertEquals(SyncStatus.SYNCED, queue.find("op-1")!!.status)
    }

    @Test
    fun `the same operation is never processed twice in one run`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1")))
        val processed = mutableListOf<String>()

        SyncEngine(
            queue = queue,
            process = { processed += it.id; SyncOutcome.Success },
            environment = { configured },
            clock = { NOW }
        ).run()

        assertEquals(listOf("op-1"), processed)
    }

    @Test
    fun `the operation id is reused as the idempotency key across retries`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1")))
        val keysSeen = mutableListOf<String>()
        val outcomes = mutableListOf<SyncOutcome>(
            SyncOutcome.Retryable("network"),
            SyncOutcome.Success
        )
        val engine = SyncEngine(
            queue = queue,
            process = { keysSeen += it.id; outcomes.removeAt(0) },
            environment = { configured },
            clock = { NOW }
        )

        engine.run()
        engine.run()

        assertEquals("Retrying must not mint a new key", listOf("op-1", "op-1"), keysSeen)
        assertEquals(SyncStatus.SYNCED, queue.find("op-1")!!.status)
    }

    // --- the rule this whole phase exists to protect -------------------------------------------

    @Test
    fun `with no TMS configured nothing is attempted and nothing is marked synced`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1"), syncOperation("op-2")))
        var attempts = 0

        val result = SyncEngine(
            queue = queue,
            process = { attempts++; SyncOutcome.Success },
            environment = { TmsEnvironment.NotConfigured },
            clock = { NOW }
        ).run()

        assertTrue(result is SyncRunResult.RemoteNotConfigured)
        assertEquals("No request may be attempted without an endpoint", 0, attempts)
        assertTrue(queue.rows.all { it.status == SyncStatus.PENDING })
    }

    @Test
    fun `a rejected endpoint is treated as unconfigured, not as a failure`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1")))

        val result = SyncEngine(
            queue = queue,
            process = { SyncOutcome.Success },
            environment = { TmsEnvironment.Rejected("Release builds require an https:// TMS base URL") },
            clock = { NOW }
        ).run()

        assertEquals(
            SyncRunResult.RemoteNotConfigured("Release builds require an https:// TMS base URL"),
            result
        )
        assertEquals(SyncStatus.PENDING, queue.find("op-1")!!.status)
    }

    @Test
    fun `a transport that refuses leaves the operation pending with no attempt recorded`() = runTest {
        val queue = FakeSyncQueue(listOf(syncOperation("op-1")))

        val result = SyncEngine(
            queue = queue,
            process = { SyncOutcome.NotConfigured("No TMS endpoint is configured for this build") },
            environment = { configured },
            clock = { NOW }
        ).run()

        assertTrue(result is SyncRunResult.RemoteNotConfigured)
        val operation = queue.find("op-1")!!
        assertEquals(SyncStatus.PENDING, operation.status)
        assertEquals(0, operation.retryCount)
    }
}
