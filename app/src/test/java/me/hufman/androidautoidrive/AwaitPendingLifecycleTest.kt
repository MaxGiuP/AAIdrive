@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.hufman.androidautoidrive

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import me.hufman.androidautoidrive.utils.awaitPending
import org.junit.Assert.*
import org.junit.Test

class AwaitPendingLifecycleTest {
	@Test
	fun ownLoadingTimeoutNotifiesThenReturnsTheEventualResult() = runTest {
		val request = CompletableDeferred<String>()
		var notifications = 0
		val result = async { request.awaitPending(100) { notifications++ } }
		runCurrent()
		advanceTimeBy(100)
		runCurrent()
		assertEquals(1, notifications)
		assertFalse(result.isCompleted)
		request.complete("loaded")
		runCurrent()
		assertEquals("loaded", result.await())
	}

	@Test
	fun cancelledRequestDoesNotTriggerTimeoutRetry() = runTest {
		val request = CompletableDeferred<String>()
		var retried = false
		val result = async { request.awaitPending(1000) { retried = true } }
		runCurrent()
		request.cancel()
		runCurrent()
		assertTrue(result.isCancelled)
		assertFalse(retried)
	}

	@Test
	fun cancelledCallerDoesNotTriggerTimeoutRetry() = runTest {
		val request = CompletableDeferred<String>()
		var retried = false
		val result = async { request.awaitPending(1000) { retried = true } }
		runCurrent()
		result.cancel()
		runCurrent()
		assertFalse(retried)
		request.cancel()
	}

	@Test
	fun parentTimeoutDoesNotTriggerAnObsoleteRetry() = runTest {
		val request = CompletableDeferred<String>()
		var retried = false
		val result = async { withTimeout(50) { request.awaitPending(1000) { retried = true } } }
		advanceUntilIdle()
		assertTrue(result.isCancelled)
		assertFalse(retried)
		request.cancel()
	}
}
