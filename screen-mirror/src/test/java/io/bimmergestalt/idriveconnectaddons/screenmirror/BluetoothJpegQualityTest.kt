package io.bimmergestalt.idriveconnectaddons.screenmirror

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothJpegQualityTest {
    @Test
    fun firstSendInitializationDoesNotCountAsCongestion() {
        val policy = BluetoothJpegQuality()
        repeat(3) { assertEquals(30, policy.onFrameSent(1000)) }
        assertEquals(25, policy.onFrameSent(1000))
    }

    @Test
    fun isolatedSlowFramesAndThresholdBoundariesDoNotReduceQuality() {
        val policy = BluetoothJpegQuality()
        policy.onFrameSent(100)
        repeat(5) {
            assertEquals(30, policy.onFrameSent(400))
            assertEquals(30, policy.onFrameSent(400))
            assertEquals(30, policy.onFrameSent(375))
        }
    }

    @Test
    fun qualityRecoversGraduallyOnlyAfterTwelveConsecutiveFastSends() {
        val policy = congestedPolicy()
        for (expected in listOf(27, 29, 30)) {
            val previous = policy.quality
            repeat(11) { assertEquals(previous, policy.onFrameSent(124)) }
            assertEquals(expected, policy.onFrameSent(124))
        }
    }

    @Test
    fun normalOrSlowSendBreaksARecoveryStreak() {
        val policy = congestedPolicy()
        for (interruption in listOf(125L, 200L, 375L, 500L)) {
            repeat(11) { policy.onFrameSent(100) }
            assertEquals(25, policy.onFrameSent(interruption))
        }
        repeat(11) { assertEquals(25, policy.onFrameSent(100)) }
        assertEquals(27, policy.onFrameSent(100))
    }

    @Test
    fun repeatedCongestionAndRecoveryStayWithinExistingQualityBounds() {
        val policy = BluetoothJpegQuality()
        repeat(100) { policy.onFrameSent(2000) }
        assertEquals(25, policy.quality)
        repeat(100) { policy.onFrameSent(0) }
        assertEquals(30, policy.quality)
    }

    @Test
    fun resetRestoresBaselineAndExcludesTheNewFirstSend() {
        val policy = congestedPolicy()
        repeat(11) { policy.onFrameSent(100) }
        policy.reset()
        assertEquals(30, policy.quality)
        repeat(3) { assertEquals(30, policy.onFrameSent(500)) }
        assertEquals(25, policy.onFrameSent(500))
    }

    @Test
    fun invalidTimingCannotContinueALatencyStreak() {
        val policy = BluetoothJpegQuality()
        policy.onFrameSent(500)
        repeat(2) { policy.onFrameSent(500) }
        assertEquals(30, policy.onFrameSent(-1))
        repeat(2) { assertEquals(30, policy.onFrameSent(500)) }
        assertEquals(25, policy.onFrameSent(500))
    }

    private fun congestedPolicy() = BluetoothJpegQuality().apply {
        repeat(4) { onFrameSent(500) }
    }
}
