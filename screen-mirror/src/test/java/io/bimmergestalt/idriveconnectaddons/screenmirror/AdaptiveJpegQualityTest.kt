package io.bimmergestalt.idriveconnectaddons.screenmirror

import org.junit.Assert.*
import org.junit.Test

class AdaptiveJpegQualityTest {
    @Test fun thirtyFpsUsesThirtyThreeMillisecondBudgetAndIgnoresFirstSendSetup() {
        val policy = AdaptiveJpegQuality(33, JpegLimits(18, 30, 40))
        policy.onFrameSent(1000)
        repeat(2) { policy.onFrameSent(34) }
        assertEquals(30, policy.quality)
        policy.onFrameSent(34)
        assertEquals(25, policy.quality)
    }

    @Test fun adaptationHasReadabilityFloorAndRecoversGradually() {
        val policy = AdaptiveJpegQuality(33, JpegLimits(18, 30, 40))
        repeat(100) { policy.onFrameSent(100) }
        assertEquals(18, policy.quality)
        repeat(29) { policy.onFrameSent(10) }
        assertEquals(18, policy.quality)
        policy.onFrameSent(10)
        assertEquals(20, policy.quality)
        repeat(1000) { policy.onFrameSent(10) }
        assertEquals(40, policy.quality)
    }

    @Test fun slowerProfileDoesNotCompressForSendsInsideItsBudget() {
        val policy = AdaptiveJpegQuality(100, JpegLimits(35, 55, 70))
        repeat(100) { policy.onFrameSent(85) }
        assertEquals(55, policy.quality)
        repeat(3) { policy.onFrameSent(101) }
        assertEquals(50, policy.quality)
    }

    @Test fun isolatedSlowTransfersDoNotRatchetQualityDown() {
        val policy = AdaptiveJpegQuality(33, JpegLimits(18, 30, 40))
        repeat(100) {
            policy.onFrameSent(100)
            policy.onFrameSent(100)
            policy.onFrameSent(30)
        }
        assertEquals(30, policy.quality)
    }
}
