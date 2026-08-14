package au.com.firstclassexpress.driver.data.remote.api

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class DeviceRegistrationRequest(
    val deviceId: String,
    val driverId: String,
    val appVersion: String,
    val platform: String = "ANDROID",
    val pushToken: String?,
    val lastSeen: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class PushTokenUpdateRequest(
    val deviceId: String,
    val driverId: String,
    val pushToken: String,
    val updatedAt: Long = System.currentTimeMillis()
)

interface DeviceRegistrationApi {
    @POST("v1/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): Response<Unit>

    @POST("v1/devices/push-token")
    suspend fun updatePushToken(@Body request: PushTokenUpdateRequest): Response<Unit>
}
