package io.bimmergestalt.idriveconnectaddons.screenmirror

import java.util.Locale

/** Numeric local pipeline measurements, containing no captured pixels or app/navigation text. */
internal data class ProjectionFrameStatsSummary(
    val elapsedMs: Long,
    val width: Int,
    val height: Int,
    val intervalMs: Int,
    val quality: Int,
    val frames: Long,
    val sent: Long,
    val unchanged: Long,
    val jpegDuplicates: Long,
    val bytes: Long,
    val captureCopyMs: Long,
    val encodeMs: Long,
    val sendMs: Long
) {
    /** Completed local sends, not a measurement of when the head unit displays each image. */
    val sendsPerSecond: Double get() = if (elapsedMs > 0) sent * 1000.0 / elapsedMs else 0.0
    val averageSentBytes: Double get() = if (sent > 0) bytes.toDouble() / sent else 0.0
    val averageCaptureCopyMs: Double get() = if (frames > 0) captureCopyMs.toDouble() / frames else 0.0
    val averageEncodeMs: Double get() = if (frames > unchanged) encodeMs.toDouble() / (frames - unchanged) else 0.0
    val averageSendMs: Double get() = if (sent > 0) sendMs.toDouble() / sent else 0.0

    fun logLine(): String = String.format(Locale.ROOT,
        "ProjectionPerf window_ms=%d width=%d height=%d interval_ms=%d quality=%d frames=%d sent=%d unchanged=%d jpeg_duplicates=%d bytes=%d sends_per_s=%.2f avg_bytes=%.1f copy_ms=%.2f encode_ms=%.2f send_ms=%.2f",
        elapsedMs, width, height, intervalMs, quality, frames, sent, unchanged, jpegDuplicates,
        bytes, sendsPerSecond, averageSentBytes, averageCaptureCopyMs, averageEncodeMs, averageSendMs)
}

/** Report only acquired frames; emit at most once per five seconds on the capture handler. */
internal class ProjectionFrameStats {
    private var windowStartedMs: Long? = null
    private var frames = 0L
    private var sent = 0L
    private var unchanged = 0L
    private var jpegDuplicates = 0L
    private var bytes = 0L
    private var captureCopyMs = 0L
    private var encodeMs = 0L
    private var sendMs = 0L

    /**
     * sentBytes is positive only after the frame's synchronous send has succeeded.
     * unchanged skips encoding; jpegDuplicate was encoded but did not need another send.
     */
    fun reportFrame(
        nowMs: Long,
        width: Int,
        height: Int,
        intervalMs: Int,
        quality: Int,
        captureCopyMs: Long,
        encodeMs: Long,
        sendMs: Long,
        sentBytes: Int = 0,
        unchanged: Boolean = false,
        jpegDuplicate: Boolean = false
    ): ProjectionFrameStatsSummary? {
        require(width > 0 && height > 0 && intervalMs >= 0 && quality in 0..100)
        require(captureCopyMs >= 0 && encodeMs >= 0 && sendMs >= 0 && sentBytes >= 0)
        val started = windowStartedMs
        if (started == null || nowMs < started) reset(nowMs)
        frames++
        if (sentBytes > 0) sent++
        if (unchanged) this.unchanged++
        if (jpegDuplicate) jpegDuplicates++
        bytes += sentBytes.toLong()
        this.captureCopyMs += captureCopyMs
        this.encodeMs += encodeMs
        this.sendMs += sendMs
        val elapsed = nowMs - windowStartedMs!!
        if (elapsed < WINDOW_MS) return null
        return ProjectionFrameStatsSummary(elapsed, width, height, intervalMs, quality,
            frames, sent, this.unchanged, jpegDuplicates, bytes,
            this.captureCopyMs, this.encodeMs, this.sendMs).also { reset(nowMs) }
    }

    /** Starting/resuming a capture should not include the previous session's idle time. */
    fun reset(nowMs: Long? = null) {
        windowStartedMs = nowMs
        frames = 0
        sent = 0
        unchanged = 0
        jpegDuplicates = 0
        bytes = 0
        captureCopyMs = 0
        encodeMs = 0
        sendMs = 0
    }

    companion object {
        const val WINDOW_MS = 5000L
    }
}
