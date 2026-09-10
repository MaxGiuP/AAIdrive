package me.hufman.androidautoidrive.music

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.carapp.AMCategory
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class MusicAppCompatibilityTest {
	private fun app(packageName: String, className: String? = "BrowserService") =
		MusicAppInfo("Player", mock(), packageName, className)

	@Test
	fun prefersRevancedOverStockAndExtendedEvenBeforeProbing() {
		val stock = app("com.google.android.apps.youtube.music")
		val extended = app("app.rvx.android.apps.youtube.music")
		val revanced = app("app.revanced.android.apps.youtube.music")
		assertSame(revanced, MusicAppCompatibility.preferredMusicApp(listOf(stock, extended, revanced)))
		assertSame(extended, MusicAppCompatibility.preferredMusicApp(listOf(stock, extended)))
		assertSame(stock, MusicAppCompatibility.preferredMusicApp(listOf(stock)))
	}

	@Test
	fun excludesHiddenAndUnavailableApps() {
		val revanced = app("app.revanced.android.apps.youtube.music").apply { hidden = true }
		val extended = app("app.rvx.android.apps.youtube.music").apply { probed = true }
		val stock = app("com.google.android.apps.youtube.music", null)
		assertNull(MusicAppCompatibility.preferredMusicApp(listOf(revanced, extended, stock)))
		stock.controllable = true
		assertSame(stock, MusicAppCompatibility.preferredMusicApp(listOf(revanced, extended, stock)))
	}

	@Test
	fun reconnectableAppsRemainPreferredAfterProbing() {
		val revanced = app("app.revanced.android.apps.youtube.music").apply {
			probed = true
			connectable = true
		}
		assertSame(revanced, MusicAppCompatibility.preferredMusicApp(listOf(revanced)))
		assertNull(MusicAppCompatibility.preferredMusicApp(listOf(app("com.spotify.music"))))
	}

	@Test
	fun classifiesKnownPlayersAsMultimediaRegardlessOfMarketingLabel() {
		MusicAppCompatibility.SESSION_APP_PACKAGES.forEach {
			assertEquals(it, AMCategory.MULTIMEDIA, MusicAppInfo.guessCategory(it, "Podcasts, live shows & news"))
		}
	}

	@Test
	fun youtubeVariantsAreVisibleByDefault() {
		val hidden = AppSettings.KEYS.HIDDEN_MUSIC_APPS.default.split(",")
		assertTrue(MusicAppCompatibility.SESSION_APP_PACKAGES.none { it in hidden })
	}

	@Test
	fun browserAndSessionRepresentationsHaveTheSameHash() {
		val browser = app("app.revanced.android.apps.youtube.music")
		val session = app(browser.packageName, null)
		assertEquals(browser, session)
		assertEquals(browser.hashCode(), session.hashCode())
		assertEquals(1, hashSetOf(browser, session).size)
	}
}
