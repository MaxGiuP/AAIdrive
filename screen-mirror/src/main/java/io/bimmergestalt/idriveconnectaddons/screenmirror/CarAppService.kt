package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.app.Service
import android.app.UiModeManager
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import io.bimmergestalt.idriveconnectaddons.lib.CarCapabilities
import io.bimmergestalt.idriveconnectaddons.screenmirror.carapp.CarApp
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionReceiver
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess

class CarAppService: Service() {
    @Volatile var thread: CarThread? = null
    @Volatile var app: CarApp? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val startRetry = CarAppStartRetry(
        { task, delay -> mainHandler.postDelayed(task, delay) },
        { task -> mainHandler.removeCallbacks(task) },
        ::tryStartThread
    )

    override fun onCreate() {
        super.onCreate()
        SecurityAccess.getInstance(applicationContext).connect()
    }

    /**
     * When a car is connected, it will bind the Addon Service
     */
    override fun onBind(intent: Intent?): IBinder? {
        intent ?: return null
        IDriveConnectionReceiver().onReceive(applicationContext, intent)
        startThread()
        return null
    }

    /**
     * If the thread crashes for any reason,
     * opening the main app will trigger a Start on the Addon Services
     * as a chance to reconnect
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent ?: return START_NOT_STICKY
        IDriveConnectionReceiver().onReceive(applicationContext, intent)
        startThread()
        return START_NOT_STICKY
    }

    /**
     * The car has disconnected, so forget the previous details
     */
    override fun onUnbind(intent: Intent?): Boolean {
        stopCarApp()
        IDriveConnectionStatus.reset()
        return super.onUnbind(intent)
    }

    /**
     * Starts the thread for the car app, if it isn't running
     */
    fun startThread() {
        startRetry.start()
    }

    /** Return false only while the connected car is waiting for asynchronous security setup. */
    private fun tryStartThread(): Boolean {
        val iDriveConnectionStatus = IDriveConnectionReceiver()
        val securityAccess = SecurityAccess.getInstance(applicationContext)
        if (!iDriveConnectionStatus.isConnected || thread?.isAlive == true) return true
        if (iDriveConnectionStatus.isConnected &&
            securityAccess.isConnected() &&
            thread?.isAlive != true) {

            L.loadResources(applicationContext)
            var ownedApp: CarApp? = null
            var ownedProvider: ScreenMirrorProvider? = null
            var ownedController: OpenHeadunitController? = null
            lateinit var worker: CarThread
            worker = CarThread("ScreenMirroring", onExit = {
                try { ownedApp?.onDestroy() } finally {
                    try { ownedProvider?.stop() } finally {
                        try { ownedController?.close() } finally {
                            if (app === ownedApp) app = null
                        }
                    }
                }
            }) {
                Log.i(TAG, "CarThread is ready, starting CarApp")
                val carCapabilities = CarCapabilities(applicationContext)
                val screenMirrorProvider = ScreenMirrorProvider(worker.handler!!).also { ownedProvider = it }
                val baselineFrameTime = if (iDriveConnectionStatus.port == 4007) 250 else 100
                screenMirrorProvider.minFrameTime = baselineFrameTime
                screenMirrorProvider.bluetoothConnection = iDriveConnectionStatus.port == 4007
                val controller = OpenHeadunitController(applicationContext).also { ownedController = it }
                ownedApp = CarApp(
                    iDriveConnectionStatus,
                    securityAccess,
                    CarAppAssetResources(applicationContext, "smartthings"),
                    AndroidResources(applicationContext),
                    carCapabilities,
                    applicationContext.getSystemService(UiModeManager::class.java),
                    screenMirrorProvider,
                    applicationContext,
                    controller,
                    baselineFrameTime
                ) {
                    // start up the notification when we enter the app
                    val foreground = NotificationService.shouldBeForeground()
                    NotificationService.startNotification(applicationContext, foreground)
                }
                if (thread === worker) app = ownedApp
            }
            thread = worker
            worker.start()
            return true
        }
        return false
    }

    private fun stopCarApp() {
        startRetry.cancel()
        val oldThread = thread
        thread = null
        oldThread?.quitSafely()
        NotificationService.stopNotification(applicationContext)
    }

    override fun onDestroy() {
        stopCarApp()
        super.onDestroy()
    }
}
