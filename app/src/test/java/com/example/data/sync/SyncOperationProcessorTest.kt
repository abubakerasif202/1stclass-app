package com.example.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.EvidenceEntity
import com.example.data.local.entity.FreightExceptionEntity
import com.example.data.local.entity.JobEntity
import com.example.data.local.entity.LocationPointEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.remote.TmsApiClient
import com.example.domain.model.EvidenceStatus
import com.example.domain.model.SyncStatus
import com.example.domain.sync.SyncEntityTypes
import com.example.domain.sync.SyncOperationTypes
import com.example.domain.sync.SyncOutcome
import com.example.testing.FakeSyncTransport
import com.example.testing.syncOperation
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Payload hydration, evidence upload safety, and the exception/evidence dependency rule. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncOperationProcessorTest {

    private lateinit var database: AppDatabase
    private lateinit var transport: FakeSyncTransport
    private lateinit var processor: SyncOperationProcessor
    private lateinit var evidenceDirectory: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transport = FakeSyncTransport()
        processor = SyncOperationProcessor(database, transport, TmsApiClient.moshi())
        // App-private storage, exactly where FileSystemEvidenceFileStore puts real evidence.
        evidenceDirectory = File(context.filesDir, "evidence").also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        evidenceDirectory.deleteRecursively()
    }

    private fun evidenceFile(name: String, bytes: ByteArray): File =
        File(evidenceDirectory, name).apply { writeBytes(bytes) }

    // --- routing -----------------------------------------------------------------------------

    @Test
    fun `an unknown entity type fails safely and names itself`() = runTest {
        val outcome = processor.process(syncOperation("op-1", entityType = "MARTIAN"))

        assertTrue(outcome is SyncOutcome.Permanent)
        assertTrue((outcome as SyncOutcome.Permanent).reason.contains("MARTIAN"))
    }

    @Test
    fun `an unknown operation type on a known entity also fails safely`() = runTest {
        val outcome = processor.process(
            syncOperation("op-1", entityType = SyncEntityTypes.JOB, operationType = "TELEPORT")
        )

        assertTrue(outcome is SyncOutcome.Permanent)
    }

    // --- job ---------------------------------------------------------------------------------

    @Test
    fun `a job status payload carries the recorded transition and its original timestamp`() = runTest {
        database.jobDao().insertAll(
            listOf(JobEntity("job-1", "{}", "PICKED_UP", updatedAt = 9_000L))
        )
        database.shiftDao().insert(
            ShiftEntity("s1", "DRV-1", "TRK-01", null, 100L, null, "ON_DUTY", 10L, 500L, null)
        )

        val outcome = processor.process(
            syncOperation(
                id = "op-1",
                entityType = SyncEntityTypes.JOB,
                entityId = "job-1",
                operationType = SyncOperationTypes.STATUS_CHANGE,
                payloadJson = """{"from":"AT_PICKUP","to":"PICKED_UP"}""",
                createdAt = 1_234L
            )
        )

        assertEquals(SyncOutcome.Success, outcome)
        val sent = transport.jobStatuses.single()
        assertEquals("AT_PICKUP", sent.fromStatus)
        assertEquals("PICKED_UP", sent.toStatus)
        assertEquals("The event time, not the retry time", 1_234L, sent.changedAt)
        assertEquals("Attributed to the shift that was running", "s1", sent.shiftId)
        assertEquals("DRV-1", sent.driverId)
    }

    @Test
    fun `the operation id is used verbatim as the idempotency key`() = runTest {
        database.jobDao().insertAll(listOf(JobEntity("job-1", "{}", "ASSIGNED", 1L)))

        processor.process(
            syncOperation("stable-operation-id", entityType = SyncEntityTypes.JOB, entityId = "job-1")
        )
        processor.process(
            syncOperation("stable-operation-id", entityType = SyncEntityTypes.JOB, entityId = "job-1")
        )

        assertEquals(
            listOf("stable-operation-id", "stable-operation-id"),
            transport.idempotencyKeys
        )
    }

    @Test
    fun `a job deleted locally fails permanently rather than sending nothing`() = runTest {
        val outcome = processor.process(
            syncOperation("op-1", entityType = SyncEntityTypes.JOB, entityId = "ghost")
        )

        assertTrue(outcome is SyncOutcome.Permanent)
        assertTrue(transport.jobStatuses.isEmpty())
    }

    // --- location ----------------------------------------------------------------------------

    @Test
    fun `a location payload preserves recordedAt and the real coordinates`() = runTest {
        database.locationPointDao().insert(
            LocationPointEntity(
                id = "loc-1", driverId = "DRV-1", shiftId = "s1", jobId = "job-1",
                latitude = -37.8136, longitude = 144.9631, accuracyMeters = 8.5f,
                speedMetersPerSecond = 12.25f, bearingDegrees = 271.5f, altitudeMeters = 31.0,
                recordedAt = 1_700_000_000_000L, createdAt = 1_700_000_000_500L,
                syncStatus = SyncStatus.PENDING.name
            )
        )

        val outcome = processor.process(
            syncOperation(
                "op-1",
                entityType = SyncEntityTypes.LOCATION_POINT,
                entityId = "loc-1",
                operationType = SyncOperationTypes.LOCATION_POINT_CREATED,
                createdAt = 1_700_000_900_000L
            )
        )

        assertEquals(SyncOutcome.Success, outcome)
        val point = transport.locations.single().single()
        assertEquals(1_700_000_000_000L, point.recordedAt)
        assertEquals(-37.8136, point.latitude, 0.000001)
        assertEquals(144.9631, point.longitude, 0.000001)
        assertEquals(12.25f, point.speedMetersPerSecond!!, 0.001f)
        assertEquals("job-1", point.jobId)
    }

    @Test
    fun `unrecorded optional location fields stay null instead of becoming zero`() = runTest {
        database.locationPointDao().insert(
            LocationPointEntity(
                id = "loc-1", driverId = "DRV-1", shiftId = "s1", jobId = null,
                latitude = 1.0, longitude = 2.0, accuracyMeters = 5f,
                speedMetersPerSecond = null, bearingDegrees = null, altitudeMeters = null,
                recordedAt = 10L, createdAt = 10L, syncStatus = SyncStatus.PENDING.name
            )
        )

        processor.process(
            syncOperation(
                "op-1",
                entityType = SyncEntityTypes.LOCATION_POINT,
                entityId = "loc-1",
                operationType = SyncOperationTypes.LOCATION_POINT_CREATED
            )
        )

        val point = transport.locations.single().single()
        assertNull(point.speedMetersPerSecond)
        assertNull(point.bearingDegrees)
        assertNull(point.altitudeMeters)
        assertNull(point.jobId)
    }

    @Test
    fun `a location point is only marked synced once acknowledged`() = runTest {
        database.locationPointDao().insert(
            LocationPointEntity(
                "loc-1", "DRV-1", "s1", null, 1.0, 2.0, 5f, null, null, null,
                10L, 10L, SyncStatus.PENDING.name
            )
        )
        transport.willReturn(SyncOutcome.Retryable("Network unavailable"))

        processor.process(
            syncOperation(
                "op-1",
                entityType = SyncEntityTypes.LOCATION_POINT,
                entityId = "loc-1",
                operationType = SyncOperationTypes.LOCATION_POINT_CREATED
            )
        )

        assertEquals(
            SyncStatus.PENDING.name,
            database.locationPointDao().getById("loc-1")!!.syncStatus
        )
    }

    // --- evidence ----------------------------------------------------------------------------

    private suspend fun insertEvidence(
        id: String = "ev-1",
        file: File?,
        type: String = "DELIVERY_SIGNATURE",
        status: EvidenceStatus = EvidenceStatus.SAVED_LOCAL,
        latitude: Double? = null
    ) = database.evidenceDao().insert(
        EvidenceEntity(
            id = id,
            jobId = "job-1",
            type = type,
            localUri = file?.toURI()?.toString(),
            status = status.name,
            createdAt = 500L,
            driverId = "DRV-1",
            shiftId = "s1",
            signerName = "A. Recipient",
            notes = "Left at reception",
            fileSizeBytes = file?.length(),
            savedAt = 600L,
            latitude = latitude,
            longitude = latitude?.let { 144.0 },
            locationAccuracyMeters = latitude?.let { 6f },
            locationRecordedAt = latitude?.let { 450L }
        )
    )

    private fun evidenceOperation(id: String = "op-1", entityId: String = "ev-1") = syncOperation(
        id = id,
        entityType = SyncEntityTypes.EVIDENCE,
        entityId = entityId,
        operationType = SyncOperationTypes.UPSERT,
        payloadJson = """{"jobId":"job-1","type":"DELIVERY_SIGNATURE"}"""
    )

    @Test
    fun `evidence metadata is sent with the file and only marked synced on acknowledgement`() = runTest {
        val file = evidenceFile("signature.png", ByteArray(64) { 1 })
        insertEvidence(file = file, latitude = -37.8)

        val outcome = processor.process(evidenceOperation())

        assertEquals(SyncOutcome.Success, outcome)
        val metadata = transport.evidenceMetadata.single()
        assertEquals("ev-1", metadata.evidenceId)
        assertEquals("job-1", metadata.jobId)
        assertEquals("DRV-1", metadata.driverId)
        assertEquals("s1", metadata.shiftId)
        assertEquals("A. Recipient", metadata.signerName)
        assertEquals("Left at reception", metadata.notes)
        assertEquals("image/png", metadata.mimeType)
        assertEquals(64L, metadata.fileSizeBytes)
        assertEquals(-37.8, metadata.latitude!!, 0.0001)
        assertEquals(
            EvidenceStatus.SYNCED.name,
            database.evidenceDao().getById("ev-1")!!.status
        )
    }

    @Test
    fun `missing GPS is left absent rather than fabricated`() = runTest {
        val file = evidenceFile("photo.jpg", ByteArray(16))
        insertEvidence(file = file, type = "DELIVERY_PHOTO", latitude = null)

        processor.process(evidenceOperation())

        val metadata = transport.evidenceMetadata.single()
        assertNull(metadata.latitude)
        assertNull(metadata.longitude)
        assertNull(metadata.locationAccuracyMeters)
        assertNull(metadata.locationRecordedAt)
        assertEquals("image/jpeg", metadata.mimeType)
    }

    @Test
    fun `a missing evidence file fails safely without uploading`() = runTest {
        val file = evidenceFile("gone.png", ByteArray(8))
        insertEvidence(file = file)
        assertTrue(file.delete())

        val outcome = processor.process(evidenceOperation())

        assertTrue(outcome is SyncOutcome.Permanent)
        assertTrue("Nothing may be uploaded", transport.evidenceUploads.isEmpty())
        assertEquals(
            "The local record must survive",
            EvidenceStatus.SAVED_LOCAL.name,
            database.evidenceDao().getById("ev-1")!!.status
        )
    }

    @Test
    fun `a failed upload keeps the signature file and does not mark it synced`() = runTest {
        val file = evidenceFile("signature.png", ByteArray(32))
        insertEvidence(file = file)
        transport.willReturn(SyncOutcome.Retryable("TMS request timed out"))

        val outcome = processor.process(evidenceOperation())

        assertTrue(outcome is SyncOutcome.Retryable)
        assertTrue("The signature must stay on disk", file.exists())
        assertEquals(32L, file.length())
        assertEquals(
            EvidenceStatus.SAVED_LOCAL.name,
            database.evidenceDao().getById("ev-1")!!.status
        )
    }

    @Test
    fun `signature evidence is read from app-private storage and streamed, not inlined`() = runTest {
        val file = evidenceFile("signature.png", ByteArray(128))
        insertEvidence(file = file)

        processor.process(evidenceOperation())

        val upload = transport.evidenceUploads.single()
        assertEquals(file.absolutePath, upload.file.absolutePath)
        assertEquals("image/png", upload.mimeType)
        // The transport is handed a File handle, never the bytes.
        assertNotNull(upload.file)
    }

    @Test
    fun `evidence with no stored file fails permanently`() = runTest {
        insertEvidence(file = null)

        val outcome = processor.process(evidenceOperation())

        assertTrue(outcome is SyncOutcome.Permanent)
        assertTrue(transport.evidenceUploads.isEmpty())
    }

    // --- freight exceptions -------------------------------------------------------------------

    private suspend fun insertException(reason: String) = database.freightExceptionDao().insert(
        FreightExceptionEntity(
            id = "fx-1", jobId = "job-1", stage = "DELIVERY", reason = reason,
            notes = "Corner crushed", driverId = "DRV-1", shiftId = "s1",
            resolved = false, createdAt = 800L, status = EvidenceStatus.SAVED_LOCAL.name
        )
    )

    private fun exceptionOperation() = syncOperation(
        "op-1",
        entityType = SyncEntityTypes.FREIGHT_EXCEPTION,
        entityId = "fx-1",
        operationType = SyncOperationTypes.UPSERT
    )

    @Test
    fun `a damage claim waits for its photo instead of referencing an unsent upload`() = runTest {
        insertException("DAMAGED")
        val file = evidenceFile("damage.jpg", ByteArray(16))
        insertEvidence(file = file, type = "DELIVERY_PHOTO", status = EvidenceStatus.SAVED_LOCAL)

        val outcome = processor.process(exceptionOperation())

        assertTrue(outcome is SyncOutcome.Deferred)
        assertTrue("Nothing may be sent yet", transport.exceptions.isEmpty())
        assertEquals(
            EvidenceStatus.SAVED_LOCAL.name,
            database.freightExceptionDao().getById("fx-1")!!.status
        )
    }

    @Test
    fun `a damage claim sends once its photo has been acknowledged`() = runTest {
        insertException("DAMAGED")
        val file = evidenceFile("damage.jpg", ByteArray(16))
        insertEvidence(file = file, type = "DELIVERY_PHOTO", status = EvidenceStatus.SYNCED)

        val outcome = processor.process(exceptionOperation())

        assertEquals(SyncOutcome.Success, outcome)
        val sent = transport.exceptions.single()
        assertEquals(listOf("ev-1"), sent.evidenceIds)
        assertEquals("Corner crushed", sent.notes)
        assertEquals(800L, sent.createdAt)
        assertEquals(
            EvidenceStatus.SYNCED.name,
            database.freightExceptionDao().getById("fx-1")!!.status
        )
    }

    @Test
    fun `an exception that needs no photo is not blocked by pending evidence`() = runTest {
        insertException("CUSTOMER_UNAVAILABLE")
        val file = evidenceFile("photo.jpg", ByteArray(16))
        insertEvidence(file = file, type = "DELIVERY_PHOTO", status = EvidenceStatus.SAVED_LOCAL)

        val outcome = processor.process(exceptionOperation())

        assertEquals(SyncOutcome.Success, outcome)
        assertTrue(
            "It must not claim evidence the TMS has never seen",
            transport.exceptions.single().evidenceIds.isEmpty()
        )
    }

    @Test
    fun `a rejected exception is not marked synced`() = runTest {
        insertException("REJECTED")
        transport.willReturn(SyncOutcome.Permanent("HTTP 400 rejected by TMS"))

        processor.process(exceptionOperation())

        assertEquals(
            EvidenceStatus.SAVED_LOCAL.name,
            database.freightExceptionDao().getById("fx-1")!!.status
        )
    }

    // --- shift and inspection ------------------------------------------------------------------

    @Test
    fun `a shift event carries the persisted shift, not a guess`() = runTest {
        database.shiftDao().insert(
            ShiftEntity("s1", "DRV-1", "TRK-01", "TRL-9", 12_000L, 12_450L, "OFF_DUTY", 10L, 500L, 9_000L)
        )

        val outcome = processor.process(
            syncOperation(
                "op-1",
                entityType = SyncEntityTypes.SHIFT,
                entityId = "s1",
                operationType = SyncOperationTypes.END,
                createdAt = 9_000L
            )
        )

        assertEquals(SyncOutcome.Success, outcome)
        val sent = transport.shiftEvents.single()
        assertEquals("END", sent.event)
        assertEquals("TRK-01", sent.vehicleId)
        assertEquals("TRL-9", sent.trailerId)
        assertEquals(12_000L, sent.startOdometer)
        assertEquals(12_450L, sent.endOdometer)
        assertEquals(9_000L, sent.occurredAt)
    }

    @Test
    fun `a shift removed from the device does not crash the run`() = runTest {
        val outcome = processor.process(
            syncOperation(
                "op-1",
                entityType = SyncEntityTypes.SHIFT,
                entityId = "ghost",
                operationType = SyncOperationTypes.START
            )
        )

        assertTrue(outcome is SyncOutcome.Permanent)
        assertFalse(transport.shiftEvents.isNotEmpty())
    }
}
