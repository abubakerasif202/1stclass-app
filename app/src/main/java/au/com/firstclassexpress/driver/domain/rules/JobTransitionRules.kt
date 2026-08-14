package au.com.firstclassexpress.driver.domain.rules

import au.com.firstclassexpress.driver.model.JobStatus

object JobTransitionRules {
    private val allowed = mapOf(
        JobStatus.ASSIGNED to setOf(JobStatus.ACCEPTED, JobStatus.IN_PROGRESS, JobStatus.DELAYED, JobStatus.ISSUE),
        JobStatus.ACCEPTED to setOf(JobStatus.IN_PROGRESS, JobStatus.DELAYED, JobStatus.ISSUE),
        JobStatus.IN_PROGRESS to setOf(JobStatus.AT_PICKUP, JobStatus.DELAYED, JobStatus.VEHICLE_ISSUE, JobStatus.ISSUE),
        JobStatus.AT_PICKUP to setOf(JobStatus.PICKED_UP, JobStatus.DELAYED, JobStatus.CUSTOMER_UNAVAILABLE, JobStatus.ISSUE),
        JobStatus.PICKED_UP to setOf(JobStatus.EN_ROUTE_DELIVERY, JobStatus.DELAYED, JobStatus.VEHICLE_ISSUE, JobStatus.ISSUE),
        JobStatus.EN_ROUTE_DELIVERY to setOf(JobStatus.AT_DELIVERY, JobStatus.DELAYED, JobStatus.VEHICLE_ISSUE, JobStatus.ISSUE),
        JobStatus.AT_DELIVERY to setOf(JobStatus.DELIVERED, JobStatus.COMPLETED, JobStatus.FAILED_DELIVERY, JobStatus.CUSTOMER_UNAVAILABLE, JobStatus.ISSUE),
        JobStatus.DELIVERED to setOf(JobStatus.COMPLETED, JobStatus.ISSUE),
        JobStatus.DELAYED to setOf(JobStatus.ACCEPTED, JobStatus.IN_PROGRESS, JobStatus.AT_PICKUP, JobStatus.PICKED_UP, JobStatus.EN_ROUTE_DELIVERY, JobStatus.AT_DELIVERY, JobStatus.ISSUE),
        JobStatus.CUSTOMER_UNAVAILABLE to setOf(JobStatus.AT_PICKUP, JobStatus.AT_DELIVERY, JobStatus.COMPLETED, JobStatus.ISSUE),
        JobStatus.FAILED_DELIVERY to setOf(JobStatus.AT_DELIVERY, JobStatus.COMPLETED, JobStatus.ISSUE),
        JobStatus.VEHICLE_ISSUE to setOf(JobStatus.IN_PROGRESS, JobStatus.EN_ROUTE_DELIVERY, JobStatus.ISSUE),
        JobStatus.OTHER_EXCEPTION to setOf(JobStatus.IN_PROGRESS, JobStatus.EN_ROUTE_DELIVERY, JobStatus.ISSUE),
        JobStatus.ISSUE to setOf(JobStatus.IN_PROGRESS, JobStatus.AT_PICKUP, JobStatus.PICKED_UP, JobStatus.EN_ROUTE_DELIVERY, JobStatus.AT_DELIVERY, JobStatus.COMPLETED)
    )

    fun canTransition(from: JobStatus, to: JobStatus): Boolean = allowed[from]?.contains(to) == true
    fun allowedNext(from: JobStatus): Set<JobStatus> = allowed[from].orEmpty()
}
