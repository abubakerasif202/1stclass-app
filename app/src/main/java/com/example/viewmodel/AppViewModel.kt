package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MockData
import com.example.model.Driver
import com.example.model.Job
import com.example.model.JobStatus
import com.example.model.ShiftStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppState(
    val isLoggedIn: Boolean = false,
    val driver: Driver? = null,
    val jobs: List<Job> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class AppViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    fun login(driverId: String, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Simulate network delay
            kotlinx.coroutines.delay(1000)
            
            if (driverId.isNotBlank() && pin.isNotBlank()) {
                _uiState.update { 
                    it.copy(
                        isLoggedIn = true, 
                        driver = MockData.currentDriver,
                        jobs = MockData.sampleJobs,
                        isLoading = false
                    ) 
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Invalid credentials. Please try again."
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
                jobs = emptyList()
            ) 
        }
    }

    fun startShift(vehicleId: String, odometer: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            kotlinx.coroutines.delay(500)
            
            _uiState.update { state ->
                val updatedDriver = state.driver?.copy(
                    shiftStatus = ShiftStatus.ON_DUTY,
                    currentVehicleId = vehicleId
                )
                state.copy(driver = updatedDriver, isLoading = false)
            }
        }
    }
    
    fun endShift() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            kotlinx.coroutines.delay(500)
            
            _uiState.update { state ->
                val updatedDriver = state.driver?.copy(
                    shiftStatus = ShiftStatus.OFF_DUTY,
                    currentVehicleId = null
                )
                state.copy(driver = updatedDriver, isLoading = false)
            }
        }
    }

    fun updateJobStatus(jobId: String, newStatus: JobStatus) {
        _uiState.update { state ->
            val updatedJobs = state.jobs.map { job ->
                if (job.id == jobId) job.copy(status = newStatus) else job
            }
            state.copy(jobs = updatedJobs)
        }
    }
    
    fun getJobById(jobId: String): Job? {
        return _uiState.value.jobs.find { it.id == jobId }
    }
}
