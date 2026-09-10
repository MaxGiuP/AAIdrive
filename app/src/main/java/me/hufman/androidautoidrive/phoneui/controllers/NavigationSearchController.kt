package me.hufman.androidautoidrive.phoneui.controllers

import android.location.Address
import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import me.hufman.androidautoidrive.DefaultDispatcherProvider
import me.hufman.androidautoidrive.DispatcherProvider
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger
import me.hufman.androidautoidrive.maps.MapPlaceSearch
import me.hufman.androidautoidrive.maps.MapResult
import me.hufman.androidautoidrive.phoneui.viewmodels.NavigationStatusModel
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

class NavigationSearchController(val scope: CoroutineScope, val parser: NavigationParser, val mapPlaceSearch: MapPlaceSearch,
                                 val navigationTrigger: NavigationTrigger, val navigationStatusModel: NavigationStatusModel,
                                 val dispatchers: DispatcherProvider = DefaultDispatcherProvider()) {
	var job: Job? = null
	enum class NavigationOutcome { CONFIRMED, SENT, FAILED }

	companion object {
		const val TRIES = 3
		const val TIMEOUT = 5000L
		const val SUCCESS = 3000L
	}

	fun startNavigation() {
		startNavigation(navigationStatusModel.query.value ?: "")
	}

	@Suppress("BlockingMethodInNonBlockingContext")
	fun startNavigation(result: MapResult) {
		launchNavigation {
			// show that we are searching while resolving the full MapResult
			navigationStatusModel.isSearching.value = true
			navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_searching) }
			navigationStatusModel.searchFailed.value = false

			val expandedResult = if (result.location != null) {
				result
			} else {
				mapPlaceSearch.resultInformationAsync(result.id).await() ?: result
			}
			if (expandedResult.address != null) {
				navigationStatusModel.query.value = expandedResult.toString()
			}

			// convert to a geo: search url for NavigationTrigger
			// Google Place results don't include a location, for some reason
			val query = if (expandedResult.location != null) {
				"geo:0,0?q=${expandedResult.location.latitude},${expandedResult.location.longitude}+%28${URLEncoder.encode(expandedResult.toString(),"UTF-8")}%29"
			} else {
				val address = expandedResult.address?.takeIf { it.isNotBlank() }
					?: result.address?.takeIf { it.isNotBlank() }
				requireNotNull(address) { "The selected place has no coordinates or address" }
				"geo:0,0?q=${URLEncoder.encode(address, "UTF-8")}"
			}
			startNavigationUpdates(query)
		}
	}

	fun startNavigation(query: CharSequence): Boolean {
		launchNavigation { startNavigationUpdates(query) }
		return false    // hide the keyboard after clicking the search button
	}

	private fun launchNavigation(block: suspend () -> Unit) {
		val previousJob = job
		previousJob?.cancel()
		job = scope.launch(dispatchers.Main) {
			// Finish the old request's cleanup before it can overwrite the new request's spinner.
			previousJob?.join()
			runNavigation(block)
		}
	}

	/** Always finish the spinner after a failed lookup; cancellation still belongs to the caller. */
	private suspend fun runNavigation(block: suspend () -> Unit) {
		try {
			block()
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_parsefailure) }
			navigationStatusModel.searchFailed.value = true
		} finally {
			navigationStatusModel.isSearching.value = false
		}
		delay(SUCCESS)
		navigationStatusModel.searchStatus.value = { "" }
	}

	/**
	 * Run the navigation process, and update the status module at different phases
	 */
	private suspend fun startNavigationUpdates(query: CharSequence) {
		navigationStatusModel.isSearching.value = true
		navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_searching) }
		navigationStatusModel.searchFailed.value = false
		val result = searchAddress(query)

		// start navigation in the car
		if (result == null) {
			navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_parsefailure) }
			navigationStatusModel.searchFailed.value = true
		} else {
			navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_pending) }

			// trigger navigation with the discovered result
			currentCoroutineContext().ensureActive()
			val outcome = triggerNavigation(result)

			// update the result label
			navigationStatusModel.searchFailed.value = outcome == NavigationOutcome.FAILED
			navigationStatusModel.searchStatus.value = when (outcome) {
				NavigationOutcome.CONFIRMED -> { { getString(R.string.lbl_navigation_listener_success) } }
				NavigationOutcome.SENT -> { { getString(R.string.lbl_navigation_listener_sent) } }
				NavigationOutcome.FAILED -> { { getString(R.string.lbl_navigation_listener_unsuccess) } }
			}
		}
	}

	suspend fun searchAddress(query: CharSequence): Address? {
		val trimmedQuery = query.toString().trim()
		val url = if (trimmedQuery.startsWith("geo:", true) ||
				trimmedQuery.startsWith("google.navigation:", true) ||
				trimmedQuery.startsWith("http", true)) {
			trimmedQuery
		} else {
			NavigationParser.extractUrl(trimmedQuery)
				?: "geo:0,0?q=${URLEncoder.encode(trimmedQuery, "UTF-8")}"
		}

		return withContext(dispatchers.IO) {
			currentCoroutineContext().ensureActive()
			parser.parseUrl(url)
		}
	}

	suspend fun triggerNavigation(destination: Address): NavigationOutcome = withContext(dispatchers.Main) {
		val acknowledgement = CompletableDeferred<Unit>()
		val sent = AtomicBoolean(false)
		var sawInactiveGuidance = false
		val observer = Observer<Boolean> { navigating ->
			if (sent.get()) {
				if (navigating != true) sawInactiveGuidance = true
				else if (sawInactiveGuidance) acknowledgement.complete(Unit)
			}
		}
		try {
			// Subscribe before dispatch so an immediate car response cannot be missed.
			navigationStatusModel.isCarNavigating.observeForever(observer)
			val alreadyNavigating = navigationStatusModel.isCarNavigating.value == true
			sawInactiveGuidance = !alreadyNavigating
			val customNavigation = navigationStatusModel.isCustomNaviSupported.value == true &&
					navigationStatusModel.isCustomNaviPreferred.value == true
			for (i in 0 until TRIES) {
				currentCoroutineContext().ensureActive()
				if (acknowledgement.isCompleted) return@withContext NavigationOutcome.CONFIRMED
				withContext(dispatchers.IO) {
					currentCoroutineContext().ensureActive()
					sent.set(true)
					navigationTrigger.triggerNavigation(destination)
				}
				if (customNavigation) return@withContext NavigationOutcome.CONFIRMED
				if (acknowledgement.isCompleted) return@withContext NavigationOutcome.CONFIRMED
				// An existing route's active flag cannot confirm a replacement destination.
				// Send it once and let the driver check the car, rather than resetting guidance.
				if (alreadyNavigating) return@withContext NavigationOutcome.SENT
				if (withTimeoutOrNull(TIMEOUT) { acknowledgement.await(); true } == true) {
					return@withContext NavigationOutcome.CONFIRMED
				}
			}
			NavigationOutcome.FAILED
		} finally {
			acknowledgement.cancel()
			navigationStatusModel.isCarNavigating.removeObserver(observer)
		}
	}
}
