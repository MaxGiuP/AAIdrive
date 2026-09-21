package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong

enum class MirroringState { NOT_ALLOWED, WAITING, ACTIVE }

/** Capture/encoding has its own worker; car RPCs and view updates stay on the car handler. */
class ScreenMirrorProvider(
    val handler: Handler,
    private val captureHandler: Handler = handler,
    private val onStopped: () -> Unit = {}
) {
    companion object {
        val state = MutableLiveData(MirroringState.NOT_ALLOWED)
    }

    private val outputSize = Point(100, 100)
    private val schedule = FrameSchedule()
    private val pixels = RgbaFrameBuffer()
    private val bitmaps = ReusableCaptureBitmaps()
    private var profile = ProjectionProfile.SMOOTH
    private var bluetooth = false
    private var motionLimited = false
    private var compression = AdaptiveJpegQuality(profile.intervalMs, profile.compression(bluetooth))
    private val frameStats = ProjectionFrameStats()
    private val jpg = ByteArrayOutputStream()
    private var imageReader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var projection: MediaProjection? = null
    private var focused = false
    @Volatile private var stopped = false
    @Volatile private var currentState: MirroringState? = null
    @Volatile private var deliveryActive = false
    private val generation = AtomicLong()
    // Accessed only on the car handler.
    private var lastSentFrame: ByteArray? = null
    private var lastSentGeneration = -1L
    private data class EncodedFrame(val bytes: ByteArray, val quality: Int, val generation: Long,
                                    val permission: MediaProjection, val consumer: (ByteArray) -> Unit)
    private val sender = LatestFrameSender<EncodedFrame>({ handler.post(it) }, ::sendFrame)

    @Volatile var minFrameTime = profile.intervalMs
        private set
    @Volatile var callback: ((ByteArray) -> Unit)? = null
        set(value) { field = value; invalidateFrames() }
    @Volatile var onStateChanged: ((MirroringState) -> Unit)? = null

    private fun invalidateFrames() {
        generation.incrementAndGet()
        sender.invalidate()
    }

    fun configure(profile: ProjectionProfile, bluetooth: Boolean) = onHandler {
        if (stopped || (this.profile == profile && this.bluetooth == bluetooth)) return@onHandler
        this.profile = profile
        this.bluetooth = bluetooth
        invalidateFrames()
        bitmaps.resetComparison()
        frameStats.reset(SystemClock.uptimeMillis())
        resetCompression()
        updateInterval()
    }

    fun setMotionLimited(limited: Boolean) = onHandler {
        if (motionLimited != limited && !stopped) {
            motionLimited = limited
            updateInterval()
        }
    }

    private fun resetCompression() {
        compression = AdaptiveJpegQuality(profile.intervalMs, profile.compression(bluetooth))
    }

    private fun updateInterval() {
        minFrameTime = if (motionLimited) maxOf(profile.intervalMs, 1000) else profile.intervalMs
        captureHandler.removeCallbacks(poll)
        schedule.reset()
        schedulePoll()
    }

    private val projectionListener: (MediaProjection?) -> Unit = { next ->
        if (!stopped && projection !== next) {
            releaseCapture()
            projection = next
            updateState(if (next == null) MirroringState.NOT_ALLOWED else MirroringState.WAITING)
            if (focused && next != null) createImageReader()
        }
    }

    private fun onHandler(action: () -> Unit) {
        if (Looper.myLooper() == captureHandler.looper) action() else captureHandler.post(action)
    }

    /** Set before focusing the car view; an existing capture keeps its negotiated size. */
    fun setSize(width: Int, height: Int) = onHandler {
        require(width > 0 && height > 0)
        outputSize.set(width, height)
    }

    fun start() {
        invalidateFrames()
        deliveryActive = true
        onHandler { startCapture() }
    }

    private fun startCapture() {
        if (!stopped) {
            focused = true
            bitmaps.resetComparison()
            resetCompression()
            frameStats.reset(SystemClock.uptimeMillis())
            val available = ProjectionSession.currentProjection
            if (projection !== available) projectionListener(available)
            if (display == null) {
                createImageReader()
            } else {
                display?.surface = imageReader?.surface
                updateState(MirroringState.ACTIVE)
                schedulePoll()
            }
        }
    }

    fun pause() {
        deliveryActive = false
        invalidateFrames()
        onHandler { pauseCapture() }
    }

    private fun pauseCapture() {
        focused = false
        bitmaps.resetComparison()
        frameStats.reset()
        captureHandler.removeCallbacks(poll)
        schedule.reset()
        display?.surface = null
        updateState(if (projection == null) MirroringState.NOT_ALLOWED else MirroringState.WAITING)
    }

    /** A released virtual display consumes its permission; another provider needs a fresh session. */
    fun stop() {
        deliveryActive = false
        invalidateFrames()
        onHandler { stopCapture() }
    }

    private fun stopCapture() {
        if (!stopped) {
            stopped = true
            focused = false
            ProjectionSession.removeListener(projectionListener)
            val permission = projection
            try {
                releaseCapture()
            } finally {
                callback = null
                onStateChanged = null
                projection = null
                try { permission?.let { ProjectionSession.stop(it) } } finally { onStopped() }
            }
        }
    }

    private fun updateState(next: MirroringState) {
        if (currentState != next) {
            currentState = next
            state.postValue(next)
            handler.post {
                if (!stopped && currentState == next) onStateChanged?.invoke(next)
            }
        }
    }

    private fun releaseCapture() {
        invalidateFrames()
        captureHandler.removeCallbacks(poll)
        schedule.reset()
        imageReader?.setOnImageAvailableListener(null, null)
        try {
            display?.release()
        } finally {
            display = null
            imageReader?.close()
            imageReader = null
            bitmaps.clear()
            resetCompression()
            frameStats.reset()
            pixels.clear()
            jpg.reset()
        }
    }

    private val poll = Runnable {
        schedule.onWake()
        if (focused && !stopped && callback != null) fetchImage()
    }

    private fun schedulePoll(delay: Long = 0) {
        if (!focused || stopped || callback == null || imageReader == null) return
        val deadline = schedule.request(SystemClock.uptimeMillis(), minFrameTime, delay) ?: return
        captureHandler.removeCallbacks(poll)
        captureHandler.postAtTime(poll, deadline)
    }

    @SuppressLint("WrongConstant")
    private fun createImageReader() {
        val permission = projection
        if (!focused || stopped || permission == null) {
            updateState(if (permission == null) MirroringState.NOT_ALLOWED else MirroringState.WAITING)
            return
        }
        if (imageReader != null) return
        val reader = ImageReader.newInstance(outputSize.x, outputSize.y, PixelFormat.RGBA_8888, 2)
        try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            val created = ProjectionSession.claimVirtualDisplay(permission) {
                permission.createVirtualDisplay("idrive-screen-mirror", reader.width, reader.height,
                    200, flags, reader.surface, null, captureHandler)
            }
            if (created == null) {
                reader.close()
                ProjectionSession.stop(permission)
                updateState(MirroringState.NOT_ALLOWED)
                return
            }
            imageReader = reader
            display = created
            frameStats.reset(SystemClock.uptimeMillis())
            reader.setOnImageAvailableListener(imageListener, captureHandler)
            updateState(MirroringState.ACTIVE)
            schedulePoll()
        } catch (e: Exception) {
            if (imageReader === reader) releaseCapture() else reader.close()
            ProjectionSession.stop(permission)
            Log.w(TAG, "Failed to create mirror display", e)
            updateState(MirroringState.NOT_ALLOWED)
        }
    }

    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        if (reader === imageReader) schedulePoll()
    }

    private fun fetchImage() {
        val delay = schedule.remainingDelay(SystemClock.uptimeMillis(), minFrameTime)
        if (delay > 0) {
            schedulePoll(delay)
            return
        }
        // A revoked session may still have a callback queued behind the current frame.
        if (projection !== ProjectionSession.currentProjection) {
            projectionListener(ProjectionSession.currentProjection)
            return
        }
        try {
            val frameGeneration = generation.get()
            val captureStarted = SystemClock.uptimeMillis()
            val captured = imageReader?.acquireLatestImage()
            if (captured == null) {
                schedulePoll(1000)
                return
            }
            schedule.onFrame(SystemClock.uptimeMillis())
            val frame = try { copyImage(captured) } finally { captured.close() }
            val unchanged = bitmaps.isUnchanged(frame)
            val captureCopyMs = SystemClock.uptimeMillis() - captureStarted
            val quality = compression.quality
            // Compare the captured pixels before spending CPU on JPEG compression.
            // Separate reusable bitmaps keep the previous pixels intact during capture.
            if (unchanged) {
                recordFrame(quality, captureCopyMs, 0, 0, 0, unchanged = true)
                schedulePoll()
                return
            }
            // The ImageReader buffer is free before compression or the slower synchronous car RPC.
            val encodeStarted = SystemClock.uptimeMillis()
            jpg.reset()
            check(frame.compress(Bitmap.CompressFormat.JPEG, quality, jpg))
            val permission = projection
            val consumer = callback
            if (permission != null && permission === ProjectionSession.currentProjection &&
                frameGeneration == generation.get() && deliveryActive && consumer != null) {
                val bytes = jpg.toByteArray()
                val encodeMs = SystemClock.uptimeMillis() - encodeStarted
                sender.offer(EncodedFrame(bytes, quality, frameGeneration, permission, consumer))
                bitmaps.rememberEncoded(frame)
                recordFrame(quality, captureCopyMs, encodeMs, 0, 0)
            }
            schedulePoll()
        } catch (e: Exception) {
            failCapture(e)
        }
    }

    /** Synchronous transport is confined to the car thread, overlapping the next JPEG encode. */
    private fun sendFrame(frame: EncodedFrame) {
        if (!deliveryActive || stopped || generation.get() != frame.generation ||
            ProjectionSession.currentProjection !== frame.permission || callback !== frame.consumer) return
        try {
            val duplicate = lastSentGeneration == frame.generation && lastSentFrame?.contentEquals(frame.bytes) == true
            val started = SystemClock.uptimeMillis()
            if (!duplicate) {
                frame.consumer(frame.bytes)
                lastSentFrame = frame.bytes
                lastSentGeneration = frame.generation
            }
            val sendMs = SystemClock.uptimeMillis() - started
            captureHandler.post {
                if (generation.get() == frame.generation && deliveryActive && !stopped) {
                    if (!duplicate) compression.onFrameSent(sendMs)
                    frameStats.reportDelivery(SystemClock.uptimeMillis(), outputSize.x, outputSize.y,
                        minFrameTime, frame.quality, sendMs, if (duplicate) 0 else frame.bytes.size, duplicate)
                        ?.let { Log.i(TAG, it.logLine()) }
                }
            }
        } catch (e: Exception) {
            captureHandler.post {
                if (generation.get() == frame.generation && !stopped) failCapture(e)
            }
        }
    }

    private fun failCapture(error: Exception) {
        Log.w(TAG, "Screen mirror frame failed", error)
        val permission = projection
        releaseCapture()
        permission?.let { ProjectionSession.stop(it) }
        updateState(MirroringState.NOT_ALLOWED)
    }

    private fun copyImage(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = pixels.prepare(plane.buffer, image.width, image.height, plane.pixelStride, plane.rowStride)
        return bitmaps.copyFrom(buffer, image.width, image.height)
    }

    private fun recordFrame(quality: Int, captureCopyMs: Long, encodeMs: Long, sendMs: Long,
                            sentBytes: Int, unchanged: Boolean = false, jpegDuplicate: Boolean = false) {
        frameStats.reportFrame(SystemClock.uptimeMillis(), outputSize.x, outputSize.y,
            minFrameTime, quality, captureCopyMs, encodeMs, sendMs, sentBytes, unchanged, jpegDuplicate)
            ?.let { Log.i(TAG, it.logLine()) }
    }

    init {
        // The dedicated worker can run immediately, so all callback fields must exist first.
        ProjectionSession.addListener(captureHandler, projectionListener)
    }

}
