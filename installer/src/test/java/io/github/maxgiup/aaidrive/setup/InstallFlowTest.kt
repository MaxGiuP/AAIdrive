package io.github.maxgiup.aaidrive.setup

import android.os.Bundle
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallFlowTest {
    @Test
    fun cancelledOrIncompleteInstallStopsTheWholeSequence() {
        for (status in listOf(null, InstalledAppState.MISSING, InstalledAppState.UPDATE)) {
            val flow = InstallFlow().apply { begin("aaidrive", true); phase = InstallPhase.WAITING_INSTALL }
            assertFalse(flow.finish(status))
            assertFalse(flow.installAll)
            assertNull(flow.pendingId)
            assertEquals(InstallPhase.IDLE, flow.phase)
        }
    }

    @Test
    fun incompatibleInstalledAppNeverCountsAsSuccessfulInstallation() {
        for (status in listOf(InstalledAppState.CONFLICT, InstalledAppState.UNSUPPORTED)) {
            val flow = InstallFlow().apply { begin("headunit", true) }
            assertFalse(flow.finish(status))
            assertFalse(flow.installAll)
        }
    }

    @Test
    fun verifiedInstalledVersionCanContinueAnExplicitInstallAllRequest() {
        val flow = InstallFlow().apply { begin("aaidrive", true) }
        assertTrue(flow.finish(InstalledAppState.READY))
        assertEquals(InstallPhase.IDLE, flow.phase)
        assertNull(flow.pendingId)
    }

    @Test
    fun installingOneComponentDoesNotAuthorizeInstallingOthers() {
        val flow = InstallFlow().apply { begin("headunit", false) }
        assertFalse(flow.finish(InstalledAppState.READY))
        assertFalse(flow.installAll)
    }

    @Test
    fun restoringAnOpenAndroidPromptKeepsWaitingInsteadOfRelaunchingIt() {
        for (phase in listOf(InstallPhase.WAITING_INSTALL, InstallPhase.WAITING_PERMISSION)) {
            val original = InstallFlow().apply { begin("projection", true); this.phase = phase }
            val saved = Bundle().also(original::save)
            val restored = InstallFlow().apply { restore(saved) }
            assertEquals(phase, restored.phase)
            assertEquals("projection", restored.pendingId)
            assertTrue(restored.installAll)
        }
    }

    @Test
    fun stoppingPreparationCannotBeResumedByALateInstallResult() {
        val flow = InstallFlow().apply { begin("projection", true); phase = InstallPhase.PREPARING }
        flow.cancel()
        assertEquals(InstallPhase.IDLE, flow.phase)
        assertNull(flow.pendingId)
        assertFalse(flow.finish(InstalledAppState.READY))
    }

    @Test
    fun processRecreationRemembersWhichApkWasBeingVerified() {
        val original = InstallFlow().apply { begin("headunit", true); phase = InstallPhase.PREPARING }
        val saved = Bundle().also(original::save)
        val restored = InstallFlow().apply { restore(saved) }
        assertEquals(InstallPhase.PREPARING, restored.phase)
        assertEquals("headunit", restored.pendingId)
        assertTrue(restored.installAll)
    }

    @Test
    fun cancellingDuringPackageRecheckCannotLeaveTheUiPermanentlyLoading() {
        val model = SetupModel(RuntimeEnvironment.getApplication())
        val store = ViewModelStore().apply { put("setup", model) }
        try {
            ReflectionHelpers.setField(model, "apps", listOf(bundledTestApp()))
            ReflectionHelpers.setField(model, "loading", true)
            model.flow.begin("aaidrive", true)
            model.flow.phase = InstallPhase.CHECKING_RESULT
            model.cancel()
            assertFalse(model.loading)
            assertEquals(InstallPhase.IDLE, model.flow.phase)
            assertFalse(model.flow.installAll)
        } finally {
            store.clear()
        }
    }
}
