package me.hufman.androidautoidrive.carapp.music

import me.hufman.androidautoidrive.carapp.AMAppInfo
import me.hufman.androidautoidrive.carapp.AMCategory
import me.hufman.androidautoidrive.music.MusicAppCompatibility
import me.hufman.androidautoidrive.music.MusicAppInfo

/** A ConnectedDrive shortcut, separate from the same player's Media entry. */
class MusicHomeAppInfo(val musicApp: MusicAppInfo) : AMAppInfo {
	override val packageName = musicApp.packageName
	override val name = when (packageName) {
		"app.revanced.android.apps.youtube.music" -> "YouTube Music ReVanced"
		"app.rvx.android.apps.youtube.music" -> "YouTube Music ReVanced Extended"
		"com.google.android.apps.youtube.music" -> "YouTube Music"
		"app.revanced.android.youtube" -> "YouTube ReVanced"
		"app.rvx.android.youtube" -> "YouTube ReVanced Extended"
		"com.google.android.youtube" -> "YouTube"
		"com.vanced.android.youtube" -> "YouTube Vanced"
		MusicAppCompatibility.AUDIBLE_PACKAGE -> "Audible"
		MusicAppCompatibility.RUMBLE_PACKAGE -> "Rumble"
		else -> musicApp.name
	}
	override val icon = musicApp.icon
	override val category = AMCategory.ONLINE_SERVICES
	override val amAppIdentifier = "androidautoidrive.home.$packageName"
	override val weight = groups.firstOrNull { packageName in it.packages }?.weight ?: 800

	companion object {
		private data class ShortcutGroup(val packages: List<String>, val weight: Int)
		private val groups = listOf(
			ShortcutGroup(MusicAppCompatibility.YOUTUBE_MUSIC_PACKAGES, 900),
			ShortcutGroup(listOf(MusicAppCompatibility.AUDIBLE_PACKAGE), 890),
			ShortcutGroup(MusicAppCompatibility.YOUTUBE_VIDEO_PACKAGES, 880),
			ShortcutGroup(listOf(MusicAppCompatibility.RUMBLE_PACKAGE), 870)
		)

		/**
		 * One installed, unhidden app per family, with stable ordering independent of labels.
		 * Discovery includes known session-only apps before playback starts, so temporarily
		 * missing media controls do not remove a shortcut. Selecting one still uses the normal
		 * player connection path; it does not claim that the app exposes browsing or video.
		 */
		fun shortcuts(apps: List<MusicAppInfo>): List<MusicHomeAppInfo> =
			groups.mapNotNull { group ->
				group.packages.firstNotNullOfOrNull { packageName ->
					apps.firstOrNull { it.packageName == packageName && !it.hidden }?.let(::MusicHomeAppInfo)
				}
			}
	}
}
