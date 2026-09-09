package com.mockrun.app.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootSuBridge @Inject constructor() {

    private var isRootedCache: Boolean? = null

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        isRootedCache?.let { return@withContext it }

        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/data/local/su"
        )
        val fileFound = paths.any { File(it).exists() }
        if (!fileFound) {
            isRootedCache = false
            return@withContext false
        }

        val executed = executeCommand("id")
        isRootedCache = executed
        executed
    }

    suspend fun executeCommand(cmd: String): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            // Close output stream immediately since we aren't writing stdin
            process.outputStream.close()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        } finally {
            runCatching { process?.inputStream?.close() }
            runCatching { process?.errorStream?.close() }
            runCatching { process?.destroy() }
        }
    }

    suspend fun injectSensorStep(@Suppress("UNUSED_PARAMETER") stepIncrement: Int): Boolean {
        return executeCommand("cmd sensor_privacy disable 0 2 2>/dev/null; input keyevent 0")
    }

    suspend fun grantMockLocation(packageName: String): Boolean {
        return executeCommand("appops set $packageName android:mock_location allow")
    }

    suspend fun disableScanningHardware(): Boolean {
        return executeCommand("settings put secure location_mode 1 && settings put global wifi_scan_always_enabled 0 && settings put global ble_scan_always_enabled 0 && settings put global assisted_gps_enabled 0")
    }
}