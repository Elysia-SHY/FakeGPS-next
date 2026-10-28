package com.mockrun.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 注入模式偏好。
 *
 * ROOT    —— Root 注入模式：应用通过 su 自动授予 android:mock_location 应用 op、
 *           恢复硬件高精度定位，并在需要时关闭扫描以阻止反查；LSPosed 是独立的手动
 *           高级通道，不再自动叠加（钩子本身与模式开关正交）。
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

    fun getMode(context: Context, isRootAvailable: Boolean? = null): InjectionMode {
        if (isRootAvailable == false) {
            return InjectionMode.NO_ROOT
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // If not set yet and isRootAvailable is explicitly false, default to NO_ROOT
        if (!prefs.contains(KEY_MODE) && isRootAvailable == false) {
            return InjectionMode.NO_ROOT
        }
        return when (prefs.getString(KEY_MODE, "ROOT")) {
            "NO_ROOT" -> InjectionMode.NO_ROOT
            else -> if (isRootAvailable == false) InjectionMode.NO_ROOT else InjectionMode.ROOT
        }
    }

    fun isRootMode(context: Context, isRootAvailable: Boolean? = null): Boolean {
        if (isRootAvailable == false) return false
        return getMode(context, isRootAvailable) == InjectionMode.ROOT
    }

    fun validateOrDowngrade(context: Context, isRootAvailable: Boolean): InjectionMode {
        if (!isRootAvailable) {
            setMode(context, InjectionMode.NO_ROOT)
            return InjectionMode.NO_ROOT
        }
        return getMode(context, isRootAvailable)
    }

    fun setMode(context: Context, mode: InjectionMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}
