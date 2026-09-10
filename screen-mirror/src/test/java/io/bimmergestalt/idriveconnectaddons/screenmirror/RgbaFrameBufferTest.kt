package io.bimmergestalt.idriveconnectaddons.screenmirror

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class RgbaFrameBufferTest {
    private fun bytes(buffer: ByteBuffer) = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }

    @Test fun removesPaddingWithoutRequiringPaddingAfterLastRow() {
        val source = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 99, 99, 99, 99,
            9, 10, 11, 12, 13, 14, 15, 16))
        val frame = RgbaFrameBuffer().prepare(source, 2, 2, 4, 12)
        assertArrayEquals((1..16).map { it.toByte() }.toByteArray(), bytes(frame))
        assertEquals(0, source.position())
        assertEquals(20, source.limit())
    }

    @Test fun reusesPaddedFrameStorageAndRewindsAfterBitmapConsumption() {
        val packer = RgbaFrameBuffer()
        val first = packer.prepare(ByteBuffer.wrap(ByteArray(12) { 1 }), 1, 2, 4, 8)
        first.position(first.limit())
        val next = packer.prepare(ByteBuffer.wrap(ByteArray(12) { 2 }), 1, 2, 4, 8)
        assertSame(first, next)
        assertEquals(0, next.position())
        assertArrayEquals(ByteArray(8) { 2 }, bytes(next))
    }

    @Test fun tightlyPackedFramesUseSourceStorageWithoutChangingSourcePosition() {
        val source = ByteBuffer.wrap(byteArrayOf(99, 1, 2, 3, 4, 99))
        source.position(1)
        val frame = RgbaFrameBuffer().prepare(source, 1, 1, 4, 4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes(frame))
        source.put(1, 10)
        assertEquals(10, frame.get(1).toInt())
        assertEquals(1, source.position())
        assertEquals(6, source.limit())
    }

    @Test fun croppedRowsRespectSourceOffset() {
        val source = ByteBuffer.wrap(byteArrayOf(99, 1, 2, 3, 4, 99, 99, 99, 99, 5, 6, 7, 8))
        source.position(1)
        val frame = RgbaFrameBuffer().prepare(source, 1, 2, 4, 8)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), bytes(frame))
    }

    @Test fun resizeOrCleanupReleasesReusableStorage() {
        val packer = RgbaFrameBuffer()
        val small = packer.prepare(ByteBuffer.allocate(12), 1, 2, 4, 8)
        val large = packer.prepare(ByteBuffer.allocate(20), 2, 2, 4, 12)
        assertNotSame(small, large)
        packer.clear()
        assertNotSame(large, packer.prepare(ByteBuffer.allocate(20), 2, 2, 4, 12))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedPixelData() {
        RgbaFrameBuffer().prepare(ByteBuffer.allocate(19), 2, 2, 4, 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnexpectedPixelFormat() {
        RgbaFrameBuffer().prepare(ByteBuffer.allocate(8), 1, 1, 2, 8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverflowingDimensions() {
        RgbaFrameBuffer().prepare(ByteBuffer.allocate(8), Int.MAX_VALUE, Int.MAX_VALUE, 4, Int.MAX_VALUE)
    }
}
