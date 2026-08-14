package au.com.firstclassexpress.driver.domain.repository

import au.com.firstclassexpress.driver.domain.evidence.StoredEvidenceFile
import au.com.firstclassexpress.driver.domain.model.EvidenceCaptureRequest
import au.com.firstclassexpress.driver.domain.model.EvidenceRecord
import kotlinx.coroutines.flow.Flow

interface EvidenceRepository {
    fun observeForJob(jobId: String): Flow<List<EvidenceRecord>>
    suspend fun getForJob(jobId: String): List<EvidenceRecord>
    suspend fun getById(id: String): EvidenceRecord?

    /** Reserves an evidence row in PENDING_CAPTURE. Nothing counts as evidence at this point. */
    suspend fun createPending(request: EvidenceCaptureRequest): Result<String>

    /**
     * Promotes pending evidence to SAVED_LOCAL. Callers must only invoke this once [file] has
     * genuinely been written to disk.
     */
    suspend fun markSavedLocal(
        id: String,
        file: StoredEvidenceFile,
        signerName: String? = null,
        notes: String? = null
    ): Result<EvidenceRecord>

    /** Removes a reservation that never produced a file (cancel/back). */
    suspend fun discardPending(id: String): Result<Unit>

    /** Removes saved evidence so the driver can retake it, returning the record that was removed. */
    suspend fun deleteSaved(id: String): Result<EvidenceRecord>
}
