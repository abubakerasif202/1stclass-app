package au.com.firstclassexpress.driver.domain.repository

interface DeviceRegistrationRepository {
    suspend fun registerDevice(
        deviceId: String,
        driverId: String,
        appVersion: String,
        pushToken: String?
    ): Result<Unit>

    suspend fun updatePushToken(
        deviceId: String,
        driverId: String,
        pushToken: String
    ): Result<Unit>

    fun getRegisteredPushToken(): String?
    fun savePushToken(token: String)
}
