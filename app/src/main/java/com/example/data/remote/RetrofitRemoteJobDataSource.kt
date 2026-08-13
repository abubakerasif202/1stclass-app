package com.example.data.remote

import com.example.domain.repository.JobRefreshResult
import com.example.domain.repository.RemoteJobDataSource
import com.example.domain.sync.SyncOutcome

/** Downloads assigned jobs from a configured TMS. Unreachable until `TMS_BASE_URL` is set. */
class RetrofitRemoteJobDataSource(private val api: TmsApi) : RemoteJobDataSource {

    override suspend fun assignedJobs(driverId: String): JobRefreshResult =
        runCatching { api.assignedJobs(driverId) }.fold(
            onSuccess = { response ->
                val body = response.body()
                when {
                    response.isSuccessful && body != null ->
                        JobRefreshResult.Loaded(body.jobs, body.serverTime)

                    response.code() == 401 || response.code() == 403 ->
                        JobRefreshResult.SignInRequired

                    response.isSuccessful ->
                        JobRefreshResult.Unavailable("TMS returned an empty job list response")

                    else -> JobRefreshResult.Unavailable("HTTP ${response.code()} from TMS")
                }
            },
            onFailure = { error ->
                when (val outcome = NetworkResultMapper.fromThrowable(error)) {
                    is SyncOutcome.Retryable -> JobRefreshResult.Unavailable(outcome.reason)
                    is SyncOutcome.Permanent -> JobRefreshResult.Unavailable(outcome.reason)
                    else -> JobRefreshResult.Unavailable("Unable to reach the TMS")
                }
            }
        )
}
