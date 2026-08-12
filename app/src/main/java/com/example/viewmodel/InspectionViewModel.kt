package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.DefectSeverity
import com.example.domain.model.InspectionAnswer
import com.example.domain.model.InspectionItemRecord
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.ShiftPhase
import com.example.domain.model.ValidationResult
import com.example.domain.repository.InspectionRepository
import com.example.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InspectionUiState(
    val items: List<InspectionItemRecord> = emptyList(),
    val declarationAccepted: Boolean = false,
    val phase: ShiftPhase = ShiftPhase.PRESTART_REQUIRED,
    val validation: ValidationResult? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false
) {
    val unansweredCount: Int
        get() = items.count { it.mandatory && it.status == InspectionItemStatus.UNANSWERED }
}

class InspectionViewModel(
    private val shiftId: String,
    private val inspectionRepository: InspectionRepository,
    private val shiftRepository: ShiftRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(InspectionUiState())
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                inspectionRepository.observeItems(shiftId),
                inspectionRepository.observeDeclaration(shiftId),
                shiftRepository.observeCurrentShift()
            ) { items, declaration, shift ->
                Triple(items, declaration, shift?.phase ?: ShiftPhase.PRESTART_REQUIRED)
            }.collect { (items, declaration, phase) ->
                _uiState.update {
                    it.copy(items = items, declarationAccepted = declaration, phase = phase)
                }
            }
        }
    }

    suspend fun setAnswer(
        itemId: String,
        status: InspectionItemStatus,
        description: String? = null,
        severity: DefectSeverity? = null
    ): Result<Unit> {
        val item = _uiState.value.items.firstOrNull { it.id == itemId }
            ?: return fail("Inspection item not found")
        val answer = InspectionAnswer(
            itemCode = item.code,
            mandatory = item.mandatory,
            status = status,
            defectDescription = description,
            defectSeverity = severity
        )
        return inspectionRepository.saveAnswer(itemId, answer).also { result ->
            _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
        }
    }

    suspend fun setDeclaration(accepted: Boolean): Result<Unit> =
        inspectionRepository.setDeclaration(shiftId, accepted).also { result ->
            _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
        }

    suspend fun completeInspection(): ValidationResult {
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        val validation = inspectionRepository.complete(shiftId)
        if (validation == ValidationResult.Valid) {
            val readyResult = shiftRepository.markReadyToStart(shiftId)
            if (readyResult.isFailure) {
                val error = readyResult.exceptionOrNull()?.message ?: "Failed to mark shift ready"
                val failed = ValidationResult.Invalid(listOf(error))
                _uiState.update {
                    it.copy(isSaving = false, validation = failed, errorMessage = error)
                }
                return failed
            }
        }
        _uiState.update {
            it.copy(
                isSaving = false,
                validation = validation,
                errorMessage = when (validation) {
                    ValidationResult.Valid -> null
                    is ValidationResult.Invalid -> validation.reasons.joinToString("; ")
                    is ValidationResult.Blocked -> validation.reasons.joinToString("; ")
                }
            )
        }
        return validation
    }

    suspend fun activateShift(): Result<Unit> = shiftRepository.activateShift(shiftId).also { result ->
        _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
    }

    private fun fail(message: String): Result<Unit> {
        _uiState.update { it.copy(errorMessage = message) }
        return Result.failure(IllegalArgumentException(message))
    }
}
