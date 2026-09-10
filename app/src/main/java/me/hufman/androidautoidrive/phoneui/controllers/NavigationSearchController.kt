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

class NavigationSearchController(val scope: CoroutineScope, val parser: NavigationParser, val mapPlaceSearch: MapPlaceSearch,
                                 val navigationTrigger: NavigationTrigger, val navigationStatusModel: NavigationStatusModel,
                                 val dispatchers: DispatcherProvider = DefaultDispatcherProvider()) {
	var job: Job? = null

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
			triggerNavigation(result)

			// update the result label
			if (navigationStatusModel.isCarNavigating.value == true ||
					navigationStatusModel.isCustomNaviSupportedAndPreferred.value == true) {
				navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_success) }
			} else {
				navigationStatusModel.searchStatus.value = { getString(R.string.lbl_navigation_listener_unsuccess) }
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

		val result = withContext(dispatchers.IO) {
			parser.parseUrl(url.toString()) ?:
			parser.parseUrl(url.toString()) // try a second time
		}
		return result
	}

	suspend fun triggerNavigation(destination: Address) {
		val observer = Observer<Boolean> {}
		try {
			// register for navigation status
			navigationStatusModel.isCarNavigating.observeForever(observer)
			navigationStatusModel.isCustomNaviSupportedAndPreferred.observeForever(observer)
			// try a few times
			for (i in 0 until TRIES) {
				withContext(dispatchers.IO) {
					navigationTrigger.triggerNavigation(destination)
				}
				for (t in 0 until TIMEOUT / 1000) {
					delay(1000)
					// wait up to TIMEOUT or until car begins navigation
					if (navigationStatusModel.isCarNavigating.value == true ||
							navigationStatusModel.isCustomNaviSupportedAndPreferred.value == true) {
						break
					}
				}
				// if the car is navigating, don't try again
				if (navigationStatusModel.isCarNavigating.value == true ||
						navigationStatusModel.isCustomNaviSupportedAndPreferred.value == true) {
					break
				}
			}
		} finally {
			navigationStatusModel.isCarNavigating.removeObserver(observer)
			navigationStatusModel.isCustomNaviSupportedAndPreferred.removeObserver(observer)
		}
	}
}