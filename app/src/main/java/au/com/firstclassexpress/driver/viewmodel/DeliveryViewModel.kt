package au.com.firstclassexpress.driver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.firstclassexpress.driver.domain.model.EvidenceRecord
import au.com.firstclassexpress.driver.domain.model.EvidenceType
import au.com.firstclassexpress.driver.domain.model.ExceptionStage
import au.com.firstclassexpress.driver.domain.model.FreightExceptionDraft
import au.com.firstclassexpress.driver.domain.model.FreightExceptionReason
import au.com.firstclassexpress.driver.domain.model.FreightExceptionRecord
import au.com.firstclassexpress.driver.domain.model.ValidationResult
import au.com.firstclassexpress.driver.domain.repository.EvidenceRepository
import au.com.firstclassexpress.driver.domain.repository.FreightExceptionRepository
import au.com.firstclassexpress.driver.domain.repository.JobRepository
import au.com.firstclassexpress.driver.domain.rules.DeliveryCompletionRules
import au.com.firstclassexpress.driver.domain.rules.EvidenceRules
import au.com.firstclassexpress.driver.domain.rules.FreightExceptionRules
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeliveryUiState(
    val job: Job? = null,
    val itemsDelivered: String = "",
    val recipientName: String = "",
    val notes: String = "",
    val evidence: List<EvidenceRecord> = emptyList(),
    val exceptions: List<FreightExceptionRecord> = emptyList(),
    val validation: ValidationResult = ValidationResult.Invalid(emptyList()),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isComplete: Boolean = false
) {
    val blockingReasons: List<String>
        get() = when (validation) {
            is ValidationResult.Valid -> emptyList()
            is ValidationResult.Invalid -> validation.reasons
            is ValidationResult.Blocked -> validation.reasons
        }

    val canComplete: Boolean get() = validation is ValidationResult.Valid && !isSubmitting
}

/**
 * Drives the proof-of-delivery workflow.
 *
 * The recipient name is pre-filled from a captured signature when one exists, so the name recorded
 * against the POD and the name on the signature cannot drift apart.
 */
class DeliveryViewModel(
    private val jobId: String,
    private val driverId: String,
    private val shiftId: String?,
    private val jobRepository: JobRepository,
    private val evidenceRepository: EvidenceRepository,
    private val exceptionRepository: FreightExceptionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeliveryUiState())
    val uiState: StateFlow<DeliveryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refreshJob() }
        viewModelScope.launch {
            combine(
                evidenceRepository.observeForJob(jobId),
                exceptionRepository.observeForJob(jobId)
            ) { evidence, exceptions -> evidence to exceptions }
                .collect { (evidence, exceptions) ->
                    _uiState.update { state ->
                        val signerName = evidence.firstOrNull {
                            it.type == EvidenceType.DELIVERY_SIGNATURE &&
                                EvidenceRules.isSatisfied(it.status)
                        }?.signerName
                        state.copy(
                            evidence = evidence,
                            exceptions = exceptions,
                            recipientName = signerName?.takeIf { it.isNotBlank() }
                                ?: state.recipientName
                        ).revalidated()
                    }
                }
        }
    }

    fun onItemsDeliveredChange(value: String) {
        _uiState.update { it.copy(itemsDelivered = value.filter(Char::isDigit)).revalidated() }
    }

    fun onRecipientNameChange(value: String) {
        _uiState.update { it.copy(recipientName = value).revalidated() }
    }

    fun onNotesChange(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    suspend fun recordException(
        reason: FreightExceptionReason,
        notes: String
    ): Result<String> {
        val validation = FreightExceptionRules.validate(reason, notes, _uiState.value.evidence)
        if (validation is ValidationResult.Invalid) {
            val message = validation.reasons.joinToString(" ")
            _uiState.update { it.copy(errorMessage = message) }
            return Result.failure(IllegalStateException(message))
        }

        return exceptionRepository.record(
            FreightExceptionDraft(
                jobId = jobId,
                stage = ExceptionStage.DELIVERY,
                reason = reason,
                notes = notes,
                driverId = driverId,
                shiftId = shiftId
            )
        ).onFailure { error ->
            _uiState.update { it.copy(errorMessage = error.message ?: "Unable to save exception") }
        }
    }

    suspend fun resolveException(id: String): Result<Unit> =
        exceptionRepository.markResolved(id, resolved = true).onFailure { error ->
            _uiState.update {
                it.copy(errorMessage = error.message ?: "Unable to update exception")
            }
        }

    suspend fun completeDelivery(): Result<JobStatus> {
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        val job = jobRepository.getJob(jobId)
        val evidence = evidenceRepository.getForJob(jobId)
        val exceptions = exceptionRepository.getForJob(jobId)
        val validation = DeliveryCompletionRules.validate(
            status = job?.status,
            itemsDelivered = _uiState.value.itemsDelivered,
            recipientName = _uiState.value.recipientName,
            evidence = evidence,
            exceptions = exceptions
        )

        if (validation !is ValidationResult.Valid) {
            val reasons = when (validation) {
                is ValidationResult.Invalid -> validation.reasons
                is ValidationResult.Blocked -> validation.reasons
                else -> emptyList()
            }
            _uiState.update {
                it.copy(
                    job = job,
                    evidence = evidence,
                    exceptions = exceptions,
                    validation = validation,
                    isSubmitting = false,
                    errorMessage = reasons.firstOrNull()
                )
            }
            return Result.failure(IllegalStateException(reasons.joinToString(" ")))
        }

        return jobRepository.transition(jobId, JobStatus.COMPLETED)
            .onSuccess { _uiState.update { it.copy(isSubmitting = false, isComplete = true) } }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.message ?: "Unable to complete delivery"
                    )
                }
            }
    }

    private suspend fun refreshJob() {
        val job = jobRepository.getJob(jobId)
        _uiState.update { state ->
            state.copy(
                job = job,
                itemsDelivered = state.itemsDelivered.ifBlank {
                    job?.itemCount?.toString().orEmpty()
                }
            ).revalidated()
        }
    }

    private fun DeliveryUiState.revalidated(): DeliveryUiState = copy(
        validation = DeliveryCompletionRules.validate(
            status = job?.status,
            itemsDelivered = itemsDelivered,
            recipientName = recipientName,
            evidence = evidence,
            exceptions = exceptions
        )
    )
}
