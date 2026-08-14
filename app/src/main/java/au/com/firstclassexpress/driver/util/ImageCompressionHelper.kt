package au.com.firstclassexpress.driver.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageCompressionHelper {
    const val MAX_IMAGE_EDGE_PX = 1920
    const val DEFAULT_JPEG_QUALITY = 80
    const val TARGET_MAX_FILE_SIZE_BYTES = 1_500_000L // 1.5 MB

    fun compressImageFile(
        inputFile: File,
        outputFile: File = inputFile,
        maxEdgePx: Int = MAX_IMAGE_EDGE_PX,
        quality: Int = DEFAULT_JPEG_QUALITY
    ): Result<File> = runCatching {
        if (!inputFile.exists() || inputFile.length() == 0L) {
            throw IllegalArgumentException("Input image file does not exist or is empty")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(inputFile.absolutePath, bounds)
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)

        var sampleSize = 1
        while (longestEdge / (sampleSize * 2) >= maxEdgePx) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOptions)
            ?: throw IllegalStateException("Failed to decode image bitmap")

        val tempStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, tempStream)
        bitmap.recycle()

        FileOutputStream(outputFile).use { fos ->
            tempStream.writeTo(fos)
        }
        outputFile
    }
}
