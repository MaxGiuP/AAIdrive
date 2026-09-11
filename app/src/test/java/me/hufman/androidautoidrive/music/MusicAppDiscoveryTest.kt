package me.hufman.androidautoidrive.music

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.graphics.drawable.Drawable
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.carapp.music.MusicHomeAppInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.io.FileNotFoundException

class MusicAppDiscoveryTest {
	private val applications = mutableMapOf<String, ApplicationInfo>()
	private val browserServices = mutableListOf<ResolveInfo>()
	private val sessions = mutableListOf<MediaController>()
	private val icon = mock<Drawable>()
	private val packageManager = mock<PackageManager> {
		on { getApplicationInfo(any<String>(), any<Int>()) } doAnswer {
			applications[it.getArgument(0)] ?: throw PackageManager.NameNotFoundException()
		}
		on { getApplicationLabel(any()) } doAnswer { it.getArgument<ApplicationInfo>(0).packageName }
		on { getApplicationIcon(any<ApplicationInfo>()) } doReturn icon
		on { queryIntentServices(any(), any<Int>()) } doAnswer { browserServices.toList() }
	}
	private val sessionManager = mock<MediaSessionManager> {
		on { getActiveSessions(any()) } doAnswer { sessions.toList() }
	}
	private val preferencesEditor = mock<SharedPreferences.Editor>()
	private val preferences = mock<SharedPreferences> { on { edit() } doReturn preferencesEditor }
	private val context = mock<Context> {
		on { packageManager } doReturn packageManager
		on { getSystemService(MediaSessionManager::class.java) } doReturn sessionManager
		on { openFileInput(any()) } doThrow FileNotFoundException()
		on { getSharedPreferences(any(), any()) } doReturn preferences
	}
	private val discovery = MusicAppDiscovery(context, mock<Handler>())

	@Before
	fun setup() {
		AppSettings.loadDefaultSettings()
		MusicSessions.hasPermission = false
	}

	private fun install(packageName: String, browser: Boolean = false) {
		val app = mock<ApplicationInfo>().apply { this.packageName = packageName }
		applications[packageName] = app
		if (browser) {
			val service = mock<ServiceInfo>().apply {
				applicationInfo = app
				name = "BrowserService"
			}
			browserServices.add(mock<ResolveInfo>().apply { serviceInfo = service })
		}
	}

	private fun addSession(packageName: String, actions: Long = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE) {
		val playback = mock<PlaybackState> {
			on { this.actions } doReturn actions
			on { state } doReturn PlaybackState.STATE_PAUSED
		}
		sessions.add(mock<MediaController> {
			on { this.packageName } doReturn packageName
			on { playbackState } doReturn playback
		})
	}

	@Test
	fun listsInstalledSessionPlayersWithoutClaimingPlaybackOrBrowseSupport() {
		install(MusicAppCompatibility.AUDIBLE_PACKAGE)
		install(MusicAppCompatibility.RUMBLE_PACKAGE)
		install("app.revanced.android.youtube")
		discovery.loadInstalledMusicApps()
		assertEquals(applications.keys, discovery.allApps.map { it.packageName }.toSet())
		assertTrue(discovery.allApps.all { it.probed && it.possiblyControllable })
		assertTrue(discovery.allApps.none { it.connectable || it.controllable || it.browseable })
		assertTrue(discovery.validApps.isEmpty())

		addSession(MusicAppCompatibility.RUMBLE_PACKAGE)
		discovery.addSessionApps()
		assertEquals(listOf(MusicAppCompatibility.RUMBLE_PACKAGE), discovery.validApps.map { it.packageName })
		sessions.clear()
		discovery.addSessionApps()
		assertTrue(discovery.validApps.isEmpty())
		assertEquals(3, discovery.allApps.size)
	}

	@Test
	fun homeShortcutsSurviveSessionTeardownAndRefreshAfterUninstall() {
		MusicAppCompatibility.SESSION_APP_PACKAGES.forEach { install(it) }
		val preferredYoutube = MusicAppCompatibility.YOUTUBE_VIDEO_PACKAGES[0]
		addSession(preferredYoutube)
		discovery.loadInstalledMusicApps()
		val before = MusicHomeAppInfo.shortcuts(discovery.allApps)
		assertEquals(4, before.size)
		assertEquals(preferredYoutube, before[2].packageName)
		assertTrue(before[2].musicApp.controllable)
		sessions.clear()
		discovery.addSessionApps()
		assertEquals(before.map { it.packageName }, MusicHomeAppInfo.shortcuts(discovery.allApps).map { it.packageName })
		assertTrue(discovery.validApps.isEmpty())

		applications.remove(preferredYoutube)
		applications.remove(MusicAppCompatibility.AUDIBLE_PACKAGE)
		discovery.loadInstalledMusicApps()
		val refreshed = MusicHomeAppInfo.shortcuts(discovery.allApps)
		assertEquals(listOf(MusicAppCompatibility.YOUTUBE_MUSIC_PACKAGES[0],
			MusicAppCompatibility.YOUTUBE_VIDEO_PACKAGES[1], MusicAppCompatibility.RUMBLE_PACKAGE),
			refreshed.map { it.packageName })
		assertTrue(refreshed.none { it.musicApp.connectable || it.musicApp.controllable })
	}

	@Test
	fun discoversPlayersThatOnlyAdvertisePlayPauseToggle() {
		install(MusicAppCompatibility.RUMBLE_PACKAGE)
		addSession(MusicAppCompatibility.RUMBLE_PACKAGE, PlaybackState.ACTION_PLAY_PAUSE)
		discovery.loadInstalledMusicApps()
		assertEquals(listOf(MusicAppCompatibility.RUMBLE_PACKAGE), discovery.validApps.map { it.packageName })
	}

	@Test
	fun mergesSessionIntoBrowserWithoutDuplicateOrLosingService() {
		val packageName = "app.revanced.android.apps.youtube.music"
		install(packageName, browser = true)
		addSession(packageName)
		discovery.loadInstalledMusicApps()
		assertEquals(1, discovery.allApps.size)
		assertEquals("BrowserService", discovery.allApps.single().className)
		assertTrue(discovery.allApps.single().controllable)
		assertFalse(discovery.allApps.single().probed)
	}

	@Test
	fun preservesExplicitHiddenSettingsAndRemovesUninstalledPlayers() {
		val packageName = MusicAppCompatibility.RUMBLE_PACKAGE
		install(packageName)
		addSession(packageName)
		AppSettings.tempSetSetting(AppSettings.KEYS.HIDDEN_MUSIC_APPS, packageName)
		discovery.loadInstalledMusicApps()
		assertTrue(discovery.allApps.single().hidden)
		assertTrue(discovery.validApps.isEmpty())
		applications.clear()
		sessions.clear()
		discovery.loadInstalledMusicApps()
		assertTrue(discovery.allApps.isEmpty())
	}
}
