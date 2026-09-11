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
		else -> musicApp.name
	}
	override val icon = musicApp.icon
	override val category = AMCategory.ONLINE_SERVICES
	override val amAppIdentifier = "androidautoidrive.home.$packageName"
	override val weight = 900

	companion object {
		/** Keep the installed shortcut visible while its browser/session is starting. */
		fun preferred(apps: List<MusicAppInfo>): MusicHomeAppInfo? =
			MusicAppCompatibility.YOUTUBE_MUSIC_PACKAGES.firstNotNullOfOrNull { packageName ->
				apps.firstOrNull { it.packageName == packageName && !it.hidden }?.let(::MusicHomeAppInfo)
			}
	}
}
