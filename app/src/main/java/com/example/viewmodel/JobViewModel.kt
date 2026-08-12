package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.domain.repository.JobRepository
import com.example.domain.rules.JobTransitionRules
import com.example.model.Job
import com.example.model.JobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class JobUiState(
    val job: Job? = null,
    val allowedNextStatuses: Set<JobStatus> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class JobViewModel(
    private val repository: JobRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(JobUiState())
    val uiState: StateFlow<JobUiState> = _uiState.asStateFlow()

    suspend fun loadJob(jobId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val job = repository.getJob(jobId)
        _uiState.value = JobUiState(
            job = job,
            allowedNextStatuses = job?.let { JobTransitionRules.allowedNext(it.status) }.orEmpty(),
            isLoading = false,
            errorMessage = if (job == null) "Job not found" else null
        )
    }

    suspend fun requestTransition(to: JobStatus): Result<JobStatus> {
        val current = _uiState.value.job
            ?: return fail("Job not loaded")
        if (!JobTransitionRules.canTransition(current.status, to)) {
            return fail("Invalid job transition: ${current.status} -> $to")
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val result = repository.transition(current.id, to)
        if (result.isSuccess) {
            loadJob(current.id)
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Job update failed"
                )
            }
        }
        return result
    }

    private fun fail(message: String): Result<JobStatus> {
        _uiState.update { it.copy(errorMessage = message) }
        return Result.failure(IllegalStateException(message))
    }
}
