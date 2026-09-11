package io.github.maxgiup.aaidrive.setup

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings

internal data class PhoneAppVariant(val packageName: String, val label: Int)
internal data class PhoneAppSource(val label: Int, val webUrl: String, val playPackage: String? = null) {
    fun intents(): List<Intent> = buildList {
        playPackage?.let { add(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$it"))) }
        add(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
    }
}
internal data class PhoneAppGroup(
    val id: String, val label: Int, val description: Int,
    val variants: List<PhoneAppVariant>, val sources: List<PhoneAppSource>
)

/** Fixed packages only: this list never scans the phone's complete app inventory. */
internal object PhoneApps {
    const val AAIDRIVE = "me.hufman.androidautoidrive"
    const val HEADUNIT = "com.andrerinas.headunitrevived"
    const val PROJECTION = "io.github.maxgiup.aaidrive.projection"
    const val ANDROID_AUTO = "com.google.android.projection.gearhead"

    private fun play(label: Int, packageName: String) = PhoneAppSource(label,
        "https://play.google.com/store/apps/details?id=$packageName", packageName)
    val revancedGuide = PhoneAppSource(R.string.get_revanced_guide,
        "https://github.com/ReVanced/revanced-manager/tree/main/docs")

    val groups = listOf(
        PhoneAppGroup("youtube_music", R.string.youtube_music, R.string.youtube_music_help, listOf(
            PhoneAppVariant("app.revanced.android.apps.youtube.music", R.string.youtube_music_revanced),
            PhoneAppVariant("app.rvx.android.apps.youtube.music", R.string.youtube_music_rvx),
            PhoneAppVariant("com.google.android.apps.youtube.music", R.string.youtube_music)
        ), listOf(revancedGuide, play(R.string.get_youtube_music, "com.google.android.apps.youtube.music"))),
        PhoneAppGroup("audible", R.string.audible, R.string.audible_help, listOf(
            PhoneAppVariant("com.audible.application", R.string.audible)
        ), listOf(play(R.string.get_audible, "com.audible.application"))),
        PhoneAppGroup("youtube", R.string.youtube, R.string.youtube_help, listOf(
            PhoneAppVariant("app.revanced.android.youtube", R.string.youtube_revanced),
            PhoneAppVariant("app.rvx.android.youtube", R.string.youtube_rvx),
            PhoneAppVariant("com.google.android.youtube", R.string.youtube),
            PhoneAppVariant("com.vanced.android.youtube", R.string.youtube_vanced)
        ), listOf(revancedGuide, play(R.string.get_youtube, "com.google.android.youtube"))),
        PhoneAppGroup("rumble", R.string.rumble, R.string.rumble_help, listOf(
            PhoneAppVariant("com.rumble.battles", R.string.rumble)
        ), listOf(play(R.string.get_rumble, "com.rumble.battles"))),
        PhoneAppGroup("maps", R.string.google_maps, R.string.maps_native_help, listOf(
            PhoneAppVariant("com.google.android.apps.maps", R.string.google_maps)
        ), listOf(play(R.string.get_google_maps, "com.google.android.apps.maps")))
    )
    val androidAuto = PhoneAppVariant(ANDROID_AUTO, R.string.android_auto)
    val androidAutoSource = play(R.string.get_android_auto, ANDROID_AUTO)
}

internal enum class PhoneAppState { MISSING, ENABLED, DISABLED, UNAVAILABLE }
internal data class PhoneAppStatus(val variant: PhoneAppVariant, val state: PhoneAppState) {
    val installed: Boolean get() = state == PhoneAppState.ENABLED || state == PhoneAppState.DISABLED
}

internal class PhoneAppResolver(private val packages: PackageManager) {
    fun inspect(variant: PhoneAppVariant): PhoneAppStatus {
        val state = try {
            @Suppress("DEPRECATION")
            val info = packages.getApplicationInfo(variant.packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            if (info.enabled) PhoneAppState.ENABLED else PhoneAppState.DISABLED
        } catch (_: PackageManager.NameNotFoundException) {
            PhoneAppState.MISSING
        } catch (_: RuntimeException) {
            PhoneAppState.UNAVAILABLE
        }
        return PhoneAppStatus(variant, state)
    }

    /** Recheck at click time because the app may have been disabled or removed meanwhile. */
    fun openIntents(variant: PhoneAppVariant): List<Intent> {
        val settings = appSettings(variant.packageName)
        if (inspect(variant).state != PhoneAppState.ENABLED) return listOf(settings)
        val launch = runCatching { packages.getLaunchIntentForPackage(variant.packageName) }.getOrNull()
        return if (launch == null) listOf(settings) else listOf(launch, settings)
    }

    fun icon(status: PhoneAppStatus): Drawable? = if (status.installed) {
        runCatching { packages.getApplicationIcon(status.variant.packageName) }.getOrNull()
    } else null

    companion object {
        fun appSettings(packageName: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"))
    }
}
