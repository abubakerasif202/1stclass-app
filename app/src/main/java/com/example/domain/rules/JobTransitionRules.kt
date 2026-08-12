package com.example.domain.rules

import com.example.model.JobStatus

object JobTransitionRules {
    private val allowed = mapOf(
        JobStatus.ASSIGNED to setOf(JobStatus.IN_PROGRESS),
        JobStatus.IN_PROGRESS to setOf(JobStatus.AT_PICKUP, JobStatus.ISSUE),
        JobStatus.AT_PICKUP to setOf(JobStatus.PICKED_UP, JobStatus.ISSUE),
        JobStatus.PICKED_UP to setOf(JobStatus.EN_ROUTE_DELIVERY, JobStatus.ISSUE),
        JobStatus.EN_ROUTE_DELIVERY to setOf(JobStatus.AT_DELIVERY, JobStatus.ISSUE),
        JobStatus.AT_DELIVERY to setOf(JobStatus.COMPLETED, JobStatus.ISSUE)
    )

    fun canTransition(from: JobStatus, to: JobStatus): Boolean = allowed[from]?.contains(to) == true
    fun allowedNext(from: JobStatus): Set<JobStatus> = allowed[from].orEmpty()
}
