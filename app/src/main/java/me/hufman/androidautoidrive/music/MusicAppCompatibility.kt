package me.hufman.androidautoidrive.music

/** Known package names; actual browsing and playback capabilities still come from each app. */
object MusicAppCompatibility {
	// Non-root ReVanced first, then Extended, then stock/root-patched YouTube Music.
	val YOUTUBE_MUSIC_PACKAGES = listOf(
		"app.revanced.android.apps.youtube.music",
		"app.rvx.android.apps.youtube.music",
		"com.google.android.apps.youtube.music"
	)
	val YOUTUBE_VIDEO_PACKAGES = setOf(
		"app.revanced.android.youtube",
		"app.rvx.android.youtube",
		"com.google.android.youtube",
		"com.vanced.android.youtube"
	)
	const val AUDIBLE_PACKAGE = "com.audible.application"
	const val RUMBLE_PACKAGE = "com.rumble.battles"
	val SESSION_APP_PACKAGES = YOUTUBE_MUSIC_PACKAGES.toSet() + YOUTUBE_VIDEO_PACKAGES +
		setOf(AUDIBLE_PACKAGE, RUMBLE_PACKAGE)

	/** Only used before the user has selected an app, without interrupting existing playback. */
	fun preferredMusicApp(apps: List<MusicAppInfo>): MusicAppInfo? {
		return YOUTUBE_MUSIC_PACKAGES.firstNotNullOfOrNull { packageName ->
			apps.firstOrNull {
				it.packageName == packageName && !it.hidden &&
					(it.connectable || it.controllable || (!it.probed && it.className != null))
			}
		}
	}
}
