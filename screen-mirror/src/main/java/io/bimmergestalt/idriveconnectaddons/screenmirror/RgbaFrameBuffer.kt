package io.bimmergestalt.idriveconnectaddons.screenmirror

import java.nio.ByteBuffer

/** Removes ImageReader row padding into reusable storage, keeping the exact image width. */
internal class RgbaFrameBuffer {
    private var packed: ByteBuffer? = null

    fun prepare(buffer: ByteBuffer, width: Int, height: Int, pixelStride: Int, rowStride: Int): ByteBuffer {
        require(width > 0 && height > 0 && pixelStride == 4)
        val rowBytesLong = width.toLong() * pixelStride
        require(rowBytesLong <= Int.MAX_VALUE)
        val totalBytes = rowBytesLong * height
        require(totalBytes <= Int.MAX_VALUE && rowStride >= rowBytesLong)
        val rowBytes = rowBytesLong.toInt()
        // Android need not map the padding after the final row.
        val requiredBytes = (height - 1L) * rowStride + rowBytes
        require(requiredBytes <= buffer.remaining())
        val source = buffer.duplicate()
        if (rowStride == rowBytes) {
            source.limit(source.position() + totalBytes.toInt())
            return source
        }

        val output = packed?.takeIf { it.capacity() == totalBytes.toInt() }
            ?: ByteBuffer.allocateDirect(totalBytes.toInt()).also { packed = it }
        output.clear()
        val start = source.position()
        for (row in 0 until height) {
            val offset = start + row * rowStride
            source.limit(offset + rowBytes)
            source.position(offset)
            output.put(source)
        }
        output.flip()
        return output
    }

    fun clear() { packed = null }
}
