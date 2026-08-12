package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.JobPayloadCodec
import com.example.data.local.entity.SyncOperationEntity
import com.example.domain.model.SyncStatus
import com.example.domain.repository.JobRepository
import com.example.domain.rules.JobTransitionRules
import com.example.model.Job
import com.example.model.JobStatus
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
            to
        }
    }
}
