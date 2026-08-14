package au.com.firstclassexpress.driver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.firstclassexpress.driver.data.remote.ConnectivityRepository
import au.com.firstclassexpress.driver.data.remote.TmsEnvironment
import au.com.firstclassexpress.driver.domain.model.SyncOperation
import au.com.firstclassexpress.driver.domain.repository.SyncRepository
import au.com.firstclassexpress.driver.domain.sync.SyncQueue
import au.com.firstclassexpress.driver.domain.sync.SyncQueueCounts
import au.com.firstclassexpress.driver.domain.sync.SyncStatusSummaries
import au.com.firstclassexpress.driver.domain.sync.SyncStatusSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val summary: SyncStatusSummary = SyncStatusSummary.AllSynced,
    val counts: SyncQueueCounts = SyncQueueCounts(),
    val operations: List<SyncOperation> = emptyList(),
    val isOnline: Boolean = false,
    val message: String? = null
)

/**
 * Drives the sync indicator, the "Sync now" action and the diagnostics screen.
 *
 * It never performs networking itself — the button asks WorkManager for a run, so a drain that
 * starts while the driver is looking at the screen survives them navigating away or the app being
 * backgrounded.
 */
class SyncViewModel(
    private val syncRepository: SyncRepository,
    private val syncQueue: SyncQueue,
    connectivityRepository: ConnectivityRepository,
    private val environment: TmsEnvironment,
    private val requestSync: () -> Unit,
    private val isOnlineNow: () -> Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                syncRepository.observeCounts(),
                syncRepository.observePending(),
                connectivityRepository.observeOnline()
            ) { counts, operations, isOnline ->
                SyncUiState(
                    summary = SyncStatusSummaries.from(
                        counts = counts,
                        isOnline = isOnline,
                        isRemoteConfigured = environment.isRemoteAvailable,
                        remoteUnavailableReason = unavailableReason()
                    ),
                    counts = counts,
                    operations = operations,
                    isOnline = isOnline
                )
            }.collect { next -> _uiState.update { next.copy(message = it.message) } }
        }
    }

    /** Queues a drain. Says nothing about success — only the queue state can do that. */
    fun syncNow() {
        val message = when {
            !environment.isRemoteAvailable ->
                "Remote sync is not available in this build. Everything stays saved on this device."

            !isOnlineNow() ->
                "No internet connection. Changes remain saved on this device."

            else -> {
                requestSync()
                "Sync requested."
            }
        }
        _uiState.update { it.copy(message = message) }
    }

    /** Moves every FAILED operation back to PENDING and asks for a drain. */
    fun retryFailed() {
        viewModelScope.launch {
            val requeued = syncQueue.requeueFailed()
            if (requeued > 0 && environment.isRemoteAvailable) requestSync()
            _uiState.update {
                it.copy(
                    message = if (requeued == 0) "There is nothing to retry."
                    else "$requeued operation${if (requeued == 1) "" else "s"} queued to retry."
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun unavailableReason(): String = when (val env = environment) {
        is TmsEnvironment.Rejected -> env.reason
        else -> "No TMS endpoint is configured for this build"
    }
}
