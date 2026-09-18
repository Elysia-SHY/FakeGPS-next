package com.mockrun.app.core.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mockrun.app.util.Diag
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootSuBridge @Inject constructor() {

    private companion object {
        const val TAG = "RootSuBridge"

        /** su commands can be very long (HookStateBridge builds multi-line ones); keep logs readable. */
        const val MAX_CMD_IN_LOG = 120
    }

    private var isRootedCache: Boolean? = null

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        // 只缓存"确认有 root"的结论；未确认时每次重探（用户可能稍后才在 Root 管理器里授权）。
        // 旧实现把"未找到 su 二进制"直接缓存为 false 并短路，导致 KernelSU 等 su 不在固定路径
        // 或路径不可 stat 的机型被误判为无 root，Root 模式于是从不自动授权。
        if (isRootedCache == true) return@withContext true

        // 权威判据：直接尝试 su -c id，成功即认为有 root（不依赖固定路径）
        Diag.d(TAG, "probing root with 'su -c id'")
        val executed = executeCommand("id")
        if (executed) {
            isRootedCache = true
            return@withContext true
        }

        // 回退判断：区分"su 存在但被拒"与"根本没有 su"，仅用于日志排障
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/data/local/su"
        )
        if (paths.any { File(it).exists() }) {
            Diag.w(TAG, "su binary exists but 'su -c id' did not succeed — root likely denied")
        } else {
            Diag.i(TAG, "no su binary on known paths and 'su -c id' failed — running unrooted")
        }
        false
    }

    suspend fun executeCommand(cmd: String): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        val shortCmd = cmd.take(MAX_CMD_IN_LOG)
        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            // Close output stream immediately since we aren't writing stdin
            process.outputStream.close()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                // Previously silent: a command that ran and failed was indistinguishable
                // from su never being available at all.
                Diag.w(TAG, "su command exited with code $exitCode :: $shortCmd")
            } else {
                Diag.d(TAG, "su command ok :: $shortCmd")
            }
            exitCode == 0
        } catch (e: Exception) {
            // Previously swallowed entirely (`catch (_: Exception) { false }`).
            Diag.w(TAG, "su unavailable or denied :: $shortCmd", e)
            false
        } finally {
            // NOTE: intentionally left un-logged. These are best-effort resource cleanups;
            // a failure here is harmless and logging it would only add noise on hot paths.
            runCatching { process?.inputStream?.close() }
            runCatching { process?.errorStream?.close() }
            runCatching { process?.destroy() }
        }
    }

    suspend fun injectSensorStep(@Suppress("UNUSED_PARAMETER") stepIncrement: Int): Boolean {
        return executeCommand("cmd sensor_privacy disable 0 2 2>/dev/null; input keyevent 0")
    }

    /**
     * 授予本应用「模拟位置信息应用」权限（android:mock_location app-op），并顺带开启全局
     * 「开发者选项」开关（部分 OEM 如 ColorOS / MIUI 在 addTestProvider 时除校验 app-op 外
     * 还要求 DEVELOPMENT_SETTINGS_ENABLED = 1）。
     *
     * 关键：两条命令分开执行、互不短路。避免因 ROM 拒绝写 development_settings_enabled
     * 导致 appops set 无法执行。appops 依次尝试多种写法，任一成功即可；
     * settings put 尽力而为，其结果不影响返回值。
     */
    suspend fun grantMockLocation(packageName: String): Boolean {
        val candidates = arrayOf(
            "appops set $packageName android:mock_location allow",
            "cmd appops set $packageName android:mock_location allow",
            "appops set --uid $packageName android:mock_location allow"
        )
        var granted = false
        for (c in candidates) {
            if (executeCommand(c)) {
                granted = true
                break
            }
        }
        // 尽力而为：失败也不影响上面的 app-op 结果
        executeCommand("settings put global development_settings_enabled 1")
        return granted
    }

    suspend fun restoreScanningHardware(): Boolean {
        return executeCommand("settings put secure location_mode 3 && settings put global wifi_scan_always_enabled 1 && settings put global ble_scan_always_enabled 1 && settings put global assisted_gps_enabled 1")
    }

    /**
     * 一键关闭蓝牙 / Wi‑Fi 与背景扫描（Root only）。
     * 用于阻止微信等内嵌腾讯定位 SDK 的应用通过周边路由器 MAC / 蓝牙信标反查真实经纬度。
     * 关闭：WLAN 始终扫描、BLE 始终扫描、Wi‑Fi 唤醒，并直接关闭 Wi‑Fi 与蓝牙射频本身。
     */
    suspend fun disableWifiBluetoothScan(): Boolean {
        return executeCommand(
            "settings put global wifi_scan_always_enabled 0 && " +
            "settings put global ble_scan_always_enabled 0 && " +
            "settings put global wifi_wakeup_enabled 0 && " +
            "svc wifi disable && " +
            "svc bluetooth disable"
        )
    }
}