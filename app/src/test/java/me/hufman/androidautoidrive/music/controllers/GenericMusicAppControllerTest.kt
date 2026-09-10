package me.hufman.androidautoidrive.music.controllers

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import org.mockito.kotlin.*
import kotlinx.coroutines.runBlocking
import me.hufman.androidautoidrive.music.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockConstruction


class GenericMusicAppControllerTest {
	val context = mock<Context>()
	val mediaTransportControls = mock<MediaControllerCompat.TransportControls>()
	val mediaController = mock<MediaControllerCompat> {
		on { transportControls } doReturn mediaTransportControls
		on { packageName } doReturn "com.musicapp"
	}
	val musicBrowser = mock<MusicBrowser> {
		on { connected } doReturn true
	}
	lateinit var controller: GenericMusicAppController

	fun createPlaybackState(stateValue: Int, positionValue: Long, actionsValue: Long): PlaybackStateCompat {
		return mock {
			on { state } doReturn stateValue
			on { position } doReturn positionValue
			on { actions } doReturn actionsValue
			on { playbackSpeed } doReturn 1f
		}
	}

	@Before
	fun setup() {
		controller = GenericMusicAppController(context, mediaController, musicBrowser)
	}

	@Test
	fun toggleOnlyPlaybackUsesMediaKeysAndFreshState() {
		val paused = createPlaybackState(PlaybackStateCompat.STATE_PAUSED, 0, PlaybackStateCompat.ACTION_PLAY_PAUSE)
		val playing = createPlaybackState(PlaybackStateCompat.STATE_PLAYING, 0, PlaybackStateCompat.ACTION_PLAY_PAUSE)
		mockConstruction(KeyEvent::class.java) { event, construction ->
			whenever(event.action) doReturn (construction.arguments()[0] as Int)
			whenever(event.keyCode) doReturn (construction.arguments()[1] as Int)
		}.use {
			whenever(mediaController.playbackState) doReturn paused
			assertTrue(controller.isSupportedAction(MusicAction.PLAY))
			assertTrue(controller.isSupportedAction(MusicAction.PAUSE))
			controller.play()
			whenever(mediaController.playbackState) doReturn playing
			controller.play() // already playing: do not accidentally pause
			controller.pause()
			whenever(mediaController.playbackState) doReturn paused
			controller.pause() // already paused: do not accidentally start playback
			val events = argumentCaptor<KeyEvent>()
			verify(mediaController, times(4)).dispatchMediaButtonEvent(events.capture())
			assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP, KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), events.allValues.map { event -> event.action })
			assertTrue(events.allValues.all { event -> event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE })
			verifyNoInteractions(mediaTransportControls)
		}
	}

	@Test
	fun prefersDiscretePlayPauseActionsWhenAvailable() {
		val state = createPlaybackState(PlaybackStateCompat.STATE_PAUSED, 0,
			PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
		whenever(mediaController.playbackState) doReturn state
		controller.play()
		controller.pause()
		verify(mediaTransportControls).play()
		verify(mediaTransportControls).pause()
		verify(mediaController, never()).dispatchMediaButtonEvent(any())
	}

	@Test
	fun doesNotToggleDuringAnAmbiguousLoadingState() {
		listOf(PlaybackStateCompat.STATE_CONNECTING, PlaybackStateCompat.STATE_BUFFERING).forEach { state ->
			val playback = createPlaybackState(state, 0, PlaybackStateCompat.ACTION_PLAY_PAUSE)
			whenever(mediaController.playbackState) doReturn playback
			controller.play()
			controller.pause()
		}
		verify(mediaController, never()).dispatchMediaButtonEvent(any())
		verifyNoInteractions(mediaTransportControls)
	}

	@Test
	fun testControl() {
		controller.play()
		verify(mediaTransportControls).play()

		controller.pause()
		verify(mediaTransportControls).pause()

		controller.skipToPrevious()
		verify(mediaTransportControls).skipToPrevious()

		controller.skipToNext()
		verify(mediaTransportControls).skipToNext()

		controller.seekTo(100)
		verify(mediaTransportControls).seekTo(100)

		val song = MusicMetadata(mediaId = "test", queueId = 2)
		controller.playSong(song)
		verify(mediaTransportControls).playFromMediaId(song.mediaId, null)
		controller.playQueue(song)
		verify(mediaTransportControls).skipToQueueItem(song.queueId as Long)

		controller.playFromSearch("query")
		verify(mediaTransportControls).playFromSearch("query", null)
	}

	@Test
	fun testSupportedAction() {
		whenever(mediaController.playbackState) doAnswer {
			createPlaybackState(0, 0, MusicAction.PLAY.flag)
		}
		assertTrue(controller.isSupportedAction(MusicAction.PLAY))
	}

	@Test
	fun testQueueSelectionPrefersQueueIdWhenSupported() {
		val playbackState = createPlaybackState(0, 0,
			PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
		whenever(mediaController.playbackState) doReturn playbackState
		controller.playQueue(MusicMetadata(mediaId = "repeated-song", queueId = 42))
		verify(mediaTransportControls).skipToQueueItem(42)
		verify(mediaTransportControls, never()).playFromMediaId(any(), anyOrNull())
	}

	@Test
	fun testQueueSelectionFallsBackToMediaId() {
		val extras = mock<Bundle>()
		val playbackState = createPlaybackState(0, 0,
			PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
		whenever(mediaController.playbackState) doReturn playbackState
		controller.playQueue(MusicMetadata(mediaId = "playlist-song", queueId = 42, extras = extras))
		verify(mediaTransportControls).playFromMediaId("playlist-song", extras)
		verify(mediaTransportControls, never()).skipToQueueItem(any())
	}

	@Test
	fun testQueueSelectionWithOnlyMediaId() {
		val playbackState = createPlaybackState(0, 0,
			PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
		whenever(mediaController.playbackState) doReturn playbackState
		controller.playQueue(MusicMetadata(mediaId = "playlist-song"))
		verify(mediaTransportControls).playFromMediaId("playlist-song", null)
		verify(mediaTransportControls, never()).skipToQueueItem(any())
	}

	@Test
	fun testQueueSelectionDoesNotSendUnknownIdsOrUnsupportedMediaIdAction() {
		controller.playQueue(MusicMetadata(queueId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()))
		controller.playQueue(MusicMetadata(mediaId = "playlist-song"))
		controller.playQueue(MusicMetadata())
		verifyNoInteractions(mediaTransportControls)
	}

	@Test
	fun testCustomActions() {
		val otherAction = CustomAction("com.wrongapp", "test", "Name", 0, null, null, null)
		controller.customAction(otherAction)
		verify(mediaTransportControls, never()).sendCustomAction(any<String>(), anyOrNull())

		val myAction = CustomAction("com.musicapp", "test", "Name", 0, null, null, null)
		controller.customAction(myAction)
		verify(mediaTransportControls).sendCustomAction(myAction.action, null)

		// empty playbackstate, empty actions
		val emptyActions = controller.getCustomActions()
		assertEquals(0, emptyActions.size)

		// parsing an official custom action
		// prepare the context to return info from the app
		val actionIcon = mock<Drawable>()
		@Suppress("DEPRECATION")
		val resources = mock<Resources> {
			on { getDrawable(any()) } doReturn actionIcon       // unit tests run under old SDK codepath in ResourcesCompat
			on { getDrawable(any(), anyOrNull()) } doReturn actionIcon
		}
		val packageManager = mock<PackageManager> {
			on { getResourcesForApplication(any<String>()) } doReturn resources
		}
		whenever(context.packageManager) doReturn packageManager

		val playbackState = createPlaybackState(PlaybackStateCompat.STATE_PLAYING, 1000, 0)
		whenever(playbackState.customActions) doAnswer {
			listOf(mock {
				on { action } doReturn "test"
				on { name } doReturn "Name"
				on { icon } doReturn 30
			})
		}
		whenever(mediaController.playbackState) doReturn playbackState
		val expectedAction = CustomAction("com.musicapp", "test", "Name", 30, actionIcon, null, null)
		val parsedAction = controller.getCustomActions()
		assertEquals(1, parsedAction.size)
		assertEquals(expectedAction, parsedAction[0])
		assertEquals(expectedAction.icon, parsedAction[0].icon)
	}

	@Test
	fun testIsShuffling() {
		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_ALL
		}
		assertTrue(controller.isShuffling())

		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_GROUP
		}
		assertTrue(controller.isShuffling())

		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_NONE
		}
		assertFalse(controller.isShuffling())

		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_INVALID
		}
		assertFalse(controller.isShuffling())
	}

	@Test
	fun testToggleShuffle() {
		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_NONE
		}
		controller.toggleShuffle()
		verify(mediaTransportControls).setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL)

		whenever(mediaController.shuffleMode) doAnswer {
			PlaybackStateCompat.SHUFFLE_MODE_ALL
		}
		controller.toggleShuffle()
		verify(mediaTransportControls).setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
	}

	@Test
	fun testGetRepeatMode() {
		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_NONE
		}
		assertEquals(RepeatMode.OFF, controller.getRepeatMode())

		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_ALL
		}
		assertEquals(RepeatMode.ALL, controller.getRepeatMode())

		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_ONE
		}
		assertEquals(RepeatMode.ONE, controller.getRepeatMode())
	}

	@Test
	fun testRepeatToggle() {
		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_NONE
		}
		controller.toggleRepeat()
		verify(mediaTransportControls).setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL)

		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_ALL
		}
		controller.toggleRepeat()
		verify(mediaTransportControls).setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)

		whenever(mediaController.repeatMode) doAnswer {
			PlaybackStateCompat.REPEAT_MODE_ONE
		}
		controller.toggleRepeat()
		verify(mediaTransportControls).setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
	}

	@Test
	fun testQueue() {
		val mediaDescription = mock<MediaDescriptionCompat> {
			on { iconBitmap } doAnswer { mock() }
			on { title } doReturn "test title"
		}
		val queueTitle = "queue title"
		whenever(mediaController.queueTitle) doAnswer { queueTitle }
		whenever(mediaController.queue) doAnswer {
			listOf(mock {
				on { queueId } doReturn 2L
				on { description } doReturn mediaDescription
			})
		}
		val queue = controller.getQueue()
		assertNotNull(queue)
		assertEquals(queueTitle, queue!!.title)
		assertNull(queue.subtitle)

		val songs = queue.songs
		assertNotNull(queue.songs)
		assertEquals(1, songs!!.size)
		assertEquals(2L, songs[0].queueId)
		assertEquals("test title", songs[0].title)
	}

	@Test
	fun testQueueParsingReusesUnchangedItemsAndUpdatesTitle() {
		val descriptionValue = mock<MediaDescriptionCompat> {
			on { mediaId } doReturn "song"
			on { title } doReturn "Song"
		}
		val queueItem = mock<MediaSessionCompat.QueueItem> {
			on { queueId } doReturn 1L
			on { description } doReturn descriptionValue
		}
		whenever(mediaController.queue) doReturn listOf(queueItem)
		val first = controller.getQueue()
		assertSame(first, controller.getQueue())
		verify(descriptionValue, times(1)).title

		whenever(mediaController.queueTitle) doReturn "Renamed playlist"
		val renamed = controller.getQueue()
		assertEquals("Renamed playlist", renamed?.title)
		assertSame(first?.songs, renamed?.songs)
		verify(descriptionValue, times(1)).title
	}

	@Test
	fun testQueueParsingDetectsChangesToTheSameList() {
		val descriptions = (1..2).map { index -> mock<MediaDescriptionCompat> {
			on { mediaId } doReturn "song-$index"
			on { title } doReturn "Song $index"
		} }
		val items = descriptions.mapIndexed { index, value -> mock<MediaSessionCompat.QueueItem> {
			on { queueId } doReturn index.toLong()
			on { description } doReturn value
		} }.toMutableList()
		whenever(mediaController.queue) doReturn items
		val original = controller.getQueue()
		items.reverse()
		val reordered = controller.getQueue()
		assertNotSame(original?.songs, reordered?.songs)
		assertEquals(listOf("song-2", "song-1"), reordered?.songs?.map { it.mediaId })
		items.clear()
		assertEquals(emptyList<MusicMetadata>(), controller.getQueue()?.songs)
	}

	@Test
	fun testQueueContainsEveryPublishedSongInOrder() {
		val queueItems = (0 until 150).map { index ->
			val descriptionValue = mock<MediaDescriptionCompat> {
				on { mediaId } doReturn "song-$index"
				on { title } doReturn "Song $index"
			}
			mock<MediaSessionCompat.QueueItem> {
				on { queueId } doReturn index.toLong()
				on { description } doReturn descriptionValue
			}
		}
		whenever(mediaController.queue) doReturn queueItems
		val songs = controller.getQueue()?.songs!!
		assertEquals(150, songs.size)
		assertEquals((0 until 150).map { "song-$it" }, songs.map { it.mediaId })
		controller.playQueue(songs.last())
		verify(mediaTransportControls).skipToQueueItem(149)
	}

	@Test
	fun testUnavailableQueueIsNotFabricatedFromCurrentTrack() {
		whenever(mediaController.queue) doReturn null
		assertNull(controller.getQueue()?.songs)
	}

	@Test
	fun testMetadata() {
		val mockMetadata = mock<Bundle> {
			on { getString(any()) } doReturn null as String?
		}
		whenever(mediaController.metadata) doAnswer {
			mock {
				on { getBitmap(any()) } doAnswer { mockMetadata.getParcelable(it.getArgument(0)) }
				on { getLong(any()) } doAnswer { mockMetadata.getLong(it.getArgument(0)) }
				on { getString(any()) } doAnswer { mockMetadata.getString(it.getArgument(0)) }
				on { bundle } doReturn mockMetadata
			}
		}

		// mediaId
		for (key in listOf("MEDIA_ID")) {
			reset(mockMetadata)
			whenever(mockMetadata.getString("android.media.metadata.$key")) doReturn "MediaId-$key"
			val metadata = controller.getMetadata()
			assertEquals("MediaId-$key", metadata?.mediaId)
		}

		// artist
		for (key in listOf("ALBUM_ARTIST", "ARTIST")) {
			reset(mockMetadata)
			whenever(mockMetadata.getString("android.media.metadata.$key")) doReturn "Artist-$key"
			val metadata = controller.getMetadata()
			assertEquals("Artist-$key", metadata?.artist)
		}

		// artist
		for (key in listOf("ALBUM")) {
			reset(mockMetadata)
			whenever(mockMetadata.getString("android.media.metadata.$key")) doReturn "Album-$key"
			val metadata = controller.getMetadata()
			assertEquals("Album-$key", metadata?.album)
		}

		// title
		for (key in listOf("DISPLAY_TITLE", "TITLE")) {
			reset(mockMetadata)
			whenever(mockMetadata.getString("android.media.metadata.$key")) doReturn "testTitle-$key"
			val metadata = controller.getMetadata()
			assertEquals("testTitle-$key", metadata?.title)
		}

		// longs
		for (item in mapOf("DURATION" to "getDuration", "TRACK_NUMBER" to  "getTrackNumber", "NUM_TRACKS" to "getTrackCount")) {
			reset(mockMetadata)
			val expected = item.key.length.toLong()
			whenever(mockMetadata.getLong("android.media.metadata.${item.key}")) doReturn expected
			val metadata = controller.getMetadata()
			val found = metadata?.javaClass?.getMethod(item.value)?.invoke(metadata)
			assertEquals(expected, found)
		}

		// coverart
		for (key in listOf("ART", "ALBUM_ART", "DISPLAY_ICON")) {
			reset(mockMetadata)
			val coverArt = mock<Bitmap>()
			whenever(mockMetadata.getParcelable<Bitmap>("android.media.metadata.$key")) doReturn coverArt
			val metadata = controller.getMetadata()
			assertEquals(coverArt, metadata?.coverArt)
		}

		// coverartUri
		for (key in listOf("ART_URI", "ALBUM_ART_URI", "DISPLAY_ICON_URI")) {
			reset(mockMetadata)
			whenever(mockMetadata.getString("android.media.metadata.$key")) doReturn "uri-$key"
			val metadata = controller.getMetadata()
			assertEquals("uri-$key", metadata?.coverArtUri)
		}
	}

	@Test
	fun testPlaybackPosition() {
		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_PAUSED, 1000, 0) }
		val playbackPosition = controller.getPlaybackPosition()
		assertTrue(playbackPosition.isPaused)
		assertFalse(playbackPosition.isBuffering)
		assertEquals(1000, playbackPosition.lastPosition)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_BUFFERING, 1000, 0) }
		assertTrue(controller.getPlaybackPosition().isPaused)
		assertTrue(controller.getPlaybackPosition().isBuffering)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_CONNECTING, 1000, 0) }
		assertTrue(controller.getPlaybackPosition().isPaused)
		assertTrue(controller.getPlaybackPosition().isBuffering)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_STOPPED, 1000, 0) }
		assertTrue(controller.getPlaybackPosition().isPaused)
		assertFalse(controller.getPlaybackPosition().isBuffering)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_NONE, 1000, 0) }
		assertTrue(controller.getPlaybackPosition().isPaused)
		assertFalse(controller.getPlaybackPosition().isBuffering)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_PLAYING, 1000, 0) }
		assertFalse(controller.getPlaybackPosition().isPaused)
		assertFalse(controller.getPlaybackPosition().isBuffering)

		whenever(mediaController.playbackState) doAnswer { createPlaybackState(PlaybackStateCompat.STATE_PLAYING or PlaybackStateCompat.STATE_BUFFERING, 1000, 0) }
		assertFalse(controller.getPlaybackPosition().isPaused)
		assertFalse(controller.getPlaybackPosition().isBuffering)

		whenever(mediaController.playbackState) doReturn null as PlaybackStateCompat?
		val defaultPlaybackPosition = controller.getPlaybackPosition()
		assertTrue(defaultPlaybackPosition.isPaused)
		assertEquals(0, defaultPlaybackPosition.lastPosition)
		assertEquals(0, defaultPlaybackPosition.maximumPosition)
	}

	@Test
	fun testPlaybackPositionPreservesPlaybackSpeed() {
		val playbackState = createPlaybackState(PlaybackStateCompat.STATE_PLAYING, 1000, 0)
		whenever(playbackState.playbackSpeed) doReturn 1.75f
		whenever(mediaController.playbackState) doReturn playbackState
		assertEquals(1.75f, controller.getPlaybackPosition().playbackSpeed, 0f)
	}

	@Test
	fun testBrowse() {
		// null results
		runBlocking {
			val results = controller.browse(null)
			assertEquals(0, results.size)
			verify(musicBrowser).browse(null)
		}

		runBlocking {
			val root = MusicMetadata(mediaId = "/")
			val descriptionValue = mock<MediaDescriptionCompat> {
				on { title } doReturn "title"
				on { extras } doAnswer { mock {
					on { getString("android.media.metadata.ARTIST") } doReturn "Artist"
				}}
			}
			whenever(musicBrowser.browse("/")) doAnswer {
				listOf(mock {
					on { mediaId } doReturn "mediaID"
					on { description } doReturn descriptionValue
				})
			}
			val results = controller.browse(root)
			assertEquals(1, results.size)
			assertEquals("mediaID", results[0].mediaId)
			assertEquals("title", results[0].title)
			assertEquals("Artist", results[0].artist)
			verify(musicBrowser).browse("/")
		}
	}

	@Test
	fun testSearch() {
		// null results
		runBlocking {
			val results = controller.search("")
			assertEquals(null, results)
			verify(musicBrowser).search("")
		}

		runBlocking {
			val descriptionValue = mock<MediaDescriptionCompat> {
				on { title } doReturn "title"
				on { extras } doAnswer { mock {
					on { getString("android.media.metadata.ARTIST") } doReturn "Artist"
				}}
			}
			whenever(musicBrowser.search("query")) doAnswer {
				listOf(mock {
					on { mediaId } doReturn "mediaID"
					on { description } doReturn descriptionValue
				})
			}
			val results = controller.search("query")
			assertEquals(1, results!!.size)
			assertEquals("mediaID", results[0].mediaId)
			assertEquals("title", results[0].title)
			assertEquals("Artist", results[0].artist)
			verify(musicBrowser).search("query")
		}
	}

	@Test
	fun testDisconnect() {
		controller.disconnect()
		// tries to load up controllerCallback, which crashes
		// and so it never gets to unregisterCallback
//		verify(mediaController).unregisterCallback(any())
		verify(musicBrowser).disconnect()
	}

	@Test
	fun testToString() {
		assertEquals("GenericMusicAppController(com.musicapp,true)", controller.toString())
	}
}
