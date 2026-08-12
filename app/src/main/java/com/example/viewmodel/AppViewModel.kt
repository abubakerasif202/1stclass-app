package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuthFailure
import com.example.domain.model.DriverSession
import com.example.domain.model.ShiftPhase
import com.example.domain.model.ShiftRecord
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.DriverRepository
import com.example.domain.repository.JobRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.repository.ShiftRepository
import com.example.domain.repository.SyncRepository
import com.example.model.Driver
import com.example.model.Job
import com.example.model.ShiftStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppState(
    val isRestoringSession: Boolean = true,
    val isLoggedIn: Boolean = false,
    val driver: Driver? = null,
    val session: DriverSession? = null,
    val currentShift: ShiftRecord? = null,
    val jobs: List<Job> = emptyList(),
    val pendingSyncCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val currentShiftId: String?
        get() = currentShift?.takeIf { it.phase == ShiftPhase.ON_DUTY || it.phase == ShiftPhase.ON_BREAK }?.id
}

/**
 * Owns sign-in, the restored session and the top-level driver/job state.
 *
 * The persisted session is the source of truth for "is a driver signed in", so the app comes back
 * signed in after a restart without re-entering a PIN.
 */
class AppViewModel(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val driverRepository: DriverRepository,
    private val jobRepository: JobRepository,
    private val shiftRepository: ShiftRepository,
    private val syncRepository: SyncRepository,
    /** Seeds local reference data and the development credential before the first sign-in. */
    private val bootstrap: suspend () -> Result<Unit>,
    private val appVersionName: String = ""
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    val versionName: String = appVersionName

    init {
        viewModelScope.launch { bootstrap() }
        viewModelScope.launch {
            sessionRepository.observeSession()
                .catch {
                    _uiState.update {
                        it.copy(
                            isRestoringSession = false,
                            isLoggedIn = false,
                            session = null,
                            driver = null,
                            error = "Unable to restore the driver session. Please sign in again."
                        )
                    }
                }
                .collect { session ->
                    val phone = session?.let { resolvePhone(it) }
                    _uiState.update { state ->
                        state.copy(
                            isRestoringSession = false,
                            isLoggedIn = session != null,
                            session = session,
                            driver = session?.let { it.toDriver(phone, state.currentShift) },
                            error = if (session != null) null else state.error
                        )
                    }
                }
        }
        viewModelScope.launch {
            jobRepository.observeJobs().collect { jobs ->
                _uiState.update { it.copy(jobs = jobs) }
            }
        }
        viewModelScope.launch {
            shiftRepository.observeCurrentShift().collect { shift ->
                _uiState.update { state ->
                    state.copy(
                        currentShift = shift,
                        driver = state.session?.toDriver(state.driver?.phone, shift)
                    )
                }
            }
        }
        viewModelScope.launch {
            syncRepository.observePending().collect { operations ->
                _uiState.update { it.copy(pendingSyncCount = operations.size) }
            }
        }
    }

    fun login(driverId: String, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val ready = bootstrap()
            if (ready.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ready.exceptionOrNull()?.message ?: "Unable to prepare local data"
                    )
                }
                return@launch
            }

            authRepository.authenticate(driverId, pin)
                .mapCatching { driver -> sessionRepository.startSession(driver).getOrThrow() }
                .onSuccess { session ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            session = session,
                            driver = session.toDriver(session.phone, state.currentShift),
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.toMessage()) }
                }
        }
    }

    /**
     * Signs the driver out by clearing the session only. Completed jobs, inspection history and
     * queued sync operations are deliberately left on the device.
     */
    fun logout() {
        viewModelScope.launch {
            sessionRepository.clearSession()
                .onSuccess {
                    _uiState.update {
                        it.copy(isLoggedIn = false, session = null, driver = null, error = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Unable to sign out") }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun getJobById(jobId: String): Job? = _uiState.value.jobs.find { it.id == jobId }

    private suspend fun resolvePhone(session: DriverSession): String? =
        session.phone ?: runCatching { driverRepository.getDriver(session.driverId)?.phone }
            .getOrNull()

    private fun DriverSession.toDriver(phone: String?, shift: ShiftRecord?): Driver {
        val onShift = shift?.phase == ShiftPhase.ON_DUTY || shift?.phase == ShiftPhase.ON_BREAK
        return Driver(
            id = driverId,
            name = name,
            email = email,
            shiftStatus = when (shift?.phase) {
                ShiftPhase.ON_DUTY -> ShiftStatus.ON_DUTY
                ShiftPhase.ON_BREAK -> ShiftStatus.ON_BREAK
                else -> ShiftStatus.OFF_DUTY
            },
            currentVehicleId = shift?.takeIf { onShift }?.vehicleId,
            phone = phone ?: this.phone
        )
    }

    private fun Throwable.toMessage(): String = when (this) {
        is AuthFailure -> message.orEmpty()
        else -> message ?: "Unable to sign in"
    }
}
