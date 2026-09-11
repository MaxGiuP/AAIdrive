package me.hufman.androidautoidrive.music

import me.hufman.androidautoidrive.carapp.AMCategory
import me.hufman.androidautoidrive.carapp.music.MusicHomeAppInfo
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class MusicHomeAppInfoTest {
	@Test
	fun prefersRevancedWithItsOwnIconEvenBeforePlaybackStarts() {
		val revanced = MusicAppInfo("YouTube Music", mock(), MusicAppCompatibility.YOUTUBE_MUSIC_PACKAGES[0], null)
		val stock = MusicAppInfo("YouTube Music", mock(), MusicAppCompatibility.YOUTUBE_MUSIC_PACKAGES[2], "Browser")
		val shortcut = MusicHomeAppInfo.shortcuts(listOf(stock, revanced)).single()
		assertSame(revanced, shortcut.musicApp)
		assertSame(revanced.icon, shortcut.icon)
		assertEquals("YouTube Music ReVanced", shortcut.name)
		assertEquals(AMCategory.ONLINE_SERVICES, shortcut.category)
		assertNotEquals(revanced.amAppIdentifier, shortcut.amAppIdentifier)
		assertFalse(revanced.controllable)
		assertFalse(revanced.connectable)
	}

	@Test
	fun hiddenOrRemovedPlayersDoNotLeaveAStaleShortcut() {
		val revanced = MusicAppInfo("YouTube Music", mock(), MusicAppCompatibility.YOUTUBE_MUSIC_PACKAGES[0], null).apply { hidden = true }
		val extended = MusicAppInfo("YouTube Music", mock(), MusicAppCompatibility.YOUTUBE_MUSIC_PACKAGES[1], null)
		val stock = MusicAppInfo("YouTube Music", mock(), MusicAppCompatibility.YOUTUBE_MUSIC_PACKAGES[2], null)
		assertEquals("YouTube Music ReVanced Extended", MusicHomeAppInfo.shortcuts(listOf(stock, revanced, extended)).single().name)
		assertEquals("YouTube Music", MusicHomeAppInfo.shortcuts(listOf(stock, revanced)).single().name)
		assertTrue(MusicHomeAppInfo.shortcuts(listOf(revanced)).isEmpty())
		assertTrue(MusicHomeAppInfo.shortcuts(emptyList()).isEmpty())
	}

	@Test
	fun selectsOneVariantPerFamilyWithStableWeightsAndInstalledIcons() {
		val apps = MusicAppCompatibility.SESSION_APP_PACKAGES.map {
			MusicAppInfo("Installed label", mock(), it, null)
		}
		val shortcuts = MusicHomeAppInfo.shortcuts(apps.reversed() + apps)
		assertEquals(listOf(
			"app.revanced.android.apps.youtube.music",
			MusicAppCompatibility.AUDIBLE_PACKAGE,
			"app.revanced.android.youtube",
			MusicAppCompatibility.RUMBLE_PACKAGE
		), shortcuts.map { it.packageName })
		assertEquals(listOf("YouTube Music ReVanced", "Audible", "YouTube ReVanced", "Rumble"),
			shortcuts.map { it.name })
		assertEquals(listOf(900, 890, 880, 870), shortcuts.map { it.weight })
		assertEquals(4, shortcuts.map { it.amAppIdentifier }.toSet().size)
		shortcuts.forEach { shortcut ->
			val installed = apps.single { it.packageName == shortcut.packageName }
			assertSame(installed, shortcut.musicApp)
			assertSame(installed.icon, shortcut.icon)
			assertEquals(AMCategory.ONLINE_SERVICES, shortcut.category)
			assertFalse(installed.controllable || installed.connectable)
		}
	}

	@Test
	fun youtubeVariantFallbackNamesReflectPackageRatherThanSharedAppLabel() {
		val variants = MusicAppCompatibility.YOUTUBE_VIDEO_PACKAGES.map {
			MusicAppInfo("YouTube", mock(), it, null)
		}
		val expectedNames = listOf("YouTube ReVanced", "YouTube ReVanced Extended", "YouTube", "YouTube Vanced")
		variants.indices.forEach { index ->
			val shortcut = MusicHomeAppInfo.shortcuts(variants.reversed()).single()
			assertSame(variants[index], shortcut.musicApp)
			assertEquals(expectedNames[index], shortcut.name)
			assertEquals(880, shortcut.weight)
			variants[index].hidden = true
		}
		assertTrue(MusicHomeAppInfo.shortcuts(variants).isEmpty())
	}

	@Test
	fun hidingOrUninstallingOneFamilyKeepsTheOtherShortcuts() {
		val music = MusicAppInfo("YouTube Music", mock(), MusicAppCompatibility.YOUTUBE_MUSIC_PACKAGES[0], null)
		val audible = MusicAppInfo("Audible", mock(), MusicAppCompatibility.AUDIBLE_PACKAGE, null)
		val youtube = MusicAppInfo("YouTube", mock(), MusicAppCompatibility.YOUTUBE_VIDEO_PACKAGES[0], null)
		val rumble = MusicAppInfo("Rumble", mock(), MusicAppCompatibility.RUMBLE_PACKAGE, null)
		val installed = listOf(music, audible, youtube, rumble)
		audible.hidden = true
		assertEquals(listOf(music, youtube, rumble), MusicHomeAppInfo.shortcuts(installed).map { it.musicApp })
		assertEquals(listOf(music, rumble), MusicHomeAppInfo.shortcuts(installed - youtube).map { it.musicApp })
		assertEquals(listOf(900, 870), MusicHomeAppInfo.shortcuts(installed - youtube).map { it.weight })
	}

	@Test
	fun stockAndSessionOnlyAppsUseShortFamilyNamesWithTheirInstalledIcons() {
		val apps = listOf(
			"com.google.android.apps.youtube.music", MusicAppCompatibility.AUDIBLE_PACKAGE,
			"com.google.android.youtube", MusicAppCompatibility.RUMBLE_PACKAGE
		).map { MusicAppInfo("Long store label with a marketing suffix", mock(), it, null) }
		val shortcuts = MusicHomeAppInfo.shortcuts(apps)
		assertEquals(listOf("YouTube Music", "Audible", "YouTube", "Rumble"), shortcuts.map { it.name })
		shortcuts.zip(apps).forEach { (shortcut, installed) -> assertSame(installed.icon, shortcut.icon) }
	}

	@Test
	fun doesNotAddSpotifyOrUnrelatedAppsWithLookalikeLabels() {
		val apps = listOf("com.spotify.music", "another.player", "another.youtube").map {
			MusicAppInfo("YouTube Music ReVanced", mock(), it, "Browser").apply { connectable = true }
		}
		assertTrue(MusicHomeAppInfo.shortcuts(apps).isEmpty())
	}
}
