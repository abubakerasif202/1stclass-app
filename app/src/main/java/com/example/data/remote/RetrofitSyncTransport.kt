package com.example.data.remote

import com.example.data.remote.dto.EvidenceDeleteSyncDto
import com.example.data.remote.dto.EvidenceMetadataSyncDto
import com.example.data.remote.dto.FreightExceptionSyncDto
import com.example.data.remote.dto.InspectionSyncDto
import com.example.data.remote.dto.JobStatusSyncDto
import com.example.data.remote.dto.LocationBatchSyncDto
import com.example.data.remote.dto.LocationPointSyncDto
import com.example.data.remote.dto.ShiftEventSyncDto
import com.example.domain.sync.EvidenceUpload
import com.example.domain.sync.SyncOutcome
import com.example.domain.sync.SyncTransport
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/**
 * Sends queued operations to a configured TMS.
 *
 * Every call funnels through [attempt], so exception handling, status-code classification and the
 * "only 2xx means synced" rule are implemented exactly once.
 */
class RetrofitSyncTransport(
    private val api: TmsApi,
    moshi: Moshi
) : SyncTransport {

    private val metadataAdapter = moshi.adapter(EvidenceMetadataSyncDto::class.java)

    override suspend fun sendJobStatus(idempotencyKey: String, payload: JobStatusSyncDto) =
        attempt { api.updateJobStatus(idempotencyKey, payload.jobId, payload) }

    override suspend fun sendShiftEvent(idempotencyKey: String, payload: ShiftEventSyncDto) =
        attempt { api.postShiftEvent(idempotencyKey, payload.shiftId, payload) }

    override suspend fun sendInspection(idempotencyKey: String, payload: InspectionSyncDto) =
        attempt { api.postInspection(idempotencyKey, payload.shiftId, payload) }

    override suspend fun sendFreightException(
        idempotencyKey: String,
        payload: FreightExceptionSyncDto
    ) = attempt { api.postFreightException(idempotencyKey, payload) }

    override suspend fun sendLocationPoints(
        idempotencyKey: String,
        payload: List<LocationPointSyncDto>
    ): SyncOutcome {
        if (payload.isEmpty()) return SyncOutcome.Permanent("No location points to send")
        return attempt { api.postLocations(idempotencyKey, LocationBatchSyncDto(payload)) }
    }

    override suspend fun uploadEvidence(
        idempotencyKey: String,
        metadata: EvidenceMetadataSyncDto,
        upload: EvidenceUpload
    ): SyncOutcome {
        // Re-checked here as well as in the processor: the file can vanish between the two.
        if (!upload.file.exists()) {
            return SyncOutcome.Permanent("Evidence file is missing on device")
        }
        val metadataPart: RequestBody =
            metadataAdapter.toJson(metadata).toRequestBody(JSON_MEDIA_TYPE)
        // asRequestBody streams from disk; the image is never held in memory in full.
        val filePart = MultipartBody.Part.createFormData(
            "file",
            metadata.fileName,
            upload.file.asRequestBody(upload.mimeType.toMediaType())
        )
        return attempt { api.uploadEvidence(idempotencyKey, metadataPart, filePart) }
    }

    override suspend fun deleteEvidence(
        idempotencyKey: String,
        evidenceId: String,
        jobId: String,
        deletedAt: Long
    ) = attempt {
        api.deleteEvidence(
            idempotencyKey,
            evidenceId,
            EvidenceDeleteSyncDto(evidenceId, jobId, deletedAt)
        )
    }

    private suspend fun attempt(call: suspend () -> Response<Unit>): SyncOutcome =
        runCatching { call() }.fold(
            onSuccess = { NetworkResultMapper.fromHttpCode(it.code()) },
            onFailure = NetworkResultMapper::fromThrowable
        )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
