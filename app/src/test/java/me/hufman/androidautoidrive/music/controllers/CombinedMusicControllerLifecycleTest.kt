@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.hufman.androidautoidrive.music.controllers

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import me.hufman.androidautoidrive.MutableObservable
import me.hufman.androidautoidrive.music.MusicMetadata
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class CombinedMusicControllerLifecycleTest {
	private class RequestsController: MusicAppController by mock() {
		var browseRequest: suspend () -> List<MusicMetadata> = { emptyList() }
		var searchRequest: suspend () -> List<MusicMetadata>? = { null }
		override suspend fun browse(directory: MusicMetadata?) = browseRequest()
		override suspend fun search(query: String) = searchRequest()
	}

	private fun observable(controller: MusicAppController) = MutableObservable<MusicAppController>().apply { value = controller }

	@Test
	fun pendingConnectionFailureWakesWaitingBrowseWithoutPolling() = runTest {
		val pending = MutableObservable<MusicAppController>()
		val controller = CombinedMusicAppController(listOf(pending))
		val probeFinished = mock<() -> Unit>()
		controller.onCreatedCallback(probeFinished)
		val browse = async { controller.browse(null) }
		runCurrent()
		assertFalse(browse.isCompleted)
		pending.value = null
		runCurrent()
		assertTrue(browse.isCompleted)
		assertEquals(emptyList<MusicMetadata>(), browse.await())
		assertEquals(0, testScheduler.currentTime)
		verify(probeFinished).invoke()
	}

	@Test
	fun disconnectWakesWaitersAndClosesLateConnections() = runTest {
		val pending = MutableObservable<MusicAppController>()
		val controller = CombinedMusicAppController(listOf(pending))
		val search = async { controller.search("query") }
		runCurrent()
		controller.disconnect()
		runCurrent()
		assertTrue(search.isCompleted)
		assertNull(search.await())
		val lateController = mock<MusicAppController> { on { isConnected() } doReturn true }
		pending.value = lateController
		assertFalse(controller.isConnected())
		assertFalse(controller.isPending())
		verify(lateController).disconnect()
		verify(lateController, never()).subscribe(any())
		controller.play()
		controller.pause()
		verify(lateController, never()).play()
		verify(lateController, never()).pause()
	}

	@Test
	fun failedProviderFallsBackForBrowsingAndSearching() = runTest {
		val failed = RequestsController().apply {
			browseRequest = { throw IllegalStateException("Browser failed") }
			searchRequest = { throw UnsupportedOperationException("Search unsupported") }
		}
		val songs = listOf(MusicMetadata(mediaId = "song"))
		val working = RequestsController().apply {
			browseRequest = { songs }
			searchRequest = { songs }
		}
		val controller = CombinedMusicAppController(listOf(observable(failed), observable(working)))
		assertEquals(songs, controller.browse(null))
		assertEquals(songs, controller.search("query"))
	}

	@Test
	fun cancellationDoesNotStartAnotherProviderRequest() = runTest {
		val cancelled = RequestsController().apply {
			browseRequest = { throw CancellationException("User navigated away") }
			searchRequest = { throw CancellationException("New search") }
		}
		val other = mock<MusicAppController>()
		val controller = CombinedMusicAppController(listOf(observable(cancelled), observable(other)))
		val browse = async { controller.browse(null) }
		val search = async { controller.search("query") }
		runCurrent()
		assertTrue(browse.isCancelled)
		assertTrue(search.isCancelled)
		verify(other, never()).browse(anyOrNull())
		verify(other, never()).search(any())
	}
}
