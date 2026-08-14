package au.com.firstclassexpress.driver.domain.rules

import au.com.firstclassexpress.driver.model.JobStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobTransitionRulesTest {
    @Test fun unassignedJobCannotStart() =
        assertFalse(JobTransitionRules.canTransition(JobStatus.UNASSIGNED, JobStatus.IN_PROGRESS))

    @Test fun assignedJobCanStart() =
        assertTrue(JobTransitionRules.canTransition(JobStatus.ASSIGNED, JobStatus.IN_PROGRESS))

    @Test fun jobCannotSkipPickupStages() =
        assertFalse(JobTransitionRules.canTransition(JobStatus.IN_PROGRESS, JobStatus.AT_DELIVERY))

    @Test fun deliveryCanCompleteOnlyFromAtDelivery() =
        assertTrue(JobTransitionRules.canTransition(JobStatus.AT_DELIVERY, JobStatus.COMPLETED))
}
