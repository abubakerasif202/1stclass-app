package com.example.domain.repository

import com.example.data.remote.dto.RemoteJobDto

/** Outcome of asking the TMS for a driver's assigned work. */
sealed interface JobRefreshResult {
    data class Loaded(val jobs: List<RemoteJobDto>, val serverTime: Long) : JobRefreshResult
    data class Unavailable(val reason: String) : JobRefreshResult
    data object SignInRequired : JobRefreshResult
}

/**
 * Downloads a driver's assigned jobs.
 *
 * No live endpoint exists, so [UnconfiguredRemoteJobDataSource] is what actually runs today: it
 * reports [JobRefreshResult.Unavailable] and the app keeps showing locally seeded work. The seam
 * exists so the merge policy and its tests can be built now.
 */
interface RemoteJobDataSource {
    suspend fun assignedJobs(driverId: String): JobRefreshResult
}

class UnconfiguredRemoteJobDataSource(
    private val reason: String = "No TMS endpoint is configured for this build"
) : RemoteJobDataSource {
    override suspend fun assignedJobs(driverId: String): JobRefreshResult =
        JobRefreshResult.Unavailable(reason)
}
