package au.com.firstclassexpress.driver.domain.evidence

import au.com.firstclassexpress.driver.domain.model.EvidenceCaptureRequest
import au.com.firstclassexpress.driver.domain.model.EvidenceStatus
import au.com.firstclassexpress.driver.domain.model.EvidenceType
import au.com.firstclassexpress.driver.testing.FakeEvidenceFileStore
import au.com.firstclassexpress.driver.testing.FakeEvidenceRepository
import au.com.firstclassexpress.driver.testing.FakeSignatureRenderer
import au.com.firstclassexpress.driver.testing.emptySignature
import au.com.firstclassexpress.driver.testing.signatureWithInk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceCaptureServiceTest {
    private val repository = FakeEvidenceRepository()
    private val fileStore = FakeEvidenceFileStore()
    private val service = EvidenceCaptureService(repository, fileStore, FakeSignatureRenderer())

    private val photoRequest = EvidenceCaptureRequest(
        jobId = "job-1",
        type = EvidenceType.PICKUP_PHOTO,
        driverId = "DRV-8492",
        shiftId = "shift-1"
    )

    @Test
    fun openingCaptureAloneDoesNotProduceEvidence() = runTest {
        service.begin(photoRequest).getOrThrow()

        val record = repository.records.single()
        assertEquals(EvidenceStatus.PENDING_CAPTURE, record.status)
        assertNull(record.localUri)
    }

    @Test
    fun cancelledCaptureLeavesNoEvidence() = runTest {
        val pending = service.begin(photoRequest).getOrThrow()

        service.cancel(pending).getOrThrow()

        assertTrue(repository.records.isEmpty())
        assertEquals(0, fileStore.storedPhotoCount)
    }

    @Test
    fun successfulPhotoBecomesSavedLocalWithMetadata() = runTest {
        val pending = service.begin(photoRequest).getOrThrow()

        val saved = service.completePhoto(pending, service.stagingPathFor(pending)).getOrThrow()

        assertEquals(EvidenceStatus.SAVED_LOCAL, saved.status)
        assertNotNull(saved.localUri)
        assertEquals("DRV-8492", saved.driverId)
        assertEquals("shift-1", saved.shiftId)
        assertEquals(2048L, saved.fileSizeBytes)
        assertNotNull(saved.savedAt)
    }

    @Test
    fun failedFileWriteDoesNotMarkEvidenceSaved() = runTest {
        val failingStore = FakeEvidenceFileStore(failStore = true)
        val failingService =
            EvidenceCaptureService(repository, failingStore, FakeSignatureRenderer())
        val pending = failingService.begin(photoRequest).getOrThrow()

        val result = failingService.completePhoto(pending, "/tmp/missing.jpg")

        assertTrue(result.isFailure)
        assertEquals(EvidenceStatus.PENDING_CAPTURE, repository.records.single().status)
    }

    @Test
    fun blankSignatureCannotBeSaved() = runTest {
        val pending = service.begin(
            photoRequest.copy(type = EvidenceType.DELIVERY_SIGNATURE)
        ).getOrThrow()

        val result = service.completeSignature(pending, emptySignature(), "Jane Receiver")

        assertTrue(result.isFailure)
        assertEquals(0, fileStore.storedSignatureCount)
        assertEquals(EvidenceStatus.PENDING_CAPTURE, repository.records.single().status)
    }

    @Test
    fun deliverySignatureRequiresSignerName() = runTest {
        val pending = service.begin(
            photoRequest.copy(type = EvidenceType.DELIVERY_SIGNATURE)
        ).getOrThrow()

        val result = service.completeSignature(pending, signatureWithInk(), "   ")

        assertTrue(result.isFailure)
        assertEquals(EvidenceStatus.PENDING_CAPTURE, repository.records.single().status)
    }

    @Test
    fun successfulSignaturePersistsWithSignerName() = runTest {
        val pending = service.begin(
            photoRequest.copy(type = EvidenceType.DELIVERY_SIGNATURE)
        ).getOrThrow()

        val saved =
            service.completeSignature(pending, signatureWithInk(), " Jane Receiver ").getOrThrow()

        assertEquals(EvidenceStatus.SAVED_LOCAL, saved.status)
        assertEquals("Jane Receiver", saved.signerName)
        assertEquals(1, fileStore.storedSignatureCount)
    }

    @Test
    fun deletingSavedEvidenceAlsoRemovesItsFile() = runTest {
        val pending = service.begin(photoRequest).getOrThrow()
        val saved = service.completePhoto(pending, service.stagingPathFor(pending)).getOrThrow()

        service.deleteSaved(saved.id).getOrThrow()

        assertTrue(repository.records.isEmpty())
        assertEquals(listOf(saved.localUri), fileStore.deleted)
    }

    @Test
    fun resumeRebuildsPendingCaptureAfterProcessDeath() = runTest {
        val pending = service.begin(photoRequest).getOrThrow()

        val resumed = service.resume(pending.evidenceId).getOrThrow()

        assertEquals(pending.evidenceId, resumed.evidenceId)
        assertEquals(EvidenceType.PICKUP_PHOTO, resumed.descriptor.type)
        assertEquals("DRV-8492", resumed.descriptor.driverId)
    }

    @Test
    fun resumeFailsOnceEvidenceIsAlreadySaved() = runTest {
        val pending = service.begin(photoRequest).getOrThrow()
        service.completePhoto(pending, service.stagingPathFor(pending)).getOrThrow()

        assertTrue(service.resume(pending.evidenceId).isFailure)
    }
}
