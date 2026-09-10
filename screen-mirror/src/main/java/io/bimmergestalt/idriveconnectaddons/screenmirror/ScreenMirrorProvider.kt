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

enum class MirroringState { NOT_ALLOWED, WAITING, ACTIVE }

/** Capture, conversion, and synchronous car sends share one handler and never queue encoded frames. */
class ScreenMirrorProvider(val handler: Handler) {
    companion object {
        val state = MutableLiveData(MirroringState.NOT_ALLOWED)
    }

    private val outputSize = Point(100, 100)
    private val schedule = FrameSchedule()
    private val pixels = RgbaFrameBuffer()
    private val jpg = ByteArrayOutputStream()
    private var bitmap: Bitmap? = null
    private var imageReader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var projection: MediaProjection? = null
    private var focused = false
    private var stopped = false
    private var currentState: MirroringState? = null
    private var lastSentFrame: ByteArray? = null

    @Volatile var minFrameTime = 100
    @Volatile var jpgQuality = 60
    var callback: ((ByteArray) -> Unit)? = null
    var onStateChanged: ((MirroringState) -> Unit)? = null

    private val projectionListener: (MediaProjection?) -> Unit = { next ->
        if (!stopped && projection !== next) {
            releaseCapture()
            projection = next
            updateState(if (next == null) MirroringState.NOT_ALLOWED else MirroringState.WAITING)
            if (focused && next != null) createImageReader()
        }
    }

    init {
        ProjectionSession.addListener(handler, projectionListener)
    }

    private fun onHandler(action: () -> Unit) {
        if (Looper.myLooper() == handler.looper) action() else handler.post(action)
    }

    /** Set before focusing the car view; an existing capture keeps its negotiated size. */
    fun setSize(width: Int, height: Int) = onHandler {
        require(width > 0 && height > 0)
        outputSize.set(width, height)
    }

    fun start() = onHandler {
        if (!stopped) {
            focused = true
            lastSentFrame = null
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

    fun pause() = onHandler { pauseCapture() }

    private fun pauseCapture() {
        focused = false
        lastSentFrame = null
        handler.removeCallbacks(poll)
        schedule.reset()
        display?.surface = null
        updateState(if (projection == null) MirroringState.NOT_ALLOWED else MirroringState.WAITING)
    }

    /** A released virtual display consumes its permission; another provider needs a fresh session. */
    fun stop() = onHandler {
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
                permission?.let { ProjectionSession.stop(it) }
            }
        }
    }

    private fun updateState(next: MirroringState) {
        if (currentState != next) {
            currentState = next
            state.postValue(next)
            onStateChanged?.invoke(next)
        }
    }

    private fun releaseCapture() {
        handler.removeCallbacks(poll)
        schedule.reset()
        lastSentFrame = null
        imageReader?.setOnImageAvailableListener(null, null)
        try {
            display?.release()
        } finally {
            display = null
            imageReader?.close()
            imageReader = null
            bitmap?.recycle()
            bitmap = null
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
        handler.removeCallbacks(poll)
        handler.postAtTime(poll, deadline)
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
                    200, flags, reader.surface, null, handler)
            }
            if (created == null) {
                reader.close()
                ProjectionSession.stop(permission)
                updateState(MirroringState.NOT_ALLOWED)
                return
            }
            imageReader = reader
            display = created
            reader.setOnImageAvailableListener(imageListener, handler)
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
            val captured = imageReader?.acquireLatestImage()
            if (captured == null) {
                schedulePoll(1000)
                return
            }
            schedule.onFrame(SystemClock.uptimeMillis())
            val frame = try { copyImage(captured) } finally { captured.close() }
            // The ImageReader buffer is free before compression or the slower synchronous car RPC.
            jpg.reset()
            check(frame.compress(Bitmap.CompressFormat.JPEG, jpgQuality.coerceIn(0, 100), jpg))
            if (projection === ProjectionSession.currentProjection) {
                val bytes = jpg.toByteArray()
                val consumer = callback
                if (consumer != null && lastSentFrame?.contentEquals(bytes) != true) {
                    consumer(bytes)
                    lastSentFrame = bytes
                }
            }
            schedulePoll()
        } catch (e: Exception) {
            Log.w(TAG, "Screen mirror frame failed", e)
            val permission = projection
            releaseCapture()
            permission?.let { ProjectionSession.stop(it) }
            updateState(MirroringState.NOT_ALLOWED)
        }
    }

    private fun copyImage(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = pixels.prepare(plane.buffer, image.width, image.height, plane.pixelStride, plane.rowStride)
        val output = bitmap?.takeIf { it.width == image.width && it.height == image.height }
            ?: Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also {
                bitmap?.recycle()
                bitmap = it
            }
        output.copyPixelsFromBuffer(buffer)
        return output
    }
}
