@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.hufman.androidautoidrive.music

import android.content.Context
import android.media.session.MediaSessionManager
import android.os.Handler
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import me.hufman.androidautoidrive.music.controllers.MusicAppController
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class MusicControllerLifecycleTest {
	private val sessionManager = mock<MediaSessionManager>()
	private val context = mock<Context> {
		on { getSystemService(MediaSessionManager::class.java) } doReturn sessionManager
	}
	private val handler = mock<Handler>()

	private class RequestsController: MusicAppController by mock() {
		var browseRequest: suspend () -> List<MusicMetadata> = { emptyList() }
		var searchRequest: suspend () -> List<MusicMetadata>? = { null }
		override suspend fun browse(directory: MusicMetadata?) = browseRequest()
		override suspend fun search(query: String) = searchRequest()
	}

	@Test
	fun disconnectedRequestsCompleteImmediately() = runTest {
		val controller = MusicController(context, handler, StandardTestDispatcher(testScheduler))
		val browse = controller.browseAsync(null)
		val search = controller.searchAsync("query")
		assertTrue(browse.isCompleted)
		assertTrue(search.isCompleted)
		assertEquals(emptyList<MusicMetadata>(), browse.await())
		assertNull(search.await())
	}

	@Test
	fun newerSearchCancelsOldResultAndItsUnderlyingRequest() = runTest {
		val controller = MusicController(context, handler, StandardTestDispatcher(testScheduler))
		val app = RequestsController()
		controller.currentAppController = app
		var oldRequestStopped = false
		app.searchRequest = { try { awaitCancellation() } finally { oldRequestStopped = true } }
		val oldResult = controller.searchAsync("old")
		runCurrent()
		val songs = listOf(MusicMetadata(mediaId = "new-result"))
		app.searchRequest = { songs }
		val newResult = controller.searchAsync("new")
		runCurrent()
		assertTrue(oldResult.isCancelled)
		assertTrue(oldRequestStopped)
		assertEquals(songs, newResult.await())
	}

	@Test
	fun cancellationOfReturnedBrowseResultStopsUnderlyingRequest() = runTest {
		val controller = MusicController(context, handler, StandardTestDispatcher(testScheduler))
		val app = RequestsController()
		controller.currentAppController = app
		var requestStopped = false
		app.browseRequest = { try { awaitCancellation() } finally { requestStopped = true } }
		val result = controller.browseAsync(null)
		runCurrent()
		result.cancel()
		runCurrent()
		assertTrue(requestStopped)
		assertTrue(controller.browseJob!!.isCancelled)
	}

	@Test
	fun disconnectCancelsBothRequestsAndRedrawTasks() = runTest {
		val controller = MusicController(context, handler, StandardTestDispatcher(testScheduler))
		val app = RequestsController().apply {
			browseRequest = { awaitCancellation() }
			searchRequest = { awaitCancellation() }
		}
		controller.currentAppController = app
		val browse = controller.browseAsync(null)
		val search = controller.searchAsync("query")
		runCurrent()
		controller.disconnectApp(pause = false)
		runCurrent()
		assertTrue(browse.isCancelled)
		assertTrue(search.isCancelled)
		assertNull(controller.currentAppController)
		assertNull(controller.browseJob)
		assertNull(controller.searchJob)
		verify(handler).removeCallbacks(controller.redrawTask)
		verify(handler).removeCallbacks(controller.redrawProgressTask)
	}

	@Test
	fun providerFailuresResolveWithoutLeavingSpinnersRunning() = runTest {
		val controller = MusicController(context, handler, StandardTestDispatcher(testScheduler))
		controller.currentAppController = RequestsController().apply {
			browseRequest = { throw IllegalStateException("Service disconnected") }
			searchRequest = { throw UnsupportedOperationException("Search unsupported") }
		}
		val browse = controller.browseAsync(null)
		val search = controller.searchAsync("query")
		runCurrent()
		assertEquals(emptyList<MusicMetadata>(), browse.await())
		assertNull(search.await())
	}

	@Test
	fun pauseWhileDisconnectedClearsPendingAutoplay() {
		val controller = MusicController(context, handler)
		controller.desiredPlayback = true
		controller.pause()
		assertFalse(controller.desiredPlayback)
	}
}
