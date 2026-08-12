package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.seed.PrototypeSeedData
import com.example.domain.model.ShiftPhase
import com.example.domain.repository.DriverRepository
import com.example.domain.repository.JobRepository
import com.example.domain.repository.ShiftRepository
import com.example.model.Driver
import com.example.model.Job
import com.example.model.ShiftStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppState(
    val isLoggedIn: Boolean = false,
    val driver: Driver? = null,
    val jobs: List<Job> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class AppViewModel(
    private val driverRepository: DriverRepository,
    private val jobRepository: JobRepository,
    private val shiftRepository: ShiftRepository,
    private val prototypeSeedData: PrototypeSeedData
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            jobRepository.observeJobs().collect { jobs ->
                _uiState.update { it.copy(jobs = jobs) }
            }
        }
        viewModelScope.launch {
            shiftRepository.observeCurrentShift().collect { shift ->
                _uiState.update { state ->
                    val driver = state.driver ?: return@update state
                    val status = when (shift?.phase) {
                        ShiftPhase.ON_DUTY -> ShiftStatus.ON_DUTY
                        ShiftPhase.ON_BREAK -> ShiftStatus.ON_BREAK
                        else -> ShiftStatus.OFF_DUTY
                    }
                    state.copy(
                        driver = driver.copy(
                            shiftStatus = status,
                            currentVehicleId = shift?.takeIf {
                                it.phase == ShiftPhase.ON_DUTY || it.phase == ShiftPhase.ON_BREAK
                            }?.vehicleId
                        )
                    )
                }
            }
        }
    }

    fun login(driverId: String, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (driverId.isBlank() || pin.isBlank()) {
                _uiState.update { it.copy(isLoading = false, error = "Driver ID and PIN are required.") }
                return@launch
            }

            delay(250)
            runCatching {
                prototypeSeedData.seedIfEmpty()
                requireNotNull(driverRepository.getPrototypeDriver()) { "Prototype driver could not be loaded" }
            }.onSuccess { driver ->
                val currentShift = shiftRepository.observeCurrentShift().first()
                val status = when (currentShift?.phase) {
                    ShiftPhase.ON_DUTY -> ShiftStatus.ON_DUTY
                    ShiftPhase.ON_BREAK -> ShiftStatus.ON_BREAK
                    else -> ShiftStatus.OFF_DUTY
                }
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = true,
                        driver = driver.copy(
                            shiftStatus = status,
                            currentVehicleId = currentShift?.takeIf {
                                it.phase == ShiftPhase.ON_DUTY || it.phase == ShiftPhase.ON_BREAK
                            }?.vehicleId
                        ),
                        isLoading = false,
                        error = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Unable to load driver profile"
                    )
                }
            }
        }
    }

    fun logout() {
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                driver = null,
                error = null
            )
        }
    }

    fun getJobById(jobId: String): Job? = _uiState.value.jobs.find { it.id == jobId }
}
