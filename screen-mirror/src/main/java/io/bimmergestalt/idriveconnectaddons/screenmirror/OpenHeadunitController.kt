package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import org.json.JSONObject
import java.util.ArrayDeque

/** Controls the separately installed Open Headunit v3.4.0-beta1 through its public intents. */
class OpenHeadunitController(context: Context, private val handler: Handler = Handler(Looper.getMainLooper())) {
    companion object {
        const val PACKAGE = "com.andrerinas.headunitrevived"
        private const val KEY_ACTION = "$PACKAGE.ACTION_KEYPRESS"
        private const val STATE_ACTION = "$PACKAGE.SESSION_STATE"
        private const val QUERY_ACTION = "com.andrerinas.openheadunit.ACTION_QUERY_STATE"
        private const val RECEIVER = "com.andrerinas.openheadunit.automation.AutomationReceiver"
    }

    private val context = context.applicationContext
    private val session = ProjectionControlSession()
    private var closed = false
    private var queryInFlight = false
    private var controlProjection: android.media.projection.MediaProjection? = null
    private val captureListener: (android.media.projection.MediaProjection?) -> Unit = {
        synchronized(this) {
            controlProjection = it
            queue.setActive(false)
            session.invalidate()
            if (!closed && session.enabled && it != null) queryState()
        }
    }
    private val queue = PacedNavigationKeys(
        now = SystemClock::uptimeMillis,
        schedule = { task, delay -> handler.postDelayed(task, delay) },
        cancel = { handler.removeCallbacks(it) },
        send = ::sendKey
    )
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == STATE_ACTION) onSessionChanged()
        }
    }

    init {
        ProjectionSession.addListener(handler, captureListener)
        val filter = IntentFilter(STATE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            this.context.registerReceiver(stateReceiver, filter, null, handler, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            this.context.registerReceiver(stateReceiver, filter, null, handler)
        }
    }

    /** Enable only while the car's Android Auto view or its control menu is open. */
    @Synchronized
    fun resume() {
        if (closed || session.enabled) return
        controlProjection = ProjectionSession.currentProjection
        session.resume()
        queue.setActive(false)
        queryState()
    }

    /** Clears queued input synchronously, including callbacks already posted to the Handler. */
    @Synchronized
    fun pause() {
        session.pause()
        queue.setActive(false)
    }

    /** Returns false for unsupported keys, an inactive session, or a full queue. */
    @Synchronized
    fun press(keyCode: Int): Boolean = !closed && captureMatches() && queue.press(keyCode)

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        pause()
        ProjectionSession.removeListener(captureListener)
        controlProjection = null
        context.unregisterReceiver(stateReceiver)
    }

    @Synchronized
    private fun onSessionChanged() {
        if (closed || !session.enabled) return
        session.invalidate()
        queue.setActive(false)
        // SESSION_STATE is public and unprotected. Never trust it to enable controls: query the
        // explicitly addressed companion receiver instead. No credentials or navigation data flow.
        queryState()
    }

    private fun queryState() {
        if (closed || !session.enabled || queryInFlight || !captureMatches()) return
        queryInFlight = true
        val generation = session.generation
        val intent = Intent(QUERY_ACTION).setComponent(ComponentName(PACKAGE, RECEIVER))
        try {
            context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val projecting = runCatching {
                        val data = resultData?.takeIf { it.length <= 4096 } ?: return@runCatching false
                        val reply = JSONObject(data)
                        // "state" contains a class simpleName and may be obfuscated in release
                        // APKs. The stable connected boolean comes from the addressed receiver;
                        // its key listener independently requires a resumed projection activity.
                        reply.optBoolean("ok") && reply.optString("action") == QUERY_ACTION &&
                                reply.optBoolean("connected")
                    }.getOrDefault(false)
                    onQueryResult(generation, projecting)
                }
            }, handler, 0, null, null)
        } catch (_: RuntimeException) {
            onQueryResult(generation, false)
        }
    }

    @Synchronized
    private fun onQueryResult(generation: Long, projecting: Boolean) {
        queryInFlight = false
        if (closed) return
        if (session.accept(generation, projecting)) {
            queue.setActive(session.projecting && captureMatches())
        } else if (session.enabled) {
            queryState()
        }
    }

    private fun captureMatches(): Boolean = controlProjection != null &&
            controlProjection === ProjectionSession.currentProjection

    private fun sendKey(keyCode: Int) {
        if (!captureMatches()) return
        val timestamp = SystemClock.uptimeMillis()
        // Both edges stay together; no key is left held when capture or the car view stops.
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val event = KeyEvent(timestamp, timestamp, action, keyCode, 0)
            try {
                context.sendBroadcast(Intent(KEY_ACTION).setPackage(PACKAGE).putExtra("event", event))
            } catch (_: RuntimeException) {
                // The companion may be removed/stopped during a session. Do not retry old input.
            }
        }
    }
}

/** A reply to a previous view/session must never enable controls in a later one. */
internal class ProjectionControlSession {
    var generation = 0L
        private set
    var enabled = false
        private set
    var projecting = false
        private set

    fun resume() { enabled = true; invalidate() }
    fun pause() { enabled = false; invalidate() }
    fun invalidate() { generation++; projecting = false }
    fun accept(generation: Long, projecting: Boolean): Boolean {
        if (!enabled || generation != this.generation) return false
        this.projecting = projecting
        return true
    }
}

/** Small ordered queue: Open Headunit's broadcast path debounces repeated keys for 300 ms. */
internal class PacedNavigationKeys(
    private val now: () -> Long,
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val send: (Int) -> Unit
) {
    companion object {
        const val INTERVAL_MS = 310L
        const val CAPACITY = 4
        private val ALLOWED = setOf(19, 20, 21, 22, 23, 4) // DPAD directions/center and Back
    }

    private val pending = ArrayDeque<Int>()
    private var active = false
    private var generation = 0L
    private var task: Runnable? = null
    private var nextSendAt = 0L

    @Synchronized
    fun setActive(value: Boolean) {
        active = value
        if (!value) {
            generation++
            pending.clear()
            task?.let(cancel)
            task = null
        }
    }

    @Synchronized
    fun press(keyCode: Int): Boolean {
        if (!active || keyCode !in ALLOWED || pending.size >= CAPACITY) return false
        pending.addLast(keyCode)
        drain()
        return true
    }

    @Synchronized
    private fun drain() {
        if (!active || pending.isEmpty() || task != null) return
        val delay = (nextSendAt - now()).coerceAtLeast(0)
        if (delay > 0) {
            val expectedGeneration = generation
            val scheduled = Runnable {
                synchronized(this) {
                    if (expectedGeneration == generation) {
                        task = null
                        drain()
                    }
                }
            }
            task = scheduled
            schedule(scheduled, delay)
        } else {
            nextSendAt = now() + INTERVAL_MS
            send(pending.removeFirst())
            drain()
        }
    }
}
