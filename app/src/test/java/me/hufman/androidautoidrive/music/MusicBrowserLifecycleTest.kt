@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.hufman.androidautoidrive.music

import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class MusicBrowserLifecycleTest {
	private val handler = mock<Handler> {
		on { post(any()) } doAnswer { it.getArgument<Runnable>(0).run(); true }
	}
	private val remoteBrowser = mock<MediaBrowserCompat>()
	private val app = MusicAppInfo("Test player", mock(), "test.player", "Browser")

	@Test
	fun browseReturnsOnCallbackWithoutPollingDelayAndUnsubscribes() = runTest {
		val browser = MusicBrowser(handler, remoteBrowser, app, StandardTestDispatcher(testScheduler))
		val result = async { browser.browse("playlist") }
		runCurrent()
		val callback = argumentCaptor<MediaBrowserCompat.SubscriptionCallback>()
		verify(remoteBrowser).subscribe(eq("playlist"), callback.capture())
		val song = mock<MediaBrowserCompat.MediaItem>()
		callback.firstValue.onChildrenLoaded("playlist", mutableListOf(song, null))
		runCurrent()
		assertTrue(result.isCompleted)
		assertEquals(listOf(song), result.await())
		assertEquals(0, testScheduler.currentTime)
		verify(remoteBrowser).unsubscribe("playlist", callback.firstValue)
	}

	@Test
	fun cancelledBrowseUnsubscribesAndIgnoresLateResults() = runTest {
		val browser = MusicBrowser(handler, remoteBrowser, app, StandardTestDispatcher(testScheduler))
		val result = async { browser.browse("playlist") }
		runCurrent()
		val callback = argumentCaptor<MediaBrowserCompat.SubscriptionCallback>()
		verify(remoteBrowser).subscribe(eq("playlist"), callback.capture())
		result.cancel()
		runCurrent()
		verify(remoteBrowser).unsubscribe("playlist", callback.firstValue)
		callback.firstValue.onChildrenLoaded("playlist", mutableListOf(mock()))
		assertTrue(result.isCancelled)
	}

	@Test
	fun timeoutUnsubscribesAndReturnsEmpty() = runTest {
		val browser = MusicBrowser(handler, remoteBrowser, app, StandardTestDispatcher(testScheduler))
		val result = async { browser.browse("playlist", 1000) }
		runCurrent()
		advanceTimeBy(1000)
		runCurrent()
		assertEquals(emptyList<MediaBrowserCompat.MediaItem>(), result.await())
		verify(remoteBrowser).unsubscribe(eq("playlist"), any<MediaBrowserCompat.SubscriptionCallback>())
	}

	@Test
	fun disconnectCancelsPendingBrowseAndSearchImmediately() = runTest {
		val browser = MusicBrowser(handler, remoteBrowser, app, StandardTestDispatcher(testScheduler))
		val browse = async { browser.browse("playlist") }
		val search = async { browser.search("query") }
		runCurrent()
		browser.disconnect()
		runCurrent()
		assertFalse(browser.connected)
		assertTrue(browse.isCancelled)
		assertTrue(search.isCancelled)
		assertEquals(emptyList<MediaBrowserCompat.MediaItem>(), browser.browse("playlist"))
		assertNull(browser.search("query"))
		assertEquals(0, testScheduler.currentTime)
		verify(remoteBrowser).disconnect()
	}

	@Test
	fun searchReturnsOnCallbackAndTimesOutWithoutPolling() = runTest {
		val browser = MusicBrowser(handler, remoteBrowser, app, StandardTestDispatcher(testScheduler))
		val search = async { browser.search("query") }
		runCurrent()
		val callback = argumentCaptor<MediaBrowserCompat.SearchCallback>()
		verify(remoteBrowser).search(eq("query"), isNull(), callback.capture())
		val song = mock<MediaBrowserCompat.MediaItem>()
		callback.firstValue.onSearchResult("query", null, mutableListOf(song))
		runCurrent()
		assertEquals(listOf(song), search.await())
		assertEquals(0, testScheduler.currentTime)
		val timeout = async { browser.search("missing", 1000) }
		advanceUntilIdle()
		assertNull(timeout.await())
	}
}
