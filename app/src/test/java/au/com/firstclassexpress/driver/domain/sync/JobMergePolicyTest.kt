package au.com.firstclassexpress.driver.domain.sync

import au.com.firstclassexpress.driver.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge rules exist to stop a stale `GET` from erasing work a driver has actually done.
 * These tests are the specification for that.
 */
class JobMergePolicyTest {

    private fun local(
        status: JobStatus,
        updatedAt: Long = 1_000L,
        pending: Boolean = false
    ) = LocalJobState("job-1", status, updatedAt, pending)

    private fun remote(status: JobStatus, updatedAt: Long = 2_000L) =
        RemoteJobState("job-1", status, updatedAt)

    @Test
    fun `an unknown job is inserted`() {
        assertEquals(
            JobMergeDecision.Insert("job-1"),
            JobMergePolicy.decide(local = null, remote = remote(JobStatus.ASSIGNED))
        )
    }

    @Test
    fun `a clean local job accepts newer server state`() {
        val decision = JobMergePolicy.decide(
            local = local(JobStatus.ASSIGNED, updatedAt = 1_000L),
            remote = remote(JobStatus.IN_PROGRESS, updatedAt = 2_000L)
        )

        assertEquals(JobMergeDecision.Update("job-1"), decision)
    }

    @Test
    fun `older server data never touches the device`() {
        val decision = JobMergePolicy.decide(
            local = local(JobStatus.IN_PROGRESS, updatedAt = 5_000L),
            remote = remote(JobStatus.IN_PROGRESS, updatedAt = 2_000L)
        )

        assertTrue(decision is JobMergeDecision.KeepLocal)
    }

    @Test
    fun `a pending local mutation is never silently overwritten`() {
        val decision = JobMergePolicy.decide(
            local = local(JobStatus.PICKED_UP, updatedAt = 1_000L, pending = true),
            remote = remote(JobStatus.ASSIGNED, updatedAt = 9_999L)
        )

        assertTrue(
            "Unsynced driver work outranks any server timestamp",
            decision is JobMergeDecision.Conflict
        )
    }

    @Test
    fun `a completed local job is protected from stale server data`() {
        val decision = JobMergePolicy.decide(
            local = local(JobStatus.COMPLETED, updatedAt = 1_000L, pending = false),
            remote = remote(JobStatus.ASSIGNED, updatedAt = 9_999L)
        )

        assertTrue(
            "A delivered job must not be reopened by the server",
            decision is JobMergeDecision.Conflict
        )
    }

    @Test
    fun `a picked up job is not walked backwards to assigned`() {
        val decision = JobMergePolicy.decide(
            local = local(JobStatus.PICKED_UP, updatedAt = 1_000L),
            remote = remote(JobStatus.IN_PROGRESS, updatedAt = 9_999L)
        )

        assertTrue(decision is JobMergeDecision.Conflict)
    }

    @Test
    fun `a completed job may still advance within completed states`() {
        val decision = JobMergePolicy.decide(
            local = local(JobStatus.AT_DELIVERY, updatedAt = 1_000L),
            remote = remote(JobStatus.COMPLETED, updatedAt = 9_999L)
        )

        assertEquals(JobMergeDecision.Update("job-1"), decision)
    }

    @Test
    fun `a mixed refresh decides each job independently`() {
        val decisions = JobMergePolicy.decideAll(
            local = mapOf(
                "a" to LocalJobState("a", JobStatus.ASSIGNED, 1_000L, false),
                "b" to LocalJobState("b", JobStatus.COMPLETED, 1_000L, true)
            ),
            remote = listOf(
                RemoteJobState("a", JobStatus.IN_PROGRESS, 2_000L),
                RemoteJobState("b", JobStatus.ASSIGNED, 2_000L),
                RemoteJobState("c", JobStatus.ASSIGNED, 2_000L)
            )
        )

        assertEquals(JobMergeDecision.Update("a"), decisions[0])
        assertTrue(decisions[1] is JobMergeDecision.Conflict)
        assertEquals(JobMergeDecision.Insert("c"), decisions[2])
    }
}
