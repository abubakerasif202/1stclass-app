package au.com.firstclassexpress.driver.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules queue drains.
 *
 * There is no periodic poll. A run happens when there is a reason for one: work was queued, the
 * app started with a backlog, or the driver asked. WorkManager's `CONNECTED` constraint means the
 * OS wakes us when the network returns rather than us waking up to ask whether it has.
 *
 * Unique work with [ExistingWorkPolicy.KEEP] guarantees a single in-flight worker, which — with
 * the queue's atomic claim — makes double-processing an operation impossible.
 */
class SyncScheduler(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Resolved on first use, not at construction. WorkManager initialises from a ContentProvider,
     * which Android creates *after* `Application.onCreate` — so touching it while the container is
     * being built throws.
     */
    private val workManager: WorkManager by lazy { WorkManager.getInstance(appContext) }

    /** Requests a drain. Cheap and safe to call on every enqueue. */
    fun requestSync() = enqueue(ExistingWorkPolicy.KEEP)

    /**
     * "Sync now" from the UI. Replaces any queued-but-not-started run so the driver's tap has an
     * immediate effect instead of joining the back of a backoff.
     */
    fun requestImmediateSync() = enqueue(ExistingWorkPolicy.REPLACE)

    private fun enqueue(policy: ExistingWorkPolicy) {
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            policy,
            OneTimeWorkRequestBuilder<TmsSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    INITIAL_BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )
                .addTag(TAG)
                .build()
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "tms-sync"
        const val TAG = "tms-sync"

        /** 30s, then exponential: 30s, 1m, 2m, 4m… up to WorkManager's 5h ceiling. */
        const val INITIAL_BACKOFF_SECONDS = 30L
    }
}
