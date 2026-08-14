package au.com.firstclassexpress.driver.domain.sync

import au.com.firstclassexpress.driver.domain.model.SyncOperation

/** How much work is outstanding, by queue state. */
data class SyncQueueCounts(
    val pending: Int = 0,
    val inProgress: Int = 0,
    val failed: Int = 0,
    val synced: Int = 0
) {
    val outstanding: Int get() = pending + inProgress + failed
}

/**
 * The durable queue as the sync engine sees it.
 *
 * State moves `PENDING → IN_PROGRESS → SYNCED`, or `PENDING → IN_PROGRESS → PENDING/FAILED`.
 * Nothing here deletes an operation or the data behind it: a failed upload is a queue problem,
 * never a reason to lose a driver's proof of delivery.
 */
interface SyncQueue {

    /**
     * Atomically takes the oldest `PENDING` operation and marks it `IN_PROGRESS`.
     * Returns null when nothing is available — including when another worker won the race.
     */
    suspend fun claimNext(): SyncOperation?

    /**
     * Returns operations stuck `IN_PROGRESS` since before [staleBefore] to `PENDING`.
     * This is how the queue survives process death mid-upload.
     */
    suspend fun releaseStale(staleBefore: Long): Int

    /** Only ever called after a real server acknowledgement. */
    suspend fun markSynced(id: String)

    /** Back to `PENDING` for another attempt, preserving the operation and its idempotency key. */
    suspend fun markRetryable(id: String, retryCount: Int, error: String)

    /** Rejected by the server. Stays visible so support can see it; never auto-retried. */
    suspend fun markFailed(id: String, retryCount: Int, error: String)

    /** Puts a deferred operation back without counting it as an attempt. */
    suspend fun release(id: String)

    /** Manual recovery: move every `FAILED` operation back to `PENDING`. */
    suspend fun requeueFailed(): Int

    suspend fun counts(): SyncQueueCounts
}
