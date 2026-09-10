package io.bimmergestalt.idriveconnectaddons.screenmirror

/** One pending wake-up for the newest frame; callers supply a monotonic clock. */
internal class FrameSchedule {
    private var pendingAt: Long? = null
    private var lastFrameAt: Long? = null

    fun remainingDelay(now: Long, minimumInterval: Int): Long =
        lastFrameAt?.let { (it + minimumInterval.coerceAtLeast(0) - now).coerceAtLeast(0) } ?: 0

    /** Returns a new deadline only when the existing wake-up must be moved earlier. */
    fun request(now: Long, minimumInterval: Int, delay: Long = 0): Long? {
        val deadline = now + maxOf(delay.coerceAtLeast(0), remainingDelay(now, minimumInterval))
        if (pendingAt?.let { it <= deadline } == true) return null
        pendingAt = deadline
        return deadline
    }

    fun onWake() { pendingAt = null }
    fun onFrame(now: Long) { lastFrameAt = now }
    fun reset() {
        pendingAt = null
        lastFrameAt = null
    }
}
