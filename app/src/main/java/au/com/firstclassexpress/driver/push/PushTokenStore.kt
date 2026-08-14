package au.com.firstclassexpress.driver.push

import android.content.Context
import java.util.UUID

/**
 * Small durable store for the FCM token lifecycle.
 *
 * The pending token is written before any network call. It is only removed after the TMS has
 * acknowledged registration, so an offline device or a transient HTTP failure can retry safely.
 */
class PushTokenStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun pendingToken(): String? = preferences.getString(KEY_PENDING_TOKEN, null)

    fun savePendingToken(token: String) {
        if (token.isBlank()) return
        preferences.edit().putString(KEY_PENDING_TOKEN, token).apply()
    }

    fun registeredToken(): String? = preferences.getString(KEY_REGISTERED_TOKEN, null)

    fun markRegistered(token: String) {
        if (token.isBlank()) return
        preferences.edit()
            .putString(KEY_REGISTERED_TOKEN, token)
            .remove(KEY_PENDING_TOKEN)
            .apply()
    }

    fun registeredDeviceId(): String? = preferences.getString(KEY_REGISTERED_DEVICE_ID, null)

    fun registeredDriverId(): String? = preferences.getString(KEY_REGISTERED_DRIVER_ID, null)

    fun markDeviceRegistered(deviceId: String, driverId: String) {
        if (deviceId.isBlank()) return
        preferences.edit()
            .putString(KEY_REGISTERED_DEVICE_ID, deviceId)
            .putString(KEY_REGISTERED_DRIVER_ID, driverId)
            .apply()
    }

    fun deviceId(): String {
        preferences.getString(KEY_DEVICE_ID, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_DEVICE_ID, generated).commit()
        return preferences.getString(KEY_DEVICE_ID, generated) ?: generated
    }

    internal fun clearForTests() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val PREFS_NAME = "device_registration_prefs"
        const val KEY_PENDING_TOKEN = "pending_push_token"
        const val KEY_REGISTERED_TOKEN = "registered_push_token"
        const val KEY_REGISTERED_DEVICE_ID = "registered_device_id"
        const val KEY_REGISTERED_DRIVER_ID = "registered_driver_id"
        const val KEY_DEVICE_ID = "device_id"
    }
}
