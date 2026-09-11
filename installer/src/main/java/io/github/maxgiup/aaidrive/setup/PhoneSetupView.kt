package io.github.maxgiup.aaidrive.setup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Native BMW Apps setup comes first; Android Auto projection is a separate optional section. */
internal class PhoneSetupView(
    context: Context,
    private val canOpenBundled: (String) -> Boolean,
    private val openBundled: (String) -> Unit,
    private val launch: (Intent) -> Boolean,
    projectionExpanded: Boolean = false,
    private val onProjectionExpanded: (Boolean) -> Unit = {},
    private val resolver: PhoneAppResolver = PhoneAppResolver(context.packageManager)
) : LinearLayout(context) {
    var projectionExpanded = projectionExpanded
        private set

    init {
        orientation = VERTICAL
        render()
    }

    private fun render() {
        removeAllViews()
        heading(R.string.native_connection)
        text(R.string.step_aaidrive)
        button(R.string.open_aaidrive, canOpenBundled(PhoneApps.AAIDRIVE)) { openBundled(PhoneApps.AAIDRIVE) }
        button(R.string.notification_access, canOpenBundled(PhoneApps.AAIDRIVE)) {
            launchFirst(listOf(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), Intent(Settings.ACTION_SETTINGS)))
        }

        heading(R.string.native_media)
        text(R.string.native_media_help)
        PhoneApps.groups.filter { it.id != "maps" }.forEach(::appGroup)
        text(R.string.revanced_source_help)
        button(R.string.revanced_guide) { launchFirst(PhoneApps.revancedGuide.intents()) }

        heading(R.string.native_navigation)
        appGroup(PhoneApps.groups.first { it.id == "maps" })

        heading(R.string.optional_projection)
        text(R.string.optional_projection_help)
        button(if (projectionExpanded) R.string.hide_projection else R.string.show_projection) {
            projectionExpanded = !projectionExpanded
            onProjectionExpanded(projectionExpanded)
            render()
        }
        if (projectionExpanded) projectionSteps()
    }

    private fun appGroup(group: PhoneAppGroup) {
        heading(group.label, 18f)
        text(group.description)
        val statuses = group.variants.map(resolver::inspect)
        statuses.filter { it.state != PhoneAppState.MISSING }.forEach { status ->
            val name = context.getString(status.variant.label)
            val label = when (status.state) {
                PhoneAppState.ENABLED -> context.getString(R.string.open_named_app, name)
                PhoneAppState.DISABLED -> context.getString(R.string.enable_named_app, name)
                else -> context.getString(R.string.check_named_app, name)
            }
            button(label, icon = resolver.icon(status)) { launchFirst(resolver.openIntents(status.variant)) }
        }
        if (statuses.none { it.state == PhoneAppState.ENABLED }) {
            if (statuses.all { it.state == PhoneAppState.MISSING }) text(R.string.no_supported_variant)
            group.sources.forEach { source -> button(source.label) { launchFirst(source.intents()) } }
        }
    }

    private fun projectionSteps() {
        text(R.string.step_google)
        val androidAuto = resolver.inspect(PhoneApps.androidAuto)
        when (androidAuto.state) {
            PhoneAppState.MISSING -> button(R.string.get_android_auto) { launchFirst(PhoneApps.androidAutoSource.intents()) }
            PhoneAppState.DISABLED -> button(context.getString(R.string.enable_named_app, context.getString(R.string.android_auto))) {
                launchFirst(resolver.openIntents(PhoneApps.androidAuto))
            }
            PhoneAppState.UNAVAILABLE -> button(context.getString(R.string.check_named_app, context.getString(R.string.android_auto))) {
                launchFirst(resolver.openIntents(PhoneApps.androidAuto))
            }
            else -> Unit
        }
        button(R.string.android_auto_settings) {
            val settings = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(PhoneApps.ANDROID_AUTO,
                "com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity"))
            launchFirst(listOf(settings, Intent(Settings.ACTION_SETTINGS)))
        }
        text(R.string.step_headunit)
        button(R.string.open_headunit, canOpenBundled(PhoneApps.HEADUNIT)) { openBundled(PhoneApps.HEADUNIT) }
        text(R.string.step_projection)
        button(R.string.open_projection, canOpenBundled(PhoneApps.PROJECTION)) { openBundled(PhoneApps.PROJECTION) }
        text(R.string.limitations)
    }

    private fun launchFirst(intents: List<Intent>) {
        if (intents.none(launch)) Toast.makeText(context, R.string.cannot_open, Toast.LENGTH_LONG).show()
    }

    private fun heading(label: Int, size: Float = 21f) = text(label, size, true)
    private fun text(label: Int, size: Float = 15f, bold: Boolean = false) {
        addView(TextView(context).apply {
            text = context.getString(label)
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(if (bold) 16 else 6), 0, dp(6))
        }, LayoutParams(-1, -2))
    }

    private fun button(label: Int, enabled: Boolean = true, action: () -> Unit) =
        button(context.getString(label), enabled, action = action)

    private fun button(label: String, enabled: Boolean = true, icon: Drawable? = null, action: () -> Unit) {
        addView(Button(context).apply {
            text = label
            isAllCaps = false
            isEnabled = enabled
            icon?.mutate()?.let {
                it.setBounds(0, 0, dp(32), dp(32))
                setCompoundDrawablesRelative(it, null, null, null)
                compoundDrawablePadding = dp(12)
            }
            setOnClickListener { action() }
        }, LayoutParams(-1, -2))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
