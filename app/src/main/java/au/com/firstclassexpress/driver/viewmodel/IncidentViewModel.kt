package au.com.firstclassexpress.driver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.firstclassexpress.driver.domain.model.IncidentCategory
import au.com.firstclassexpress.driver.domain.model.IncidentDraft
import au.com.firstclassexpress.driver.domain.model.IncidentRecord
import au.com.firstclassexpress.driver.domain.model.IncidentSeverity
import au.com.firstclassexpress.driver.domain.repository.IncidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IncidentUiState(
    val selectedCategory: IncidentCategory = IncidentCategory.DELAY,
    val selectedSeverity: IncidentSeverity = IncidentSeverity.MEDIUM,
    val description: String = "",
    val photoUri: String? = null,
    val selectedJobId: String? = null,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val recentIncidents: List<IncidentRecord> = emptyList()
) {
    val canSubmit: Boolean
        get() = description.isNotBlank() &&
            (!selectedCategory.requiresPhoto || !photoUri.isNullOrBlank()) &&
            !isSubmitting
}

class IncidentViewModel(
    private val incidentRepository: IncidentRepository,
    private val driverId: String,
    private val shiftId: String?,
    initialJobId: String? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(IncidentUiState(selectedJobId = initialJobId))
    val uiState: StateFlow<IncidentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            incidentRepository.observeIncidents().collect { incidents ->
                _uiState.update { it.copy(recentIncidents = incidents) }
            }
        }
    }

    fun onCategoryChange(category: IncidentCategory) {
        _uiState.update { it.copy(selectedCategory = category, errorMessage = null) }
    }

    fun onSeverityChange(severity: IncidentSeverity) {
        _uiState.update { it.copy(selectedSeverity = severity) }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(description = description, errorMessage = null) }
    }

    fun onPhotoAttached(uri: String?) {
        _uiState.update { it.copy(photoUri = uri, errorMessage = null) }
    }

    fun onJobSelected(jobId: String?) {
        _uiState.update { it.copy(selectedJobId = jobId) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    suspend fun submitIncident(latitude: Double? = null, longitude: Double? = null): Result<IncidentRecord> {
        val state = _uiState.value
        if (state.description.isBlank()) {
            val error = "Please provide an incident description."
            _uiState.update { it.copy(errorMessage = error) }
            return Result.failure(IllegalArgumentException(error))
        }

        if (state.selectedCategory.requiresPhoto && state.photoUri.isNullOrBlank()) {
            val error = "${state.selectedCategory.label} requires a photo as evidence."
            _uiState.update { it.copy(errorMessage = error) }
            return Result.failure(IllegalArgumentException(error))
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        val draft = IncidentDraft(
            driverId = driverId,
            shiftId = shiftId,
            jobId = state.selectedJobId,
            category = state.selectedCategory,
            severity = state.selectedSeverity,
            description = state.description.trim(),
            photoUri = state.photoUri,
            latitude = latitude,
            longitude = longitude
        )

        return incidentRepository.reportIncident(draft)
            .onSuccess {
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.message ?: "Failed to report incident"
                    )
                }
            }
    }
}
