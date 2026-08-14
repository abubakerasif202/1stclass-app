package au.com.firstclassexpress.driver.domain.sync

import au.com.firstclassexpress.driver.model.JobStatus

/** A job as it exists on the device, reduced to what the merge decision needs. */
data class LocalJobState(
    val jobId: String,
    val status: JobStatus,
    val updatedAt: Long,
    /** True when the device holds queued changes for this job that the TMS has not accepted yet. */
    val hasPendingLocalMutations: Boolean
)

/** A job as the TMS reports it. */
data class RemoteJobState(
    val jobId: String,
    val status: JobStatus,
    val updatedAt: Long
)

/** What the app should do with one downloaded job. */
sealed interface JobMergeDecision {
    /** Not on the device yet — take it. */
    data class Insert(val jobId: String) : JobMergeDecision

    /** No local changes at risk and the server is newer — take it. */
    data class Update(val jobId: String) : JobMergeDecision

    /** Server data is the same age or older. Leave the device alone. */
    data class KeepLocal(val jobId: String, val reason: String) : JobMergeDecision

    /**
     * Server and device disagree and the device has work that has not been accepted yet.
     * Local state wins and the disagreement is recorded for later resolution.
     */
    data class Conflict(val jobId: String, val reason: String) : JobMergeDecision
}

/**
 * Decides, per job, whether downloaded data may touch the device.
 *
 * The governing principle: **a driver's completed work is never overwritten by the server.** The
 * device is the source of truth for anything the driver has done but the TMS has not yet
 * acknowledged, because the alternative — a stale `GET` erasing a delivery that is queued for
 * upload — destroys evidence and is unrecoverable.
 *
 * The policy is deliberately conservative in this first implementation: when in doubt, keep local
 * and flag a conflict. Automatic resolution can only be designed once the real TMS contract tells
 * us what the server's `updatedAt` actually means.
 */
object JobMergePolicy {

    /** Statuses that represent work the driver has physically performed. */
    private val DRIVER_COMPLETED_STATUSES = setOf(
        JobStatus.PICKED_UP,
        JobStatus.EN_ROUTE_DELIVERY,
        JobStatus.AT_DELIVERY,
        JobStatus.COMPLETED
    )

    fun decide(local: LocalJobState?, remote: RemoteJobState): JobMergeDecision {
        if (local == null) return JobMergeDecision.Insert(remote.jobId)

        if (local.hasPendingLocalMutations && local.status != remote.status) {
            return JobMergeDecision.Conflict(
                remote.jobId,
                "Device has unsynced changes (local ${local.status}, server ${remote.status})"
            )
        }

        // Even with a clean queue, the server must never walk a job backwards out of a state the
        // driver reached on the ground.
        if (local.status in DRIVER_COMPLETED_STATUSES && remote.status !in DRIVER_COMPLETED_STATUSES) {
            return JobMergeDecision.Conflict(
                remote.jobId,
                "Server would undo completed work (local ${local.status}, server ${remote.status})"
            )
        }

        if (remote.updatedAt <= local.updatedAt) {
            return JobMergeDecision.KeepLocal(remote.jobId, "Server data is not newer")
        }

        return JobMergeDecision.Update(remote.jobId)
    }

    fun decideAll(
        local: Map<String, LocalJobState>,
        remote: List<RemoteJobState>
    ): List<JobMergeDecision> = remote.map { decide(local[it.jobId], it) }
}
