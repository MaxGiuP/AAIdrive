package me.hufman.androidautoidrive.carapp.navigation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.google.openlocationcode.OpenLocationCode
import me.hufman.androidautoidrive.maps.LatLong
import java.io.IOException
import java.net.*
import java.util.*


interface AddressSearcher {
	fun search(query: String): Address?
}

class AndroidGeocoderSearcher(context: Context): AddressSearcher {
	val geocoder = Geocoder(context)
	override fun search(query: String): Address? {
		return try {
			// TODO https://github.com/BimmerGestalt/AAIdrive/issues/729
			geocoder.getFromLocationName(query, 1)?.getOrNull(0)
		} catch (e: IOException) {
			null
		}
	}
}

class NavigationParser(val addressSearcher: AddressSearcher, val redirector: URLRedirector) {
	companion object {
		val TAG = "NavigationParser"
		val NUM_MATCHER = Regex("^([0-9]+)\\s+(.*)")
		private const val NUMBER = "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"
		private val COORDINATES = Regex("^($NUMBER)\\s*,\\s*($NUMBER)(?:\\s*,$NUMBER)?\\s*(?:\\((.*)\\))?$", RegexOption.DOT_MATCHES_ALL)
		private val COORDINATE_PREFIX = Regex("^($NUMBER\\s*,\\s*$NUMBER)(?:,|$)")
		private val PLACE_COORDINATES = Regex("!3d($NUMBER)!4d($NUMBER)(?:!|$)")
		val PLUSCODE_SPLITTER = Regex("^([2-9CFGHJMPQRVWX+]+)([, ](.*))?")
		val PLUSCODE_URL_MATCHER = Regex("^/+([2-9CFGHJMPQRVWX+]+([, ](.*))?)$")
		private val GOOGLE_HOST = Regex("(?:[a-z0-9-]+\\.)*google\\.(?:com|[a-z]{2}|(?:com|co)\\.[a-z]{2})", RegexOption.IGNORE_CASE)
		private val SHORT_LINK_HOSTS = setOf("goo.gl", "maps.app.goo.gl")
		val URL_MATCHER = Regex("(?:https?://|geo:|google\\.navigation:)[^\\s<>\"]+", RegexOption.IGNORE_CASE)

		/** Shares often include a place name and a URL on separate lines. */
		fun extractUrl(text: CharSequence?): String? {
			text ?: return null
			val match = URL_MATCHER.find(text) ?: return null
			val nativeUri = match.value.startsWith("geo:", true) || match.value.startsWith("google.navigation:", true)
			// Some senders leave address spaces unescaped in a native URI on its own line.
			val prefix = text.subSequence(0, match.range.first).toString().substringAfterLast('\n')
			return if (nativeUri && prefix.isBlank()) {
				text.subSequence(match.range.first, text.length).toString().lineSequence().first().trim()
			} else match.value
		}

		fun latlongToAddress(latlong: String, label: String = ""): Address {
			val splits = latlong.split(',')
			return latlongToAddress(LatLong(splits[0].toDouble(), splits[1].toDouble()), label)
		}

		fun latlongToAddress(latlong: LatLong, label: String = ""): Address {
			return Address(Locale.ROOT).apply {
				latitude = latlong.latitude
				longitude = latlong.longitude
				featureName = label
			}
		}

		fun parseUri(url: String): URI {
			// Preserve URI separators and existing escapes when a sender leaves spaces unescaped.
			return URI(url.replace(Regex("[\\s<>\"{}|\\\\^`]")) {
				URLEncoder.encode(it.value, "UTF-8").replace("+", "%20")
			})
		}

		private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

		private fun queryParameters(rawQuery: String?): Map<String, String> = rawQuery.orEmpty()
			.split('&').mapNotNull {
				val parts = it.split('=', limit = 2)
				if (parts.size == 2) decode(parts[0]) to decode(parts[1]) else null
			}.toMap()
	}

	fun parseUrl(url: String?): Address? {
		url ?: return null
		return try {
			val uri = parseUri(url.trim())
			when (uri.scheme?.lowercase(Locale.ROOT)) {
				"geo" -> parseGeoUrl(uri)
				"google.navigation" -> parseLocation(queryParameters(uri.rawSchemeSpecificPart)["q"])
				"http", "https" -> parsePlusUrl(uri.toString()) ?: parseGoogleUrl(uri)
				else -> null
			}
		} catch (e: IllegalArgumentException) {
			// Malformed coordinates or percent escapes are invalid destinations, not crashes.
			null
		} catch (e: URISyntaxException) {
			null
		} catch (e: IOException) {
			// Failed short-link resolution should show the existing navigation error state.
			null
		}
	}

	private fun parseGeoUrl(uri: URI): Address? {
		val parts = uri.rawSchemeSpecificPart.split('?', limit = 2)
		val query = queryParameters(parts.getOrNull(1))["q"]
		if (!query.isNullOrBlank()) {
			parseLocation(query)?.let { return it }
		}
		val coordinates = decode(parts[0]).substringBefore(';').trim()
		val position = parseCoordinates(coordinates) ?: return null
		// 0,0 is the placeholder used for an unresolved geo search.
		return position.takeUnless { query != null && it.latitude == 0.0 && it.longitude == 0.0 }
	}

	private fun parseCoordinates(value: String, label: String = ""): Address? {
		val match = COORDINATES.matchEntire(value.trim()) ?: return null
		val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
		val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
		if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
		return latlongToAddress(LatLong(latitude, longitude), match.groupValues[3].ifBlank { label })
	}

	private fun parseLocation(value: String?): Address? {
		val location = value?.trim()?.removePrefix("loc:")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
		if (COORDINATES.matches(location)) return parseCoordinates(location)
		return parsePlusCode(location) ?: addressSearcher.search(location)
	}

	private fun parsePlusUrl(url: String): Address? {
		val uri = parseUri(url)
		if (uri.host?.lowercase(Locale.ROOT) !in setOf("plus.codes", "www.plus.codes")) return null
		val matcher = PLUSCODE_URL_MATCHER.matchEntire(uri.path) ?: return null
		return parsePlusCode(matcher.groupValues[1])
	}

	private fun parsePlusCode(plusCode: String): Address? {
		val parsed = PLUSCODE_SPLITTER.matchEntire(plusCode) ?: return null
		val code = parsed.groupValues[1]
		val reference = parsed.groupValues[3]
		var olc = try {
			OpenLocationCode(code)
		} catch (e: IllegalArgumentException) {
			return null
		}
		if (olc.isShort && reference.isNotBlank()) {
			val referenceName = reference.replace('+', ' ').trim()
			val result = addressSearcher.search(referenceName) ?: return null
			olc = olc.recover(result.latitude, result.longitude)
		} else if (olc.isShort) {
			// can't resolve short code
			return null
		}

		val area = olc.decode()
		return latlongToAddress(LatLong(area.centerLatitude, area.centerLongitude))
	}

	private fun parseGoogleUrl(original: URI): Address? {
		var uri = original
		val visited = mutableSetOf<String>()
		// Resolve only Maps shorteners, including relative Location headers and redirect chains.
		repeat(5) {
			if (uri.host?.lowercase(Locale.ROOT) in SHORT_LINK_HOSTS) {
				if (!visited.add(uri.toString())) return null
				val location = redirector.tryRedirect(uri.toString()) ?: return null
				uri = uri.resolve(parseUri(location))
			}
		}
		if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
		val host = uri.host
		if (host != null && !GOOGLE_HOST.matches(host)) return null

		val query = queryParameters(uri.rawQuery)
		// Directions endpoints outrank search text and map camera coordinates, regardless of order.
		val destination = query["destination"] ?: query["daddr"]
		if (destination != null) return parseLocation(destination)

		val segments = uri.rawPath.orEmpty().split('/')
		if (segments.getOrNull(1) == "maps" && segments.getOrNull(2) == "dir") {
			val stops = segments.drop(3).takeWhile { !it.startsWith("@") && !it.startsWith("data=") }
				.dropLastWhile { it.isBlank() }
			// The first segment is the origin (possibly blank); use the final stop, not a waypoint.
			if (stops.size >= 2) {
				val finalStop = stops.last()
				parsePlusCode(decode(finalStop.replace("+", "%2B")))?.let { return it }
				return parseLocation(decode(finalStop))
			}
		}

		val search = query["query"] ?: query["q"]
		if (search != null) {
			parseCoordinates(search.removePrefix("loc:").trim())?.let { return it }
			// Preserve legacy Maps links that supply the searched place's coordinates in ll.
			query["ll"]?.let { parseCoordinates(it, search)?.let { result -> return result } }
			return parseLocation(search)
		}

		val path = uri.path.orEmpty()
		if (segments.getOrNull(1) == "maps" && segments.getOrNull(2) in setOf("place", "search")) {
			val name = segments.drop(3).firstOrNull { it.isNotBlank() }?.let { decode(it) }
			if (segments[2] == "place") {
				// Shared place links contain a pin (!3d/!4d) as well as a different @ camera center.
				PLACE_COORDINATES.findAll(path).lastOrNull()?.let {
					parseCoordinates("${it.groupValues[1]},${it.groupValues[2]}", name.orEmpty())?.let { result -> return result }
				}
				segments.firstOrNull { it.startsWith("@") }?.let { camera ->
					COORDINATE_PREFIX.find(decode(camera.removePrefix("@")))?.groupValues?.get(1)?.let {
						parseCoordinates(it, name.orEmpty())?.let { result -> return result }
					}
				}
			}
			return parseLocation(name)
		}
		return query["ll"]?.let { parseCoordinates(it) }
	}
}

class URLRedirector {
	fun tryRedirect(url: String): String? {
		val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
		return try {
			connection.instanceFollowRedirects = false
			connection.connectTimeout = 5000
			connection.readTimeout = 5000
			if (connection.responseCode in 300..399) connection.getHeaderField("Location") else null
		} finally {
			connection.disconnect()
		}
	}
}
