package com.example.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.FirstClassExpressApplication
import com.example.domain.model.SyncStatus
import com.example.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end through the real worker, real container and real Room database, with no TMS
 * configured — which is exactly how the app ships today.
 *
 * The assertion that matters: the queue is untouched. A worker that ran and found nowhere to send
 * anything must not invent an acknowledgement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TmsSyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
    }

    @Test
    fun `with no TMS configured the worker completes and leaves everything pending`() = runTest {
        val container = (context as FirstClassExpressApplication).container
        val dao = container.database.syncOperationDao()
        dao.insert(
            SyncOperationEntity(
                id = "op-1",
                entityType = "JOB",
                entityId = "job-1",
                operationType = "STATUS_CHANGE",
                payloadJson = """{"from":"ASSIGNED","to":"IN_PROGRESS"}""",
                createdAt = 1_000L,
                retryCount = 0,
                lastError = null,
                status = SyncStatus.PENDING.name
            )
        )

        val result = TestListenableWorkerBuilder<TmsSyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val operation = dao.getById("op-1")!!
        assertEquals(
            "Nothing may be marked synced without a server acknowledgement",
            SyncStatus.PENDING.name,
            operation.status
        )
        assertEquals("No attempt was made, so none is recorded", 0, operation.retryCount)
    }

    @Test
    fun `the worker survives an empty queue`() = runTest {
        val result = TestListenableWorkerBuilder<TmsSyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `the queue is read from the database, not from worker memory`() = runTest {
        val container = (context as FirstClassExpressApplication).container
        val dao = container.database.syncOperationDao()
        dao.insert(
            SyncOperationEntity(
                "op-restart", "LOCATION_POINT", "loc-1", "LOCATION_POINT_CREATED",
                "{}", 1_000L, 0, null, SyncStatus.PENDING.name
            )
        )

        // A brand new worker instance — as after process death — still sees the queued work.
        TestListenableWorkerBuilder<TmsSyncWorker>(context).build().doWork()
        val freshWorkerResult = TestListenableWorkerBuilder<TmsSyncWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), freshWorkerResult)
        assertEquals(SyncStatus.PENDING.name, dao.getById("op-restart")!!.status)
    }
}
