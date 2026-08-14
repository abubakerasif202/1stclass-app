package au.com.firstclassexpress.driver.data.evidence

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import au.com.firstclassexpress.driver.domain.evidence.SignatureDrawing
import au.com.firstclassexpress.driver.domain.evidence.SignatureRenderer
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a signature onto a clean white PNG.
 *
 * Strokes are drawn as smoothed quadratic curves through the captured points so the result matches
 * what the driver saw on screen rather than a jagged polyline.
 */
class BitmapSignatureRenderer(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SignatureRenderer {

    override suspend fun renderPng(drawing: SignatureDrawing): Result<ByteArray> =
        withContext(ioDispatcher) {
            runCatching {
                require(drawing.hasInk) { "Signature is empty" }

                val bitmap = Bitmap.createBitmap(
                    drawing.widthPx,
                    drawing.heightPx,
                    Bitmap.Config.ARGB_8888
                )
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = STROKE_WIDTH_PX
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }

                    drawing.strokes.filter { it.size >= 2 }.forEach { stroke ->
                        val path = Path()
                        path.moveTo(stroke.first().x, stroke.first().y)
                        for (i in 1 until stroke.size) {
                            val previous = stroke[i - 1]
                            val current = stroke[i]
                            path.quadTo(
                                previous.x,
                                previous.y,
                                (previous.x + current.x) / 2f,
                                (previous.y + current.y) / 2f
                            )
                        }
                        path.lineTo(stroke.last().x, stroke.last().y)
                        canvas.drawPath(path, paint)
                    }

                    val output = ByteArrayOutputStream()
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "Signature could not be encoded"
                    }
                    output.toByteArray().also {
                        check(it.isNotEmpty()) { "Signature encoded to an empty file" }
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }

    private companion object {
        const val STROKE_WIDTH_PX = 6f
    }
}
