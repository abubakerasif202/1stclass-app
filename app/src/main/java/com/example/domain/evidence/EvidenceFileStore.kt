package com.example.domain.evidence

import com.example.domain.model.EvidenceType

/** Identifies the evidence a file belongs to, and drives the generated filename. */
data class EvidenceFileDescriptor(
    val jobId: String,
    val evidenceId: String,
    val type: EvidenceType,
    val driverId: String,
    val shiftId: String? = null
)

/** A file that has actually been written to disk. */
data class StoredEvidenceFile(
    val uri: String,
    val sizeBytes: Long,
    val savedAt: Long
)

/**
 * Durable local storage for captured evidence files.
 *
 * Every operation is expected to run off the main thread. A [Result.success] means bytes are on
 * disk — callers use that, and nothing earlier, as the trigger for marking evidence SAVED_LOCAL.
 */
interface EvidenceFileStore {
    /** Absolute path for a camera to write its raw capture into before compression. */
    fun createStagingPhotoPath(descriptor: EvidenceFileDescriptor): String

    /** Compresses the staged capture into permanent storage and removes the staged file. */
    suspend fun storePhoto(
        descriptor: EvidenceFileDescriptor,
        stagingPath: String
    ): Result<StoredEvidenceFile>

    /** Writes an already-rendered PNG (signature) into permanent storage. */
    suspend fun storeSignature(
        descriptor: EvidenceFileDescriptor,
        pngBytes: ByteArray
    ): Result<StoredEvidenceFile>

    /** Removes a stored evidence file. Missing files are not an error. */
    suspend fun delete(uri: String): Result<Unit>
}
