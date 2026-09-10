@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.hufman.androidautoidrive.music.controllers

import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import kotlinx.coroutines.test.*
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicBrowser
import org.junit.Test
import org.mockito.kotlin.*

class GenericMusicControllerLifecycleTest {
	private val handler = mock<Handler> {
		on { post(any()) } doAnswer { it.getArgument<Runnable>(0).run(); true }
	}
	private val remoteBrowser = mock<MediaBrowserCompat>()
	private val remoteController = mock<MediaControllerCompat>()
	private val spotify = MusicAppInfo("Spotify", mock(), "com.spotify.music", "Browser")

	@Test
	fun spotifyReadsShareOneRefreshAndDoNotLaunchAgainOnEveryRedraw() = runTest {
		val browser = MusicBrowser(handler, remoteBrowser, spotify, StandardTestDispatcher(testScheduler))
		val controller = GenericMusicAppController(mock(), remoteController, browser)
		repeat(20) {
			controller.getQueue()
			controller.getCustomActions()
		}
		runCurrent()
		verify(remoteBrowser, times(1)).subscribe(any(), any<MediaBrowserCompat.SubscriptionCallback>())
		advanceTimeBy(200)
		runCurrent()
		repeat(20) { controller.getQueue() }
		runCurrent()
		verify(remoteBrowser, times(1)).subscribe(any(), any<MediaBrowserCompat.SubscriptionCallback>())
		controller.disconnect()
	}

	@Test
	fun disconnectCancelsSpotifyRefreshAndPreventsFurtherProbes() = runTest {
		val browser = MusicBrowser(handler, remoteBrowser, spotify, StandardTestDispatcher(testScheduler))
		val controller = GenericMusicAppController(mock(), remoteController, browser)
		controller.getQueue()
		runCurrent()
		controller.disconnect()
		runCurrent()
		controller.getQueue()
		controller.getCustomActions()
		runCurrent()
		verify(remoteBrowser, times(1)).subscribe(any(), any<MediaBrowserCompat.SubscriptionCallback>())
		verify(remoteBrowser, times(1)).unsubscribe(any(), any<MediaBrowserCompat.SubscriptionCallback>())
		verify(remoteBrowser).disconnect()
	}
}
