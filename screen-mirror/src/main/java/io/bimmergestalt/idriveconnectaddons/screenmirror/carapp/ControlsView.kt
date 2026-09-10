package io.bimmergestalt.idriveconnectaddons.screenmirror.carapp

import android.content.Context
import android.view.KeyEvent
import android.os.Handler
import io.bimmergestalt.idriveconnectaddons.screenmirror.OpenHeadunitController
import io.bimmergestalt.idriveconnectaddons.screenmirror.R
import io.bimmergestalt.idriveconnectkit.rhmi.*

/** The BMW Back button returns here from the image; its normal exit behavior is retained. */
class ControlsView(val state: RHMIState, imageState: RHMIState,
                   context: Context, controller: OpenHeadunitController, handler: Handler) {
    init {
        state.getTextModel()?.asRaDataModel()?.value = context.getString(R.string.car_controls)
        state.componentsList.forEach { it.setVisible(false) }
        val list = state.componentsList.filterIsInstance<RHMIComponent.List>().first()
        val keys = listOf(null, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK)
        val labels = context.resources.getStringArray(R.array.car_commands)
        list.getModel()?.value = RHMIModel.RaListModel.RHMIListConcrete(1).also { model ->
            labels.forEach { model.addRow(arrayOf(it)) }
        }
        list.setProperty(RHMIProperty.PropertyId.LIST_COLUMNWIDTH, "*")
        list.setVisible(true)
        list.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionListCallback { index ->
            if (index == 0) {
                list.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = imageState.id
            } else {
                list.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = state.id
                keys.getOrNull(index)?.let { key -> handler.post { controller.press(key) } }
            }
        }
        state.focusCallback = FocusCallback { focused ->
            handler.post { if (focused) controller.resume() else controller.pause() }
        }
    }
}
