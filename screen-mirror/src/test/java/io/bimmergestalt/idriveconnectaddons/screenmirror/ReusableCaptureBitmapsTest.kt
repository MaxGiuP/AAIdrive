package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReusableCaptureBitmapsTest {
    private val captures = ReusableCaptureBitmaps()

    @After fun tearDown() { captures.clear() }

    private fun pixels(vararg colors: Int): ByteBuffer =
        ByteBuffer.allocate(colors.size * 4).order(ByteOrder.nativeOrder()).apply {
            colors.forEach { putInt(it) }
            flip()
        }

    private fun capture(first: Int = Color.RED, second: Int = Color.BLUE): Bitmap =
        captures.copyFrom(pixels(first, second), 2, 1)

    @Test fun comparesActualPixelsAndReusesTwoBuffersWithoutOverwritingAcceptedFrame() {
        val first = capture()
        assertFalse(captures.isUnchanged(first))
        captures.rememberEncoded(first)
        val firstPixel = first.getPixel(0, 0)

        val second = capture(Color.GREEN)
        assertNotSame(first, second)
        assertNotEquals(firstPixel, second.getPixel(0, 0))
        assertEquals(firstPixel, first.getPixel(0, 0))
        assertFalse(captures.isUnchanged(second))
        captures.rememberEncoded(second)

        val third = capture(Color.GREEN)
        assertSame(first, third)
        assertTrue(captures.isUnchanged(third))
        val fourth = capture(Color.GREEN, Color.WHITE)
        assertSame(third, fourth)
        assertFalse(captures.isUnchanged(fourth))
        captures.rememberEncoded(fourth)
        assertSame(second, capture(Color.GREEN, Color.WHITE))
    }

    @Test fun failedOrSkippedEncodingDoesNotAdvanceComparisonBaseline() {
        val accepted = capture()
        captures.rememberEncoded(accepted)
        val failed = capture(Color.GREEN)
        assertFalse(captures.isUnchanged(failed))
        // The caller deliberately does not remember a frame whose encoder/send failed.
        val restored = capture()
        assertSame(failed, restored)
        assertTrue(captures.isUnchanged(restored))
        val retry = capture(Color.GREEN)
        assertFalse(captures.isUnchanged(retry))
        captures.rememberEncoded(retry)
        assertTrue(captures.isUnchanged(capture(Color.GREEN)))
    }

    @Test fun resetForcesFirstDeliveryWithoutReallocatingEitherBitmap() {
        val first = capture()
        captures.rememberEncoded(first)
        val second = capture()
        assertTrue(captures.isUnchanged(second))
        captures.resetComparison()
        assertSame(second, capture())
        assertFalse(captures.isUnchanged(second))
        assertFalse(first.isRecycled)
        assertFalse(second.isRecycled)
        captures.rememberEncoded(second)
        assertSame(first, capture())
        assertTrue(captures.isUnchanged(first))
    }

    @Test fun resizeRecyclesBothBuffersAndStartsANewComparison() {
        val oldFirst = capture()
        captures.rememberEncoded(oldFirst)
        val oldSecond = capture()
        // Same pixel count, different dimensions must still invalidate both buffers.
        val resized = captures.copyFrom(pixels(Color.RED, Color.BLUE), 1, 2)
        assertTrue(oldFirst.isRecycled)
        assertTrue(oldSecond.isRecycled)
        assertEquals(1, resized.width)
        assertEquals(2, resized.height)
        assertFalse(captures.isUnchanged(resized))
        captures.rememberEncoded(resized)
        assertTrue(captures.isUnchanged(captures.copyFrom(pixels(Color.RED, Color.BLUE), 1, 2)))
    }

    @Test fun clearRecyclesAllStorageAndAllowsFreshCapture() {
        val first = capture()
        captures.rememberEncoded(first)
        val second = capture()
        captures.clear()
        captures.clear()
        assertTrue(first.isRecycled)
        assertTrue(second.isRecycled)
        val fresh = capture()
        assertNotSame(first, fresh)
        assertNotSame(second, fresh)
        assertFalse(captures.isUnchanged(fresh))
    }

    @Test fun shortOrOverflowingBuffersFailBeforeChangingAcceptedFrame() {
        val first = capture()
        captures.rememberEncoded(first)
        for ((width, height) in listOf(2 to 1, Int.MAX_VALUE to Int.MAX_VALUE, 0 to 1)) {
            try {
                captures.copyFrom(ByteBuffer.allocate(4), width, height)
                fail("Invalid image must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
        assertFalse(first.isRecycled)
        assertTrue(captures.isUnchanged(capture()))
    }
}
