package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.location.Address
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonObject
import org.mockito.kotlin.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runBlockingTest
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.CoroutineTestRule
import me.hufman.androidautoidrive.DispatcherProvider
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger
import me.hufman.androidautoidrive.cds.CDSDataProvider
import me.hufman.androidautoidrive.phoneui.controllers.NavigationSearchController
import me.hufman.androidautoidrive.phoneui.viewmodels.NavigationStatusModel
import io.bimmergestalt.idriveconnectkit.CDS
import kotlinx.coroutines.test.advanceTimeBy
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.MapResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class NavigationSearchControllerTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	@Rule
	@JvmField
	val coroutineTestRule = CoroutineTestRule()

	val context = mock<Context> {
		on { getString(any()) } doReturn "test"
	}
	val searcher = mock<MapPlaceSearch>()
	val parser = mock<NavigationParser>()
	val navigationTrigger = mock<NavigationTrigger>()
	val cdsData = CDSDataProvider()
	val carInformation = mock<CarInformation> {
		on { cdsData } doReturn cdsData
	}

	val testAddress = mock<Address> {
		on {latitude} doReturn 1.0
		on {longitude} doReturn 2.0
		on {featureName} doReturn "Test Location"
	}

	@Test
	fun testBadSearch() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		whenever(parser.parseUrl(any())) doAnswer {
			// when the parseUrl is called, the label should say
			context.run(model.searchStatus.value!!)
			verify(context).getString(R.string.lbl_navigation_listener_searching)
			clearInvocations(context)
			assertEquals(true, model.isSearching.value)
			assertEquals(false, model.searchFailed.value)
			// then return the address
			null
		}
		model.query.value = "test address"
		controller.startNavigation()
		verify(parser).parseUrl("geo:0,0?q=test+address")

		// show an error
		assertEquals(false, model.isSearching.value)
		assertEquals(true, model.searchFailed.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_parsefailure)
		clearInvocations(context)

		// hide the text after a timeout
		testScheduler.apply { advanceTimeBy(NavigationSearchController.SUCCESS); runCurrent() }
		assertEquals("", context.run(model.searchStatus.value!!))
		verify(context, never()).getString(any())
	}

	@Test
	fun testRetries() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)

		whenever(parser.parseUrl(any())) doReturn testAddress
		controller.startNavigation("test address")

		// should now be trying to send to the car
		assertEquals(true, model.isSearching.value)
		assertEquals(false, model.searchFailed.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_pending)
		clearInvocations(context)
		verify(navigationTrigger).triggerNavigation(testAddress)
		clearInvocations(navigationTrigger)

		// the car missed it the first time, try to send again
		testScheduler.apply { advanceTimeBy(NavigationSearchController.TIMEOUT + 100); runCurrent() }
		verify(navigationTrigger).triggerNavigation(testAddress)
		clearInvocations(navigationTrigger)

		// now wait for the car to notice
		testScheduler.apply { advanceTimeBy(NavigationSearchController.TIMEOUT / 2); runCurrent() }
		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GUIDANCESTATUS, JsonObject().apply { addProperty("guidanceStatus", 1) })

		// The status event wakes the request without a polling interval.
		val acknowledgementTime = testScheduler.currentTime
		testScheduler.runCurrent()
		assertEquals(acknowledgementTime, testScheduler.currentTime)

		// UI should update with success
		assertEquals(false, model.isSearching.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_success)
		clearInvocations(context)

		// hide the text after a timeout
		testScheduler.apply { advanceTimeBy(NavigationSearchController.SUCCESS); runCurrent() }
		assertEquals("", context.run(model.searchStatus.value!!))
		verify(context, never()).getString(any())
	}

	@Test
	fun testUnsuccess() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)

		whenever(parser.parseUrl(any())) doReturn testAddress
		controller.startNavigation("test address")

		// should now be trying to send to the car
		assertEquals(true, model.isSearching.value)
		assertEquals(false, model.searchFailed.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_pending)
		clearInvocations(context)
		verify(navigationTrigger).triggerNavigation(testAddress)
		clearInvocations(navigationTrigger)

		// the car missed it the first time, try to send again
		testScheduler.apply { advanceTimeBy(NavigationSearchController.TIMEOUT + 100); runCurrent() }
		verify(navigationTrigger).triggerNavigation(testAddress)
		clearInvocations(navigationTrigger)

		// the car missed it the second time, try to send again
		testScheduler.apply { advanceTimeBy(NavigationSearchController.TIMEOUT + 100); runCurrent() }
		verify(navigationTrigger).triggerNavigation(testAddress)
		clearInvocations(navigationTrigger)

		// the car missed it the third time, should not try again
		testScheduler.apply { advanceTimeBy(NavigationSearchController.TIMEOUT + 100); runCurrent() }
		verify(navigationTrigger, never()).triggerNavigation(testAddress)

		// UI should update with success
		assertEquals(false, model.isSearching.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_unsuccess)
		clearInvocations(context)

		// hide the text after a timeout
		testScheduler.apply { advanceTimeBy(NavigationSearchController.SUCCESS); runCurrent() }
		assertEquals("", context.run(model.searchStatus.value!!))
		verify(context, never()).getString(any())
	}
	@Test
	fun expandedResultUsesItsResolvedAddress() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		whenever(searcher.resultInformationAsync("place")).thenReturn(CompletableDeferred(MapResult("place", "Museum", "Resolved address")))
		controller.startNavigation(MapResult("place", "Museum"))
		verify(parser).parseUrl("geo:0,0?q=Resolved+address")
		assertEquals(false, model.isSearching.value)
	}

	@Test
	fun missingPlaceDetailsShowsFailureAndStopsSpinner() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		whenever(searcher.resultInformationAsync("place")).thenReturn(CompletableDeferred<MapResult?>().apply { complete(null) })
		controller.startNavigation(MapResult("place", "Museum"))
		assertEquals(false, model.isSearching.value)
		assertEquals(true, model.searchFailed.value)
		verifyNoInteractions(navigationTrigger, parser)
	}

	@Test
	fun parserExceptionShowsFailureAndStopsSpinner() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		whenever(parser.parseUrl(any())).thenThrow(IllegalArgumentException("Malformed destination"))
		controller.startNavigation("bad address")
		assertEquals(false, model.isSearching.value)
		assertEquals(true, model.searchFailed.value)
		verifyNoInteractions(navigationTrigger)
	}

	@Test
	fun cancellationStopsSpinnerWithoutReportingFailure() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		whenever(searcher.resultInformationAsync("place")).thenReturn(CompletableDeferred<MapResult?>())
		controller.startNavigation(MapResult("place", "Museum"))
		assertEquals(true, model.isSearching.value)
		controller.job!!.cancel()
		testScheduler.runCurrent()
		assertEquals(false, model.isSearching.value)
		assertEquals(false, model.searchFailed.value)
		verifyNoInteractions(navigationTrigger, parser)
	}

	@Test
	fun pastedMultilineShareUsesItsLink() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		controller.startNavigation("Museum\nhttps://maps.app.goo.gl/example\nShared place")
		verify(parser).parseUrl("https://maps.app.goo.gl/example")
	}

	@Test
	fun alreadyActiveGuidanceSendsOnceWithoutClaimingNewDestinationWasConfirmed() = coroutineTestRule.testDispatcher.runBlockingTest {
		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GUIDANCESTATUS, JsonObject().apply { addProperty("guidanceStatus", 1) })
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		whenever(parser.parseUrl(any())) doReturn testAddress
		val startTime = testScheduler.currentTime
		controller.startNavigation("replacement destination")
		assertEquals(startTime, testScheduler.currentTime)
		assertEquals(false, model.isSearching.value)
		context.run(model.searchStatus.value!!)
		verify(context).getString(R.string.lbl_navigation_listener_sent)
		verify(context, never()).getString(R.string.lbl_navigation_listener_success)
		assertFalse(model.isCarNavigating.hasObservers())
		testScheduler.advanceUntilIdle()
		verify(navigationTrigger, times(1)).triggerNavigation(testAddress)
	}

	@Test
	fun synchronousGuidanceResponseIsNotMissedAndRemovesObserver() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		whenever(navigationTrigger.triggerNavigation(testAddress)) doAnswer {
			cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GUIDANCESTATUS, JsonObject().apply { addProperty("guidanceStatus", 1) })
		}
		val startTime = testScheduler.currentTime
		assertEquals(NavigationSearchController.NavigationOutcome.CONFIRMED, controller.triggerNavigation(testAddress))
		assertEquals(startTime, testScheduler.currentTime)
		assertFalse(model.isCarNavigating.hasObservers())
		verify(navigationTrigger, times(1)).triggerNavigation(testAddress)
	}

	@Test
	fun cancellingPendingAcknowledgementRemovesObserverAndStopsRetries() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		whenever(parser.parseUrl(any())) doReturn testAddress
		controller.startNavigation("destination")
		assertTrue(model.isCarNavigating.hasObservers())
		controller.job!!.cancel()
		testScheduler.runCurrent()
		assertFalse(model.isCarNavigating.hasObservers())
		assertEquals(false, model.isSearching.value)
		testScheduler.advanceUntilIdle()
		verify(navigationTrigger, times(1)).triggerNavigation(testAddress)
	}

	@Test
	fun cancellationBeforeQueuedDispatchDoesNotSendObsoleteDestination() = coroutineTestRule.testDispatcher.runBlockingTest {
		val queuedIo = StandardTestDispatcher(testScheduler)
		val dispatchers = object : DispatcherProvider by coroutineTestRule.testDispatcherProvider {
			override val IO = queuedIo
		}
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, dispatchers)
		val request = launch { controller.triggerNavigation(testAddress) }
		assertTrue(model.isCarNavigating.hasObservers())
		request.cancel()
		testScheduler.runCurrent()
		assertFalse(model.isCarNavigating.hasObservers())
		verifyNoInteractions(navigationTrigger)
	}

	@Test
	fun customNavigationHandoffDoesNotWaitForNativeGuidance() = coroutineTestRule.testDispatcher.runBlockingTest {
		val model = NavigationStatusModel(carInformation, MutableLiveData(true), MutableLiveData(true), MutableLiveData(null))
		val controller = NavigationSearchController(this, parser, searcher, navigationTrigger, model, coroutineTestRule.testDispatcherProvider)
		val startTime = testScheduler.currentTime
		assertEquals(NavigationSearchController.NavigationOutcome.CONFIRMED, controller.triggerNavigation(testAddress))
		assertEquals(startTime, testScheduler.currentTime)
		assertFalse(model.isCarNavigating.hasObservers())
		verify(navigationTrigger, times(1)).triggerNavigation(testAddress)
	}

}
