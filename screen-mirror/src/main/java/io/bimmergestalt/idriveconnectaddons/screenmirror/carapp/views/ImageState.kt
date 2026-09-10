package io.bimmergestalt.idriveconnectaddons.screenmirror.carapp.views

import io.bimmergestalt.idriveconnectaddons.screenmirror.L
import io.bimmergestalt.idriveconnectaddons.screenmirror.MirroringState
import io.bimmergestalt.idriveconnectaddons.screenmirror.ScreenMirrorProvider
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import io.bimmergestalt.idriveconnectkit.rhmi.*

class ImageState(val state: RHMIState, val screenMirrorProvider: ScreenMirrorProvider,
                 val rhmiDimensions: RHMIDimensions,
                 val onFocusChanged: (Boolean) -> Unit = {},
                 val onScroll: (Boolean) -> Unit = {},
                 val onClick: () -> Unit = {}) {
    companion object {
        fun fits(state: RHMIState): Boolean {
            return state is RHMIState.PlainState &&
                state.componentsList.filterIsInstance<RHMIComponent.Image>().any {
                    it.getModel() is RHMIModel.RaImageModel
                } && state.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
        }
    }

    val image = state.componentsList.filterIsInstance<RHMIComponent.Image>().first {
        it.getModel() is RHMIModel.RaImageModel
    }
    val imageModel = image.getModel()?.asRaImageModel()!!
    val infoList = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
    private val focusEvent = state.app.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first()
    private var showingImage: Boolean? = null
    private var focused = false
    private var controlFocused = false
    private val permissionRows = RHMIModel.RaListModel.RHMIListConcrete(1).apply {
        addRow(arrayOf("${L.PERMISSION_PROMPT}\n"))
    }
    private val inputRows = RHMIModel.RaListModel.RHMIListConcrete(1).apply {
        repeat(7) { addRow(arrayOf("")) }
    }

    fun initWidgets() {
        // The borrowed SmartThings layout also contains artwork and labels unrelated to capture.
        state.componentsList.forEach { it.setVisible(false) }
        showingImage = null
        state.setProperty(RHMIProperty.PropertyId.HMISTATE_TABLETYPE, 3)
        state.setProperty(RHMIProperty.PropertyId.HMISTATE_TABLELAYOUT, "1,0,7")
        state.getTextModel()?.asRaDataModel()?.value = L.MIRRORING_TITLE
        image.setProperty(RHMIProperty.PropertyId.WIDTH, rhmiDimensions.visibleWidth)
        image.setProperty(RHMIProperty.PropertyId.HEIGHT, rhmiDimensions.visibleHeight)
        image.setProperty(RHMIProperty.PropertyId.POSITION_X, -rhmiDimensions.paddingLeft)
        image.setProperty(RHMIProperty.PropertyId.POSITION_Y, -rhmiDimensions.paddingTop)
        infoList.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "*")
        infoList.setProperty(RHMIProperty.PropertyId.BOOKMARKABLE, false)
        infoList.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = state.id
        showPermissionPrompt()

        // The hidden list follows the existing full-map input pattern: center after each wheel step.
        infoList.getSelectAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
            screenMirrorProvider.handler.post {
                if (focused && showingImage == true && index in 0..6 && index != 3) {
                    onScroll(index < 3)
                    focusInput()
                }
            }
        }
        infoList.getAction()?.asRAAction()?.rhmiActionCallback = object : RHMIActionButtonCallback {
            override fun onAction(invokedBy: Int?) {
                screenMirrorProvider.handler.post {
                    if (focused && showingImage == true && invokedBy != 2) onClick()
                }
            }
        }
        state.focusCallback = FocusCallback { nextFocused ->
            screenMirrorProvider.handler.post {
                if (focused == nextFocused) return@post
                focused = nextFocused
                notifyControlFocus(nextFocused)
                if (nextFocused) {
                    // A fresh image is required before enabling controls or showing old pixels.
                    showPermissionPrompt()
                    screenMirrorProvider.onStateChanged = { next ->
                        if (next != MirroringState.ACTIVE) {
                            showPermissionPrompt()
                            notifyControlFocus(false)
                        }
                    }
                    screenMirrorProvider.callback = { bytes ->
                        if (focused) {
                            imageModel.value = bytes
                            showImage()
                        }
                    }
                    screenMirrorProvider.start()
                } else {
                    showPermissionPrompt()
                    screenMirrorProvider.callback = null
                    screenMirrorProvider.onStateChanged = null
                    screenMirrorProvider.pause()
                }
            }
        }
    }

    private fun focusInput() {
        focusEvent.triggerEvent(mapOf(0 to infoList.id, 41 to 3))
    }

    private fun notifyControlFocus(active: Boolean) {
        if (controlFocused == active) return
        controlFocused = active
        onFocusChanged(active)
    }

    private fun showImage() {
        if (showingImage == true) return
        infoList.getModel()?.value = inputRows
        infoList.setProperty(RHMIProperty.PropertyId.POSITION_X, -50000)
        infoList.setProperty(RHMIProperty.PropertyId.POSITION_Y, 0)
        infoList.setVisible(true)
        image.setVisible(true)
        showingImage = true
        notifyControlFocus(true)
        focusInput()
    }

    private fun showPermissionPrompt() {
        if (showingImage == false) return
        image.setVisible(false)
        infoList.getModel()?.value = permissionRows
        infoList.setProperty(RHMIProperty.PropertyId.POSITION_X, 0)
        infoList.setProperty(RHMIProperty.PropertyId.POSITION_Y, 0)
        infoList.setVisible(true)
        showingImage = false
    }
}
