package au.com.firstclassexpress.driver.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import au.com.firstclassexpress.driver.domain.model.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Queue mechanics against real SQLite — the claim guard is the part that must not be wrong. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomSyncQueueTest {
    private lateinit var database: AppDatabase
    private var now = 1_000_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private fun queue() = RoomSyncQueue(database) { now }

    private suspend fun insert(
        id: String,
        createdAt: Long = 10L,
        status: SyncStatus = SyncStatus.PENDING,
        updatedAt: Long = 0L,
        retryCount: Int = 0
    ) = database.syncOperationDao().insert(
        SyncOperationEntity(
            id = id,
            entityType = "JOB",
            entityId = "job-1",
            operationType = "STATUS_CHANGE",
            payloadJson = "{}",
            createdAt = createdAt,
            retryCount = retryCount,
            lastError = null,
            status = status.name,
            updatedAt = updatedAt
        )
    )

    @Test
    fun `claims the oldest pending operation first`() = runTest {
        insert("newer", createdAt = 200L)
        insert("older", createdAt = 100L)

        assertEquals("older", queue().claimNext()!!.id)
    }

    @Test
    fun `a claimed operation is not handed out again`() = runTest {
        insert("op-1")
        val queue = queue()

        assertEquals("op-1", queue.claimNext()!!.id)
        assertNull("A second claim must not return the in-flight operation", queue.claimNext())
        assertEquals(
            SyncStatus.IN_PROGRESS.name,
            database.syncOperationDao().getById("op-1")!!.status
        )
    }

    @Test
    fun `two competing claims cannot both win the same operation`() = runTest {
        insert("op-1")
        val dao = database.syncOperationDao()

        // Both callers see the same candidate; only the guarded UPDATE decides the winner.
        val candidate = dao.nextPending()!!
        val first = dao.claim(candidate.id, now)
        val second = dao.claim(candidate.id, now)

        assertEquals("Exactly one claim may succeed", 1, first)
        assertEquals(0, second)
    }

    @Test
    fun `an operation abandoned in progress is recovered and keeps its id`() = runTest {
        insert("op-1", status = SyncStatus.IN_PROGRESS, updatedAt = 1L)

        val released = queue().releaseStale(staleBefore = now - 1000L)

        assertEquals(1, released)
        val recovered = database.syncOperationDao().getById("op-1")!!
        assertEquals(SyncStatus.PENDING.name, recovered.status)
        assertEquals("The idempotency key must survive recovery", "op-1", recovered.id)
    }

    @Test
    fun `an operation still within its lease is left alone`() = runTest {
        insert("op-1", status = SyncStatus.IN_PROGRESS, updatedAt = now)

        assertEquals(0, queue().releaseStale(staleBefore = now - 1000L))
        assertEquals(
            SyncStatus.IN_PROGRESS.name,
            database.syncOperationDao().getById("op-1")!!.status
        )
    }

    @Test
    fun `a failed operation is preserved with its error and can be requeued`() = runTest {
        insert("op-1")
        val queue = queue()
        queue.claimNext()

        queue.markFailed("op-1", retryCount = 1, error = "HTTP 400 rejected by TMS")

        val failed = database.syncOperationDao().getById("op-1")!!
        assertEquals(SyncStatus.FAILED.name, failed.status)
        assertEquals("HTTP 400 rejected by TMS", failed.lastError)

        assertEquals(1, queue.requeueFailed())
        assertEquals(SyncStatus.PENDING.name, database.syncOperationDao().getById("op-1")!!.status)
    }

    @Test
    fun `retryable operations keep their row and accumulate attempts`() = runTest {
        insert("op-1")
        val queue = queue()
        queue.claimNext()

        queue.markRetryable("op-1", retryCount = 3, error = "Network unavailable")

        val row = database.syncOperationDao().getById("op-1")!!
        assertEquals(SyncStatus.PENDING.name, row.status)
        assertEquals(3, row.retryCount)
        assertNotNull(row.lastError)
    }

    @Test
    fun `counts report each queue state separately`() = runTest {
        insert("a")
        insert("b")
        insert("c", status = SyncStatus.FAILED)
        insert("d", status = SyncStatus.SYNCED)

        val counts = queue().counts()

        assertEquals(2, counts.pending)
        assertEquals(1, counts.failed)
        assertEquals(1, counts.synced)
        assertEquals("Synced work is not outstanding", 3, counts.outstanding)
    }

    @Test
    fun `error summaries are truncated so the diagnostics screen stays readable`() = runTest {
        insert("op-1")
        val queue = queue()
        queue.claimNext()

        queue.markFailed("op-1", retryCount = 1, error = "x".repeat(5_000))

        assertEquals(200, database.syncOperationDao().getById("op-1")!!.lastError!!.length)
    }
}
