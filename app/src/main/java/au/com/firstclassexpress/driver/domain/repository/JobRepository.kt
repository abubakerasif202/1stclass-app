package au.com.firstclassexpress.driver.domain.repository

import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import kotlinx.coroutines.flow.Flow

interface JobRepository {
    fun observeJobs(): Flow<List<Job>>
    suspend fun getJob(id: String): Job?
    suspend fun transition(id: String, to: JobStatus): Result<JobStatus>
}
