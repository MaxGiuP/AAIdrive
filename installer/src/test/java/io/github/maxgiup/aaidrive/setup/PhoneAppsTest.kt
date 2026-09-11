package io.github.maxgiup.aaidrive.setup

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal class PhoneAppFixture {
    val packages = mock(PackageManager::class.java)
    val installed = mutableMapOf<String, ApplicationInfo>()
    val launches = mutableMapOf<String, Intent>()
    val resolver = PhoneAppResolver(packages)

    init {
        doAnswer { call ->
            installed[call.getArgument<String>(0)] ?: throw PackageManager.NameNotFoundException()
        }.`when`(packages).getApplicationInfo(anyString(), anyInt())
        doAnswer { call -> launches[call.getArgument<String>(0)] }
            .`when`(packages).getLaunchIntentForPackage(anyString())
    }

    fun install(variant: PhoneAppVariant, enabled: Boolean = true, launcher: Boolean = true) {
        installed[variant.packageName] = ApplicationInfo().apply {
            packageName = variant.packageName
            this.enabled = enabled
        }
        if (launcher) launches[variant.packageName] = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(variant.packageName, "${variant.packageName}.MainActivity"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneAppsTest {
    @Test fun detectsCoexistingVariantsAndDisabledAppWithoutInventingPatches() {
        val fixture = PhoneAppFixture()
        val variants = PhoneApps.groups.first().variants
        fixture.install(variants[0], enabled = false)
        fixture.install(variants[1])
        fixture.install(variants[2])

        assertEquals(listOf(PhoneAppState.DISABLED, PhoneAppState.ENABLED, PhoneAppState.ENABLED),
            variants.map { fixture.resolver.inspect(it).state })
        assertEquals(R.string.youtube_music_rvx, variants[1].label)
        assertEquals(R.string.youtube_music, variants[2].label)
        assertEquals(PhoneAppState.MISSING, fixture.resolver.inspect(PhoneApps.groups[1].variants.single()).state)
    }

    @Test fun unavailablePackageInformationIsNotReportedAsMissingOrEnabled() {
        val fixture = PhoneAppFixture()
        val variant = PhoneApps.groups.first().variants.first()
        doThrow(SecurityException("Package information unavailable")).`when`(fixture.packages)
            .getApplicationInfo(variant.packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertEquals(PhoneAppState.UNAVAILABLE, fixture.resolver.inspect(variant).state)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, fixture.resolver.openIntents(variant).single().action)
    }

    @Test fun disablingOrRemovingAppAfterRenderingCannotLaunchItsStaleActivity() {
        val fixture = PhoneAppFixture()
        val variant = PhoneApps.groups.first().variants.first()
        fixture.install(variant)
        assertEquals(variant.packageName, fixture.resolver.openIntents(variant).first().component?.packageName)
        fixture.installed.getValue(variant.packageName).enabled = false
        val disabled = fixture.resolver.openIntents(variant).single()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, disabled.action)
        assertEquals("package:${variant.packageName}", disabled.dataString)
        fixture.installed.clear()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, fixture.resolver.openIntents(variant).single().action)
    }

    @Test fun installedAudioAppWithoutLauncherFallsBackToItsSettings() {
        val fixture = PhoneAppFixture()
        val audible = PhoneApps.groups[1].variants.single()
        fixture.install(audible, launcher = false)
        val intent = fixture.resolver.openIntents(audible).single()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:com.audible.application", intent.dataString)
    }

    @Test fun curatedSourcesOnlyOpenOfficialGuidesOrAppListings() {
        assertEquals(listOf("youtube_music", "audible", "youtube", "rumble", "maps"), PhoneApps.groups.map { it.id })
        assertEquals(10, PhoneApps.groups.flatMap { it.variants }.map { it.packageName }.toSet().size)
        val guide = PhoneApps.revancedGuide.intents().single()
        assertEquals("https://github.com/ReVanced/revanced-manager/tree/main/docs", guide.dataString)
        for (source in PhoneApps.groups.flatMap { it.sources } + PhoneApps.androidAutoSource) {
            val intents = source.intents()
            assertTrue(intents.all { it.action == Intent.ACTION_VIEW })
            assertEquals("https", intents.last().data?.scheme)
            source.playPackage?.let { packageName ->
                assertEquals("market://details?id=$packageName", intents.first().dataString)
                assertEquals("https://play.google.com/store/apps/details?id=$packageName", intents.last().dataString)
            }
        }
    }
}
