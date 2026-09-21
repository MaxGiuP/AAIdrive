package io.bimmergestalt.idriveconnectaddons.screenmirror

import android.content.Context

enum class ProjectionProfile(val intervalMs: Int) {
    SMOOTH(33), BALANCED(67), SHARP(100);

    internal fun compression(bluetooth: Boolean): JpegLimits = when (this) {
        SMOOTH -> if (bluetooth) JpegLimits(18, 30, 40) else JpegLimits(30, 60, 70)
        BALANCED -> if (bluetooth) JpegLimits(25, 40, 55) else JpegLimits(40, 65, 80)
        SHARP -> if (bluetooth) JpegLimits(35, 55, 70) else JpegLimits(50, 75, 85)
    }
}

enum class KnobMode {
    ROTARY, DIRECTIONAL;

    // Open Headunit maps SOFT_LEFT/RIGHT (1/2) to Android Auto ScrollWheelEvent.
    fun keyCode(previous: Boolean, reversed: Boolean): Int = when (this) {
        ROTARY -> if (previous xor reversed) 1 else 2
        DIRECTIONAL -> if (previous xor reversed) 19 else 20
    }
}

class ProjectionSettings(context: Context) {
    val preferences = context.applicationContext.getSharedPreferences("projection", Context.MODE_PRIVATE)
    var profile: ProjectionProfile
        get() = ProjectionProfile.values().firstOrNull { it.name == preferences.getString(PROFILE, null) }
            ?: ProjectionProfile.SMOOTH
        set(value) { preferences.edit().putString(PROFILE, value.name).apply() }
    var knobMode: KnobMode
        get() = KnobMode.values().firstOrNull { it.name == preferences.getString("knob_mode", null) }
            ?: KnobMode.ROTARY
        set(value) { preferences.edit().putString("knob_mode", value.name).apply() }
    var reverseKnob: Boolean
        get() = preferences.getBoolean("reverse_knob", false)
        set(value) { preferences.edit().putBoolean("reverse_knob", value).apply() }

    companion object { const val PROFILE = "profile" }
}
