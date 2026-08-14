package au.com.firstclassexpress.driver.domain.sync

import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus

sealed interface JobConflictResolution {
    data class AcceptServer(val updatedJob: Job) : JobConflictResolution
    data class MergeKeepLocalStatus(val mergedJob: Job, val conflictNote: String) : JobConflictResolution
    data class JobCancelledRemotely(val cancelledJob: Job, val preserveEvidence: Boolean) : JobConflictResolution
    data object KeepLocalUnchanged : JobConflictResolution
}

object JobConflictResolver {

    fun resolve(
        localJob: Job,
        serverJob: Job,
        hasPendingLocalOperations: Boolean
    ): JobConflictResolution {
        // 1. If server cancelled the job
        if (serverJob.status == JobStatus.UNASSIGNED) {
            return JobConflictResolution.JobCancelledRemotely(
                cancelledJob = serverJob.copy(
                    status = JobStatus.ISSUE,
                    specialInstructions = "⚠ DISPATCH NOTICE: Job was cancelled or reassigned by operations. ${serverJob.specialInstructions}".trim()
                ),
                preserveEvidence = true
            )
        }

        // 2. If server has higher revision and driver has NO pending local changes
        if (serverJob.revision > localJob.revision && !hasPendingLocalOperations) {
            return JobConflictResolution.AcceptServer(serverJob)
        }

        // 3. If driver is actively progressing locally (e.g. has moved to AT_PICKUP, PICKED_UP, AT_DELIVERY, COMPLETED)
        if (localJob.status.ordinal > serverJob.status.ordinal || hasPendingLocalOperations) {
            // Keep local status and lifecycle, but merge updated customer address/window/instructions if changed
            val merged = localJob.copy(
                pickup = if (localJob.status.ordinal < JobStatus.PICKED_UP.ordinal) serverJob.pickup else localJob.pickup,
                delivery = serverJob.delivery,
                pickupWindowStart = serverJob.pickupWindowStart,
                pickupWindowEnd = serverJob.pickupWindowEnd,
                deliveryWindowStart = serverJob.deliveryWindowStart,
                deliveryWindowEnd = serverJob.deliveryWindowEnd,
                specialInstructions = if (serverJob.specialInstructions != localJob.specialInstructions) {
                    "${localJob.specialInstructions}\n[Updated by Dispatch]: ${serverJob.specialInstructions}".trim()
                } else localJob.specialInstructions,
                priority = serverJob.priority,
                revision = maxOf(localJob.revision, serverJob.revision) + 1L,
                serverUpdatedAt = serverJob.serverUpdatedAt
            )
            return JobConflictResolution.MergeKeepLocalStatus(
                mergedJob = merged,
                conflictNote = "Merged server details while preserving driver on-device job status: ${localJob.status.displayLabel}"
            )
        }

        // 4. Equal or local is ahead
        return JobConflictResolution.KeepLocalUnchanged
    }
}
