package com.example.domain.sync

import com.example.data.remote.TmsEnvironment
import com.example.domain.model.SyncOperation

/** What one drain of the queue achieved. */
sealed interface SyncRunResult {
    /** No endpoint configured. Nothing was attempted and nothing was marked. */
    data class RemoteNotConfigured(val reason: String) : SyncRunResult

    data class Completed(val synced: Int, val failed: Int, val deferred: Int) : SyncRunResult

    /** Transient problem. Work remains `PENDING`; WorkManager should back off and try again. */
    data class RetryLater(val synced: Int, val reason: String) : SyncRunResult

    /** The server rejected our session. Everything is preserved; the driver must sign in again. */
    data class AuthenticationRequired(val synced: Int) : SyncRunResult
}

/**
 * Drains the durable queue exactly once.
 *
 * Deliberately free of Android and WorkManager types so the whole retry, idempotency and
 * state-transition story can be tested as plain Kotlin. [com.example.sync.TmsSyncWorker] is a thin
 * shell that calls [run] and translates the result into a WorkManager `Result`.
 *
 * The engine never deletes an operation and never marks one `SYNCED` without a
 * [SyncOutcome.Success] from a real server.
 */
class SyncEngine(
    private val queue: SyncQueue,
    private val process: suspend (SyncOperation) -> SyncOutcome,
    private val environment: () -> TmsEnvironment,
    private val onUnauthorized: suspend () -> Unit = {},
    private val maxOperationsPerRun: Int = DEFAULT_MAX_OPERATIONS_PER_RUN,
    private val maxRetriesBeforeFailing: Int = DEFAULT_MAX_RETRIES,
    private val staleLeaseMillis: Long = DEFAULT_STALE_LEASE_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    suspend fun run(): SyncRunResult {
        // Anything a dead process left mid-flight comes back to the queue first. The idempotency
        // key is unchanged, so a request the server did receive is deduplicated on replay.
        queue.releaseStale(staleBefore = clock() - staleLeaseMillis)

        when (val env = environment()) {
            is TmsEnvironment.NotConfigured ->
                return SyncRunResult.RemoteNotConfigured("No TMS endpoint configured")

            is TmsEnvironment.Rejected ->
                return SyncRunResult.RemoteNotConfigured(env.reason)

            is TmsEnvironment.Configured -> Unit
        }

        var synced = 0
        var failed = 0
        val deferred = mutableListOf<String>()

        try {
            var processed = 0
            while (processed < maxOperationsPerRun) {
                val operation = queue.claimNext() ?: break
                processed++

                when (val outcome = process(operation)) {
                    is SyncOutcome.Success -> {
                        queue.markSynced(operation.id)
                        synced++
                    }

                    is SyncOutcome.Permanent -> {
                        queue.markFailed(operation.id, operation.retryCount + 1, outcome.reason)
                        failed++
                    }

                    is SyncOutcome.Deferred -> {
                        // Held out of this run so it cannot be re-claimed in the same loop, and
                        // released back to PENDING once the run finishes.
                        deferred += operation.id
                    }

                    is SyncOutcome.Retryable -> {
                        val attempts = operation.retryCount + 1
                        if (attempts >= maxRetriesBeforeFailing) {
                            // Stop hammering a request that has never once succeeded.
                            queue.markFailed(
                                operation.id,
                                attempts,
                                "${outcome.reason} (gave up after $attempts attempts)"
                            )
                            failed++
                        } else {
                            queue.markRetryable(operation.id, attempts, outcome.reason)
                            // A transient fault hits every operation, not just this one — stop and
                            // let WorkManager's backoff decide when to come back.
                            return SyncRunResult.RetryLater(synced, outcome.reason)
                        }
                    }

                    is SyncOutcome.Unauthorized -> {
                        queue.markRetryable(operation.id, operation.retryCount, "Sign-in required")
                        onUnauthorized()
                        return SyncRunResult.AuthenticationRequired(synced)
                    }

                    is SyncOutcome.NotConfigured -> {
                        // Not the operation's fault: no attempt is counted against it.
                        queue.release(operation.id)
                        return SyncRunResult.RemoteNotConfigured(outcome.reason)
                    }
                }
            }
        } finally {
            deferred.forEach { queue.release(it) }
        }

        return SyncRunResult.Completed(synced = synced, failed = failed, deferred = deferred.size)
    }

    companion object {
        /** Keeps one wake-up bounded; leftover work simply schedules another run. */
        const val DEFAULT_MAX_OPERATIONS_PER_RUN = 100

        /** After this many transient failures an operation is parked for a human to look at. */
        const val DEFAULT_MAX_RETRIES = 10

        /** An operation claimed longer ago than this is assumed orphaned by process death. */
        const val DEFAULT_STALE_LEASE_MILLIS = 10 * 60 * 1000L
    }
}
