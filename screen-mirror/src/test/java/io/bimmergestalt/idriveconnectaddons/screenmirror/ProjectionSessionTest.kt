package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
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
class ProjectionSessionTest {
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(MediaProjection?) -> Unit>()

    @Before
    fun setUp() {
        ProjectionSession.stop()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        listeners.forEach(ProjectionSession::removeListener)
        ProjectionSession.stop()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun listen(listener: (MediaProjection?) -> Unit) {
        listeners.add(listener)
        ProjectionSession.addListener(handler, listener)
    }

    private fun callback(projection: MediaProjection): MediaProjection.Callback =
        mockingDetails(projection).invocations.single { it.method.name == "registerCallback" }
            .arguments[0] as MediaProjection.Callback

    @Test
    fun callbackIsRegisteredBeforeProviderSeesProjection() {
        val projection = mock(MediaProjection::class.java)
        var received = false
        listen {
            if (it != null) {
                assertSame(projection, it)
                assertNotNull(callback(it))
                received = true
            }
        }
        ProjectionSession.start(projection, handler) {}
        assertFalse(received)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(received)
    }

    @Test
    fun consentTokenCanCreateOnlyOneDisplay() {
        val projection = mock(MediaProjection::class.java)
        val display = mock(VirtualDisplay::class.java)
        ProjectionSession.start(projection, handler) {}

        assertSame(display, ProjectionSession.claimVirtualDisplay(projection) { display })
        assertNull(ProjectionSession.claimVirtualDisplay(projection) { error("Token reused") })
        assertSame(projection, ProjectionSession.currentProjection)
        verify(projection, never()).stop()
    }

    @Test
    fun displayCreationFailureRevokesConsumedSession() {
        val projection = mock(MediaProjection::class.java)
        var stops = 0
        ProjectionSession.start(projection, handler) { stops++ }

        assertThrows(SecurityException::class.java) {
            ProjectionSession.claimVirtualDisplay(projection) { throw SecurityException("Token expired") }
        }
        assertNull(ProjectionSession.currentProjection)
        assertNull(ProjectionSession.claimVirtualDisplay(projection) { error("Failed token reused") })
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, stops)
        verify(projection).stop()
    }

    @Test
    fun androidRevocationNotifiesOwnerOnlyOnce() {
        val projection = mock(MediaProjection::class.java)
        var stops = 0
        ProjectionSession.start(projection, handler) { stops++ }
        val callback = callback(projection)
        callback.onStop()
        callback.onStop()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(ProjectionSession.currentProjection)
        assertEquals(MirroringState.NOT_ALLOWED, ScreenMirrorProvider.state.value)
        assertEquals(1, stops)
        verify(projection, never()).stop()
        verify(projection).unregisterCallback(callback)
    }

    @Test
    fun oldRevocationAndOldOwnerCannotStopReplacementSession() {
        val old = mock(MediaProjection::class.java)
        val replacement = mock(MediaProjection::class.java)
        ProjectionSession.start(old, handler) {}
        val oldCallback = callback(old)
        ProjectionSession.start(replacement, handler) {}

        oldCallback.onStop()
        ProjectionSession.stop(old)
        shadowOf(Looper.getMainLooper()).idle()

        assertSame(replacement, ProjectionSession.currentProjection)
        verify(old).stop()
        verify(replacement, never()).stop()
    }

    @Test
    fun removingListenerCancelsQueuedDelivery() {
        var notifications = 0
        val listener: (MediaProjection?) -> Unit = { notifications++ }
        listen(listener)
        ProjectionSession.start(mock(MediaProjection::class.java), handler) {}
        ProjectionSession.removeListener(listener)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, notifications)
    }

    @Test
    fun queuedStartCannotRestoreRevokedProjection() {
        val received = mutableListOf<MediaProjection?>()
        listen { received.add(it) }
        ProjectionSession.start(mock(MediaProjection::class.java), handler) {}
        ProjectionSession.stop()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(received.isNotEmpty())
        assertTrue(received.all { it == null })
    }

    @Test
    fun revocationDuringDisplayCreationReleasesNewDisplay() {
        val projection = mock(MediaProjection::class.java)
        val display = mock(VirtualDisplay::class.java)
        ProjectionSession.start(projection, handler) {}
        val result = ProjectionSession.claimVirtualDisplay(projection) {
            callback(projection).onStop()
            display
        }
        assertNull(result)
        verify(display).release()
    }
}
