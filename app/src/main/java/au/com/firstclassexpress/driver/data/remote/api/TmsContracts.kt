package au.com.firstclassexpress.driver.data.remote.api

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

// 1. AUTHENTICATION & SESSION
@JsonClass(generateAdapter = true)
data class DriverLoginRequest(
    val driverId: String,
    val pin: String,
    val deviceId: String,
    val appVersion: String
)

@JsonClass(generateAdapter = true)
data class DriverAuthResponse(
    val token: String,
    val refreshToken: String,
    val driverId: String,
    val driverName: String,
    val expiresAt: Long
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    val refreshToken: String
)

interface DriverAuthApi {
    @POST("v1/auth/driver/login")
    suspend fun login(@Body request: DriverLoginRequest): Response<DriverAuthResponse>

    @POST("v1/auth/driver/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<DriverAuthResponse>
}

// 2. DRIVER PROFILE
@JsonClass(generateAdapter = true)
data class DriverProfileDto(
    val id: String,
    val name: String,
    val email: String,
    val phone: String?,
    val licenseNumber: String?,
    val assignedVehicleId: String?,
    val shiftStatus: String
)

interface DriverProfileApi {
    @GET("v1/driver/profile")
    suspend fun getProfile(): Response<DriverProfileDto>
}

// 3. VEHICLE OPERATIONS & PRE-START
@JsonClass(generateAdapter = true)
data class VehicleDto(
    val rego: String,
    val type: String,
    val trailerRego: String?,
    val currentOdometer: Long,
    val isRoadworthy: Boolean
)

@JsonClass(generateAdapter = true)
data class DefectReportDto(
    val vehicleId: String,
    val trailerId: String?,
    val severity: String,
    val description: String,
    val photoUri: String?,
    val reportedAt: Long
)

interface VehicleOperationsApi {
    @GET("v1/driver/vehicle/{vehicleId}")
    suspend fun getVehicleDetails(@Path("vehicleId") vehicleId: String): Response<VehicleDto>

    @POST("v1/driver/vehicle/{vehicleId}/defect")
    suspend fun reportDefect(
        @Path("vehicleId") vehicleId: String,
        @Body defect: DefectReportDto
    ): Response<Unit>
}

// 4. REMOTE APP CONFIG
@JsonClass(generateAdapter = true)
data class RemoteAppConfigDto(
    val minSupportedAppVersion: String,
    val latestAppVersion: String,
    val trackingConfig: RemoteTrackingConfigDto,
    val features: Map<String, Boolean>
)

@JsonClass(generateAdapter = true)
data class RemoteTrackingConfigDto(
    val stationaryIntervalSeconds: Int = 60,
    val enRouteIntervalSeconds: Int = 15,
    val nearStopIntervalSeconds: Int = 5,
    val nearStopRadiusMeters: Int = 500
)

interface AppConfigApi {
    @GET("v1/app/config")
    suspend fun getAppConfig(): Response<RemoteAppConfigDto>
}
