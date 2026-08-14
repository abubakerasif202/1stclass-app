package au.com.firstclassexpress.driver.domain.sync

/**
 * The result of attempting one remote mutation.
 *
 * Only [Success] — a real acknowledgement from a real server — may move a queued operation to
 * `SYNCED`. Every other outcome leaves the local record and any evidence file untouched.
 */
sealed interface SyncOutcome {

    /** The server acknowledged the operation. */
    data object Success : SyncOutcome

    /** Transient: network, timeout, throttling or a server-side fault. Try again later. */
    data class Retryable(val reason: String) : SyncOutcome

    /** The server rejected the operation and will keep rejecting it. Needs a human. */
    data class Permanent(val reason: String) : SyncOutcome

    /** The session is not valid. Stop sending, keep everything, ask the driver to sign in. */
    data object Unauthorized : SyncOutcome

    /** No TMS endpoint is configured for this build, or the transport refuses to run. */
    data class NotConfigured(val reason: String) : SyncOutcome

    /**
     * The operation depends on something that has not synced yet (for example a freight exception
     * whose photo is still queued). Deferring keeps the queue ordered without failing the item.
     */
    data class Deferred(val reason: String) : SyncOutcome
}
