package au.com.firstclassexpress.driver.data.repository

import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.entity.JobTimelineEventEntity
import au.com.firstclassexpress.driver.domain.model.JobTimelineEvent
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.repository.JobTimelineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomJobTimelineRepository(
    private val database: AppDatabase
) : JobTimelineRepository {
    private val timelineDao = database.jobTimelineEventDao()

    override fun observeEventsForJob(jobId: String): Flow<List<JobTimelineEvent>> =
        timelineDao.observeByJobId(jobId).map { rows ->
            rows.map { row ->
                JobTimelineEvent(
                    id = row.id,
                    jobId = row.jobId,
                    status = row.status,
                    title = row.title,
                    description = row.description,
                    timestamp = row.timestamp,
                    latitude = row.latitude,
                    longitude = row.longitude,
                    syncStatus = runCatching { SyncStatus.valueOf(row.syncStatus) }.getOrDefault(SyncStatus.PENDING)
                )
            }
        }

    override suspend fun recordEvent(event: JobTimelineEvent): Result<Unit> = runCatching {
        timelineDao.insert(
            JobTimelineEventEntity(
                id = event.id,
                jobId = event.jobId,
                status = event.status,
                title = event.title,
                description = event.description,
                timestamp = event.timestamp,
                latitude = event.latitude,
                longitude = event.longitude,
                syncStatus = event.syncStatus.name
            )
        )
    }

    override suspend fun getEventsForJob(jobId: String): List<JobTimelineEvent> =
        timelineDao.getByJobId(jobId).map { row ->
            JobTimelineEvent(
                id = row.id,
                jobId = row.jobId,
                status = row.status,
                title = row.title,
                description = row.description,
                timestamp = row.timestamp,
                latitude = row.latitude,
                longitude = row.longitude,
                syncStatus = runCatching { SyncStatus.valueOf(row.syncStatus) }.getOrDefault(SyncStatus.PENDING)
            )
        }
}
