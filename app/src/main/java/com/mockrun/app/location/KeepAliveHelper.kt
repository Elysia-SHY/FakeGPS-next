package com.mockrun.app.location

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/**
 * System-level keep-alive and anti-kill helper.
 * Provides battery optimization exemption, OEM auto-start jumps,
 * task removal resurrection alarms, and device-specific instructions.
 */
object KeepAliveHelper {

    private const val TAG = "KeepAliveHelper"

    /**
     * Check if app is in system battery optimization whitelist (Doze mode exemption).
     */
    fun isBatteryOptimized(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Request ignore battery optimization.
     * Uses direct system prompt dialog (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
     * Falls back to general battery optimization list or app details if needed.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed direct REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, fallback to settings", e)
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (e2: Exception) {
                openAppDetailsSettings(context)
            }
        }
    }

    /**
     * Open application details settings page.
     */
    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details", e)
            false
        }
    }

    /**
     * Jump directly to OEM-specific Auto-Start / Background Management settings page.
     * Supports Xiaomi/HyperOS, Huawei/HarmonyOS, OPPO/ColorOS, Vivo/OriginOS, Meizu, Samsung.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                intents.add(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")))
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity")))
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")))
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity")))
            }
            manufacturer.contains("meizu") -> {
                intents.add(Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC")))
            }
            manufacturer.contains("samsung") -> {
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")))
            }
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try next intent
            }
        }

        // Fallback to app details
        return openAppDetailsSettings(context)
    }

    /**
     * Schedule a quick resurrection alarm via AlarmManager when onTaskRemoved is triggered.
     */
    fun scheduleServiceResurrection(
        context: Context,
        serviceClass: Class<*>,
        action: String? = null,
        extras: Bundle? = null
    ) {
        try {
            val intent = Intent(context, serviceClass).apply {
                if (action != null) this.action = action
                if (extras != null) putExtras(extras)
            }
            val pendingIntent = PendingIntent.getService(
                context,
                8888,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val triggerTime = SystemClock.elapsedRealtime() + 500L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d(TAG, "Scheduled service resurrection for ${serviceClass.simpleName} in 500ms")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule resurrection alarm: ${e.message}")
        }
    }

    /**
     * Get user-friendly Chinese brand name for UI display.
     */
    fun getDeviceBrandName(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") -> "小米/红米 (MIUI/HyperOS)"
            m.contains("huawei") || m.contains("honor") -> "华为/荣耀 (HarmonyOS/MagicOS)"
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> "OPPO/一加/真我 (ColorOS)"
            m.contains("vivo") || m.contains("iqoo") -> "vivo/iQOO (OriginOS)"
            m.contains("meizu") -> "魅族 (Flyme)"
            m.contains("samsung") -> "三星 (OneUI)"
            else -> "原生 Android"
        }
    }

    /**
     * Brand-specific instructions to lock app in recent apps list.
     */
    fun getRecentsLockGuide(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") ->
                "打开多任务卡片列表，长按 Fake GPS 卡片并点击【加锁】图标（或向下拉动卡片上锁）。"
            m.contains("huawei") || m.contains("honor") ->
                "打开多任务任务栏，向下拉动 Fake GPS 卡片，看到卡片右上角出现【锁定】图标即可。"
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
                "打开多任务界面，点击卡片右上角【三个点】或向下拉动，选择【锁定】。"
            m.contains("vivo") || m.contains("iqoo") ->
                "打开多任务界面，向下拉动 Fake GPS 卡片，点击卡片上方【锁定】图标。"
            m.contains("samsung") ->
                "打开多任务卡片，点击应用顶部的小图标，选择【保留在后台运行】或【锁定此应用程序】。"
            else ->
                "在多任务卡片界面长按或下拉卡片，将应用设置为锁定保护，防止一键清理误杀。"
        }
    }
}
