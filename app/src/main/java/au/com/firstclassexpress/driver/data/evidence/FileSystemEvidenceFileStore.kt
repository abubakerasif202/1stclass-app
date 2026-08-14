package au.com.firstclassexpress.driver.data.evidence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import au.com.firstclassexpress.driver.domain.evidence.EvidenceFileDescriptor
import au.com.firstclassexpress.driver.domain.evidence.EvidenceFileStore
import au.com.firstclassexpress.driver.domain.evidence.StoredEvidenceFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores evidence in app-private storage under `files/evidence/<jobId>/`.
 *
 * Photos are downscaled to [MAX_EDGE_PX] on the long edge and re-encoded as JPEG at
 * [JPEG_QUALITY] — small enough to sync over a patchy mobile connection while still legible as
 * proof. All decoding, scaling and I/O happens on [ioDispatcher], never on the main thread.
 */
class FileSystemEvidenceFileStore(
    rootDirectory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : EvidenceFileStore {

    constructor(
        context: Context,
        clock: () -> Long = System::currentTimeMillis,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(File(context.applicationContext.filesDir, "evidence"), clock, ioDispatcher)

    private val root: File = rootDirectory

    override fun createStagingPhotoPath(descriptor: EvidenceFileDescriptor): String {
        val stagingDir = File(root, "staging").also { it.mkdirs() }
        return File(stagingDir, "${fileStem(descriptor)}.staging.jpg").absolutePath
    }

    override suspend fun storePhoto(
        descriptor: EvidenceFileDescriptor,
        stagingPath: String
    ): Result<StoredEvidenceFile> = withContext(ioDispatcher) {
        runCatching {
            val source = File(stagingPath)
            require(source.isFile && source.length() > 0L) { "No captured image was written" }

            val target = targetFile(descriptor, "jpg")
            try {
                val bitmap = decodeScaled(source)
                    ?: throw IllegalStateException("Captured image could not be decoded")
                val oriented = try {
                    applyExifRotation(bitmap, source)
                } catch (error: Exception) {
                    bitmap
                }
                try {
                    target.outputStream().use { out ->
                        check(oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                            "Captured image could not be compressed"
                        }
                    }
                } finally {
                    if (oriented !== bitmap) oriented.recycle()
                    bitmap.recycle()
                }
            } catch (error: Throwable) {
                target.delete()
                throw error
            } finally {
                source.delete()
            }

            check(target.length() > 0L) { "Captured image was not written to disk" }
            StoredEvidenceFile(
                uri = target.toURI().toString(),
                sizeBytes = target.length(),
                savedAt = clock()
            )
        }
    }

    override suspend fun storeSignature(
        descriptor: EvidenceFileDescriptor,
        pngBytes: ByteArray
    ): Result<StoredEvidenceFile> = withContext(ioDispatcher) {
        runCatching {
            require(pngBytes.isNotEmpty()) { "Signature image is empty" }
            val target = targetFile(descriptor, "png")
            try {
                target.outputStream().use { it.write(pngBytes) }
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
            check(target.length() > 0L) { "Signature was not written to disk" }
            StoredEvidenceFile(
                uri = target.toURI().toString(),
                sizeBytes = target.length(),
                savedAt = clock()
            )
        }
    }

    override suspend fun delete(uri: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val file = resolve(uri) ?: return@runCatching
            if (file.exists()) {
                check(file.delete()) { "Unable to delete evidence file" }
            }
        }
    }

    private fun resolve(uri: String): File? = runCatching {
        if (uri.startsWith("file:")) File(java.net.URI(uri)) else File(uri)
    }.getOrNull()

    private fun targetFile(descriptor: EvidenceFileDescriptor, extension: String): File {
        val jobDir = File(root, sanitise(descriptor.jobId)).also { it.mkdirs() }
        return File(jobDir, "${fileStem(descriptor)}.$extension")
    }

    /** Unique per capture: type, driver, timestamp, evidence id and a random suffix. */
    private fun fileStem(descriptor: EvidenceFileDescriptor): String = listOf(
        descriptor.type.name.lowercase(),
        sanitise(descriptor.driverId.ifBlank { "unknown-driver" }),
        clock().toString(),
        sanitise(descriptor.evidenceId),
        UUID.randomUUID().toString().take(8)
    ).joinToString("_")

    private fun sanitise(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "-").ifBlank { "unknown" }

    private fun decodeScaled(source: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longestEdge <= 0) return null

        var sampleSize = 1
        while (longestEdge / (sampleSize * 2) >= MAX_EDGE_PX) sampleSize *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(source.absolutePath, options)
    }

    private fun applyExifRotation(bitmap: Bitmap, source: File): Bitmap {
        val rotation = when (
            ExifInterface(source.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val MAX_EDGE_PX = 1600
        const val JPEG_QUALITY = 80
    }
}
