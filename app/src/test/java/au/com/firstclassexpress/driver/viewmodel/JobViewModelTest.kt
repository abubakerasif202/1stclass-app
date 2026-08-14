package au.com.firstclassexpress.driver.viewmodel

import au.com.firstclassexpress.driver.domain.repository.JobRepository
import au.com.firstclassexpress.driver.domain.rules.JobTransitionRules
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import au.com.firstclassexpress.driver.model.Location
import au.com.firstclassexpress.driver.model.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobViewModelTest {
    @Test
    fun unassignedJobHasNoStartAction() = runTest {
        val repository = FakeJobRepository(sampleJob(JobStatus.UNASSIGNED))
        val viewModel = JobViewModel(repository)

        viewModel.loadJob(repository.current.id)

        assertFalse(viewModel.uiState.value.allowedNextStatuses.contains(JobStatus.IN_PROGRESS))
    }

    @Test
    fun assignedJobCanOnlyStartNormally() = runTest {
        val repository = FakeJobRepository(sampleJob(JobStatus.ASSIGNED))
        val viewModel = JobViewModel(repository)

        viewModel.loadJob(repository.current.id)

        assertTrue(viewModel.uiState.value.allowedNextStatuses.contains(JobStatus.IN_PROGRESS))
        assertFalse(viewModel.uiState.value.allowedNextStatuses.contains(JobStatus.AT_DELIVERY))
    }

    @Test
    fun repositoryRejectionDoesNotChangeDisplayedStatus() = runTest {
        val repository = FakeJobRepository(sampleJob(JobStatus.ASSIGNED), rejectTransitions = true)
        val viewModel = JobViewModel(repository)
        viewModel.loadJob(repository.current.id)

        viewModel.requestTransition(JobStatus.IN_PROGRESS)

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.job?.status == JobStatus.ASSIGNED)
    }

    private class FakeJobRepository(
        var current: Job,
        var rejectTransitions: Boolean = false
    ) : JobRepository {
        private val jobs = MutableStateFlow(listOf(current))
        override fun observeJobs(): Flow<List<Job>> = jobs
        override suspend fun getJob(id: String): Job? = current.takeIf { it.id == id }
        override suspend fun transition(id: String, to: JobStatus): Result<JobStatus> {
            if (rejectTransitions) return Result.failure(IllegalStateException("Rejected"))
            if (!JobTransitionRules.canTransition(current.status, to)) {
                return Result.failure(IllegalArgumentException("Invalid transition"))
            }
            current = current.copy(status = to)
            jobs.value = listOf(current)
            return Result.success(to)
        }
    }

    companion object {
        private fun sampleJob(status: JobStatus) = Job(
            id = "j1",
            reference = "REF-1",
            status = status,
            pickup = Location("1 Pickup Rd", "Sydney", -33.86, 151.20, "Pickup", "A", "000"),
            delivery = Location("2 Delivery Rd", "Sydney", -33.87, 151.21, "Delivery", "B", "000"),
            pickupWindowStart = "08:00",
            pickupWindowEnd = "09:00",
            deliveryWindowStart = "10:00",
            deliveryWindowEnd = "11:00",
            freightDescription = "Test freight",
            itemCount = 1,
            priority = Priority.NORMAL
        )
    }
}
