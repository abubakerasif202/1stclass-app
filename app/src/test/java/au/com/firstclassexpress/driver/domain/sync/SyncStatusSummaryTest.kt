package au.com.firstclassexpress.driver.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Connected" and "synced" are different facts, and the driver-facing wording has to keep them
 * apart without using networking jargon.
 */
class SyncStatusSummaryTest {

    private fun summary(
        counts: SyncQueueCounts,
        online: Boolean = true,
        configured: Boolean = true
    ) = SyncStatusSummaries.from(counts, isOnline = online, isRemoteConfigured = configured)

    @Test
    fun `an empty queue on a working connection is all synced`() {
        val result = summary(SyncQueueCounts(synced = 12))

        assertEquals(SyncStatusSummary.AllSynced, result)
        assertEquals("All synced", result.driverMessage())
    }

    @Test
    fun `being online does not mean the work reached the TMS`() {
        val result = summary(SyncQueueCounts(pending = 8), online = true, configured = false)

        assertTrue(result is SyncStatusSummary.RemoteUnavailable)
        assertEquals("Sync unavailable — 8 changes saved on this device", result.driverMessage())
    }

    @Test
    fun `offline reports the backlog without alarming the driver`() {
        val result = summary(SyncQueueCounts(pending = 1), online = false)

        assertEquals("Offline — 1 change waiting to sync", result.driverMessage())
    }

    @Test
    fun `a backlog waiting on a working connection is counted plainly`() {
        assertEquals("8 changes waiting to sync", summary(SyncQueueCounts(pending = 8)).driverMessage())
    }

    @Test
    fun `failures outrank a merely waiting queue because they need a human`() {
        val result = summary(SyncQueueCounts(pending = 3, failed = 2))

        assertTrue(result is SyncStatusSummary.Failed)
        assertEquals("2 sync failures — tap for details", result.driverMessage())
        assertEquals(5, result.outstandingCount)
    }

    @Test
    fun `an unconfigured endpoint explains the silence before anything else`() {
        val result = summary(SyncQueueCounts(failed = 2), configured = false)

        assertTrue(
            "Nothing can fail against a server that was never contacted",
            result is SyncStatusSummary.RemoteUnavailable
        )
    }

    @Test
    fun `work in flight reads as syncing`() {
        assertTrue(summary(SyncQueueCounts(inProgress = 2)) is SyncStatusSummary.Syncing)
    }

    @Test
    fun `an expired session asks for a sign-in rather than reporting a failure`() {
        val result = SyncStatusSummaries.from(
            SyncQueueCounts(pending = 4),
            isOnline = true,
            isRemoteConfigured = true,
            requiresSignIn = true
        )

        assertEquals("Sign in again to sync 4 changes", result.driverMessage())
    }

    @Test
    fun `synced operations are not counted as outstanding work`() {
        assertEquals(0, summary(SyncQueueCounts(synced = 500)).outstandingCount)
    }
}
