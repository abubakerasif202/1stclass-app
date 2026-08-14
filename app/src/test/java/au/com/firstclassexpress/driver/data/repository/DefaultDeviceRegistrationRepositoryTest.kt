package au.com.firstclassexpress.driver.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import au.com.firstclassexpress.driver.data.remote.api.DeviceRegistrationApi
import au.com.firstclassexpress.driver.data.remote.api.DeviceRegistrationRequest
import au.com.firstclassexpress.driver.data.remote.api.PushTokenUpdateRequest
import au.com.firstclassexpress.driver.push.PushTokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultDeviceRegistrationRepositoryTest {
    @Test
    fun nonSuccessfulRegistrationDoesNotReportSuccessOrPersistToken() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PushTokenStore(context).also { it.clearForTests() }
        val repository = DefaultDeviceRegistrationRepository(
            context = context,
            apiProvider = { FakeApi(Response.error(503, okhttp3.ResponseBody.create(null, "offline"))) },
            tokenStore = store
        )

        val result = repository.registerDevice("device-1", "driver-1", "1.0.0", "token-1")

        assertFalse(result.isSuccess)
        assertNull(repository.getRegisteredPushToken())
        assertNull(store.pendingToken())
    }

    @Test
    fun missingApiIsAnExplicitFailure() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DefaultDeviceRegistrationRepository(context, { null })

        assertFalse(repository.registerDevice("device-1", "driver-1", "1.0.0", null).isSuccess)
    }

    private class FakeApi(private val response: Response<Unit>) : DeviceRegistrationApi {
        override suspend fun registerDevice(request: DeviceRegistrationRequest): Response<Unit> = response
        override suspend fun updatePushToken(request: PushTokenUpdateRequest): Response<Unit> = response
    }
}
