package io.bimmergestalt.idriveconnectaddons.screenmirror

internal data class JpegLimits(val minimum: Int, val initial: Int, val maximum: Int)

/** Tune against the selected frame budget, using completed sends rather than capture speed. */
internal class AdaptiveJpegQuality(
    private val intervalMs: Int,
    private val limits: JpegLimits
) {
    var quality = limits.initial
        private set
    private var firstSend = true
    private var slow = 0
    private var fast = 0

    fun onFrameSent(durationMs: Long) {
        if (durationMs < 0) return
        // Initial image visibility/focus RPCs aren't steady-state transfer costs.
        if (firstSend) { firstSend = false; return }
        when {
            durationMs > intervalMs -> {
                fast = 0
                if (++slow >= 3) {
                    quality = (quality - 5).coerceAtLeast(limits.minimum)
                    slow = 0
                }
            }
            durationMs * 4 < intervalMs * 3L -> {
                slow = 0
                if (++fast >= 30) {
                    quality = (quality + 2).coerceAtMost(limits.maximum)
                    fast = 0
                }
            }
            else -> { slow = 0; fast = 0 }
        }
    }
}
