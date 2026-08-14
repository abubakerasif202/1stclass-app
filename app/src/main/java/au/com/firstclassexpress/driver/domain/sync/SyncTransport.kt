package au.com.firstclassexpress.driver.domain.sync

import au.com.firstclassexpress.driver.data.remote.dto.EvidenceDeleteSyncDto
import au.com.firstclassexpress.driver.data.remote.dto.EvidenceMetadataSyncDto
import au.com.firstclassexpress.driver.data.remote.dto.FreightExceptionSyncDto
import au.com.firstclassexpress.driver.data.remote.dto.IncidentSyncDto
import au.com.firstclassexpress.driver.data.remote.dto.InspectionSyncDto
import au.com.firstclassexpress.driver.data.remote.dto.JobStatusSyncDto
import au.com.firstclassexpress.driver.data.remote.dto.LocationPointSyncDto
import au.com.firstclassexpress.driver.data.remote.dto.PodCompletionSyncDto
import au.com.firstclassexpress.driver.data.remote.dto.ShiftEventSyncDto
import java.io.File

/** An evidence file about to be streamed to the TMS. */
data class EvidenceUpload(val file: File, val mimeType: String)

/**
 * Everything the sync engine needs from a server, expressed in domain terms.
 *
 * This is the boundary that lets the queue, the worker and the UI exist and be tested before the
 * TMS does. Two implementations ship:
 *  - [au.com.firstclassexpress.driver.data.remote.RetrofitSyncTransport] — talks to a configured endpoint.
 *  - [UnconfiguredSyncTransport] — the runtime default today; refuses everything with
 *    [SyncOutcome.NotConfigured] so operations stay `PENDING`.
 *
 * Test doubles live in the test source set only. There is no fake that reports success at runtime.
 *
 * Every method takes `idempotencyKey`, which is the durable `SyncOperation.id` and is identical
 * across all retries of the same logical operation.
 */
interface SyncTransport {

    suspend fun sendJobStatus(idempotencyKey: String, payload: JobStatusSyncDto): SyncOutcome

    suspend fun sendShiftEvent(idempotencyKey: String, payload: ShiftEventSyncDto): SyncOutcome

    suspend fun sendInspection(idempotencyKey: String, payload: InspectionSyncDto): SyncOutcome

    suspend fun sendFreightException(
        idempotencyKey: String,
        payload: FreightExceptionSyncDto
    ): SyncOutcome

    suspend fun sendIncident(
        idempotencyKey: String,
        payload: IncidentSyncDto
    ): SyncOutcome

    suspend fun sendPodCompletion(
        idempotencyKey: String,
        payload: PodCompletionSyncDto
    ): SyncOutcome

    suspend fun sendLocationPoints(
        idempotencyKey: String,
        payload: List<LocationPointSyncDto>
    ): SyncOutcome

    suspend fun uploadEvidence(
        idempotencyKey: String,
        metadata: EvidenceMetadataSyncDto,
        upload: EvidenceUpload
    ): SyncOutcome

    suspend fun deleteEvidence(
        idempotencyKey: String,
        evidenceId: String,
        jobId: String,
        deletedAt: Long
    ): SyncOutcome
}

/**
 * The transport used when no TMS endpoint is configured.
 *
 * It never claims success. Queued work stays `PENDING` on the device until a real server
 * acknowledges it.
 */
class UnconfiguredSyncTransport(
    private val reason: String = "No TMS endpoint is configured for this build"
) : SyncTransport {
    private fun refuse() = SyncOutcome.NotConfigured(reason)

    override suspend fun sendJobStatus(idempotencyKey: String, payload: JobStatusSyncDto) = refuse()
    override suspend fun sendShiftEvent(idempotencyKey: String, payload: ShiftEventSyncDto) = refuse()
    override suspend fun sendInspection(idempotencyKey: String, payload: InspectionSyncDto) = refuse()
    override suspend fun sendFreightException(
        idempotencyKey: String,
        payload: FreightExceptionSyncDto
    ) = refuse()

    override suspend fun sendIncident(
        idempotencyKey: String,
        payload: IncidentSyncDto
    ) = refuse()

    override suspend fun sendPodCompletion(
        idempotencyKey: String,
        payload: PodCompletionSyncDto
    ) = refuse()

    override suspend fun sendLocationPoints(
        idempotencyKey: String,
        payload: List<LocationPointSyncDto>
    ) = refuse()

    override suspend fun uploadEvidence(
        idempotencyKey: String,
        metadata: EvidenceMetadataSyncDto,
        upload: EvidenceUpload
    ) = refuse()

    override suspend fun deleteEvidence(
        idempotencyKey: String,
        evidenceId: String,
        jobId: String,
        deletedAt: Long
    ) = refuse()
}
