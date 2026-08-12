package com.example.viewmodel

import com.example.domain.model.AuthFailure
import com.example.domain.model.AuthenticatedDriver
import com.example.domain.model.DriverSession
import com.example.domain.model.ShiftRecord
import com.example.domain.model.SyncOperation
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.DriverRepository
import com.example.domain.repository.JobRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.repository.ShiftRepository
import com.example.domain.repository.SyncRepository
import com.example.model.Driver
import com.example.model.Job
import com.example.model.JobStatus
import com.example.model.ShiftStatus
import com.example.testing.FakeSessionRepository
import com.example.testing.testJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val session = FakeSessionRepository()
    private val auth = FakeAuthRepository()
    private val jobs = FakeJobRepository()
    private val sync = FakeSyncRepository()
    private var bootstrapCalls = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(sessionRepository: SessionRepository = session) = AppViewModel(
        authRepository = auth,
        sessionRepository = sessionRepository,
        driverRepository = FakeDriverRepository(),
        jobRepository = jobs,
        shiftRepository = FakeShiftRepository(),
        syncRepository = sync,
        bootstrap = {
            bootstrapCalls++
            Result.success(Unit)
        },
        appVersionName = "1.0"
    )

    @Test
    fun correctCredentialsSignTheDriverIn() = runTest {
        val model = viewModel()

        model.login("DRV-8492", "1234")

        assertTrue(model.uiState.value.isLoggedIn)
        assertEquals("DRV-8492", model.uiState.value.driver?.id)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun incorrectPinSurfacesTheGenericMessage() = runTest {
        val model = viewModel()

        model.login("DRV-8492", "0000")

        assertFalse(model.uiState.value.isLoggedIn)
        assertEquals("Incorrect Driver ID or PIN", model.uiState.value.error)
    }

    @Test
    fun blankCredentialsAreRejected() = runTest {
        val model = viewModel()

        model.login("", "")

        assertFalse(model.uiState.value.isLoggedIn)
        assertEquals("Driver ID and PIN are required", model.uiState.value.error)
    }

    @Test
    fun aStoredSessionIsRestoredWithoutSigningInAgain() = runTest {
        session.seed(
            DriverSession(
                driverId = "DRV-8492",
                name = "James Miller",
                email = "james.miller@firstclassexpress.com.au",
                phone = null,
                authenticatedAt = 10L
            )
        )

        val model = viewModel()

        assertTrue(model.uiState.value.isLoggedIn)
        assertFalse(model.uiState.value.isRestoringSession)
        assertEquals("James Miller", model.uiState.value.driver?.name)
    }

    @Test
    fun sessionRestoreFailureExitsLoadingAndAllowsSignIn() = runTest {
        val failingSession = object : SessionRepository by session {
            override fun observeSession(): Flow<DriverSession?> = flow {
                throw IllegalStateException("Stored session could not be read")
            }
        }
        val model = viewModel(failingSession)

        assertFalse(model.uiState.value.isRestoringSession)
        assertFalse(model.uiState.value.isLoggedIn)
        assertEquals(
            "Unable to restore the driver session. Please sign in again.",
            model.uiState.value.error
        )

        model.login("DRV-8492", "1234")

        assertTrue(model.uiState.value.isLoggedIn)
        assertEquals("DRV-8492", model.uiState.value.driver?.id)
    }

    @Test
    fun logoutClearsTheSessionOnly() = runTest {
        val model = viewModel()
        model.login("DRV-8492", "1234")

        model.logout()

        assertFalse(model.uiState.value.isLoggedIn)
        assertNull(model.uiState.value.driver)
        assertEquals(1, session.clearCount)
        // Operational data is untouched by signing out.
        assertEquals(1, model.uiState.value.jobs.size)
        assertEquals(2, model.uiState.value.pendingSyncCount)
    }

    @Test
    fun signingInPreparesLocalDataFirst() = runTest {
        val model = viewModel()
        val callsAfterInit = bootstrapCalls

        model.login("DRV-8492", "1234")

        assertTrue(bootstrapCalls > callsAfterInit)
    }

    private class FakeAuthRepository : AuthRepository {
        override suspend fun authenticate(
            loginId: String,
            pin: String
        ): Result<AuthenticatedDriver> {
            if (loginId.isBlank() || pin.isBlank()) {
                return Result.failure(AuthFailure.MissingFields)
            }
            if (loginId.trim().lowercase() != "drv-8492" || pin != "1234") {
                return Result.failure(AuthFailure.InvalidCredentials)
            }
            return Result.success(
                AuthenticatedDriver(
                    driverId = "DRV-8492",
                    name = "James Miller",
                    email = "james.miller@firstclassexpress.com.au",
                    phone = null
                )
            )
        }
    }

    private class FakeDriverRepository : DriverRepository {
        override suspend fun getPrototypeDriver(): Driver? = null
        override suspend fun getDriver(id: String): Driver? = Driver(
            id = id,
            name = "James Miller",
            email = "james.miller@firstclassexpress.com.au",
            shiftStatus = ShiftStatus.OFF_DUTY
        )
    }

    private class FakeJobRepository : JobRepository {
        private val flow = MutableStateFlow(listOf(testJob()))
        override fun observeJobs(): Flow<List<Job>> = flow
        override suspend fun getJob(id: String): Job? = flow.value.find { it.id == id }
        override suspend fun transition(id: String, to: JobStatus): Result<JobStatus> =
            Result.success(to)
    }

    private class FakeShiftRepository : ShiftRepository {
        override fun observeCurrentShift(): Flow<ShiftRecord?> = MutableStateFlow(null)
        override suspend fun createPreStartDraft(
            driverId: String,
            vehicleId: String,
            trailerId: String?,
            startOdometer: Long
        ): Result<String> = Result.success("shift-1")

        override suspend fun markReadyToStart(shiftId: String): Result<Unit> = Result.success(Unit)
        override suspend fun activateShift(shiftId: String): Result<Unit> = Result.success(Unit)
        override suspend fun endShift(shiftId: String, endOdometer: Long): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeSyncRepository : SyncRepository {
        private val flow = MutableStateFlow(
            listOf(operation("op-1"), operation("op-2"))
        )

        override fun observePending(): Flow<List<SyncOperation>> = flow
        override suspend fun enqueue(
            entityType: String,
            entityId: String,
            operationType: String,
            payloadJson: String
        ): Result<String> = Result.success("op")

        override suspend fun markFailure(id: String, error: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun markSynced(id: String): Result<Unit> = Result.success(Unit)

        private companion object {
            fun operation(id: String) = SyncOperation(
                id = id,
                entityType = "JOB",
                entityId = "job-1",
                operationType = "STATUS_CHANGE",
                payloadJson = "{}",
                createdAt = 1L,
                retryCount = 0,
                lastError = null,
                status = com.example.domain.model.SyncStatus.PENDING
            )
        }
    }
}
