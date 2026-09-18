package com.mockrun.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 注入模式偏好。
 *
 * ROOT    —— Root 注入模式：应用通过 su 自动授予 android:mock_location 应用 op、
 *           恢复硬件高精度定位，并在需要时关闭扫描以阻止反查；若 LSPosed 已激活，
 *           则自动叠加 system_server 框架层抗检测增强（钩子本身与模式开关正交）。
 * NO_ROOT —— 免 Root 模式：应用完全不调用 su，仅依赖用户在【开发者选项】中手动
 *           将本应用勾选为「模拟位置信息应用」，随后使用应用进程的
 *           LocationManager.addTestProvider 注入。
 *
 * 默认值 ROOT，与历史行为保持一致（升级后不会跳变到免 Root）。
 */
enum class InjectionMode { ROOT, NO_ROOT }

object InjectionModePrefs {
    private const val PREFS_NAME = "fake_gps_injection_mode_prefs"
    private const val KEY_MODE = "injection_mode"
    private const val DEFAULT = "ROOT"

    fun getMode(context: Context): InjectionMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_MODE, DEFAULT)) {
            "NO_ROOT" -> InjectionMode.NO_ROOT
            else -> InjectionMode.ROOT
        }
    }

    fun isRootMode(context: Context): Boolean = getMode(context) == InjectionMode.ROOT

    fun setMode(context: Context, mode: InjectionMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}
