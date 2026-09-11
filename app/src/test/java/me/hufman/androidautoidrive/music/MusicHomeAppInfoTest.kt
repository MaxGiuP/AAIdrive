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
		val shortcut = MusicHomeAppInfo.preferred(listOf(stock, revanced))!!
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
		assertEquals("YouTube Music ReVanced Extended", MusicHomeAppInfo.preferred(listOf(stock, revanced, extended))?.name)
		assertEquals("YouTube Music", MusicHomeAppInfo.preferred(listOf(stock, revanced))?.name)
		assertNull(MusicHomeAppInfo.preferred(listOf(revanced)))
		assertNull(MusicHomeAppInfo.preferred(emptyList()))
	}
}
