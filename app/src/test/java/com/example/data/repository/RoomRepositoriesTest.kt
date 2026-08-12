package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.JobPayloadCodec
import com.example.data.local.entity.JobEntity
import com.example.data.seed.PrototypeSeedData
import com.example.domain.model.EvidenceCaptureRequest
import com.example.domain.model.EvidenceStatus
import com.example.domain.model.EvidenceType
import com.example.domain.model.ShiftPhase
import com.example.model.Job
import com.example.model.JobStatus
import com.example.model.Location
import com.example.model.Priority
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomRepositoriesTest {
    private lateinit var database: AppDatabase
    private lateinit var codec: JobPayloadCodec
    private var nextId = 0
    private val clock = { 1_000L }
    private val idGenerator = { "generated-${nextId++}" }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        codec = JobPayloadCodec()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun preStartDraftIsNotOnDuty() = runTest {
        val inspectionRepository = RoomInspectionRepository(database, clock, idGenerator)
        val shiftRepository = RoomShiftRepository(database, inspectionRepository, clock, idGenerator)

        val id = shiftRepository.createPreStartDraft("d1", "TRK-01", null, 1000L).getOrThrow()

        assertEquals(ShiftPhase.PRESTART_REQUIRED.name, database.shiftDao().getById(id)!!.phase)
    }

    @Test
    fun unassignedJobTransitionIsRejected() = runTest {
        val job = sampleJob("unassigned-job", JobStatus.UNASSIGNED)
        database.jobDao().insertAll(listOf(JobEntity(job.id, codec.encode(job), job.status.name, clock())))
        val repository = RoomJobRepository(database, codec, clock, idGenerator)

        val result = repository.transition(job.id, JobStatus.IN_PROGRESS)

        assertTrue(result.isFailure)
        assertEquals(JobStatus.UNASSIGNED.name, database.jobDao().getById(job.id)!!.status)
    }

    @Test
    fun validJobTransitionQueuesExactlyOneOperation() = runTest {
        val job = sampleJob("assigned-job", JobStatus.ASSIGNED)
        database.jobDao().insertAll(listOf(JobEntity(job.id, codec.encode(job), job.status.name, clock())))
        val repository = RoomJobRepository(database, codec, clock, idGenerator)

        repository.transition(job.id, JobStatus.IN_PROGRESS).getOrThrow()

        assertEquals(JobStatus.IN_PROGRESS.name, database.jobDao().getById(job.id)!!.status)
        assertEquals(1, database.syncOperationDao().pendingCountFor("JOB", job.id, "STATUS_CHANGE"))
    }

    @Test
    fun pendingEvidenceDoesNotBecomeSavedWithoutUri() = runTest {
        val repository = RoomEvidenceRepository(database, clock, idGenerator)

        val id = repository.createPending(
            EvidenceCaptureRequest("j1", EvidenceType.PICKUP_PHOTO, "DRV-8492")
        ).getOrThrow()

        assertEquals(EvidenceStatus.PENDING_CAPTURE.name, database.evidenceDao().getById(id)!!.status)
    }

    @Test
    fun secondSeedDoesNotOverwriteChangedJobStatus() = runTest {
        val seed = PrototypeSeedData(database, codec, clock)
        seed.seedIfEmpty()
        val initialCount = database.jobDao().count()
        val seeded = seed.jobs.first { it.status == JobStatus.ASSIGNED }
        val repository = RoomJobRepository(database, codec, clock, idGenerator)

        repository.transition(seeded.id, JobStatus.IN_PROGRESS).getOrThrow()
        seed.seedIfEmpty()

        assertEquals(initialCount, database.jobDao().count())
        assertEquals(JobStatus.IN_PROGRESS.name, database.jobDao().getById(seeded.id)!!.status)
    }

    private fun sampleJob(id: String, status: JobStatus): Job = Job(
        id = id,
        reference = "REF-$id",
        status = status,
        pickup = Location(
            address = "1 Pickup Road",
            suburb = "Sydney",
            lat = -33.8688,
            lng = 151.2093,
            companyName = "Pickup",
            contactName = "Pickup Contact",
            contactPhone = "0000"
        ),
        delivery = Location(
            address = "2 Delivery Road",
            suburb = "Sydney",
            lat = -33.8700,
            lng = 151.2100,
            companyName = "Delivery",
            contactName = "Delivery Contact",
            contactPhone = "0000"
        ),
        pickupWindowStart = "08:00",
        pickupWindowEnd = "09:00",
        deliveryWindowStart = "10:00",
        deliveryWindowEnd = "11:00",
        freightDescription = "Test freight",
        itemCount = 1,
        priority = Priority.NORMAL
    )
}
