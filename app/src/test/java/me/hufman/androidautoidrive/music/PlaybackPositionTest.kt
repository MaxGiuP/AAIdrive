package me.hufman.androidautoidrive.music

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionTest {
	@Test
	fun audiobookPlaybackSpeed() {
		assertEquals(13000L, PlaybackPosition(false, false, 1000, 10000, 60000, 1.5f).getPosition(3000))
		assertEquals(11000L, PlaybackPosition(false, false, 1000, 10000, 60000, 0.5f).getPosition(3000))
	}

	@Test
	fun pausedAndUnstampedPositionsDoNotAdvance() {
		assertEquals(10000L, PlaybackPosition(true, false, 1000, 10000, 60000, 2f).getPosition(3000))
		assertEquals(10000L, PlaybackPosition(false, false, 0, 10000, 60000, 2f).getPosition(3000))
	}

	@Test
	fun clampsToTrackBounds() {
		assertEquals(12000L, PlaybackPosition(false, false, 1000, 10000, 12000, 2f).getPosition(3000))
		assertEquals(0L, PlaybackPosition(false, false, 1000, 1000, 12000, -2f).getPosition(3000))
		assertEquals(10000L, PlaybackPosition(false, false, 5000, 10000, -1).getPosition(3000))
	}

	@Test
	fun unknownDurationAndInvalidSpeed() {
		assertEquals(14000L, PlaybackPosition(false, false, 1000, 10000, -1, 2f).getPosition(3000))
		assertEquals(12000L, PlaybackPosition(false, false, 1000, 10000, -1, Float.NaN).getPosition(3000))
	}
}
