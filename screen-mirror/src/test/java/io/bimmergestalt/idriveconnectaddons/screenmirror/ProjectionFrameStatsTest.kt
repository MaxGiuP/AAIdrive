package io.bimmergestalt.idriveconnectaddons.screenmirror

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class ProjectionFrameStatsTest {
    @Test
    fun aggregatesFramesAndSuccessfulSendsOverFiveSeconds() {
        val stats = ProjectionFrameStats().apply { reset(0) }
        assertNull(stats.reportFrame(100, 800, 480, 250, 30, 4, 8, 100, 2000))
        assertNull(stats.reportFrame(4999, 800, 480, 250, 30, 2, 0, 0,
            unchanged = true))
        val summary = stats.reportFrame(5000, 800, 480, 250, 25, 6, 12, 400, 4000)!!
        assertEquals(5000L, summary.elapsedMs)
        assertEquals(3L, summary.frames)
        assertEquals(2L, summary.sent)
        assertEquals(1L, summary.unchanged)
        assertEquals(0L, summary.jpegDuplicates)
        assertEquals(6000L, summary.bytes)
        assertEquals(25, summary.quality)
        assertEquals(0.4, summary.sendsPerSecond, 0.0001)
        assertEquals(3000.0, summary.averageSentBytes, 0.0001)
        assertEquals(4.0, summary.averageCaptureCopyMs, 0.0001)
        assertEquals(10.0, summary.averageEncodeMs, 0.0001)
        assertEquals(250.0, summary.averageSendMs, 0.0001)
    }

    @Test
    fun emittedWindowDoesNotLeakCountersIntoTheNextWindow() {
        val stats = ProjectionFrameStats().apply { reset(0) }
        stats.reportFrame(5000, 800, 480, 250, 30, 1, 2, 3, 100)
        assertNull(stats.reportFrame(9999, 800, 480, 1000, 25, 5, 0, 0,
            unchanged = true))
        val next = stats.reportFrame(10000, 800, 480, 1000, 25, 7, 0, 0,
            unchanged = true)!!
        assertEquals(2L, next.frames)
        assertEquals(0L, next.sent)
        assertEquals(0L, next.bytes)
        assertEquals(2L, next.unchanged)
        assertEquals(1000, next.intervalMs)
        assertEquals(0.0, next.sendsPerSecond, 0.0)
        assertEquals(0.0, next.averageSendMs, 0.0)
        assertEquals(0.0, next.averageEncodeMs, 0.0)
    }

    @Test
    fun explicitResetExcludesPausedTimeAndPreviousSamples() {
        val stats = ProjectionFrameStats().apply { reset(0) }
        stats.reportFrame(1000, 800, 480, 250, 30, 1, 2, 3, 100)
        stats.reset(100000)
        assertNull(stats.reportFrame(100001, 800, 480, 250, 30, 4, 5, 6, 200))
        val next = stats.reportFrame(105000, 800, 480, 250, 30, 4, 5, 6, 200)!!
        assertEquals(5000L, next.elapsedMs)
        assertEquals(2L, next.sent)
        assertEquals(400L, next.bytes)
    }

    @Test
    fun jpegDuplicateCountsAsEncodedButNotTransmitted() {
        val stats = ProjectionFrameStats().apply { reset(0) }
        val summary = stats.reportFrame(5000, 800, 480, 250, 30, 2, 7, 0,
            jpegDuplicate = true)!!
        assertEquals(0L, summary.unchanged)
        assertEquals(1L, summary.jpegDuplicates)
        assertEquals(0L, summary.sent)
        assertEquals(7.0, summary.averageEncodeMs, 0.0)
    }

    @Test
    fun firstSampleStartsAWindowAndBackwardClockResetsIt() {
        val stats = ProjectionFrameStats()
        assertNull(stats.reportFrame(20000, 800, 480, 250, 30, 1, 2, 3, 100))
        assertNull(stats.reportFrame(10000, 800, 480, 250, 30, 1, 2, 3, 100))
        val summary = stats.reportFrame(15000, 800, 480, 250, 30, 1, 2, 3, 100)!!
        assertEquals(2L, summary.sent)
        assertEquals(200L, summary.bytes)
        assertEquals(5000L, summary.elapsedMs)
    }

    @Test
    fun logOutputIsNumericAndStableAcrossPhoneLocales() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val stats = ProjectionFrameStats().apply { reset(0) }
            val summary = stats.reportFrame(5000, 800, 480, 250, 30, 2, 3, 100, 2500)!!
            assertEquals("ProjectionPerf window_ms=5000 width=800 height=480 interval_ms=250 quality=30 frames=1 sent=1 unchanged=0 jpeg_duplicates=0 bytes=2500 sends_per_s=0.20 avg_bytes=2500.0 copy_ms=2.00 encode_ms=3.00 send_ms=100.00", summary.logLine())
        } finally {
            Locale.setDefault(original)
        }
    }
}
