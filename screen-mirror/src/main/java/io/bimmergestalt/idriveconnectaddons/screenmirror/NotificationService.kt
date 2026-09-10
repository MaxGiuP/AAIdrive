package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import java.util.UUID

/** Starts only after screen-capture consent, and owns the resulting projection lifetime. */
class NotificationService : Service() {
    companion object {
        const val PERMISSION_NOTIFICATION_ID = 53456
        const val ONGOING_NOTIFICATION_ID = 53457
        const val PERMISSION_CHANNEL_ID = "PermissionNotification"
        const val ONGOING_CHANNEL_ID = "ConnectionNotification"
        const val TIMEOUT = 60000L
        const val INTENT_EXTRA_FOREGROUND = "foreground"
        const val RESULT_READY = 1
        const val RESULT_FAILED = 2
        private const val TAG = "MirrorNotification"
        private const val ACTION_START = "start_projection"
        private const val ACTION_STOP = "stop_projection"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_RECEIVER = "result_receiver"
        private const val EXTRA_REQUEST_ID = "request_id"
        @Volatile private var activeService: NotificationService? = null

        fun shouldBeForeground(): Boolean = ProjectionSession.currentProjection != null

        /** A reminder must never start a mediaProjection FGS before the user consents. */
        @Suppress("UNUSED_PARAMETER")
        fun startNotification(context: Context, foreground: Boolean) {
            if (shouldBeForeground()) {
                activeService?.let { service -> service.handler.post { service.refreshNotification() } }
            } else {
                createNotificationChannels(context)
                val notification = buildNotification(context, MirroringState.NOT_ALLOWED, false)
                try {
                    context.getSystemService(NotificationManager::class.java)
                        .notify(PERMISSION_NOTIFICATION_ID, notification)
                } catch (e: SecurityException) {
                    // Notification permission is optional; the phone UI can still request capture.
                    Log.i(TAG, "Mirroring reminder notification is not permitted")
                }
            }
        }

        /** Only call from the visible consent activity after RESULT_OK. Never persist data. */
        fun startProjection(context: Context, resultCode: Int, data: Intent, receiver: ResultReceiver) {
            val serviceIntent = Intent(context, NotificationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
                .putExtra(EXTRA_RECEIVER, receiver)
                .putExtra(EXTRA_REQUEST_ID, UUID.randomUUID().toString())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        fun stopNotification(context: Context) {
            context.stopService(Intent(context, NotificationService::class.java))
            context.getSystemService(NotificationManager::class.java).cancel(PERMISSION_NOTIFICATION_ID)
        }

        private fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel(PERMISSION_CHANNEL_ID,
                    context.getString(R.string.notification_requests_channel_name), NotificationManager.IMPORTANCE_DEFAULT))
                manager.createNotificationChannel(NotificationChannel(ONGOING_CHANNEL_ID,
                    context.getString(R.string.notification_ongoing_channel_name), NotificationManager.IMPORTANCE_LOW))
            }
        }

        private fun buildNotification(context: Context, state: MirroringState, foreground: Boolean): Notification {
            val activity = if (foreground) MainActivity::class.java else RequestActivity::class.java
            val contentIntent = PendingIntent.getActivity(context, 0,
                Intent(context, activity).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = NotificationCompat.Builder(context,
                if (foreground) ONGOING_CHANNEL_ID else PERMISSION_CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(foreground)
                .setAutoCancel(!foreground)
                .setContentTitle(context.getText(when (state) {
                    MirroringState.ACTIVE -> R.string.lbl_status_active
                    MirroringState.WAITING -> R.string.lbl_status_waiting
                    else -> R.string.lbl_status_not_ready
                }))
            if (!foreground) {
                builder.setContentText(context.getText(R.string.btn_grant_mirror_auth))
            } else {
                val stopIntent = PendingIntent.getService(context, 1,
                    Intent(context, NotificationService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(0, context.getString(R.string.btn_stop_mirroring), stopIntent)
                if (state == MirroringState.WAITING && !IDriveConnectionStatus.isConnected) {
                    builder.setStyle(NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.lbl_status_autoclose)))
                }
            }
            return builder.build()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var projection: MediaProjection? = null
    private var foregroundStarted = false
    private var destroyed = false
    private val consumedRequests = mutableSetOf<String>()
    private val autostop = Runnable {
        if (!IDriveConnectionStatus.isConnected || projection == null) stopSelf() else refreshNotification()
    }
    private val stateObserver = Observer<MirroringState> { refreshNotification() }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        ScreenMirrorProvider.state.observeForever(stateObserver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER)
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        // Redelivery/restart can never provide new consent. Avoid consuming a token twice.
        intent?.removeExtra(EXTRA_RESULT_DATA)
        intent?.removeExtra(EXTRA_RECEIVER)
        if (intent?.action != ACTION_START || resultCode != Activity.RESULT_OK || data == null ||
            requestId == null || !consumedRequests.add(requestId)) {
            receiver?.send(RESULT_FAILED, null)
            if (projection == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            createNotificationChannels(this)
            val notification = buildNotification(this, MirroringState.WAITING, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ONGOING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(ONGOING_NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
            notificationManager.cancel(PERMISSION_NOTIFICATION_ID)
            // Android requires the foreground service to be running before this call.
            val granted = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("No projection returned after consent")
            projection = granted
            ProjectionSession.start(granted, handler) {
                if (projection === granted) {
                    projection = null
                    stopSelf()
                }
            }
            handler.removeCallbacks(autostop)
            handler.postDelayed(autostop, TIMEOUT)
            receiver?.send(RESULT_READY, null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not start consented screen projection", e)
            receiver?.send(RESULT_FAILED, null)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun refreshNotification() {
        if (!destroyed && foregroundStarted && projection != null) {
            try {
                notificationManager.notify(ONGOING_NOTIFICATION_ID,
                    buildNotification(this, ScreenMirrorProvider.state.value ?: MirroringState.WAITING, true))
            } catch (e: SecurityException) {
                Log.i(TAG, "Mirroring status notification is not permitted")
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        if (activeService === this) activeService = null
        handler.removeCallbacks(autostop)
        ScreenMirrorProvider.state.removeObserver(stateObserver)
        ProjectionSession.stop(projection)
        projection = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }
}
