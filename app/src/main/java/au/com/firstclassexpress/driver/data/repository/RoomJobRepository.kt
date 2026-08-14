package au.com.firstclassexpress.driver.data.repository

import androidx.room.withTransaction
import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.JobPayloadCodec
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.repository.JobRepository
import au.com.firstclassexpress.driver.domain.rules.JobTransitionRules
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomJobRepository(
    private val database: AppDatabase,
    private val codec: JobPayloadCodec = JobPayloadCodec(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : JobRepository {
    private val jobDao = database.jobDao()
    private val syncDao = database.syncOperationDao()
    private val timelineDao = database.jobTimelineEventDao()

    override fun observeJobs(): Flow<List<Job>> =
        jobDao.observeAll().map { rows -> rows.map { codec.decode(it.payloadJson, it.status) } }

    override suspend fun getJob(id: String): Job? =
        jobDao.getById(id)?.let { codec.decode(it.payloadJson, it.status) }

    override suspend fun transition(id: String, to: JobStatus): Result<JobStatus> = runCatching {
        database.withTransaction {
            val row = requireNotNull(jobDao.getById(id)) { "Job not found" }
            val from = JobStatus.valueOf(row.status)
            require(JobTransitionRules.canTransition(from, to)) {
                "Invalid job transition: $from -> $to"
            }

            val timestamp = clock()
            check(jobDao.updateStatus(id, to.name, timestamp) == 1) { "Failed to update job" }
            syncDao.insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = "JOB",
                    entityId = id,
                    operationType = "STATUS_CHANGE",
                    payloadJson = "{\"from\":\"${from.name}\",\"to\":\"${to.name}\"}",
                    createdAt = timestamp,
                    retryCount = 0,
                    lastError = null,
                    status = SyncStatus.PENDING.name
                )
            )

            val eventTitle = when (to) {
                JobStatus.ASSIGNED -> "Job assigned"
                JobStatus.ACCEPTED -> "Driver accepted job"
                JobStatus.IN_PROGRESS -> "En route to pickup"
                JobStatus.AT_PICKUP -> "Arrived at pickup location"
                JobStatus.PICKED_UP -> "Pickup complete — freight loaded"
                JobStatus.EN_ROUTE_DELIVERY -> "Departed pickup — en route to delivery"
                JobStatus.AT_DELIVERY -> "Arrived at delivery destination"
                JobStatus.DELIVERED -> "POD captured"
                JobStatus.COMPLETED -> "Job completed"
                JobStatus.DELAYED -> "Job marked delayed"
                JobStatus.FAILED_DELIVERY -> "Delivery failed"
                JobStatus.CUSTOMER_UNAVAILABLE -> "Customer unavailable"
                JobStatus.VEHICLE_ISSUE -> "Vehicle issue reported"
                JobStatus.OTHER_EXCEPTION -> "Exception recorded"
                JobStatus.ISSUE -> "Job placed on hold"
                JobStatus.UNASSIGNED -> "Job unassigned"
            }

            timelineDao.insert(
                au.com.firstclassexpress.driver.data.local.entity.JobTimelineEventEntity(
                    id = idGenerator(),
                    jobId = id,
                    status = to.name,
                    title = eventTitle,
                    description = "Status updated to ${to.displayLabel}",
                    timestamp = timestamp,
                    latitude = null,
                    longitude = null,
                    syncStatus = SyncStatus.PENDING.name
                )
            )

            to
        }
    }
}
