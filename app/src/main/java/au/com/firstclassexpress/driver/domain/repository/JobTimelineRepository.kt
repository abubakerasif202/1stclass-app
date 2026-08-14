package au.com.firstclassexpress.driver.domain.repository

import au.com.firstclassexpress.driver.domain.model.JobTimelineEvent
import kotlinx.coroutines.flow.Flow

interface JobTimelineRepository {
    fun observeEventsForJob(jobId: String): Flow<List<JobTimelineEvent>>
    suspend fun recordEvent(event: JobTimelineEvent): Result<Unit>
    suspend fun getEventsForJob(jobId: String): List<JobTimelineEvent>
}
