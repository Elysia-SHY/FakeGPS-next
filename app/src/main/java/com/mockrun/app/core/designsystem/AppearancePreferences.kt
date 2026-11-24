package com.mockrun.app.core.designsystem

import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableStateOf

/**
 * Global visual material preference.
 *
 * Liquid Glass is only enabled when the platform can provide a real backdrop
 * blur. Older devices stay on the high-contrast solid surface automatically.
 */
object AppAppearancePreferences {
    const val PREFS_NAME = "fake_gps_ui_prefs"
    const val KEY_LIQUID_GLASS_ENABLED = "is_liquid_glass_enabled"
    const val KEY_ACCENT_COLOR = "accent_color_argb"

    val liquidGlassEnabled = mutableStateOf(false)
    val dynamicColorEnabled = mutableStateOf(false)
    val accentColorArgb = mutableStateOf<Int?>(null)

    val supportsLiquidGlass: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val requested = preferences.getBoolean(KEY_LIQUID_GLASS_ENABLED, true)
        liquidGlassEnabled.value = requested && supportsLiquidGlass
        dynamicColorEnabled.value = false
        accentColorArgb.value = if (preferences.contains(KEY_ACCENT_COLOR)) {
            preferences.getInt(KEY_ACCENT_COLOR, 0)
        } else {
            null
        }
    }

    fun setLiquidGlassEnabled(context: Context, enabled: Boolean) {
        val effectiveValue = enabled && supportsLiquidGlass
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LIQUID_GLASS_ENABLED, effectiveValue)
            .apply()
        liquidGlassEnabled.value = effectiveValue
    }

    fun setAccentColor(context: Context, argb: Int?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (argb == null) remove(KEY_ACCENT_COLOR)
                else putInt(KEY_ACCENT_COLOR, argb)
            }
            .apply()
        accentColorArgb.value = argb
    }

    fun parseAccentColor(input: String): Int? {
        val hex = input.trim().removePrefix("#")
        if (!hex.matches(Regex("[0-9a-fA-F]{6}"))) return null
        return (0xFF000000L or hex.toLong(16)).toInt()
    }

    @Deprecated("Dynamic color is not part of the Atlas material system.")
    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        dynamicColorEnabled.value = false
    }
}
