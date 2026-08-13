package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.FirstClassExpressApplication
import com.example.domain.sync.SyncRunResult

/**
 * Drains the durable sync queue in the background.
 *
 * The worker owns no state: everything it needs is in Room, so a process killed mid-upload simply
 * loses the run, not the work. On restart the stale-lease sweep returns the abandoned operation to
 * `PENDING` and the same idempotency key is replayed.
 */
class TmsSyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? FirstClassExpressApplication)?.container
            ?: return Result.success() // Nothing to drain without an initialised container.

        return when (val outcome = runCatching { container.syncEngine.run() }.getOrElse { error ->
            Log.w(TAG, "Sync run failed", error)
            return Result.retry()
        }) {
            // Nowhere to send anything. Retrying on a timer would burn battery for no reason;
            // the next enqueue or app start will schedule another run.
            is SyncRunResult.RemoteNotConfigured -> Result.success()

            is SyncRunResult.Completed ->
                if (outcome.deferred > 0) Result.retry() else Result.success()

            is SyncRunResult.RetryLater -> Result.retry()

            // Backing off is pointless until the driver signs in again; the queue is intact.
            is SyncRunResult.AuthenticationRequired -> Result.success()
        }
    }

    private companion object {
        const val TAG = "TmsSyncWorker"
    }
}
