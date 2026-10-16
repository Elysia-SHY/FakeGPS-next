package com.mockrun.app.feature.map.tabs

import android.content.Context
import com.mockrun.app.core.designsystem.LiquidGlassDefaults

/**
 * 定位页底部抽屉（LocationControlPanel）三态折叠状态的持久化。
 *
 * 复用既有 UI 偏好文件 [LiquidGlassDefaults.PREFS_NAME]，键名 [KEY_LOCATION_PANEL_STATE]。
 * 键缺失（首次进入）或取值无法解析时返回 [LocationPanelState.COLLAPSED]，
 * 因此「首次默认」与「用户主动选择后保存的状态」是两条独立路径：
 * 只有用户拖拽或点击手柄改变状态时才写回，进入页面不会覆盖用户的选择。
 */
internal object LocationPanelPrefs {

    private const val KEY_LOCATION_PANEL_STATE = "location_panel_state"

    private fun prefs(context: Context) =
        context.getSharedPreferences(LiquidGlassDefaults.PREFS_NAME, Context.MODE_PRIVATE)

    fun readPanelState(context: Context): LocationPanelState = runCatching {
        val raw = prefs(context).getString(KEY_LOCATION_PANEL_STATE, null)
        LocationPanelState.values().firstOrNull { it.name == raw } ?: LocationPanelState.COLLAPSED
    }.getOrDefault(LocationPanelState.COLLAPSED)

    fun writePanelState(context: Context, state: LocationPanelState) {
        runCatching {
            prefs(context).edit().putString(KEY_LOCATION_PANEL_STATE, state.name).apply()
        }
    }
}
