package com.mockrun.app.core.location

import android.content.Context

/**
 * 步频配置落盘。
 *
 * 存的是「用户意图」，不是「实际是否注入」：
 * Root 探测晚于首帧，如果把 `enabled && rootOk` 的结果写回，冷启动会把用户的开关悄悄关掉。
 */
object CadencePrefs {

    private const val FILE = "fake_gps_cadence_prefs"
    private const val KEY_ENABLED = "cadence_enabled"
    private const val KEY_MODE = "cadence_mode"
    private const val KEY_CUSTOM = "cadence_custom_spm"

    const val DEFAULT_CUSTOM_SPM = 172
    private const val MIN_SPM = 40
    private const val MAX_SPM = 300

    data class Snapshot(
        val enabled: Boolean,
        val mode: CadenceMode,
        val customSpm: Int
    )

    fun load(context: Context): Snapshot {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val mode = runCatching {
            CadenceMode.valueOf(sp.getString(KEY_MODE, CadenceMode.AUTO.name) ?: CadenceMode.AUTO.name)
        }.getOrDefault(CadenceMode.AUTO)
        val spm = sp.getInt(KEY_CUSTOM, DEFAULT_CUSTOM_SPM).coerceIn(MIN_SPM, MAX_SPM)
        return Snapshot(
            enabled = sp.getBoolean(KEY_ENABLED, false),
            mode = mode,
            customSpm = spm
        )
    }

    fun saveIntent(context: Context, enabled: Boolean, mode: CadenceMode, customSpm: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_MODE, mode.name)
            .putInt(KEY_CUSTOM, customSpm.coerceIn(MIN_SPM, MAX_SPM))
            .apply()
    }
}
