package com.mockrun.app.util

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class PermissionIssueType {
    NONE,
    LOCATION_PERMISSION_MISSING,
    MOCK_LOCATION_APP_NOT_SET
}

object PermissionHelper {

    /**
     * 是否具备可用的定位权限。
     *
     * 必须同时接受 ACCESS_COARSE_LOCATION。
     * Android 12+ 的位置权限弹窗允许用户选「大致位置」，此时只授予 COARSE，
     * FINE 返回 DENIED。原实现只测 FINE，于是：
     *   checkPrimaryPermissions 返回 LOCATION_PERMISSION_MISSING
     *   → PermissionCoordinator / SimulationViewModel 全部提前 return
     *   → 模拟与单点伪装永远无法启动，
     * 而 MainActivity 是一次性同时请求 FINE+COARSE、只在一个缺失时才重弹，
     * 所以选过「大致位置」的用户不会再被提示，永久卡在待机，
     * 只能自己去系统设置里改。这是产品级卡死，不是提示问题。
     *
     * 虚拟定位本身不依赖高精度：注入坐标由用户指定，与设备实际精度无关。
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * 是否只拿到了「大致位置」（仅 COARSE，无 FINE）。
     *
     * 用于在 UI 上给出针对性提示（可去系统设置改为精确位置），
     * 而不是复用笼统的「缺少定位权限」文案。
     */
    fun hasOnlyCoarseLocation(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse && !fine
    }

    fun isMockLocationAppSet(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            }
            if (mode == AppOpsManager.MODE_ALLOWED) {
                true
            } else {
                probeMockLocationApp(context)
            }
        } catch (e: Exception) {
            probeMockLocationApp(context)
        }
    }

    /**
     * 通过 add/remove test provider 探测本应用是否已被设为「模拟位置信息应用」。
     *
     * 注意三点，缺一即会误报：
     *  1. 必须先 remove 一次 —— 上次探测若在 add 与 remove 之间进程被杀，provider
     *     会残留在系统里，此时 addTestProvider 抛的是
     *     IllegalArgumentException("Provider ... already exists")，而不是 SecurityException。
     *  2. 清理必须放在 finally —— 否则成功路径之外都会留下残留。
     *  3. 只有「add 成功」才算通过；把任意 Exception 当作 true 会让
     *     checkPrimaryPermissions 返回 NONE，跳过引导，之后注入失败时报的是不相干的错。
     */
    private fun probeMockLocationApp(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        val probe = "mock_probe_test"
        var ok = false
        try {
            runCatching { lm.removeTestProvider(probe) }
            lm.addTestProvider(probe, false, false, false, false, false, false, false, 1, 1)
            ok = true
        } catch (se: SecurityException) {
            ok = false
        } catch (e: Exception) {
            ok = false
        } finally {
            runCatching { lm.removeTestProvider(probe) }
        }
        return ok
    }

    fun checkPrimaryPermissions(context: Context): PermissionIssueType {
        if (!hasLocationPermission(context)) {
            return PermissionIssueType.LOCATION_PERMISSION_MISSING
        }
        if (!isMockLocationAppSet(context)) {
            return PermissionIssueType.MOCK_LOCATION_APP_NOT_SET
        }
        return PermissionIssueType.NONE
    }

    fun openDevelopmentSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun openAppSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}