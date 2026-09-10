package me.hufman.androidautoidrive.music

import android.os.SystemClock
import kotlin.math.min

/** Given a snapshot of playback position, return the song's current playback position in ms */
class PlaybackPosition(val isPaused: Boolean,     // basically whether to show a Play button and blink the time
                       val isBuffering: Boolean,  // whether to show a loader spinner in AudioHmiState
                       val lastPositionUpdateTime: Long = SystemClock.elapsedRealtime(),
                       val lastPosition: Long,
                       val maximumPosition: Long,
                       val playbackSpeed: Float = 1f) {
	fun getPosition(): Long {
		return getPosition(if (isPaused || lastPositionUpdateTime == 0L) lastPositionUpdateTime else SystemClock.elapsedRealtime())
	}

	/** Estimate at a monotonic timestamp, respecting audiobook/video playback speed. */
	fun getPosition(elapsedRealtime: Long): Long {
		return if (isPaused || lastPositionUpdateTime == 0L) {
			lastPosition
		} else {
			val speed = if (playbackSpeed.isFinite()) playbackSpeed else 1f
			val elapsed = (elapsedRealtime - lastPositionUpdateTime).coerceAtLeast(0)
			val estimatedPosition = (lastPosition + (elapsed * speed.toDouble()).toLong()).coerceAtLeast(0)
			if (maximumPosition >= 0) {
				min(estimatedPosition, maximumPosition)
			} else {
				estimatedPosition
			}
		}
	}
}
