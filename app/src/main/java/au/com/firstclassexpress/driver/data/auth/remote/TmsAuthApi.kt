package au.com.firstclassexpress.driver.data.auth.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit contract for 1st Class Express TMS authentication.
 *
 * Matches the server's driver login contract.
 */
interface TmsAuthApi {
    @POST("v1/auth/driver/login")
    suspend fun authenticate(@Body request: TmsAuthRequest): TmsAuthResponse
}

@JsonClass(generateAdapter = true)
data class TmsAuthRequest(val driverId: String, val pin: String) {
    /** The PIN must never reach a log or a crash report. */
    override fun toString(): String = "TmsAuthRequest(driverId=$driverId, pin=***)"
}

@JsonClass(generateAdapter = true)
data class TmsAuthResponse(
    val driverId: String,
    val name: String,
    val token: String,
    val refreshToken: String,
    /** Absolute Unix epoch time in milliseconds. */
    val expiresAt: Long
) {
    override fun toString(): String =
        "TmsAuthResponse(driverId=$driverId, name=$name, hasToken=${token.isNotBlank()})"
}
