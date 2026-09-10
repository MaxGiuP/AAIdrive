package me.hufman.androidautoidrive.music

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.mockito.kotlin.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicMetadataTest {
	val fakeBundle = mock<Bundle> {
		on {keySet()} doReturn emptySet()
	}

	@Test
	fun testQueueItemPreservesPlaybackIdAndArtwork() {
		val artwork = mock<Bitmap>()
		val artworkUri = mock<Uri> {
			on { toString() } doReturn "content://music/cover"
		}
		val descriptionValue = mock<MediaDescriptionCompat> {
			on { mediaId } doReturn "playlist/track-17"
			on { title } doReturn "Track 17"
			on { iconBitmap } doReturn artwork
			on { iconUri } doReturn artworkUri
			on { extras } doReturn fakeBundle
		}
		val item = mock<MediaSessionCompat.QueueItem> {
			on { queueId } doReturn 17L
			on { description } doReturn descriptionValue
		}
		val parsed = MusicMetadata.fromQueueItem(item)
		assertEquals("playlist/track-17", parsed.mediaId)
		assertEquals(17L, parsed.queueId)
		assertEquals("Track 17", parsed.title)
		assertSame(artwork, parsed.coverArt)
		assertSame(artwork, parsed.icon)
		assertEquals("content://music/cover", parsed.coverArtUri)
		assertSame(fakeBundle, parsed.extras)
		assertTrue(parsed.playable)
	}

	@Test
	fun testUnknownActiveQueueIdIsAbsent() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
		}
		val playbackState = mock<PlaybackStateCompat> {
			on { activeQueueItemId } doReturn MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
		}
		assertNull(MusicMetadata.fromMediaMetadata(metadata, playbackState).queueId)
	}

	@Test
	fun testQueueMatchingFallsBackToMediaIdButPreservesRepeatedSongs() {
		val song = MusicMetadata(mediaId = "track", queueId = 10)
		assertTrue(song.matchesQueueItem(MusicMetadata(mediaId = "track")))
		assertTrue(song.matchesQueueItem(MusicMetadata(mediaId = "track", queueId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong())))
		assertTrue(song.matchesQueueItem(MusicMetadata(queueId = 10)))
		assertFalse(song.matchesQueueItem(MusicMetadata(mediaId = "track", queueId = 11)))
		assertFalse(song.matchesQueueItem(MusicMetadata(mediaId = "different")))
		assertFalse(song.matchesQueueItem(null))
		assertFalse(MusicMetadata().matchesQueueItem(MusicMetadata()))
		assertFalse(MusicMetadata(mediaId = "").matchesQueueItem(MusicMetadata(mediaId = "")))
	}

	/**
	 * SomaFM has both DISPLAY_TITLE and weird metadata fields
	 * So ignore the given artist/title and use DISPLAY_TITLE
	 */
	@Test
	fun testSomaFm() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
			on { getString(any()) } doReturn null
			on { getLong(any()) } doReturn 0
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)) } doReturn "Rusty Hodge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ARTIST)) } doReturn "Frank Chacksfield Orchestra - Holiday In Rhodes"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_COMPILATION)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_TITLE)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)) } doReturn "Classic bachelor pad, playful exotica and vintage music of tomorrow."
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)) } doReturn "Frank Chacksfield Orchestra - Holiday In Rhodes"
		}
		val parsed = MusicMetadata.fromMediaMetadata(metadata)
		assertEquals("artist", "Illinois Street Lounge", parsed.artist)
		assertEquals("album", "Illinois Street Lounge", parsed.album)
		assertEquals("title", "Frank Chacksfield Orchestra - Holiday In Rhodes", parsed.title)
	}

	/**
	 * A variant of SomaFM with correct artist/title fields
	 */
	@Test
	fun testSomaFmWithMetadata() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
			on { getString(any()) } doReturn null
			on { getLong(any()) } doReturn 0
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)) } doReturn "Rusty Hodge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ARTIST)) } doReturn "Frank Chacksfield Orchestra"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_COMPILATION)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_TITLE)) } doReturn "Holiday In Rhodes"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)) } doReturn "Classic bachelor pad, playful exotica and vintage music of tomorrow."
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)) } doReturn "Illinois Street Lounge"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)) } doReturn "Frank Chacksfield Orchestra - Holiday In Rhodes"
		}
		val parsed = MusicMetadata.fromMediaMetadata(metadata)
		assertEquals("artist", "Frank Chacksfield Orchestra", parsed.artist)
		assertEquals("album", "Illinois Street Lounge", parsed.album)
		assertEquals("title", "Holiday In Rhodes", parsed.title)
	}

	/**
	 * Energy Radio only has DISPLAY fields
	 */
	@Test
	fun testEnergyRadio() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
			on { getString(any()) } doReturn null
			on { getLong(any()) } doReturn 0
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM)) } doReturn "Tout l'univers (EP)"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_COMPILATION)) } doReturn "Energy Basel"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)) } doReturn "Energy Basel"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)) } doReturn "Tout l'univers - Gjon's Tears"        // title and artist?
		}
		val parsed = MusicMetadata.fromMediaMetadata(metadata)
		assertEquals("artist", "Energy Basel", parsed.artist)
		assertEquals("album", "Tout l'univers (EP)", parsed.album)
		assertEquals("title", "Tout l'univers - Gjon's Tears", parsed.title)
	}

	/**
	 * A variant of Energy Radio with valid artist and title fields
	 */
	@Test
	fun testEnergyRadioWithMetadata() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
			on { getString(any()) } doReturn null
			on { getLong(any()) } doReturn 0
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ARTIST)) } doReturn "Gjon's Tears"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM)) } doReturn "Tout l'univers (EP)"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_TITLE)) } doReturn "Tout l'univers"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_COMPILATION)) } doReturn "Energy Basel"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)) } doReturn "Energy Basel"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)) } doReturn "Tout l'univers - Gjon's Tears"        // title and artist?
		}
		val parsed = MusicMetadata.fromMediaMetadata(metadata)
		assertEquals("artist", "Gjon's Tears", parsed.artist)
		assertEquals("album", "Tout l'univers (EP)", parsed.album)
		assertEquals("title", "Tout l'univers", parsed.title)
	}

	/**
	 * Swapped DISPLAY fields
	 */
	@Test
	fun testAimp() {
		val metadata = mock<MediaMetadataCompat> {
			on { bundle } doReturn fakeBundle
			on { getString(any()) } doReturn null
			on { getLong(any()) } doReturn 0
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ARTIST)) } doReturn "dj TAKA"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)) } doReturn "dj TAKA"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_ALBUM)) } doReturn "milestone"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_TITLE)) } doReturn "Votum stellarum"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)) } doReturn "Votum stellarum"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)) } doReturn "milestone"
			on { getString(eq(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)) } doReturn "dj TAKA"
		}
		val parsed = MusicMetadata.fromMediaMetadata(metadata)
		assertEquals("artist", "dj TAKA", parsed.artist)
		assertEquals("album", "milestone", parsed.album)
		assertEquals("title", "Votum stellarum", parsed.title)
	}
}
