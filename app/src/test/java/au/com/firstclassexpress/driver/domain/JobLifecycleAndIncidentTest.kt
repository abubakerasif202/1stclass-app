package au.com.firstclassexpress.driver.domain

import au.com.firstclassexpress.driver.domain.model.IncidentCategory
import au.com.firstclassexpress.driver.domain.model.IncidentDraft
import au.com.firstclassexpress.driver.domain.model.IncidentSeverity
import au.com.firstclassexpress.driver.domain.rules.JobTransitionRules
import au.com.firstclassexpress.driver.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobLifecycleAndIncidentTest {

    @Test
    fun `assigned job can transition to accepted or in progress`() {
        assertTrue(JobTransitionRules.canTransition(JobStatus.ASSIGNED, JobStatus.ACCEPTED))
        assertTrue(JobTransitionRules.canTransition(JobStatus.ASSIGNED, JobStatus.IN_PROGRESS))
    }

    @Test
    fun `accepted job can transition to in progress`() {
        assertTrue(JobTransitionRules.canTransition(JobStatus.ACCEPTED, JobStatus.IN_PROGRESS))
    }

    @Test
    fun `at delivery job can transition to delivered and completed`() {
        assertTrue(JobTransitionRules.canTransition(JobStatus.AT_DELIVERY, JobStatus.DELIVERED))
        assertTrue(JobTransitionRules.canTransition(JobStatus.AT_DELIVERY, JobStatus.COMPLETED))
        assertTrue(JobTransitionRules.canTransition(JobStatus.DELIVERED, JobStatus.COMPLETED))
    }

    @Test
    fun `job in transit can encounter exception and recover`() {
        assertTrue(JobTransitionRules.canTransition(JobStatus.IN_PROGRESS, JobStatus.DELAYED))
        assertTrue(JobTransitionRules.canTransition(JobStatus.DELAYED, JobStatus.IN_PROGRESS))

        assertTrue(JobTransitionRules.canTransition(JobStatus.EN_ROUTE_DELIVERY, JobStatus.VEHICLE_ISSUE))
        assertTrue(JobTransitionRules.canTransition(JobStatus.VEHICLE_ISSUE, JobStatus.EN_ROUTE_DELIVERY))

        assertTrue(JobTransitionRules.canTransition(JobStatus.AT_DELIVERY, JobStatus.CUSTOMER_UNAVAILABLE))
        assertTrue(JobTransitionRules.canTransition(JobStatus.CUSTOMER_UNAVAILABLE, JobStatus.AT_DELIVERY))
    }

    @Test
    fun `terminal status cannot transition further`() {
        assertFalse(JobTransitionRules.canTransition(JobStatus.COMPLETED, JobStatus.IN_PROGRESS))
    }

    @Test
    fun `incident category requirements check`() {
        assertTrue(IncidentCategory.FREIGHT_DAMAGE.requiresPhoto)
        assertTrue(IncidentCategory.ACCIDENT.requiresPhoto)
        assertFalse(IncidentCategory.DELAY.requiresPhoto)
        assertFalse(IncidentCategory.CUSTOMER_UNAVAILABLE.requiresPhoto)
    }

    @Test
    fun `incident draft creation`() {
        val draft = IncidentDraft(
            driverId = "DRV-101",
            shiftId = "SHIFT-202",
            jobId = "JOB-303",
            category = IncidentCategory.BREAKDOWN,
            severity = IncidentSeverity.HIGH,
            description = "Tyre puncture on M4 motorway near Parramatta exit."
        )

        assertEquals("DRV-101", draft.driverId)
        assertEquals(IncidentCategory.BREAKDOWN, draft.category)
        assertEquals(IncidentSeverity.HIGH, draft.severity)
        assertEquals("JOB-303", draft.jobId)
    }
}
