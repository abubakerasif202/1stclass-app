package au.com.firstclassexpress.driver.domain

import au.com.firstclassexpress.driver.data.remote.api.DeviceRegistrationRequest
import au.com.firstclassexpress.driver.data.remote.api.PushTokenUpdateRequest
import au.com.firstclassexpress.driver.domain.config.FeatureFlags
import au.com.firstclassexpress.driver.domain.config.RemoteAppConfig
import au.com.firstclassexpress.driver.domain.model.LocationPoint
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.rules.BarcodeVerificationHelper
import au.com.firstclassexpress.driver.domain.rules.JobChangeDiffHelper
import au.com.firstclassexpress.driver.domain.rules.ShiftEndValidator
import au.com.firstclassexpress.driver.domain.sync.JobConflictResolution
import au.com.firstclassexpress.driver.domain.sync.JobConflictResolver
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import au.com.firstclassexpress.driver.model.Location
import au.com.firstclassexpress.driver.model.Priority
import au.com.firstclassexpress.driver.util.ImageCompressionHelper
import au.com.firstclassexpress.driver.util.SafeOpsLogger
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class Phase4ProductionPilotTest {

    private val samplePickup = Location(
        address = "12 Industrial Ave",
        suburb = "Truganina",
        lat = -37.8300,
        lng = 144.7500,
        companyName = "Truganina DC",
        contactName = "Dock Manager",
        contactPhone = "0400111222"
    )

    private val sampleDelivery = Location(
        address = "88 Port Road",
        suburb = "West Melbourne",
        lat = -37.8100,
        lng = 144.9100,
        companyName = "Express Depots",
        contactName = "Receiving Officer",
        contactPhone = "0400333444"
    )

    private val sampleJob = Job(
        id = "job-101",
        reference = "1CE-MEL-101",
        status = JobStatus.ACCEPTED,
        pickup = samplePickup,
        delivery = sampleDelivery,
        pickupWindowStart = "08:00",
        pickupWindowEnd = "10:00",
        deliveryWindowStart = "11:00",
        deliveryWindowEnd = "13:00",
        freightDescription = "4 Pallets Automotive Parts",
        itemCount = 4,
        priority = Priority.NORMAL,
        revision = 1L
    )

    @Test
    fun versionComparison_correctlyIdentifiesMandatoryUpdate() {
        assertTrue(RemoteAppConfig.isUpdateRequired(currentVersion = "1.0.0", minSupportedVersion = "1.1.0"))
        assertTrue(RemoteAppConfig.isUpdateRequired(currentVersion = "1.0.9", minSupportedVersion = "2.0.0"))
        assertFalse(RemoteAppConfig.isUpdateRequired(currentVersion = "1.2.0", minSupportedVersion = "1.1.0"))
        assertFalse(RemoteAppConfig.isUpdateRequired(currentVersion = "2.0.0", minSupportedVersion = "1.9.9"))
        assertFalse(RemoteAppConfig.isUpdateRequired(currentVersion = "1.1.0", minSupportedVersion = "1.1.0"))
    }

    @Test
    fun barcodeVerification_matchesJobReference() {
        val result = BarcodeVerificationHelper.verifyScannedCode("1CE-MEL-101", sampleJob)
        assertTrue(result.isMatched)
        assertEquals("REFERENCE_MATCH", result.matchType)
    }

    @Test
    fun barcodeVerification_matchesManifestSubstring() {
        val result = BarcodeVerificationHelper.verifyScannedCode("Automotive", sampleJob)
        assertTrue(result.isMatched)
        assertEquals("MANIFEST_MATCH", result.matchType)
    }

    @Test
    fun barcodeVerification_rejectsUnmatchedCode() {
        val result = BarcodeVerificationHelper.verifyScannedCode("UNKNOWN-BARCODE-999", sampleJob)
        assertFalse(result.isMatched)
        assertEquals("UNMATCHED", result.matchType)
    }

    @Test
    fun jobChangeDiff_detectsDeliveryAddressAndWindowModifications() {
        val updatedJob = sampleJob.copy(
            delivery = sampleDelivery.copy(address = "99 King Street", suburb = "Melbourne CBD"),
            deliveryWindowStart = "14:00",
            deliveryWindowEnd = "16:00",
            priority = Priority.URGENT,
            revision = 2L
        )

        val diff = JobChangeDiffHelper.compareJobs(previous = sampleJob, current = updatedJob)
        assertTrue(diff.hasChanges)
        assertTrue(diff.changedFields.contains("Delivery Address"))
        assertTrue(diff.changedFields.contains("Delivery Window"))
        assertTrue(diff.changedFields.contains("Priority"))
        assertEquals(3, diff.changedFields.size)
    }

    @Test
    fun deviceRegistrationDto_validatesAttributes() {
        val req = DeviceRegistrationRequest(
            deviceId = "dev-pixel-7a",
            driverId = "drv-001",
            appVersion = "1.0.0",
            platform = "ANDROID",
            pushToken = "fcm_token_sample_abc123"
        )
        assertEquals("dev-pixel-7a", req.deviceId)
        assertEquals("ANDROID", req.platform)
        assertEquals("fcm_token_sample_abc123", req.pushToken)
    }

    @Test
    fun pushTokenUpdateDto_validatesAttributes() {
        val req = PushTokenUpdateRequest(
            deviceId = "dev-pixel-7a",
            driverId = "drv-001",
            pushToken = "fcm_token_new_xyz789"
        )
        assertEquals("fcm_token_new_xyz789", req.pushToken)
    }

    @Test
    fun featureFlags_defaultAllEnabled() {
        val flags = FeatureFlags()
        assertTrue(flags.liveTrackingEnabled)
        assertTrue(flags.barcodeScannerEnabled)
        assertTrue(flags.geofenceSuggestionsEnabled)
        assertTrue(flags.driverMessagingEnabled)
        assertTrue(flags.offlineSyncEnabled)
    }

    @Test
    fun safeDiagnosticsCopy_stripsPiiAndSecrets() {
        val rawDiagnostics = """
            [1st Class Express System Diagnostics]
            App Version: 1.0.0
            Driver ID: DRV-4412
            Bearer Token: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secretToken
            PIN: 8841
            GPS Telemetry: Tracking Active
            Pending Sync: 0
        """.trimIndent()

        val redacted = SafeOpsLogger.redact(rawDiagnostics)
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertFalse(redacted.contains("8841"))
        assertTrue(redacted.contains("[REDACTED]"))
        assertTrue(redacted.contains("App Version: 1.0.0"))
        assertTrue(redacted.contains("Tracking Active"))
    }

    @Test
    fun simulatedFullShiftWorkflow_progressesThroughAllStatesSafely() {
        var currentJob = sampleJob

        // 1. Accept
        assertEquals(JobStatus.ACCEPTED, currentJob.status)

        // 2. In Progress (En route to pickup)
        currentJob = currentJob.copy(status = JobStatus.IN_PROGRESS)
        assertEquals(JobStatus.IN_PROGRESS, currentJob.status)

        // 3. At Pickup
        currentJob = currentJob.copy(status = JobStatus.AT_PICKUP)
        assertEquals(JobStatus.AT_PICKUP, currentJob.status)

        // 4. Picked up
        currentJob = currentJob.copy(status = JobStatus.PICKED_UP)
        assertEquals(JobStatus.PICKED_UP, currentJob.status)

        // 5. En route delivery
        currentJob = currentJob.copy(status = JobStatus.EN_ROUTE_DELIVERY)
        assertEquals(JobStatus.EN_ROUTE_DELIVERY, currentJob.status)

        // 6. At Delivery
        currentJob = currentJob.copy(status = JobStatus.AT_DELIVERY)
        assertEquals(JobStatus.AT_DELIVERY, currentJob.status)

        // 7. Delivered (POD ready)
        currentJob = currentJob.copy(status = JobStatus.DELIVERED)
        assertEquals(JobStatus.DELIVERED, currentJob.status)

        // 8. Completed
        currentJob = currentJob.copy(status = JobStatus.COMPLETED)
        assertEquals(JobStatus.COMPLETED, currentJob.status)
        assertTrue(currentJob.status.isTerminal)

        // Verify shift can end safely
        val validation = ShiftEndValidator.validate(
            assignedJobs = listOf(currentJob),
            pendingSyncOperationsCount = 0,
            pendingLocationPointsCount = 0
        )
        assertTrue(validation.canEndSafely)
        assertEquals(0, validation.pendingJobsCount)
        assertEquals(0, validation.pendingSyncCount)
    }

    @Test
    fun simulatedOfflineRecovery_queuesAndPreservesEvidence() {
        // Driver performs POD offline
        val localJobDelivered = sampleJob.copy(status = JobStatus.DELIVERED)
        
        // Remote server sends revision update while offline
        val serverJobUpdate = sampleJob.copy(
            revision = 2L,
            specialInstructions = "Call customer 15m prior"
        )

        val resolution = JobConflictResolver.resolve(
            localJob = localJobDelivered,
            serverJob = serverJobUpdate,
            hasPendingLocalOperations = true
        )

        assertTrue(resolution is JobConflictResolution.MergeKeepLocalStatus)
        val merged = (resolution as JobConflictResolution.MergeKeepLocalStatus).mergedJob
        // Ensures driver POD status is strictly preserved
        assertEquals(JobStatus.DELIVERED, merged.status)
        assertTrue(merged.specialInstructions.contains("Call customer 15m prior"))
    }
}
