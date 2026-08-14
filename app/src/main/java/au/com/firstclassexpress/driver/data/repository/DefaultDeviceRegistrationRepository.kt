package au.com.firstclassexpress.driver.data.repository

import android.content.Context
import android.content.SharedPreferences
import au.com.firstclassexpress.driver.data.remote.api.DeviceRegistrationApi
import au.com.firstclassexpress.driver.data.remote.api.DeviceRegistrationRequest
import au.com.firstclassexpress.driver.data.remote.api.PushTokenUpdateRequest
import au.com.firstclassexpress.driver.domain.repository.DeviceRegistrationRepository
import au.com.firstclassexpress.driver.push.PushTokenStore

class DefaultDeviceRegistrationRepository(
    private val context: Context,
    private val apiProvider: () -> DeviceRegistrationApi?,
    private val tokenStore: PushTokenStore = PushTokenStore(context)
) : DeviceRegistrationRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("device_registration_prefs", Context.MODE_PRIVATE)
    }

    override suspend fun registerDevice(
        deviceId: String,
        driverId: String,
        appVersion: String,
        pushToken: String?
    ): Result<Unit> = runCatching {
        val api = apiProvider() ?: throw DeviceRegistrationException("Device registration is not configured")
        val response = api.registerDevice(
            DeviceRegistrationRequest(
                deviceId = deviceId,
                driverId = driverId,
                appVersion = appVersion,
                pushToken = pushToken
            )
        )
        if (!response.isSuccessful) {
            throw DeviceRegistrationException("Device registration HTTP failed: ${response.code()}")
        }
        pushToken?.let { tokenStore.markRegistered(it) }
    }

    override suspend fun updatePushToken(
        deviceId: String,
        driverId: String,
        pushToken: String
    ): Result<Unit> = runCatching {
        val api = apiProvider() ?: throw DeviceRegistrationException("Push-token update is not configured")
        val response = api.updatePushToken(
            PushTokenUpdateRequest(
                deviceId = deviceId,
                driverId = driverId,
                pushToken = pushToken
            )
        )
        if (!response.isSuccessful) {
            throw DeviceRegistrationException("Push token update HTTP failed: ${response.code()}")
        }
        tokenStore.markRegistered(pushToken)
    }

    override fun getRegisteredPushToken(): String? {
        return tokenStore.registeredToken() ?: prefs.getString(KEY_PUSH_TOKEN, null)
    }

    override fun savePushToken(token: String) {
        tokenStore.markRegistered(token)
        prefs.edit().putString(KEY_PUSH_TOKEN, token).apply()
    }

    class DeviceRegistrationException(message: String) : IllegalStateException(message)

    private companion object {
        const val KEY_PUSH_TOKEN = "registered_push_token"
    }
}
