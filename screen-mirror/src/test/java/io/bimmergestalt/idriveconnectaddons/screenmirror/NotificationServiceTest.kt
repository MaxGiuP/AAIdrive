package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.app.NotificationManager
import android.app.Service
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationServiceTest {
    @After
    fun tearDown() {
        ProjectionSession.stop()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun reminderNeverStartsForegroundServiceWithoutConsent() {
        val context = RuntimeEnvironment.getApplication()
        NotificationService.startNotification(context, true)

        assertNull(shadowOf(context).nextStartedService)
        assertNotNull(shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(NotificationService.PERMISSION_NOTIFICATION_ID))
        assertFalse(NotificationService.shouldBeForeground())
    }

    @Test
    fun processRestartDoesNotAttemptToReuseConsent() {
        val controller = Robolectric.buildService(NotificationService::class.java).create()
        try {
            assertEquals(Service.START_NOT_STICKY, controller.get().onStartCommand(null, 0, 1))
            assertTrue(shadowOf(controller.get()).isStoppedBySelf)
            assertNull(ProjectionSession.currentProjection)
        } finally {
            controller.destroy()
        }
    }
}
