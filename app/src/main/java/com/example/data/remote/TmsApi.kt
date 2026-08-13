package com.example.data.remote

import com.example.data.remote.dto.EvidenceDeleteSyncDto
import com.example.data.remote.dto.FreightExceptionSyncDto
import com.example.data.remote.dto.InspectionSyncDto
import com.example.data.remote.dto.JobStatusSyncDto
import com.example.data.remote.dto.LocationBatchSyncDto
import com.example.data.remote.dto.RemoteJobListDto
import com.example.data.remote.dto.ShiftEventSyncDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for the 1st Class Express TMS driver API.
 *
 * **No live endpoint exists yet.** Every path below is the app's proposal, not a documented
 * contract, and is unreachable until `TMS_BASE_URL` is configured. The value of fixing the shape
 * now is that the queue, the worker and the UI can be built and tested against it; swapping in the
 * real paths and payloads is an edit to this file and the DTOs, not to anything above the
 * transport.
 *
 * ### Idempotency
 * Every mutating call takes `X-Idempotency-Key`, which carries the durable `SyncOperation.id`.
 * The key is generated once when the operation is queued and reused verbatim on every retry, so a
 * response lost on the wire cannot become a duplicate job status, a duplicate exception or a
 * duplicate POD. The server must treat a repeated key as "already applied" and answer 2xx.
 */
interface TmsApi {

    @POST("v1/driver/jobs/{jobId}/status")
    suspend fun updateJobStatus(
        @Header(IDEMPOTENCY_HEADER) idempotencyKey: String,
        @Path("jobId") jobId: String,
        @Body body: JobStatusSyncDto
    ): Response<Unit>

    @POST("v1/driver/shifts/{shiftId}/events")
    suspend fun postShiftEvent(
        @Header(IDEMPOTENCY_HEADER) idempotencyKey: String,
        @Path("shiftId") shiftId: String,
        @Body body: ShiftEventSyncDto
    ): Response<Unit>

    @POST("v1/driver/shifts/{shiftId}/inspection")
    suspend fun postInspection(
        @Header(IDEMPOTENCY_HEADER) idempotencyKey: String,
        @Path("shiftId") shiftId: String,
        @Body body: InspectionSyncDto
    ): Response<Unit>

    @POST("v1/driver/exceptions")
    suspend fun postFreightException(
        @Header(IDEMPOTENCY_HEADER) idempotencyKey: String,
        @Body body: FreightExceptionSyncDto
    ): Response<Unit>

    /**
     * Accepts a batch so the interface does not need to change when the TMS supports batching.
     * The processor currently sends one point per queued operation, because each point is its own
     * durable queue entry with its own idempotency key.
     */
    @POST("v1/driver/locations")
    suspend fun postLocations(
        @Header(IDEMPOTENCY_HEADER) idempotencyKey: String,
        @Body body: LocationBatchSyncDto
    ): Response<Unit>

    /**
     * Multipart POD upload. The file part is streamed from disk — evidence photos are never read
     * fully into memory.
     *
     * If the TMS later issues pre-signed upload URLs instead, only
     * [com.example.data.remote.RetrofitSyncTransport] changes: the processor asks the transport to
     * "upload this evidence", not to "POST this multipart body".
     */
    @Multipart
    @POST("v1/driver/evidence")
    suspend fun uploadEvidence(
        @Header(IDEMPOTENCY_HEADER) idempotencyKey: String,
        @Part("metadata") metadata: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @DELETE("v1/driver/evidence/{evidenceId}")
    suspend fun deleteEvidence(
        @Header(IDEMPOTENCY_HEADER) idempotencyKey: String,
        @Path("evidenceId") evidenceId: String,
        @Body body: EvidenceDeleteSyncDto
    ): Response<Unit>

    @GET("v1/driver/jobs")
    suspend fun assignedJobs(
        @Query("driverId") driverId: String
    ): Response<RemoteJobListDto>

    companion object {
        const val IDEMPOTENCY_HEADER = "X-Idempotency-Key"
    }
}
