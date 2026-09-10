package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.Log

/** Owns one consent token and its one permitted virtual display. */
object ProjectionSession {
    private const val TAG = "ProjectionSession"
    private val lock = Any()
    private val listeners = mutableMapOf<(MediaProjection?) -> Unit, Handler>()
    private var session: Session? = null

    private class Session(
        val projection: MediaProjection,
        val handler: Handler,
        val onStopped: () -> Unit
    ) {
        lateinit var callback: MediaProjection.Callback
        var displayClaimed = false
    }

    val currentProjection: MediaProjection?
        get() = synchronized(lock) { session?.projection }

    /** Called only by the foreground service, after it has entered the foreground. */
    fun start(projection: MediaProjection, callbackHandler: Handler, onStopped: () -> Unit) {
        stop()
        val next = Session(projection, callbackHandler, onStopped)
        next.callback = object : MediaProjection.Callback() {
            override fun onStop() {
                finish(next, stopProjection = false)
            }
        }
        // Android 14 requires this callback before createVirtualDisplay, including the
        // case where a waiting provider reacts immediately to the new session.
        try {
            projection.registerCallback(next.callback, callbackHandler)
        } catch (e: RuntimeException) {
            projection.stop()
            throw e
        }
        synchronized(lock) {
            session = next
            ScreenMirrorProvider.state.postValue(MirroringState.WAITING)
        }
        notifyListeners()
    }

    fun addListener(ownerHandler: Handler, listener: (MediaProjection?) -> Unit) {
        synchronized(lock) { listeners[listener] = ownerHandler }
        notifyListener(listener, ownerHandler)
    }

    fun removeListener(listener: (MediaProjection?) -> Unit) {
        synchronized(lock) { listeners.remove(listener) }
    }

    /**
     * A token is consumed before calling the factory, even if display creation fails.
     * Pausing/resuming must reuse the returned display, never call the factory again.
     */
    fun claimVirtualDisplay(projection: MediaProjection, factory: () -> VirtualDisplay): VirtualDisplay? {
        val owner = synchronized(lock) {
            val active = session
            if (active == null || active.projection !== projection || active.displayClaimed) return null
            active.displayClaimed = true
            active
        }
        val display = try {
            factory()
        } catch (e: Throwable) {
            finish(owner, stopProjection = true)
            throw e
        }
        // The user may revoke consent while createVirtualDisplay is in a binder call.
        if (synchronized(lock) { session !== owner }) {
            display.release()
            return null
        }
        return display
    }

    /** A stale service callback must never stop a replacement session. */
    fun stop(expectedProjection: MediaProjection? = currentProjection) {
        val active = synchronized(lock) {
            session?.takeIf { it.projection === expectedProjection }
        } ?: return
        finish(active, stopProjection = true)
    }

    private fun finish(owner: Session, stopProjection: Boolean) {
        synchronized(lock) {
            if (session !== owner) return
            session = null
            ScreenMirrorProvider.state.postValue(MirroringState.NOT_ALLOWED)
        }
        try {
            owner.projection.unregisterCallback(owner.callback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not unregister stopped projection callback", e)
        }
        if (stopProjection) {
            try {
                owner.projection.stop()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not stop projection", e)
            }
        }
        notifyListeners()
        owner.handler.post(owner.onStopped)
    }

    private fun notifyListeners() {
        val snapshot = synchronized(lock) { listeners.toList() }
        snapshot.forEach { (listener, handler) -> notifyListener(listener, handler) }
    }

    private fun notifyListener(listener: (MediaProjection?) -> Unit, handler: Handler) {
        handler.post {
            // Deliver the latest identity, so an old queued update cannot restore a
            // revoked projection. Providers serialize their capture work on this handler.
            val current = synchronized(lock) {
                if (listeners[listener] !== handler) return@post
                session?.projection
            }
            listener(current)
        }
    }
}
