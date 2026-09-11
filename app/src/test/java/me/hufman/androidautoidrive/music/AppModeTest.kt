package me.hufman.androidautoidrive.music

import org.mockito.kotlin.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MockAppSettings
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import org.junit.Assert.*
import org.junit.Test

class AppModeTest {
	val usbConnection = mock<IDriveConnectionStatus> {
		on {host} doReturn "127.0.0.1"
		on {port} doReturn MusicAppMode.TRANSPORT_PORTS.USB.toPort()
	}
	val btConnection = mock<IDriveConnectionStatus> {
		on {host} doReturn "127.0.0.1"
		on {port} doReturn MusicAppMode.TRANSPORT_PORTS.BT.toPort()
	}
	val id4Capabilities = mapOf("hmi.type" to "ID4")
	val id6Capabilities = mapOf("hmi.type" to "ID6")

	@Test
	fun testMusicAppManual() {
		// Allow the user to force enable the context
		val settings = MockAppSettings(AppSettings.KEYS.AUDIO_FORCE_CONTEXT to "false", AppSettings.KEYS.AUDIO_SUPPORTS_USB to "false")
		val mode = MusicAppMode(usbConnection, emptyMap(), settings, true, null, null, null)
		assertFalse(mode.shouldRequestAudioContext())

		settings[AppSettings.KEYS.AUDIO_FORCE_CONTEXT] = "true"
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testUSBSupport() {
		// Test that the USB connection is handled properly
		val settings = MockAppSettings(AppSettings.KEYS.AUDIO_FORCE_CONTEXT to "false", AppSettings.KEYS.AUDIO_SUPPORTS_USB to "false")
		val mode = MusicAppMode(usbConnection, emptyMap(), settings, true, null, null, null)
		assertFalse(mode.shouldRequestAudioContext())

		// the phone is old enough to support it over USB
		settings[AppSettings.KEYS.AUDIO_SUPPORTS_USB] = "true"
		assertTrue(mode.shouldRequestAudioContext())

		// should work over BT too, even if the phone is old
		val btMode = MusicAppMode(btConnection, emptyMap(), settings, true, null, null, null)
		assertTrue(btMode.shouldRequestAudioContext())
	}

	@Test
	fun testBTSupport() {
		// Verify that the BT connection is handled properly
		val settings = MockAppSettings(AppSettings.KEYS.AUDIO_FORCE_CONTEXT to "false", AppSettings.KEYS.AUDIO_SUPPORTS_USB to "false")
		val mode = MusicAppMode(btConnection, emptyMap(), settings, true, null, null, null)
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testIPSupport() {
		// Verify that the BT connection is handled properly
		whenever(btConnection.host) doReturn "192.168.1.10"
		val settings = MockAppSettings(AppSettings.KEYS.AUDIO_FORCE_CONTEXT to "false", AppSettings.KEYS.AUDIO_SUPPORTS_USB to "false")
		val mode = MusicAppMode(btConnection, emptyMap(), settings, true, null, null, null)
		assertFalse(mode.shouldRequestAudioContext())
	}

	@Test
	fun standardLayoutIsDefaultRegardlessOfSpotifyOrConnectedInstallation() {
		val settings = MockAppSettings(AppSettings.KEYS.AUDIO_SUPPORTS_USB to "true")
		for (connection in listOf(btConnection, usbConnection)) {
			for (capabilities in listOf(id4Capabilities, id6Capabilities, emptyMap())) {
				for (connectedInstalled in listOf(false, true)) {
					for (spotify in listOf(null, "8.4.98.892", "8.5.68.904", "8.6.20", "9.1.0", "8.6.536-dogfood-xmax")) {
						val mode = MusicAppMode(connection, capabilities, settings, connectedInstalled, null, null, spotify)
						assertFalse("Spotify $spotify must not select branded resources", mode.supportsId5Playback())
						assertFalse(mode.shouldId5Playback())
						assertTrue("Standard layout retains audio context", mode.shouldRequestAudioContext())
					}
				}
			}
		}
	}

	@Test
	fun spotifyLayoutRequiresExplicitOptInAndCanBeTurnedOffAgain() {
		val settings = MockAppSettings(AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT to "true")
		val mode = MusicAppMode(btConnection, id6Capabilities, settings, true, null, null, "9.1.0")
		assertTrue(mode.supportsId5Playback())
		assertTrue(mode.shouldId5Playback())
		settings[AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT] = "false"
		assertFalse(mode.supportsId5Playback())
		assertFalse(mode.shouldId5Playback())
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun explicitSpotifyLayoutKeepsWorkingWithoutSpotifyInstalled() {
		val settings = MockAppSettings(AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT to "true")
		val mode = MusicAppMode(btConnection, id6Capabilities, settings, false, null, null, null)
		assertTrue(mode.supportsId5Playback())
		assertTrue(mode.shouldId5Playback())
	}

	@Test
	fun spotifyLayoutCannotBeForcedOnId4() {
		val settings = MockAppSettings(AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT to "true")
		val mode = MusicAppMode(btConnection, id4Capabilities, settings, true, null, null, "9.1.0")
		assertFalse(mode.supportsId5Playback())
		assertFalse(mode.shouldId5Playback())
	}

	@Test
	fun legacyPlayerPreferenceDoesNotRemoveExplicitSpotifyResourceSelection() {
		val settings = MockAppSettings(AppSettings.KEYS.FORCE_SPOTIFY_LAYOUT to "true",
			AppSettings.KEYS.FORCE_AUDIOPLAYER_LAYOUT to "true")
		val mode = MusicAppMode(btConnection, id6Capabilities, settings, true, null, null, null)
		assertTrue(mode.supportsId5Playback())
		assertFalse(mode.shouldId5Playback())
	}

	@Test
	fun testId5Radio() {
		val settings = MockAppSettings()

		// no radio app
		run {
			val noRadioMode = MusicAppMode(btConnection, id6Capabilities, settings, true, null, null, null)
			assertEquals(null, noRadioMode.getRadioAppName())
		}
		// iHeartRadio
		run {
			val ihrRadioMode = MusicAppMode(btConnection, id6Capabilities, settings, true, "yes", null, null)
			assertEquals("iHeartRadio", ihrRadioMode.getRadioAppName())
		}
		// Pandora
		run {
			val pandoraRadioMode = MusicAppMode(btConnection, id6Capabilities, settings, true, null, "yes", null)
			assertEquals("Pandora", pandoraRadioMode.getRadioAppName())
		}
		// Both
		run {
			val bothRadioMode = MusicAppMode(btConnection, id6Capabilities, settings, true, "yes", "yes", null)
			assertEquals(null, bothRadioMode.getRadioAppName())
		}

		// disabled in usb mode
		run {
			val ihrRadioMode = MusicAppMode(usbConnection, id6Capabilities, settings, true, "yes", null, null)
			assertEquals(null, ihrRadioMode.getRadioAppName())
		}
	}

}