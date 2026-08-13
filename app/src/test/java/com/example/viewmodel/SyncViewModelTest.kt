package com.example.viewmodel

import com.example.data.remote.ConnectivityRepository
import com.example.data.remote.TmsEnvironment
import com.example.domain.model.SyncOperation
import com.example.domain.repository.SyncRepository
import com.example.domain.sync.SyncQueueCounts
import com.example.domain.sync.SyncStatusSummary
import com.example.testing.FakeSyncQueue
import com.example.testing.syncOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    private val operations = MutableStateFlow<List<SyncOperation>>(emptyList())
    private val online = MutableStateFlow(true)

    private val syncRepository = object : SyncRepository {
        override fun observePending(): Flow<List<SyncOperation>> = operations
        override fun observeCounts(): Flow<SyncQueueCounts> =
            operations.map { SyncQueueCounts(pending = it.size) }

        override suspend fun enqueue(
            entityType: String,
            entityId: String,
            operationType: String,
            payloadJson: String
        ): Result<String> = Result.success("op")

        override suspend fun markFailure(id: String, error: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun markSynced(id: String): Result<Unit> = Result.success(Unit)
    }

    private val connectivity = object : ConnectivityRepository {
        override fun observeOnline(): Flow<Boolean> = online
        override fun isOnline(): Boolean = online.value
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        environment: TmsEnvironment = TmsEnvironment.Configured("https://tms.example.com/"),
        queue: FakeSyncQueue = FakeSyncQueue(),
        onRequestSync: () -> Unit = {}
    ) = SyncViewModel(
        syncRepository = syncRepository,
        syncQueue = queue,
        connectivityRepository = connectivity,
        environment = environment,
        requestSync = onRequestSync,
        isOnlineNow = { online.value }
    )

    @Test
    fun `queued work is surfaced as a waiting count`() = runTest {
        operations.value = listOf(syncOperation("op-1"), syncOperation("op-2"))

        val state = viewModel().uiState.value

        assertTrue(state.summary is SyncStatusSummary.Waiting)
        assertEquals(2, state.counts.pending)
    }

    @Test
    fun `sync now schedules background work rather than networking inline`() = runTest {
        var scheduled = 0
        val model = viewModel(onRequestSync = { scheduled++ })

        model.syncNow()

        assertEquals(1, scheduled)
        assertEquals("Sync requested.", model.uiState.value.message)
    }

    @Test
    fun `sync now while offline reassures the driver and schedules nothing`() = runTest {
        online.value = false
        var scheduled = 0
        val model = viewModel(onRequestSync = { scheduled++ })

        model.syncNow()

        assertEquals(0, scheduled)
        assertEquals(
            "No internet connection. Changes remain saved on this device.",
            model.uiState.value.message
        )
    }

    @Test
    fun `sync now with no TMS configured says so instead of pretending`() = runTest {
        var scheduled = 0
        val model = viewModel(
            environment = TmsEnvironment.NotConfigured,
            onRequestSync = { scheduled++ }
        )

        model.syncNow()

        assertEquals(0, scheduled)
        assertTrue(model.uiState.value.message!!.contains("not available"))
        assertTrue(model.uiState.value.summary is SyncStatusSummary.RemoteUnavailable)
    }

    @Test
    fun `retrying failed work requeues it and asks for a run`() = runTest {
        val queue = FakeSyncQueue(
            listOf(
                syncOperation("op-1", status = com.example.domain.model.SyncStatus.FAILED),
                syncOperation("op-2", status = com.example.domain.model.SyncStatus.FAILED)
            )
        )
        var scheduled = 0
        val model = viewModel(queue = queue, onRequestSync = { scheduled++ })

        model.retryFailed()

        assertEquals(2, queue.requeuedFailedCount)
        assertEquals(1, scheduled)
        assertEquals("2 operations queued to retry.", model.uiState.value.message)
    }

    @Test
    fun `retrying with nothing failed says so plainly`() = runTest {
        val model = viewModel(queue = FakeSyncQueue())

        model.retryFailed()

        assertEquals("There is nothing to retry.", model.uiState.value.message)
    }
}
