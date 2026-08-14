package au.com.firstclassexpress.driver.domain.rules

import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus

data class ShiftEndValidationResult(
    val canEndSafely: Boolean,
    val pendingJobsCount: Int,
    val pendingSyncCount: Int,
    val warnings: List<String>
)

object ShiftEndValidator {

    fun validate(
        assignedJobs: List<Job>,
        pendingSyncOperationsCount: Int,
        pendingLocationPointsCount: Int
    ): ShiftEndValidationResult {
        val incompleteJobs = assignedJobs.filter { it.status != JobStatus.COMPLETED && !it.status.isTerminal }
        val warnings = mutableListOf<String>()

        if (incompleteJobs.isNotEmpty()) {
            warnings.add("${incompleteJobs.size} job(s) are still active or uncompleted.")
        }

        val totalPendingSync = pendingSyncOperationsCount + pendingLocationPointsCount
        if (totalPendingSync > 0) {
            warnings.add("$totalPendingSync operational item(s) are still queued to sync. Ensure you stay online so all PODs and records reach dispatch.")
        }

        return ShiftEndValidationResult(
            canEndSafely = incompleteJobs.isEmpty() && totalPendingSync == 0,
            pendingJobsCount = incompleteJobs.size,
            pendingSyncCount = totalPendingSync,
            warnings = warnings
        )
    }
}
