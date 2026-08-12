package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.ShiftRecord
import com.example.domain.repository.InspectionRepository
import com.example.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShiftUiState(
    val currentShift: ShiftRecord? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ShiftViewModel(
    private val shiftRepository: ShiftRepository,
    private val inspectionRepository: InspectionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShiftUiState())
    val uiState: StateFlow<ShiftUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            shiftRepository.observeCurrentShift().collect { shift ->
                _uiState.update { it.copy(currentShift = shift) }
            }
        }
    }

    suspend fun beginPreStart(
        driverId: String,
        vehicleId: String,
        trailerId: String,
        odometer: String
    ): Result<String> {
        val cleanedVehicle = vehicleId.trim()
        val cleanedTrailer = trailerId.trim().takeIf { it.isNotEmpty() }
        val parsedOdometer = odometer.trim().toLongOrNull()

        if (cleanedVehicle.isEmpty()) {
            return fail("Vehicle registration / ID is required")
        }
        if (parsedOdometer == null || parsedOdometer < 0L) {
            return fail("Enter a valid non-negative odometer reading")
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val result = shiftRepository.createPreStartDraft(
            driverId = driverId,
            vehicleId = cleanedVehicle,
            trailerId = cleanedTrailer,
            startOdometer = parsedOdometer
        ).fold(
            onSuccess = { shiftId ->
                inspectionRepository.ensureForShift(
                    shiftId = shiftId,
                    hasTrailer = cleanedTrailer != null
                ).fold(
                    onSuccess = { Result.success(shiftId) },
                    onFailure = { Result.failure(it) }
                )
            },
            onFailure = { Result.failure(it) }
        )

        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
        return result
    }

    private fun fail(message: String): Result<String> {
        _uiState.update { it.copy(errorMessage = message) }
        return Result.failure(IllegalArgumentException(message))
    }
}
