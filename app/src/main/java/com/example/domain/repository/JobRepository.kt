package com.example.domain.repository

import com.example.model.Job
import com.example.model.JobStatus
import kotlinx.coroutines.flow.Flow

interface JobRepository {
    fun observeJobs(): Flow<List<Job>>
    suspend fun getJob(id: String): Job?
    suspend fun transition(id: String, to: JobStatus): Result<JobStatus>
}
