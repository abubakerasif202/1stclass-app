package com.example.domain.evidence

/** A point on the signature canvas, in canvas pixels. */
data class SignaturePoint(val x: Float, val y: Float)

/**
 * A finger/stylus signature as captured on screen, independent of any UI framework so it can be
 * validated and rendered without a Compose or Android dependency.
 */
data class SignatureDrawing(
    val strokes: List<List<SignaturePoint>>,
    val widthPx: Int,
    val heightPx: Int
) {
    /** A stray tap is not a signature: at least one stroke must actually travel. */
    val hasInk: Boolean
        get() = widthPx > 0 && heightPx > 0 && strokes.any { it.size >= MIN_POINTS_PER_STROKE }

    private companion object {
        const val MIN_POINTS_PER_STROKE = 2
    }
}

/** Renders a captured signature to PNG bytes. */
interface SignatureRenderer {
    suspend fun renderPng(drawing: SignatureDrawing): Result<ByteArray>
}
