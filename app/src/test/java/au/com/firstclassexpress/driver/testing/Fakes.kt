package au.com.firstclassexpress.driver.testing

import au.com.firstclassexpress.driver.domain.evidence.EvidenceFileDescriptor
import au.com.firstclassexpress.driver.domain.evidence.EvidenceFileStore
import au.com.firstclassexpress.driver.domain.evidence.SignatureDrawing
import au.com.firstclassexpress.driver.domain.evidence.SignaturePoint
import au.com.firstclassexpress.driver.domain.evidence.SignatureRenderer
import au.com.firstclassexpress.driver.domain.evidence.StoredEvidenceFile
import au.com.firstclassexpress.driver.domain.model.AuthenticatedDriver
import au.com.firstclassexpress.driver.domain.model.DriverSession
import au.com.firstclassexpress.driver.domain.model.EvidenceCaptureRequest
import au.com.firstclassexpress.driver.domain.model.EvidenceRecord
import au.com.firstclassexpress.driver.domain.model.EvidenceStatus
import au.com.firstclassexpress.driver.domain.model.FreightExceptionDraft
import au.com.firstclassexpress.driver.domain.model.FreightExceptionRecord
import au.com.firstclassexpress.driver.domain.repository.EvidenceRepository
import au.com.firstclassexpress.driver.domain.repository.FreightExceptionRepository
import au.com.firstclassexpress.driver.domain.repository.SessionRepository
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import au.com.firstclassexpress.driver.model.Location
import au.com.firstclassexpress.driver.model.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory evidence store mirroring the Room repository's status transitions. */
class FakeEvidenceRepository : EvidenceRepository {
    private val flow = MutableStateFlow<List<EvidenceRecord>>(emptyList())
    val records: List<EvidenceRecord> get() = flow.value
    private var next = 0

    override fun observeForJob(jobId: String): Flow<List<EvidenceRecord>> = flow

    override suspend fun getForJob(jobId: String): List<EvidenceRecord> =
        flow.value.filter { it.jobId == jobId }

    override suspend fun getById(id: String): EvidenceRecord? = flow.value.find { it.id == id }

    override suspend fun createPending(request: EvidenceCaptureRequest): Result<String> {
        val id = "e${next++}"
        flow.value = flow.value + EvidenceRecord(
            id = id,
            jobId = request.jobId,
            type = request.type,
            localUri = null,
            status = EvidenceStatus.PENDING_CAPTURE,
            createdAt = 1L,
            driverId = request.driverId,
            shiftId = request.shiftId
        )
        return Result.success(id)
    }

    override suspend fun markSavedLocal(
        id: String,
        file: StoredEvidenceFile,
        signerName: String?,
        notes: String?
    ): Result<EvidenceRecord> {
        val existing = flow.value.find { it.id == id }
            ?: return Result.failure(IllegalStateException("Evidence not found"))
        if (existing.status != EvidenceStatus.PENDING_CAPTURE) {
            return Result.failure(IllegalStateException("Evidence is not awaiting capture"))
        }
        val updated = existing.copy(
            localUri = file.uri,
            status = EvidenceStatus.SAVED_LOCAL,
            signerName = signerName,
            notes = notes,
            fileSizeBytes = file.sizeBytes,
            savedAt = file.savedAt
        )
        flow.value = flow.value.map { if (it.id == id) updated else it }
        return Result.success(updated)
    }

    override suspend fun discardPending(id: String): Result<Unit> {
        flow.value = flow.value.filterNot {
            it.id == id && it.status == EvidenceStatus.PENDING_CAPTURE
        }
        return Result.success(Unit)
    }

    override suspend fun deleteSaved(id: String): Result<EvidenceRecord> {
        val existing = flow.value.find { it.id == id }
            ?: return Result.failure(IllegalStateException("Evidence not found"))
        flow.value = flow.value.filterNot { it.id == id }
        return Result.success(existing)
    }
}

/** File store that records what it was asked to write without touching the filesystem. */
class FakeEvidenceFileStore(
    private val failStore: Boolean = false
) : EvidenceFileStore {
    val deleted = mutableListOf<String>()
    var storedPhotoCount = 0
        private set
    var storedSignatureCount = 0
        private set

    override fun createStagingPhotoPath(descriptor: EvidenceFileDescriptor): String =
        "/tmp/${descriptor.evidenceId}.staging.jpg"

    override suspend fun storePhoto(
        descriptor: EvidenceFileDescriptor,
        stagingPath: String
    ): Result<StoredEvidenceFile> {
        if (failStore) return Result.failure(IllegalStateException("No captured image was written"))
        storedPhotoCount++
        return Result.success(
            StoredEvidenceFile("file:///evidence/${descriptor.evidenceId}.jpg", 2048L, 99L)
        )
    }

    override suspend fun storeSignature(
        descriptor: EvidenceFileDescriptor,
        pngBytes: ByteArray
    ): Result<StoredEvidenceFile> {
        if (failStore) return Result.failure(IllegalStateException("Signature not written"))
        storedSignatureCount++
        return Result.success(
            StoredEvidenceFile(
                "file:///evidence/${descriptor.evidenceId}.png",
                pngBytes.size.toLong(),
                99L
            )
        )
    }

    override suspend fun delete(uri: String): Result<Unit> {
        deleted += uri
        return Result.success(Unit)
    }
}

/** Renders a fixed byte payload so tests exercise ordering, not image encoding. */
class FakeSignatureRenderer : SignatureRenderer {
    override suspend fun renderPng(drawing: SignatureDrawing): Result<ByteArray> =
        if (drawing.hasInk) {
            Result.success(ByteArray(64) { 1 })
        } else {
            Result.failure(IllegalArgumentException("Signature is empty"))
        }
}

class FakeSessionRepository : SessionRepository {
    private val flow = MutableStateFlow<DriverSession?>(null)
    var clearCount = 0
        private set

    override fun observeSession(): Flow<DriverSession?> = flow

    override suspend fun currentSession(): DriverSession? = flow.value

    override suspend fun startSession(driver: AuthenticatedDriver): Result<DriverSession> {
        val session = DriverSession(
            driverId = driver.driverId,
            name = driver.name,
            email = driver.email,
            phone = driver.phone,
            authenticatedAt = 1_000L
        )
        flow.value = session
        return Result.success(session)
    }

    override suspend fun clearSession(): Result<Unit> {
        clearCount++
        flow.value = null
        return Result.success(Unit)
    }

    fun seed(session: DriverSession) {
        flow.value = session
    }
}

class FakeFreightExceptionRepository : FreightExceptionRepository {
    private val flow = MutableStateFlow<List<FreightExceptionRecord>>(emptyList())
    private var next = 0

    override fun observeForJob(jobId: String): Flow<List<FreightExceptionRecord>> = flow

    override suspend fun getForJob(jobId: String): List<FreightExceptionRecord> =
        flow.value.filter { it.jobId == jobId }

    override suspend fun record(draft: FreightExceptionDraft): Result<String> {
        if (draft.notes.isBlank()) {
            return Result.failure(IllegalArgumentException("Notes are required"))
        }
        val id = "x${next++}"
        flow.value = flow.value + FreightExceptionRecord(
            id = id,
            jobId = draft.jobId,
            stage = draft.stage,
            reason = draft.reason,
            notes = draft.notes,
            driverId = draft.driverId,
            shiftId = draft.shiftId,
            resolved = false,
            createdAt = 5L,
            status = EvidenceStatus.SAVED_LOCAL
        )
        return Result.success(id)
    }

    override suspend fun markResolved(id: String, resolved: Boolean): Result<Unit> {
        flow.value = flow.value.map { if (it.id == id) it.copy(resolved = resolved) else it }
        return Result.success(Unit)
    }
}

fun signatureWithInk(): SignatureDrawing = SignatureDrawing(
    strokes = listOf(listOf(SignaturePoint(1f, 1f), SignaturePoint(20f, 30f))),
    widthPx = 400,
    heightPx = 200
)

fun emptySignature(): SignatureDrawing = SignatureDrawing(
    strokes = emptyList(),
    widthPx = 400,
    heightPx = 200
)

fun testJob(id: String = "job-1", status: JobStatus = JobStatus.AT_PICKUP): Job = Job(
    id = id,
    reference = "REF-$id",
    status = status,
    pickup = Location(
        address = "1 Pickup Road",
        suburb = "Brisbane",
        lat = -27.0,
        lng = 153.0,
        companyName = "Pickup",
        contactName = "Pickup Contact",
        contactPhone = "0000"
    ),
    delivery = Location(
        address = "2 Delivery Road",
        suburb = "Brisbane",
        lat = -27.1,
        lng = 153.1,
        companyName = "Delivery",
        contactName = "Delivery Contact",
        contactPhone = "0000"
    ),
    pickupWindowStart = "08:00",
    pickupWindowEnd = "09:00",
    deliveryWindowStart = "10:00",
    deliveryWindowEnd = "11:00",
    freightDescription = "Test freight",
    itemCount = 4,
    priority = Priority.NORMAL
)
