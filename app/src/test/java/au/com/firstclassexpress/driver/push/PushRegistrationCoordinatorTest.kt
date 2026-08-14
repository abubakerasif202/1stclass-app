package au.com.firstclassexpress.driver.push

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import au.com.firstclassexpress.driver.data.remote.api.DeviceRegistrationApi
import au.com.firstclassexpress.driver.domain.model.DriverSession
import au.com.firstclassexpress.driver.domain.repository.DeviceRegistrationRepository
import au.com.firstclassexpress.driver.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushRegistrationCoordinatorTest {
    private lateinit var store: PushTokenStore

    @Before
    fun setUp() {
        store = PushTokenStore(ApplicationProvider.getApplicationContext<Context>())
        store.clearForTests()
    }

    @Test
    fun authenticatedSessionRegistersPendingTokenAndClearsPendingState() = runTest {
        val repository = FakeDeviceRegistrationRepository()
        val coordinator = coordinator(repository, token = "token-new")

        val result = coordinator.registerIfAuthenticated()

        assertFalse(result.isFailure)
        assertEquals("token-new", repository.registeredToken)
        assertNull(store.pendingToken())
        assertEquals("token-new", store.registeredToken())
    }

    @Test
    fun refreshUpdatesExistingRegistration() = runTest {
        val repository = FakeDeviceRegistrationRepository().apply { registeredToken = "token-old" }
        store.markRegistered("token-old")
        val coordinator = coordinator(repository, token = "token-new")

        coordinator.registerIfAuthenticated()

        assertEquals("token-new", repository.updatedToken)
        assertEquals("token-new", store.registeredToken())
    }

    @Test
    fun failedRegistrationLeavesPendingTokenForRetry() = runTest {
        val repository = FakeDeviceRegistrationRepository().apply { failure = true }
        val coordinator = coordinator(repository, token = "token-pending")

        val result = coordinator.registerIfAuthenticated()

        assertTrue(result.isFailure)
        assertEquals("token-pending", store.pendingToken())
        assertNull(store.registeredToken())
    }

    @Test
    fun unauthenticatedTokenIsStoredButNeverRegistered() = runTest {
        val repository = FakeDeviceRegistrationRepository()
        val coordinator = coordinator(repository, token = "token-1", session = null)

        coordinator.registerIfAuthenticated()

        assertEquals(0, repository.registerCalls)
        assertNull(store.pendingToken())
    }

    private fun coordinator(
        repository: FakeDeviceRegistrationRepository,
        token: String,
        session: DriverSession? = DriverSession("driver-1", "Driver", "driver@example.com", null, 1L)
    ) = PushRegistrationCoordinator(
        sessionRepository = FakeSessionRepository(session),
        deviceRegistrationRepository = repository,
        tokenStore = store,
        tokenProvider = { Result.success(token) },
        appVersionName = "1.0.0",
        deviceIdProvider = { "device-1" }
    )

    private class FakeSessionRepository(session: DriverSession?) : SessionRepository {
        private val state = MutableStateFlow(session)
        override fun observeSession(): Flow<DriverSession?> = state
        override suspend fun currentSession(): DriverSession? = state.value
        override suspend fun startSession(driver: au.com.firstclassexpress.driver.domain.model.AuthenticatedDriver) =
            error("not used")
        override suspend fun clearSession() = Result.success(Unit)
    }

    private class FakeDeviceRegistrationRepository : DeviceRegistrationRepository {
        var registeredToken: String? = null
        var updatedToken: String? = null
        var registerCalls = 0
        var failure = false

        override suspend fun registerDevice(
            deviceId: String,
            driverId: String,
            appVersion: String,
            pushToken: String?
        ): Result<Unit> {
            registerCalls++
            if (failure) return Result.failure(IllegalStateException("offline"))
            registeredToken = pushToken
            return Result.success(Unit)
        }

        override suspend fun updatePushToken(deviceId: String, driverId: String, pushToken: String): Result<Unit> {
            if (failure) return Result.failure(IllegalStateException("offline"))
            updatedToken = pushToken
            return Result.success(Unit)
        }

        override fun getRegisteredPushToken(): String? = registeredToken
        override fun savePushToken(token: String) { registeredToken = token }
    }
}
