package com.example.domain.sync

/**
 * What the driver is told about sync, in one value.
 *
 * "Connected to the internet" and "our work reached the TMS" are separate facts and this type
 * keeps them separate: [RemoteUnavailable] can be true while the device is online.
 */
sealed interface SyncStatusSummary {
    /** No network. Everything is saved locally. */
    data class Offline(val outstanding: Int) : SyncStatusSummary

    /** Online, but there is no TMS to talk to. Work is safe and stays queued. */
    data class RemoteUnavailable(val outstanding: Int, val reason: String) : SyncStatusSummary

    /** The session expired; uploads are paused until the driver signs in again. */
    data class SignInRequired(val outstanding: Int) : SyncStatusSummary

    /** Work is on its way. */
    data class Syncing(val outstanding: Int) : SyncStatusSummary

    /** Some operations were rejected and need attention. */
    data class Failed(val failed: Int, val outstanding: Int) : SyncStatusSummary

    /** Queued and waiting for a window to send. */
    data class Waiting(val outstanding: Int) : SyncStatusSummary

    /** Nothing outstanding. */
    data object AllSynced : SyncStatusSummary

    val outstandingCount: Int
        get() = when (this) {
            is Offline -> outstanding
            is RemoteUnavailable -> outstanding
            is SignInRequired -> outstanding
            is Syncing -> outstanding
            is Failed -> outstanding
            is Waiting -> outstanding
            AllSynced -> 0
        }

    /** Short enough for the dashboard; no networking jargon. */
    fun driverMessage(): String = when (this) {
        is Offline ->
            if (outstanding == 0) "Offline — everything is saved"
            else "Offline — ${plural(outstanding)} waiting to sync"

        is RemoteUnavailable ->
            if (outstanding == 0) "Sync unavailable — nothing is waiting"
            else "Sync unavailable — ${plural(outstanding)} saved on this device"

        is SignInRequired -> "Sign in again to sync ${plural(outstanding)}"

        is Syncing -> "Syncing ${plural(outstanding)}…"

        is Failed ->
            if (failed == 1) "1 sync failure — tap for details"
            else "$failed sync failures — tap for details"

        is Waiting -> "${plural(outstanding)} waiting to sync"

        AllSynced -> "All synced"
    }

    private fun plural(count: Int): String =
        if (count == 1) "1 change" else "$count changes"
}

/**
 * Builds the summary from the three independent inputs. Order matters: a failure the driver can
 * act on outranks a queue that is merely waiting, and a missing endpoint outranks both because it
 * explains why nothing is moving.
 */
object SyncStatusSummaries {
    fun from(
        counts: SyncQueueCounts,
        isOnline: Boolean,
        isRemoteConfigured: Boolean,
        remoteUnavailableReason: String = "No TMS endpoint is configured",
        requiresSignIn: Boolean = false
    ): SyncStatusSummary {
        val outstanding = counts.outstanding
        return when {
            !isRemoteConfigured -> SyncStatusSummary.RemoteUnavailable(outstanding, remoteUnavailableReason)
            requiresSignIn -> SyncStatusSummary.SignInRequired(outstanding)
            !isOnline -> SyncStatusSummary.Offline(outstanding)
            counts.failed > 0 -> SyncStatusSummary.Failed(counts.failed, outstanding)
            counts.inProgress > 0 -> SyncStatusSummary.Syncing(outstanding)
            counts.pending > 0 -> SyncStatusSummary.Waiting(outstanding)
            else -> SyncStatusSummary.AllSynced
        }
    }
}
