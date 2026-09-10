package io.bimmergestalt.idriveconnectaddons.screenmirror.carapp

import android.app.UiModeManager
import android.content.Context
import android.view.KeyEvent
import android.content.res.Configuration
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import io.bimmergestalt.idriveconnectaddons.lib.CarCapabilities
import io.bimmergestalt.idriveconnectaddons.screenmirror.*
import io.bimmergestalt.idriveconnectaddons.screenmirror.carapp.views.ImageState
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import io.bimmergestalt.idriveconnectkit.RHMIUtils.rhmi_setResourceCached
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.*
import io.bimmergestalt.idriveconnectkit.rhmi.deserialization.loadFromXML
import org.json.JSONException
import org.json.JSONObject

class CarApp(val iDriveConnectionStatus: IDriveConnectionStatus, securityAccess: SecurityAccess,
             val carAppResources: CarAppResources, val androidResources: AndroidResources, val carCapabilities: CarCapabilities,
             val uiModeManager: UiModeManager, val screenMirrorProvider: ScreenMirrorProvider,
             val context: Context, val controller: OpenHeadunitController,
             val baselineFrameTime: Int, val onEntry: () -> Unit) {
    private val carConnection: BMWRemotingServer
    private val rhmiDimensions: RHMIDimensions
    private var amHandle = -1
    private var carApp: RHMIApplication
    private lateinit var stateImage: ImageState
    private lateinit var controlsView: ControlsView

    init {
        Log.i(TAG, "Starting connecting to car")
        val carappListener = CarAppListener()
        carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, carappListener)
        try {
        val appCert = carAppResources.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes()
        val sas_challenge = carConnection.sas_certificate(appCert)
        val sas_response = securityAccess.signChallenge(challenge = sas_challenge)
        carConnection.sas_login(sas_response)

        // set the capture size based on the car's screen size
        val preloadedCapabilities = carCapabilities.getCapabilities()
        val capabilities = if (preloadedCapabilities.containsKey("hmi.display-width")) {
            preloadedCapabilities
        } else {
            carConnection.rhmi_getCapabilities("", 255).map { it.key as String to it.value as String }.toMap()
        }
        rhmiDimensions = RHMIDimensions.create(capabilities)
        screenMirrorProvider.setSize(rhmiDimensions.visibleWidth, rhmiDimensions.visibleHeight)

        createAmApp()

        createCdsSubscription()

        carApp = createRhmiApp()
        createViews()

        initWidgets()
        Log.i(TAG, "CarApp running")
        } catch (error: Exception) {
            try { IDriveConnection.disconnectEtchConnection(carConnection) }
            catch (_: Exception) { }
            throw error
        }
    }

    private fun createAmApp() {
        if (amHandle < 0) {
            val handle = carConnection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
            carConnection.am_addAppEventHandler(handle, BuildConfig.APPLICATION_ID)
            amHandle = handle
        }

        val amInfo = mutableMapOf<Int, Any>(
            0 to 145,   // basecore version
            1 to L.MIRRORING_TITLE,  // app name
            2 to androidResources.getRaw(R.raw.ic_carapp), // icon
            3 to "Multimedia",   // section
            4 to true,
            5 to 1500,   // weight
            8 to -1  // mainstateId
        )
        // language translations, dunno which one is which
        for (languageCode in 101..123) {
            amInfo[languageCode] = L.MIRRORING_TITLE
        }
        carConnection.am_registerApp(amHandle, BuildConfig.APPLICATION_ID, amInfo)
    }

    private fun createRhmiApp(): RHMIApplication {
        // create the app in the car
        val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData(BuildConfig.APPLICATION_ID, BMWRemoting.VersionInfo(0, 1, 0), BuildConfig.APPLICATION_ID, "io.bimmergestalt"))
        carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppResources.getUiDescription())
//        carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppResources.getTextsDB(iDriveConnectionStatus.brand ?: "common"))
        carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppResources.getImagesDB(iDriveConnectionStatus.brand ?: "common"))
        carConnection.rhmi_initialize(rhmiHandle)

        // register for events from the car
        carConnection.rhmi_addActionEventHandler(rhmiHandle, BuildConfig.APPLICATION_ID, -1)
        carConnection.rhmi_addHmiEventHandler(rhmiHandle, BuildConfig.APPLICATION_ID, -1, -1)

        return RHMIApplicationSynchronized(RHMIApplicationIdempotent(
            RHMIApplicationEtch(carConnection, rhmiHandle)
        ), carConnection).apply {
            loadFromXML(carAppResources.getUiDescription()!!.readBytes())
        }
    }

    private fun recreateRhmiApp() {
        // Called on the capture handler, so no frame can target the disposed application.
        screenMirrorProvider.callback = null
        screenMirrorProvider.onStateChanged = null
        screenMirrorProvider.pause()
        controller.pause()
        synchronized(carConnection) {
            val oldApp = carApp
            if (oldApp is RHMIApplicationWrapper) {
                // tear down
                val etchApp = oldApp.unwrap() as RHMIApplicationEtch
                carConnection.rhmi_dispose(etchApp.rhmiHandle)
                // recreate
                carApp = createRhmiApp()
                createViews()
                initWidgets()
            }
        }
    }

    private fun createViews() {
        stateImage = ImageState(carApp.states.values.first { ImageState.fits(it) }, screenMirrorProvider, rhmiDimensions,
            onFocusChanged = { if (it) controller.resume() else controller.pause() },
            onScroll = { controller.press(if (it) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN) },
            onClick = { controller.press(KeyEvent.KEYCODE_DPAD_CENTER) })
        val menu = carApp.states.values.first {
            it is RHMIState.PlainState && it.id != stateImage.state.id &&
                it.componentsList.filterIsInstance<RHMIComponent.List>().isNotEmpty()
        }
        controlsView = ControlsView(menu, stateImage.state, context, controller, screenMirrorProvider.handler)
    }

    private fun initWidgets() {
        carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
            it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = controlsView.state.id
            it.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionCallback {
                onEntry()
            }
        }
        stateImage.initWidgets()
    }

    fun createCdsSubscription() {
        val cdsHandle = carConnection.cds_create()
        carConnection.cds_addPropertyChangedEventHandler(cdsHandle, CDS.DRIVING.SPEEDACTUAL.propertyName, CDS.DRIVING.SPEEDACTUAL.ident.toString(), 2000)
    }

    fun onDestroy() {
        try {
            controller.close()
        } finally {
            try { screenMirrorProvider.stop() } finally {
                try { IDriveConnection.disconnectEtchConnection(carConnection) }
                catch (e: Exception) { Log.d(TAG, "Car connection already closed", e) }
            }
        }
    }

    inner class CarAppListener(): BaseBMWRemotingClient() {
        override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?
        ) {
            screenMirrorProvider.handler.post { handleEntry() }
        }

        private fun handleEntry() {
            onEntry()
            val focusEvent = carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first()
            try {
                focusEvent.triggerEvent(mapOf(0.toByte() to controlsView.state.id))
            } catch (e: Exception) {
                Log.i(TAG, "Failed to trigger focus event for AM icon, recreating RHMI and trying again")
                try {
                    recreateRhmiApp()
                    carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first().triggerEvent(mapOf(0.toByte() to controlsView.state.id))
                } catch (e: Exception) {
                    Log.e(TAG, "Received exception while handling am_onAppEvent", e)
                }
            }
            screenMirrorProvider.handler.postDelayed({ createAmApp() }, 200)
        }

        override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
            try {
                carApp.actions[actionId]?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
                synchronized(carConnection) {
                    carConnection.rhmi_ackActionEvent(handle, actionId, 1, true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while calling onActionEvent handler!", e)
                synchronized(carConnection) {
                    carConnection.rhmi_ackActionEvent(handle, actionId, 1, true)
                }
            }
        }

        override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
            try {
                // generic event handler
                carApp.states[componentId]?.onHmiEvent(eventId, args)
                carApp.components[componentId]?.onHmiEvent(eventId, args)
            } catch (e: Exception) {
                Log.e(TAG, "Received exception while handling rhmi_onHmiEvent", e)
            }
        }

        override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
            if (propertyName == "driving.speedActual") {
                val data = try {
                    JSONObject(propertyValue ?: "{}")
                } catch (e: JSONException) {
                    JSONObject()
                }
                val speed = data.optDouble("speedActual", 0.0)
                val moving = speed > 0
                val carMode = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR
                if (moving && !carMode) {
                    screenMirrorProvider.minFrameTime = maxOf(baselineFrameTime, 1000)
                } else {
                    screenMirrorProvider.minFrameTime = baselineFrameTime
                }
            }
        }
    }

}