package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenMirrorProviderTest {
    private val carHandler = Handler(Looper.getMainLooper())
    private lateinit var worker: HandlerThread
    private lateinit var provider: ScreenMirrorProvider
    private var stops = 0

    @Before fun setUp() {
        ProjectionSession.stop()
        worker = HandlerThread("TestCapture").apply { start() }
        provider = ScreenMirrorProvider(carHandler, Handler(worker.looper)) { stops++ }
        shadowOf(worker.looper).idle()
    }

    @After fun tearDown() {
        provider.stop()
        shadowOf(worker.looper).idle()
        ProjectionSession.stop()
        shadowOf(carHandler.looper).idle()
        worker.quitSafely()
        worker.join(3000)
    }

    @Test fun bluetoothAndUsbBothAllowThirtyFpsAndProfileChangesRespectMotionLimit() {
        for (bluetooth in listOf(true, false)) {
            provider.configure(ProjectionProfile.SMOOTH, bluetooth)
            shadowOf(worker.looper).idle()
            assertEquals(33, provider.minFrameTime)
            provider.setMotionLimited(true)
            provider.configure(ProjectionProfile.SHARP, bluetooth)
            shadowOf(worker.looper).idle()
            assertEquals(1000, provider.minFrameTime)
            provider.setMotionLimited(false)
            shadowOf(worker.looper).idle()
            assertEquals(100, provider.minFrameTime)
        }
    }

    @Test fun captureWorkerRoutesStateCallbacksToCarHandlerAndCleansUpOnce() {
        val projection = mock(MediaProjection::class.java)
        val callbackLoopers = mutableListOf<Looper?>()
        provider.onStateChanged = { callbackLoopers.add(Looper.myLooper()) }
        ProjectionSession.start(projection, carHandler) {}
        shadowOf(worker.looper).idle()
        assertTrue(callbackLoopers.isEmpty())
        shadowOf(carHandler.looper).idle()
        assertEquals(listOf(carHandler.looper), callbackLoopers)

        provider.stop()
        provider.stop()
        shadowOf(worker.looper).idle()
        assertEquals(1, stops)
        assertNull(provider.callback)
        assertNull(provider.onStateChanged)
        assertNull(ProjectionSession.currentProjection)
        verify(projection).stop()
    }
}
