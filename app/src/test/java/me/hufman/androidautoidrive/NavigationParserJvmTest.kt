package me.hufman.androidautoidrive

import android.location.Address
import me.hufman.androidautoidrive.carapp.navigation.AddressSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.URLRedirector
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.*
import java.io.IOException

class NavigationParserJvmTest {
	private val searcher = mock<AddressSearcher>()
	private val redirector = mock<URLRedirector>()
	private val parser = NavigationParser(searcher, redirector)
	private lateinit var addresses: MockedConstruction<Address>

	@Before
	fun androidAddressValueObjects() {
		// Supply Android's Address value storage while running the actual parser on the JVM.
		addresses = mockConstruction(Address::class.java) { address, _ ->
			var latitude = 0.0
			var longitude = 0.0
			var label: String? = null
			doAnswer { latitude = it.getArgument(0); null }.whenever(address).latitude = any()
			doAnswer { longitude = it.getArgument(0); null }.whenever(address).longitude = any()
			doAnswer { label = it.getArgument(0); null }.whenever(address).featureName = any()
			whenever(address.latitude).thenAnswer { latitude }
			whenever(address.longitude).thenAnswer { longitude }
			whenever(address.featureName).thenAnswer { label }
		}
	}

	@After
	fun closeAddresses() = addresses.close()

	private fun assertLocation(url: String, latitude: Double, longitude: Double, label: String = "") {
		val result = parser.parseUrl(url)
		assertNotNull(url, result)
		assertEquals(url, latitude, result!!.latitude, 0.0000001)
		assertEquals(url, longitude, result.longitude, 0.0000001)
		assertEquals(url, label, result.featureName)
	}

	@Test
	fun supportedCoordinateFormats() {
		listOf(
			"geo:47.5951518,+-122.3316393+;u=35",
			"geo:0,0?q=47.5951518,-122.3316393&z=15",
			"geo:0,0?z=15&q=47.5951518,-122.3316393",
			"google.navigation:q=47.5951518,-122.3316393&mode=d",
			"https://www.google.com/maps/search/?api=1&query=47.5951518%2C-122.3316393",
			"https://www.google.com/maps/dir/?api=1&destination=47.5951518,+-122.3316393",
			"https://www.google.com/maps/dir/Current+Location/47.5951518,-122.3316393",
			"https://www.google.com/maps/search/47.5951518,-122.3316393",
			"http://maps.google.co.uk/maps?q=loc:+47.5951518,-122.3316393&z=15",
			"https:///maps?q=47.5951518,-122.3316393"
		).forEach { assertLocation(it, 47.5951518, -122.3316393) }
		assertLocation("geo:0,0?q=47.5951518,-122.3316393 (Main Street)&z=15", 47.5951518, -122.3316393, "Main Street")
		assertLocation("geo:0,0?q=47.5951518,-122.3316393%28Main+Street%29", 47.5951518, -122.3316393, "Main Street")
		assertLocation("geo:0,0?q=47.5951518,-122.3316393%28Museum%0AMain+Street%29", 47.5951518, -122.3316393, "Museum\nMain Street")
		verifyNoInteractions(searcher)
	}

	@Test
	fun directionsPreferDestinationRegardlessOfParameterOrder() {
		listOf(
			"https://www.google.com/maps/dir/?api=1&destination=51.5,-0.12&query=1,2&ll=3,4&origin=5,6",
			"https://www.google.com/maps/dir/?api=1&origin=5,6&query=1,2&ll=3,4&destination=51.5,-0.12",
			"https://maps.google.com/maps?daddr=51.5,-0.12&saddr=5,6&q=1,2&ll=3,4"
		).forEach { assertLocation(it, 51.5, -0.12) }
		verifyNoInteractions(searcher)
	}

	@Test
	fun finalRouteStopWinsOverOriginWaypointAndCamera() {
		assertLocation("https://www.google.com/maps/dir/1,2/3,4/51.5,-0.12/@8,9,10z/data=!4m2", 51.5, -0.12)
		assertLocation("https://www.google.com/maps/dir//51.5,-0.12/", 51.5, -0.12)
		assertNull(parser.parseUrl("https://www.google.com/maps/dir/51.5,-0.12/"))
	}

	@Test
	fun directionsDoNotFallBackToAnUnrelatedMapCenter() {
		assertNull(parser.parseUrl("https://maps.google.com/maps?destination=Missing+Place&ll=1,2"))
		verify(searcher).search("Missing Place")
	}

	@Test
	fun decodedSeparatorsAndLiteralPlusStayInsideTheAddress() {
		val found = mock<Address>()
		whenever(searcher.search("A&B + C, London")).thenReturn(found)
		listOf(
			"geo:0,0?z=14&q=A%26B+%2B+C%2C+London",
			"google.navigation:q=A%26B+%2B+C%2C+London&mode=d",
			"https://www.google.com/maps/search/?api=1&query=A%26B+%2B+C%2C+London",
			"https://www.google.com/maps/dir/?api=1&destination=A%26B+%2B+C%2C+London&ll=3,4",
			"https://www.google.com/maps/dir//A%26B+%2B+C%2C+London"
		).forEach { assertSame(it, found, parser.parseUrl(it)) }
	}

	@Test
	fun plusCodesRemainSupported() {
		listOf(
			"http://plus.codes/849VQJQ5+XX",
			"https://www.google.com/maps/dir//849VQJQ5+XX",
			"https://www.google.com/maps/search/?api=1&query=849VQJQ5%2BXX",
			"geo:0,0?q=849VQJQ5%2BXX"
		).forEach { assertLocation(it, 37.7899375, -122.3900625) }
		assertNull(parser.parseUrl("https://plus.codes/849QJQ5+XX"))
		assertNull(parser.parseUrl("https://plus.codes/QJQ5+XX"))
	}

	@Test
	fun placePinWinsOverMapCameraAndLegacyLinksKeepTheirLabels() {
		assertLocation("https://www.google.com/maps/place/A+Museum/@1,2,15z/data=!4m2!3d51.5!4d-0.12!16s123", 51.5, -0.12, "A Museum")
		assertLocation("https://maps.google.com/maps/place/A+Museum/@51.5,-0.12,15z", 51.5, -0.12, "A Museum")
		assertLocation("https://maps.google.com/maps?q=A+Museum&ll=51.5,-0.12", 51.5, -0.12, "A Museum")
	}

	@Test
	fun rejectsMalformedAndOutOfRangeCoordinates() {
		listOf("geo:95,0", "geo:0,190", "geo:0,0?q=95,0", "geo:0,0?q=1..2,3", "geo:0,0?q=%ZZ", "google.navigation:q=NaN,0", "smtp:example").forEach {
			assertNull(it, parser.parseUrl(it))
		}
	}

	@Test
	fun shortLinksFollowMultipleAndRelativeRedirects() {
		whenever(redirector.tryRedirect("https://maps.app.goo.gl/first")).thenReturn("/second")
		whenever(redirector.tryRedirect("https://maps.app.goo.gl/second")).thenReturn("https://goo.gl/maps/third")
		whenever(redirector.tryRedirect("https://goo.gl/maps/third")).thenReturn("https://www.google.com/maps/search/?api=1&query=51.5,-0.12")
		assertLocation("https://maps.app.goo.gl/first", 51.5, -0.12)
		verify(redirector, times(3)).tryRedirect(any())
	}

	@Test
	fun redirectsStopOnLoopsNetworkErrorsAndUnrelatedSites() {
		whenever(redirector.tryRedirect("https://maps.app.goo.gl/loop")).thenReturn("/loop")
		assertNull(parser.parseUrl("https://maps.app.goo.gl/loop"))
		verify(redirector, times(1)).tryRedirect("https://maps.app.goo.gl/loop")
		whenever(redirector.tryRedirect("https://maps.app.goo.gl/error")).thenAnswer { throw IOException("offline") }
		assertNull(parser.parseUrl("https://maps.app.goo.gl/error"))
		assertNull(parser.parseUrl("https://not-google.example/maps?q=1,2"))
		assertNull(parser.parseUrl("https://google.evil.example/maps?q=1,2"))
	}

	@Test
	fun extractsStandardAndroidUrisAndMultilineShares() {
		assertEquals("geo:0,0?q=51.5,-0.12", NavigationParser.extractUrl("A place\ngeo:0,0?q=51.5,-0.12\nSent from my phone"))
		assertEquals("google.navigation:q=London", NavigationParser.extractUrl("Route: google.navigation:q=London"))
		assertEquals("https://maps.app.goo.gl/abc?g_st=ac", NavigationParser.extractUrl("A place\nhttps://maps.app.goo.gl/abc?g_st=ac\nA note"))
		assertEquals("geo:0,0?q=1600 Amphitheatre Parkway", NavigationParser.extractUrl("geo:0,0?q=1600 Amphitheatre Parkway\nShared location"))
		assertEquals("google.navigation:q=Main Street", NavigationParser.extractUrl("A route\ngoogle.navigation:q=Main Street\nShared location"))
		assertNull(NavigationParser.extractUrl("No link"))
	}
}
