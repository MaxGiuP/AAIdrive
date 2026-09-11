package io.github.maxgiup.aaidrive.setup

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.TextView
import androidx.core.view.children
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneSetupViewTest {
    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Setup)
    private fun PhoneSetupView.button(label: Int) = children.filterIsInstance<Button>()
        .single { it.text.toString() == context.getString(label) }
    private fun PhoneSetupView.labels() = children.filterIsInstance<TextView>().map { it.text.toString() }.toList()

    @Test fun nativeSetupComesFirstAndProjectionNeedsExplicitExpansion() {
        val opened = mutableListOf<String>()
        val intents = mutableListOf<Intent>()
        val expansion = mutableListOf<Boolean>()
        val fixture = PhoneAppFixture()
        val view = PhoneSetupView(context, { true }, { opened.add(it) }, { intents.add(it); true },
            onProjectionExpanded = { expansion.add(it) }, resolver = fixture.resolver)
        val labels = view.labels()
        assertTrue(labels.indexOf(context.getString(R.string.native_connection)) < labels.indexOf(context.getString(R.string.native_media)))
        assertTrue(labels.indexOf(context.getString(R.string.native_navigation)) < labels.indexOf(context.getString(R.string.optional_projection)))
        assertFalse(labels.contains(context.getString(R.string.android_auto_settings)))
        assertFalse(labels.contains(context.getString(R.string.open_headunit)))
        assertTrue(opened.isEmpty())
        assertTrue(intents.isEmpty())

        view.button(R.string.open_aaidrive).performClick()
        view.button(R.string.notification_access).performClick()
        assertEquals(listOf(PhoneApps.AAIDRIVE), opened)
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, intents.single().action)

        view.button(R.string.show_projection).performClick()
        assertTrue(view.projectionExpanded)
        assertTrue(view.labels().contains(context.getString(R.string.android_auto_settings)))
        assertEquals(listOf(true), expansion)
        view.button(R.string.hide_projection).performClick()
        assertFalse(view.projectionExpanded)
        assertFalse(view.labels().contains(context.getString(R.string.open_projection)))
    }

    @Test fun restoredExpansionOnlyShowsInstructionsWithoutStartingAnything() {
        val view = PhoneSetupView(context, { true }, { error("App launched without a click") },
            { error("Intent launched without a click") }, projectionExpanded = true,
            resolver = PhoneAppFixture().resolver)
        assertTrue(view.projectionExpanded)
        assertTrue(view.labels().contains(context.getString(R.string.open_projection)))
    }

    @Test fun missingNotificationAccessScreenFallsBackToGeneralSettings() {
        val intents = mutableListOf<Intent>()
        val view = PhoneSetupView(context, { true }, {}, {
            intents.add(it)
            it.action == Settings.ACTION_SETTINGS
        }, resolver = PhoneAppFixture().resolver)
        view.button(R.string.notification_access).performClick()
        assertEquals(listOf(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, Settings.ACTION_SETTINGS),
            intents.map { it.action })
    }

    @Test fun disabledVariantOffersSettingsAndEnabledVariantUsesItsOwnIconAndLaunch() {
        val fixture = PhoneAppFixture()
        val variants = PhoneApps.groups.first().variants
        fixture.install(variants[0], enabled = false)
        fixture.install(variants[1])
        val icon = ColorDrawable(Color.RED)
        doReturn(icon).`when`(fixture.packages).getApplicationIcon(variants[1].packageName)
        val intents = mutableListOf<Intent>()
        val view = PhoneSetupView(context, { true }, {}, { intents.add(it); true }, resolver = fixture.resolver)
        val buttons = view.children.filterIsInstance<Button>().toList()
        val disabled = buttons.single { it.text.toString() == context.getString(R.string.enable_named_app, context.getString(variants[0].label)) }
        disabled.performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intents.last().action)
        val enabled = buttons.single { it.text.toString() == context.getString(R.string.open_named_app, context.getString(variants[1].label)) }
        assertNotNull(enabled.compoundDrawablesRelative[0])
        enabled.performClick()
        assertEquals(variants[1].packageName, intents.last().component?.packageName)
        assertFalse(buttons.any { it.text.toString() == context.getString(R.string.open_named_app, context.getString(variants[0].label)) })
    }

    @Test fun missingPlayStoreFallsBackToBrowserForTheSameApp() {
        val intents = mutableListOf<Intent>()
        val view = PhoneSetupView(context, { false }, {}, {
            intents.add(it)
            it.data?.scheme == "https"
        }, resolver = PhoneAppFixture().resolver)
        assertFalse(view.button(R.string.open_aaidrive).isEnabled)
        assertFalse(view.button(R.string.notification_access).isEnabled)
        view.button(R.string.get_audible).performClick()
        assertEquals(listOf("market://details?id=com.audible.application",
            "https://play.google.com/store/apps/details?id=com.audible.application"), intents.map { it.dataString })
        assertTrue(intents.all { it.action == Intent.ACTION_VIEW })
    }

    @Test fun anUnavailablePlayerActivityFallsBackToItsOwnAppSettings() {
        val fixture = PhoneAppFixture()
        val audible = PhoneApps.groups[1].variants.single()
        fixture.install(audible)
        val intents = mutableListOf<Intent>()
        val view = PhoneSetupView(context, { true }, {}, {
            intents.add(it)
            it.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }, resolver = fixture.resolver)
        view.children.filterIsInstance<Button>().single {
            it.text.toString() == context.getString(R.string.open_named_app, context.getString(R.string.audible))
        }.performClick()
        assertEquals(Intent.ACTION_MAIN, intents.first().action)
        assertEquals("package:com.audible.application", intents.last().dataString)
    }
}
