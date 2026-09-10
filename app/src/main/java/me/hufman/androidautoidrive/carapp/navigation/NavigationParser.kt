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
		private val MAP_DATA_FIELD = Regex("^([1-9][0-9]*)([a-z])(.*)$", RegexOption.DOT_MATCHES_ALL)
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

	private fun parsePlusCode(plusCode: String, allowReferenceLookup: Boolean = true): Address? {
		val parsed = PLUSCODE_SPLITTER.matchEntire(plusCode) ?: return null
		val code = parsed.groupValues[1]
		val reference = parsed.groupValues[3]
		var olc = try {
			OpenLocationCode(code)
		} catch (e: IllegalArgumentException) {
			return null
		}
		if (olc.isShort && reference.isNotBlank()) {
			if (!allowReferenceLookup) return null
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

	private data class MapDataField(val number: Int, val type: Char, val value: String, val children: List<MapDataField> = emptyList())

	/**
	 * Google share links use !NmK messages whose following K tokens belong to that message.
	 * This is an undocumented format: reject unfamiliar or malformed structure and geocode instead.
	 */
	private fun parseMapData(data: String): List<MapDataField>? {
		if (!data.startsWith('!') || data.length > 65536) return null
		val tokens = data.substring(1).split('!')
		if (tokens.size > 4096) return null
		fun parseFields(start: Int, end: Int, depth: Int): List<MapDataField>? {
			if (depth > 16) return null
			val fields = ArrayList<MapDataField>()
			var index = start
			while (index < end) {
				val match = MAP_DATA_FIELD.matchEntire(tokens[index]) ?: return null
				val number = match.groupValues[1].toIntOrNull() ?: return null
				val type = match.groupValues[2][0]
				val value = match.groupValues[3]
				index++
				if (type == 'm') {
					val count = value.toIntOrNull()?.takeIf { it >= 0 && it <= end - index } ?: return null
					val children = parseFields(index, index + count, depth + 1) ?: return null
					fields.add(MapDataField(number, type, value, children))
					index += count
				} else {
					fields.add(MapDataField(number, type, value))
				}
			}
			return fields
		}
		return parseFields(0, tokens.size, 0)
	}

	private fun parseDirectionsDestination(data: String?, stopCount: Int, label: String): Address? {
		val fields = data?.let { parseMapData(it) } ?: return null
		val routes = ArrayList<List<MapDataField>>()
		fun findRoutes(fields: List<MapDataField>) {
			// Route containers are nested !4m messages. Their immediate !1m children are ordered stops.
			fields.filter { it.number == 4 && it.type == 'm' }.forEach { route ->
				val stops = route.children.filter { it.number == 1 && it.type == 'm' }
				if (stops.size == stopCount) routes.add(stops)
				findRoutes(route.children)
			}
		}
		findRoutes(fields)
		val destination = routes.singleOrNull()?.lastOrNull() ?: return null
		// Only a coordinate block directly attached to the final stop qualifies. Nested route
		// shaping points, other stops, and the @ camera center are not destination coordinates.
		val coordinates = destination.children.filter { it.type == 'm' && (it.number == 2 || it.number == 8) }.singleOrNull() ?: return null
		if (coordinates.children.size != 2) return null
		val latitudeField = if (coordinates.number == 2) 2 else 3
		val longitudeField = if (coordinates.number == 2) 1 else 4
		val latitude = coordinates.children.singleOrNull { it.number == latitudeField && it.type == 'd' }?.value ?: return null
		val longitude = coordinates.children.singleOrNull { it.number == longitudeField && it.type == 'd' }?.value ?: return null
		return parseCoordinates("$latitude,$longitude", label)
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
				val name = decode(finalStop)
				val literalCoordinates = name.removePrefix("loc:").trim()
				if (COORDINATES.matches(literalCoordinates)) return parseCoordinates(literalCoordinates)
				val plusCode = decode(finalStop.replace("+", "%2B"))
				parsePlusCode(plusCode, allowReferenceLookup = false)?.let { return it }
				val dataSegments = segments.filter { it.startsWith("data=") }
				val data = if (dataSegments.isEmpty()) query["data"] else dataSegments.singleOrNull()?.let {
					decode(it.removePrefix("data=").replace("+", "%2B"))
				}
				parseDirectionsDestination(data, stops.size, name)?.let { return it }
				parsePlusCode(plusCode)?.let { return it }
				return parseLocation(name)
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
