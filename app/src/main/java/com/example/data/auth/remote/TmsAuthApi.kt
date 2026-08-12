package com.example.data.auth.remote

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit contract for 1st Class Express TMS authentication.
 *
 * The endpoint does not exist yet. This interface fixes the shape so a production implementation
 * can be dropped in without changing anything above [com.example.domain.repository.AuthRepository].
 */
interface TmsAuthApi {
    @POST("v1/driver/auth")
    suspend fun authenticate(@Body request: TmsAuthRequest): TmsAuthResponse
}

data class TmsAuthRequest(val loginId: String, val pin: String)

data class TmsAuthResponse(
    val driverId: String,
    val name: String,
    val email: String,
    val phone: String?
)
