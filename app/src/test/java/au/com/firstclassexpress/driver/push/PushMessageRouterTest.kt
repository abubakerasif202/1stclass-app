package au.com.firstclassexpress.driver.push

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushMessageRouterTest {
    @Test
    fun jobUpdateWithJobIdRoutesToJobDetail() {
        assertEquals(
            PushDestination.JobDetail("job-42"),
            PushMessageRouter.destination("JOB_UPDATED", "job-42")
        )
    }

    @Test
    fun backendJobTypesNormalizeAndRouteToJobDetail() {
        listOf("NEW_JOB", "JOB_UPDATED", "JOB_CANCELLED").forEach { type ->
            assertEquals(
                "Expected $type with a job ID to open job detail",
                PushDestination.JobDetail("job-42"),
                PushMessageRouter.destination(type, "job-42")
            )
        }
    }

    @Test
    fun vehicleUrgentAndMessageTypesRouteToMessages() {
        listOf("VEHICLE_ALERT", "URGENT_ALERT", "MESSAGE").forEach { type ->
            assertEquals(
                "Expected $type to open operational messages",
                PushDestination.Messages,
                PushMessageRouter.destination(type, null)
            )
        }
    }

    @Test
    fun dispatchWithoutJobIdRoutesToMessages() {
        assertEquals(PushDestination.Messages, PushMessageRouter.destination("dispatch", null))
    }

    @Test
    fun dataPayloadUsesSafeDefaultsAndMapsJobIdAliases() {
        val message = PushMessageRouter.fromData(
            data = mapOf("type" to "job_revision", "job_id" to "job-7"),
            notificationTitle = null,
            notificationBody = null,
            messageId = "message-1"
        )

        assertEquals("Job update", message?.title)
        assertEquals("New operational message received", message?.body)
        assertEquals(PushDestination.JobDetail("job-7"), message?.destination)
    }

    @Test
    fun intentRoundTripPreservesMessageDestination() {
        val intent = PushMessageRouter.applyToIntent(
            Intent("test"),
            PushDestination.JobDetail("job-9")
        )

        assertEquals(PushDestination.JobDetail("job-9"), PushMessageRouter.fromIntent(intent))
        assertNull(PushMessageRouter.fromIntent(Intent("test")))
    }
}
