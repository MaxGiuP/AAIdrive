package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.os.Handler
import android.os.Looper
import io.bimmergestalt.idriveconnectaddons.screenmirror.carapp.views.ImageState
import io.bimmergestalt.idriveconnectkit.GenericRHMIDimensions
import io.bimmergestalt.idriveconnectkit.rhmi.*
import io.bimmergestalt.idriveconnectkit.rhmi.deserialization.loadFromXML
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ImageStateTest {
    private lateinit var carApp: RHMIApplicationConcrete
    private lateinit var provider: ScreenMirrorProvider
    private lateinit var view: ImageState
    private val scrolls = mutableListOf<Boolean>()
    private val controlFocus = mutableListOf<Boolean>()
    private var clicks = 0

    @Before fun setUp() {
        ProjectionSession.stop()
        carApp = RHMIApplicationConcrete()
        val description = RuntimeEnvironment.getApplication().assets
            .open("carapplications/smartthings/rhmi/ui_description.xml").use { it.readBytes() }
        carApp.loadFromXML(description)
        provider = ScreenMirrorProvider(Handler(Looper.getMainLooper()))
        view = ImageState(carApp.states.values.first { ImageState.fits(it) }, provider,
            GenericRHMIDimensions(1280, 480),
            onFocusChanged = { controlFocus.add(it) },
            onScroll = { scrolls.add(it) }, onClick = { clicks++ })
        view.initWidgets()
        idle()
    }

    @After fun tearDown() {
        provider.stop()
        ProjectionSession.stop()
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun focus(value: Boolean) {
        view.state.onHmiEvent(1, mapOf(4.toByte() to value))
        idle()
    }
    private fun frame() {
        provider.callback!!.invoke(byteArrayOf(1, 2, 3))
    }
    private fun select(index: Int) {
        view.infoList.getSelectAction()!!.asRAAction()!!.rhmiActionCallback!!
            .onActionEvent(mapOf(1.toByte() to index))
        idle()
    }
    private fun click(invokedBy: Int = 0) {
        view.infoList.getAction()!!.asRAAction()!!.rhmiActionCallback!!
            .onActionEvent(mapOf(1.toByte() to 3, 43.toByte() to invokedBy))
        idle()
    }
    private fun imageVisible() = view.image.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value
    private fun rowCount() = (view.infoList.getModel()!!.value as RHMIModel.RaListModel.RHMIList).height

    @Test fun inheritedSmartThingsArtworkAndLabelsRemainHidden() {
        val unused = view.state.componentsList.filter { it !== view.image && it !== view.infoList }
        assertTrue(unused.isNotEmpty())
        unused.forEach { assertEquals(false, it.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value) }
        focus(true)
        frame()
        unused.forEach { assertEquals(false, it.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value) }
        assertEquals(true, view.image.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
        assertEquals(true, view.infoList.properties[RHMIProperty.PropertyId.VISIBLE.id]?.value)
    }

    @Test fun inputsStayDisabledUntilFirstFrameAndBookmarksNeverClick() {
        focus(true)
        assertEquals(false, imageVisible())
        assertEquals(1, rowCount())
        click()
        select(2)
        assertEquals(0, clicks)
        assertTrue(scrolls.isEmpty())

        frame()
        assertEquals(true, imageVisible())
        assertEquals(7, rowCount())
        assertEquals(-50000, view.infoList.properties[RHMIProperty.PropertyId.POSITION_X.id]?.value)
        click(2)
        assertEquals(0, clicks)
        click()
        assertEquals(1, clicks)
    }

    @Test fun rotaryStepsUseDirectionAndRecenterWithoutRecursion() {
        focus(true)
        frame()
        val focusEvent = carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first()
        select(2)
        assertEquals(listOf(true), scrolls)
        assertEquals(view.infoList.id, carApp.triggeredEvents[focusEvent.id]?.get(0))
        assertEquals(3, carApp.triggeredEvents[focusEvent.id]?.get(41))
        select(4)
        assertEquals(listOf(true, false), scrolls)
        carApp.triggeredEvents.clear()
        select(3)
        select(-1)
        select(7)
        assertEquals(listOf(true, false), scrolls)
        assertTrue(carApp.triggeredEvents.isEmpty())
    }

    @Test fun subsequentFramesAndDuplicateFocusDoNotStealRotaryFocus() {
        focus(true)
        frame()
        carApp.triggeredEvents.clear()
        frame()
        focus(true)
        assertTrue(carApp.triggeredEvents.isEmpty())
        assertEquals(true, imageVisible())
        assertEquals(7, rowCount())
    }

    @Test fun focusLossClearsImageCallbacksAndIgnoresLateFrame() {
        focus(true)
        frame()
        val staleFrame = provider.callback!!
        focus(false)
        assertEquals(false, imageVisible())
        assertEquals(1, rowCount())
        assertNull(provider.callback)
        assertNull(provider.onStateChanged)
        staleFrame(byteArrayOf(4, 5, 6))
        select(2)
        click()
        assertEquals(false, imageVisible())
        assertTrue(scrolls.isEmpty())
        assertEquals(0, clicks)
        assertEquals(false, controlFocus.last())
    }

    @Test fun revocationRestoresPromptAndNewFrameCanResumeControls() {
        focus(true)
        frame()
        provider.onStateChanged!!.invoke(MirroringState.NOT_ALLOWED)
        assertEquals(false, imageVisible())
        assertEquals(1, rowCount())
        assertEquals(0, view.infoList.properties[RHMIProperty.PropertyId.POSITION_X.id]?.value)
        select(4)
        click()
        assertEquals(0, clicks)
        assertTrue(scrolls.isEmpty())
        assertEquals(false, controlFocus.last())
        frame()
        assertEquals(true, imageVisible())
        assertEquals(true, controlFocus.last())
        click()
        assertEquals(1, clicks)
    }
}
