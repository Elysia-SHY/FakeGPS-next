package com.mockrun.app.core.designsystem

import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableStateOf

/**
 * App-level Material 3 appearance preferences.
 *
 * The legacy "liquid glass" key is migrated into the dynamic-color preference so
 * existing users keep a meaningful visual choice after the blur effect is removed.
 */
object AppAppearancePreferences {
    const val PREFS_NAME = "fake_gps_ui_prefs"
    const val KEY_DYNAMIC_COLOR_ENABLED = "is_dynamic_color_enabled"
    private const val LEGACY_KEY_LIQUID_GLASS_ENABLED = "is_liquid_glass_enabled"

    val dynamicColorEnabled = mutableStateOf(false)

    val supportsDynamicColor: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = if (preferences.contains(KEY_DYNAMIC_COLOR_ENABLED)) {
            preferences.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, false)
        } else {
            val migrated = preferences.getBoolean(LEGACY_KEY_LIQUID_GLASS_ENABLED, false)
            preferences.edit()
                .putBoolean(KEY_DYNAMIC_COLOR_ENABLED, migrated)
                .apply()
            migrated
        }
        dynamicColorEnabled.value = enabled && supportsDynamicColor
    }

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        val effectiveValue = enabled && supportsDynamicColor
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DYNAMIC_COLOR_ENABLED, effectiveValue)
            .apply()
        dynamicColorEnabled.value = effectiveValue
    }
}
