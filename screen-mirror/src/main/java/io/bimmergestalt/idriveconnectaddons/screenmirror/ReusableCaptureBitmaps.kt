package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * Two bitmap buffers owned by the capture handler. Comparing the copied pixels before JPEG
 * encoding avoids compressing an unchanged screen. The accepted frame is never the next target.
 */
internal class ReusableCaptureBitmaps {
    private var working: Bitmap? = null
    private var previousEncoded: Bitmap? = null
    private var comparisonValid = false

    /** Copies packed RGBA pixels, consuming the source buffer. The returned bitmap is borrowed. */
    fun copyFrom(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        require(width > 0 && height > 0)
        val pixelCount = width.toLong() * height
        require(pixelCount <= Int.MAX_VALUE / 4 && buffer.remaining() >= pixelCount * 4)
        val allocated = working ?: previousEncoded
        if (allocated != null && (allocated.width != width || allocated.height != height)) clear()
        val target = working ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            .also { working = it }
        target.copyPixelsFromBuffer(buffer)
        return target
    }

    fun isUnchanged(frame: Bitmap): Boolean =
        comparisonValid && previousEncoded?.sameAs(frame) == true

    /** Call only after successful encoding and delivery (or verified identical encoded bytes). */
    fun rememberEncoded(frame: Bitmap) {
        require(frame === working) { "Only the current capture can be remembered" }
        working = previousEncoded
        previousEncoded = frame
        comparisonValid = true
    }

    /** Force a fresh delivery after focus/consumer/encoding settings change, retaining buffers. */
    fun resetComparison() {
        comparisonValid = false
    }

    fun clear() {
        working?.recycle()
        previousEncoded?.recycle()
        working = null
        previousEncoded = null
        comparisonValid = false
    }
}
