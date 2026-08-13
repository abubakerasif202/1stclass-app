package com.example.data.auth.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit contract for 1st Class Express TMS authentication.
 *
 * The endpoint does not exist yet. This interface fixes the shape so a production implementation
 * can be dropped in without changing anything above [com.example.domain.repository.AuthRepository].
 *
 * Note what is deliberately **absent**: there is no refresh-token call. The app will not invent an
 * endpoint the TMS has not defined. If the real contract offers refresh, add it here and teach
 * [RemoteAuthRepository] to use it; until then an expired token means a re-login, which is honest.
 */
interface TmsAuthApi {
    @POST("v1/driver/auth")
    suspend fun authenticate(@Body request: TmsAuthRequest): TmsAuthResponse
}

@JsonClass(generateAdapter = true)
data class TmsAuthRequest(val loginId: String, val pin: String) {
    /** The PIN must never reach a log or a crash report. */
    override fun toString(): String = "TmsAuthRequest(loginId=$loginId, pin=***)"
}

@JsonClass(generateAdapter = true)
data class TmsAuthResponse(
    val driverId: String,
    val name: String,
    val email: String,
    val phone: String?,
    /** Null until the TMS issues bearer tokens; the app then simply sends no auth header. */
    val accessToken: String? = null,
    /** Seconds until [accessToken] expires, when the server tells us. */
    val expiresInSeconds: Long? = null
) {
    override fun toString(): String =
        "TmsAuthResponse(driverId=$driverId, name=$name, hasToken=${accessToken != null})"
}
