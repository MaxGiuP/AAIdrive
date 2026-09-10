package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.widget.Toast

/** Keeps consent and the subsequent OpenHU launch in a visible, user-started activity. */
class RequestActivity : Activity() {
    companion object {
        const val PROJECTION_PERMISSION_CODE = 8345
        const val EXTRA_LAUNCH_HEADUNIT = "launch_headunit"
        private const val STATE_REQUESTED = "consent_requested"
        private const val STATE_AWAITING_SERVICE = "awaiting_service"
    }

    /** Retain the one-shot service reply over rotations without retaining an activity. */
    private class RequestState(val launchHeadunit: Boolean) {
        var activity: RequestActivity? = null
        var requested = false
        var awaitingService = false
        var serviceResult: Int? = null
        var handled = false
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                awaitingService = false
                serviceResult = resultCode
                activity?.handleServiceResult()
            }
        }
    }

    private lateinit var request: RequestState
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val retained = lastNonConfigurationInstance as? RequestState
        request = retained ?: RequestState(
            savedInstanceState?.getBoolean(EXTRA_LAUNCH_HEADUNIT)
                ?: intent.getBooleanExtra(EXTRA_LAUNCH_HEADUNIT, false)
        ).apply {
            requested = savedInstanceState?.getBoolean(STATE_REQUESTED) ?: false
            awaitingService = savedInstanceState?.getBoolean(STATE_AWAITING_SERVICE) ?: false
        }
        request.activity = this
        if (retained == null && request.awaitingService) {
            // A process restart invalidates the old token and reply. Never replay it.
            finish()
        } else if (!request.requested) {
            request.requested = true
            requestPermission()
        }
    }

    private fun requestPermission() {
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val consent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // OpenHU opens after consent: capture the display instead of the grant UI app.
            projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        try {
            startActivityForResult(consent, PROJECTION_PERMISSION_CODE)
        } catch (e: RuntimeException) {
            Toast.makeText(this, R.string.lbl_mirror_start_failed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PROJECTION_PERMISSION_CODE || request.awaitingService || request.handled) return
        if (resultCode == RESULT_OK && data != null) {
            request.awaitingService = true
            try {
                NotificationService.startProjection(applicationContext, resultCode, data, request.receiver)
            } catch (e: RuntimeException) {
                request.receiver.send(NotificationService.RESULT_FAILED, null)
            }
        } else {
            NotificationService.startNotification(applicationContext, false)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        handleServiceResult()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    private fun handleServiceResult() {
        val result = request.serviceResult ?: return
        if (!resumed || isFinishing || request.handled) return
        request.handled = true
        if (result != NotificationService.RESULT_READY || ProjectionSession.currentProjection == null) {
            Toast.makeText(this, R.string.lbl_mirror_start_failed, Toast.LENGTH_LONG).show()
        } else if (request.launchHeadunit) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("headunit://selfmode"))
                    .setPackage("com.andrerinas.headunitrevived"))
            } catch (e: ActivityNotFoundException) {
                NotificationService.stopNotification(this)
                Toast.makeText(this, R.string.lbl_headunit_missing, Toast.LENGTH_LONG).show()
            } catch (e: SecurityException) {
                NotificationService.stopNotification(this)
                Toast.makeText(this, R.string.lbl_headunit_missing, Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_REQUESTED, request.requested)
        outState.putBoolean(STATE_AWAITING_SERVICE, request.awaitingService)
        outState.putBoolean(EXTRA_LAUNCH_HEADUNIT, request.launchHeadunit)
        super.onSaveInstanceState(outState)
    }

    override fun onRetainNonConfigurationInstance(): Any = request

    override fun onDestroy() {
        if (request.activity === this) request.activity = null
        super.onDestroy()
    }
}
