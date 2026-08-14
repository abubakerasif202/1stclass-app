package au.com.firstclassexpress.driver.domain

import au.com.firstclassexpress.driver.domain.model.GpsStatus
import au.com.firstclassexpress.driver.domain.model.LocationPoint
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import au.com.firstclassexpress.driver.domain.model.trackingStatusFor
import au.com.firstclassexpress.driver.domain.rules.DelayDetectionEngine
import au.com.firstclassexpress.driver.domain.rules.GeofenceArrivalEngine
import au.com.firstclassexpress.driver.domain.rules.ShiftEndValidator
import au.com.firstclassexpress.driver.domain.sync.JobConflictResolution
import au.com.firstclassexpress.driver.domain.sync.JobConflictResolver
import au.com.firstclassexpress.driver.location.AdaptiveLocationFrequency
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import au.com.firstclassexpress.driver.model.Location
import au.com.firstclassexpress.driver.model.Priority
import au.com.firstclassexpress.driver.util.SafeOpsLogger
import org.junit.Assert.*
import org.junit.Test

class Phase3LiveOperationsTest {

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
    fun adaptiveLocation_offDuty_returnsNull() {
        val interval = AdaptiveLocationFrequency.calculate(
            isShiftActive = false,
            activeJob = sampleJob,
            lastPoint = null
        )
        assertNull(interval)
    }

    @Test
    fun adaptiveLocation_stationary_usesStandbyInterval() {
        val interval = AdaptiveLocationFrequency.calculate(
            isShiftActive = true,
            activeJob = null,
            lastPoint = null
        )
        assertNotNull(interval)
        assertEquals(60_000L, interval?.intervalMillis)
    }

    @Test
    fun adaptiveLocation_enRoute_usesActiveTransitInterval() {
        val inTransitJob = sampleJob.copy(status = JobStatus.IN_PROGRESS)
        val pointFar = LocationPoint(
            id = "p-1",
            driverId = "drv-1",
            shiftId = "sh-1",
            latitude = -37.7000,
            longitude = 144.6000,
            accuracyMeters = 5f,
            recordedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        val interval = AdaptiveLocationFrequency.calculate(
            isShiftActive = true,
            activeJob = inTransitJob,
            lastPoint = pointFar
        )
        assertNotNull(interval)
        assertEquals(15_000L, interval?.intervalMillis)
    }

    @Test
    fun adaptiveLocation_nearStop_usesHighPrecisionInterval() {
        val inTransitJob = sampleJob.copy(status = JobStatus.IN_PROGRESS)
        // Point right near Truganina DC (-37.8300, 144.7500)
        val pointNear = LocationPoint(
            id = "p-2",
            driverId = "drv-1",
            shiftId = "sh-1",
            latitude = -37.8301,
            longitude = 144.7501,
            accuracyMeters = 5f,
            recordedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        val interval = AdaptiveLocationFrequency.calculate(
            isShiftActive = true,
            activeJob = inTransitJob,
            lastPoint = pointNear
        )
        assertNotNull(interval)
        assertEquals(5_000L, interval?.intervalMillis)
    }

    @Test
    fun trackingStatusFor_evaluatesCorrectly() {
        assertEquals(GpsStatus.OFF, trackingStatusFor(onDuty = false, hasCoarsePermission = true, hasFinePermission = true, locationServicesEnabled = true, hasFix = true))
        assertEquals(GpsStatus.PERMISSION_REQUIRED, trackingStatusFor(onDuty = true, hasCoarsePermission = false, hasFinePermission = false, locationServicesEnabled = true, hasFix = true))
        assertEquals(GpsStatus.GPS_OFF, trackingStatusFor(onDuty = true, hasCoarsePermission = true, hasFinePermission = true, locationServicesEnabled = false, hasFix = true))
        assertEquals(GpsStatus.ACTIVE, trackingStatusFor(onDuty = true, hasCoarsePermission = true, hasFinePermission = true, locationServicesEnabled = true, hasFix = true))
        assertEquals(GpsStatus.OFFLINE_QUEUED, trackingStatusFor(onDuty = true, hasCoarsePermission = true, hasFinePermission = true, locationServicesEnabled = true, hasFix = true, isOffline = true))
    }

    @Test
    fun jobConflictResolver_acceptsServerWhenNoLocalPending() {
        val serverJob = sampleJob.copy(
            revision = 2L,
            specialInstructions = "Deliver to rear dock"
        )
        val resolution = JobConflictResolver.resolve(
            localJob = sampleJob,
            serverJob = serverJob,
            hasPendingLocalOperations = false
        )
        assertTrue(resolution is JobConflictResolution.AcceptServer)
        assertEquals(2L, (resolution as JobConflictResolution.AcceptServer).updatedJob.revision)
    }

    @Test
    fun jobConflictResolver_preservesLocalStatusWhenDriverAhead() {
        val localJobAhead = sampleJob.copy(
            status = JobStatus.PICKED_UP,
            revision = 1L
        )
        val serverJob = sampleJob.copy(
            status = JobStatus.ACCEPTED,
            revision = 2L,
            specialInstructions = "Please call 10 mins before arrival"
        )
        val resolution = JobConflictResolver.resolve(
            localJob = localJobAhead,
            serverJob = serverJob,
            hasPendingLocalOperations = true
        )
        assertTrue(resolution is JobConflictResolution.MergeKeepLocalStatus)
        val merged = (resolution as JobConflictResolution.MergeKeepLocalStatus).mergedJob
        assertEquals(JobStatus.PICKED_UP, merged.status)
        assertTrue(merged.specialInstructions.contains("Please call 10 mins before arrival"))
    }

    @Test
    fun geofenceArrivalEngine_detectsArrivalWithinRadius() {
        val jobEnRoute = sampleJob.copy(status = JobStatus.IN_PROGRESS)
        val nearPoint = LocationPoint(
            id = "p-near",
            driverId = "drv-1",
            shiftId = "sh-1",
            latitude = -37.83005,
            longitude = 144.75005,
            accuracyMeters = 5f,
            recordedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        val suggestion = GeofenceArrivalEngine.checkArrival(jobEnRoute, nearPoint)
        assertNotNull(suggestion)
        assertTrue(suggestion?.isNearDestination == true)
        assertEquals(JobStatus.AT_PICKUP, suggestion?.targetStatus)
    }

    @Test
    fun delayDetectionEngine_flagsStationaryInTransit() {
        val inTransitJob = sampleJob.copy(status = JobStatus.IN_PROGRESS)
        val now = System.currentTimeMillis()
        val stationarySince = now - 15 * 60 * 1000L // 15 mins ago

        val result = DelayDetectionEngine.evaluate(
            job = inTransitJob,
            lastPoint = null,
            stationarySinceMillis = stationarySince,
            currentTime = now
        )
        assertTrue(result.isDelayed)
        assertNotNull(result.suggestedPrompt)
    }

    @Test
    fun shiftEndValidator_warnsOnIncompleteJobsOrQueue() {
        val activeJobs = listOf(sampleJob.copy(status = JobStatus.IN_PROGRESS))
        val validation = ShiftEndValidator.validate(
            assignedJobs = activeJobs,
            pendingSyncOperationsCount = 2,
            pendingLocationPointsCount = 5
        )
        assertFalse(validation.canEndSafely)
        assertEquals(1, validation.pendingJobsCount)
        assertEquals(7, validation.pendingSyncCount)
        assertEquals(2, validation.warnings.size)
    }

    @Test
    fun safeOpsLogger_redactsSensitiveTokens() {
        val raw = "Authorization: Bearer secret_jwt_token_12345, pin: 4892, password: mySecretPassword1"
        val redacted = SafeOpsLogger.redact(raw)
        assertFalse(redacted.contains("secret_jwt_token_12345"))
        assertFalse(redacted.contains("4892"))
        assertFalse(redacted.contains("mySecretPassword1"))
        assertTrue(redacted.contains("[REDACTED]"))
    }
}
