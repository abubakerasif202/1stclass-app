package au.com.firstclassexpress.driver.data.repository

import androidx.room.withTransaction
import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.entity.DriverIncidentEntity
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import au.com.firstclassexpress.driver.domain.model.IncidentCategory
import au.com.firstclassexpress.driver.domain.model.IncidentDraft
import au.com.firstclassexpress.driver.domain.model.IncidentRecord
import au.com.firstclassexpress.driver.domain.model.IncidentSeverity
import au.com.firstclassexpress.driver.domain.model.JobTimelineEvent
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.repository.IncidentRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomIncidentRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : IncidentRepository {
    private val incidentDao = database.driverIncidentDao()
    private val syncDao = database.syncOperationDao()
    private val timelineDao = database.jobTimelineEventDao()

    override fun observeIncidents(): Flow<List<IncidentRecord>> =
        incidentDao.observeAll().map { rows -> rows.map(::toRecord) }

    override fun observeIncidentsForJob(jobId: String): Flow<List<IncidentRecord>> =
        incidentDao.observeByJobId(jobId).map { rows -> rows.map(::toRecord) }

    override suspend fun reportIncident(draft: IncidentDraft): Result<IncidentRecord> = runCatching {
        database.withTransaction {
            val id = idGenerator()
            val now = clock()
            val entity = DriverIncidentEntity(
                id = id,
                driverId = draft.driverId,
                shiftId = draft.shiftId,
                jobId = draft.jobId,
                category = draft.category.name,
                severity = draft.severity.name,
                description = draft.description,
                photoUri = draft.photoUri,
                latitude = draft.latitude,
                longitude = draft.longitude,
                createdAt = now,
                syncStatus = SyncStatus.PENDING.name
            )
            incidentDao.insert(entity)

            // Queue for sync
            syncDao.insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = "INCIDENT",
                    entityId = id,
                    operationType = "REPORT_INCIDENT",
                    payloadJson = """{"incidentId":"$id","category":"${draft.category.name}","severity":"${draft.severity.name}","description":"${escapeJson(draft.description)}","jobId":${draft.jobId?.let { "\"$it\"" } ?: "null"}}""",
                    createdAt = now,
                    retryCount = 0,
                    lastError = null,
                    status = SyncStatus.PENDING.name
                )
            )

            // If attached to a job, also add to the job timeline
            if (!draft.jobId.isNullOrBlank()) {
                val timelineEvent = JobTimelineEvent(
                    id = idGenerator(),
                    jobId = draft.jobId,
                    status = "INCIDENT",
                    title = "Incident: ${draft.category.label}",
                    description = draft.description,
                    timestamp = now,
                    latitude = draft.latitude,
                    longitude = draft.longitude,
                    syncStatus = SyncStatus.PENDING
                )
                timelineDao.insert(
                    au.com.firstclassexpress.driver.data.local.entity.JobTimelineEventEntity(
                        id = timelineEvent.id,
                        jobId = timelineEvent.jobId,
                        status = timelineEvent.status,
                        title = timelineEvent.title,
                        description = timelineEvent.description,
                        timestamp = timelineEvent.timestamp,
                        latitude = timelineEvent.latitude,
                        longitude = timelineEvent.longitude,
                        syncStatus = timelineEvent.syncStatus.name
                    )
                )
            }

            toRecord(entity)
        }
    }

    private fun toRecord(entity: DriverIncidentEntity): IncidentRecord =
        IncidentRecord(
            id = entity.id,
            driverId = entity.driverId,
            shiftId = entity.shiftId,
            jobId = entity.jobId,
            category = runCatching { IncidentCategory.valueOf(entity.category) }.getOrDefault(IncidentCategory.OTHER),
            severity = runCatching { IncidentSeverity.valueOf(entity.severity) }.getOrDefault(IncidentSeverity.MEDIUM),
            description = entity.description,
            photoUri = entity.photoUri,
            latitude = entity.latitude,
            longitude = entity.longitude,
            createdAt = entity.createdAt,
            syncStatus = runCatching { SyncStatus.valueOf(entity.syncStatus) }.getOrDefault(SyncStatus.PENDING)
        )

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
