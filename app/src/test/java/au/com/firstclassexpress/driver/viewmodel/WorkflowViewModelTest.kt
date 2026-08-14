package au.com.firstclassexpress.driver.viewmodel

import au.com.firstclassexpress.driver.domain.evidence.EvidenceCaptureService
import au.com.firstclassexpress.driver.domain.model.EvidenceCaptureRequest
import au.com.firstclassexpress.driver.domain.model.EvidenceType
import au.com.firstclassexpress.driver.domain.model.FreightExceptionReason
import au.com.firstclassexpress.driver.domain.model.ValidationResult
import au.com.firstclassexpress.driver.domain.repository.JobRepository
import au.com.firstclassexpress.driver.domain.rules.DeliveryCompletionRules
import au.com.firstclassexpress.driver.domain.rules.PickupCompletionRules
import au.com.firstclassexpress.driver.testing.FakeEvidenceFileStore
import au.com.firstclassexpress.driver.testing.FakeEvidenceRepository
import au.com.firstclassexpress.driver.testing.FakeFreightExceptionRepository
import au.com.firstclassexpress.driver.testing.FakeSignatureRenderer
import au.com.firstclassexpress.driver.testing.signatureWithInk
import au.com.firstclassexpress.driver.testing.testJob
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowViewModelTest {
    private val evidence = FakeEvidenceRepository()
    private val exceptions = FakeFreightExceptionRepository()
    private val fileStore = FakeEvidenceFileStore()
    private val capture = EvidenceCaptureService(evidence, fileStore, FakeSignatureRenderer())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Pickup ---------------------------------------------------------------------------

    @Test
    fun pickupWithoutAPhotoCannotBeConfirmed() = runTest {
        val jobs = FakeJobRepository(testJob(status = JobStatus.AT_PICKUP))
        val model = pickupViewModel(jobs)

        val result = model.confirmPickup()

        assertTrue(result.isFailure)
        assertEquals(JobStatus.AT_PICKUP, jobs.current().status)
        assertTrue(
            model.uiState.value.blockingReasons.contains(PickupCompletionRules.PHOTO_REQUIRED)
        )
    }

    @Test
    fun cancelledPhotoDoesNotUnlockPickup() = runTest {
        val jobs = FakeJobRepository(testJob(status = JobStatus.AT_PICKUP))
        val model = pickupViewModel(jobs)
        val pending = capture.begin(
            EvidenceCaptureRequest("job-1", EvidenceType.PICKUP_PHOTO, "DRV-8492")
        ).getOrThrow()

        capture.cancel(pending).getOrThrow()

        assertFalse(model.uiState.value.canConfirm)
        assertTrue(model.confirmPickup().isFailure)
    }

    @Test
    fun savedPhotoAndQuantityPermitPickedUp() = runTest {
        val jobs = FakeJobRepository(testJob(status = JobStatus.AT_PICKUP))
        val model = pickupViewModel(jobs)
        saveEvidence(EvidenceType.PICKUP_PHOTO)

        val result = model.confirmPickup()

        assertTrue(result.isSuccess)
        assertEquals(JobStatus.PICKED_UP, jobs.current().status)
        assertTrue(model.uiState.value.isComplete)
    }

    @Test
    fun unresolvedPickupExceptionBlocksConfirmation() = runTest {
        val jobs = FakeJobRepository(testJob(status = JobStatus.AT_PICKUP))
        val model = pickupViewModel(jobs)
        saveEvidence(EvidenceType.PICKUP_PHOTO)
        model.recordException(FreightExceptionReason.DAMAGED, "Corner crushed").getOrThrow()

        assertTrue(model.confirmPickup().isFailure)
        assertEquals(JobStatus.AT_PICKUP, jobs.current().status)
    }

    @Test
    fun damageExceptionWithoutAPhotoIsRejected() = runTest {
        val model = pickupViewModel(FakeJobRepository(testJob(status = JobStatus.AT_PICKUP)))

        val result = model.recordException(FreightExceptionReason.DAMAGED, "Corner crushed")

        assertTrue(result.isFailure)
        assertTrue(model.uiState.value.exceptions.isEmpty())
    }

    @Test
    fun rejectedExceptionWithoutNotesIsRejected() = runTest {
        val model = pickupViewModel(FakeJobRepository(testJob(status = JobStatus.AT_PICKUP)))

        val result = model.recordException(FreightExceptionReason.REJECTED, "  ")

        assertTrue(result.isFailure)
        assertTrue(model.uiState.value.exceptions.isEmpty())
    }

    // --- Delivery -------------------------------------------------------------------------

    @Test
    fun deliveryWithoutRecipientCannotComplete() = runTest {
        val jobs = FakeJobRepository(testJob(status = JobStatus.AT_DELIVERY))
        val model = deliveryViewModel(jobs)
        saveEvidence(EvidenceType.DELIVERY_PHOTO)
        saveSignature("Jane Receiver")
        model.onRecipientNameChange("")

        assertTrue(model.completeDelivery().isFailure)
        assertEquals(JobStatus.AT_DELIVERY, jobs.current().status)
    }

    @Test
    fun deliveryWithoutSignatureCannotComplete() = runTest {
        val jobs = FakeJobRepository(testJob(status = JobStatus.AT_DELIVERY))
        val model = deliveryViewModel(jobs)
        saveEvidence(EvidenceType.DELIVERY_PHOTO)
        model.onRecipientNameChange("Jane Receiver")

        assertTrue(model.completeDelivery().isFailure)
        assertTrue(
            model.uiState.value.blockingReasons
                .contains(DeliveryCompletionRules.SIGNATURE_REQUIRED)
        )
        assertEquals(JobStatus.AT_DELIVERY, jobs.current().status)
    }

    @Test
    fun deliveryWithoutPhotoCannotComplete() = runTest {
        val jobs = FakeJobRepository(testJob(status = JobStatus.AT_DELIVERY))
        val model = deliveryViewModel(jobs)
        saveSignature("Jane Receiver")

        assertTrue(model.completeDelivery().isFailure)
        assertTrue(
            model.uiState.value.blockingReasons.contains(DeliveryCompletionRules.PHOTO_REQUIRED)
        )
    }

    @Test
    fun completePodPermitsCompletion() = runTest {
        val jobs = FakeJobRepository(testJob(status = JobStatus.AT_DELIVERY))
        val model = deliveryViewModel(jobs)
        saveEvidence(EvidenceType.DELIVERY_PHOTO)
        saveSignature("Jane Receiver")

        val result = model.completeDelivery()

        assertTrue(result.isSuccess)
        assertEquals(JobStatus.COMPLETED, jobs.current().status)
        assertEquals(ValidationResult.Valid, model.uiState.value.validation)
    }

    @Test
    fun signerNameFromTheSignaturePreFillsTheRecipient() = runTest {
        val model = deliveryViewModel(FakeJobRepository(testJob(status = JobStatus.AT_DELIVERY)))

        saveSignature("Jane Receiver")

        assertEquals("Jane Receiver", model.uiState.value.recipientName)
    }

    // --- Helpers --------------------------------------------------------------------------

    private fun pickupViewModel(jobs: JobRepository) = PickupViewModel(
        jobId = "job-1",
        driverId = "DRV-8492",
        shiftId = "shift-1",
        jobRepository = jobs,
        evidenceRepository = evidence,
        exceptionRepository = exceptions
    )

    private fun deliveryViewModel(jobs: JobRepository) = DeliveryViewModel(
        jobId = "job-1",
        driverId = "DRV-8492",
        shiftId = "shift-1",
        jobRepository = jobs,
        evidenceRepository = evidence,
        exceptionRepository = exceptions
    )

    private suspend fun saveEvidence(type: EvidenceType) {
        val pending = capture.begin(
            EvidenceCaptureRequest("job-1", type, "DRV-8492", "shift-1")
        ).getOrThrow()
        capture.completePhoto(pending, capture.stagingPathFor(pending)).getOrThrow()
    }

    private suspend fun saveSignature(signerName: String) {
        val pending = capture.begin(
            EvidenceCaptureRequest(
                "job-1",
                EvidenceType.DELIVERY_SIGNATURE,
                "DRV-8492",
                "shift-1"
            )
        ).getOrThrow()
        capture.completeSignature(pending, signatureWithInk(), signerName).getOrThrow()
    }

    private class FakeJobRepository(job: Job) : JobRepository {
        private val flow = MutableStateFlow(listOf(job))

        fun current(): Job = flow.value.single()

        override fun observeJobs(): Flow<List<Job>> = flow

        override suspend fun getJob(id: String): Job? = flow.value.find { it.id == id }

        override suspend fun transition(id: String, to: JobStatus): Result<JobStatus> {
            flow.value = flow.value.map { if (it.id == id) it.copy(status = to) else it }
            return Result.success(to)
        }
    }
}
