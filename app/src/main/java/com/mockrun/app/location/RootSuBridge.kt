package com.mockrun.app.location

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
        isRootedCache?.let { return@withContext it }

        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/data/local/su"
        )
        val fileFound = paths.any { File(it).exists() }
        if (!fileFound) {
            Diag.i(TAG, "no su binary on any known path — running unrooted")
            isRootedCache = false
            return@withContext false
        }

        Diag.d(TAG, "su binary present, probing with 'id'")
        val executed = executeCommand("id")
        isRootedCache = executed
        if (!executed) {
            Diag.w(TAG, "su binary exists but 'su -c id' did not succeed — root likely denied")
        }
        executed
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

    suspend fun grantMockLocation(packageName: String): Boolean {
        return executeCommand("appops set $packageName android:mock_location allow")
    }

    suspend fun restoreScanningHardware(): Boolean {
        return executeCommand("settings put secure location_mode 3 && settings put global wifi_scan_always_enabled 1 && settings put global ble_scan_always_enabled 1 && settings put global assisted_gps_enabled 1")
    }

    @Deprecated("Disabling scanning permanently breaks indoor positioning. Use restoreScanningHardware instead.")
    suspend fun disableScanningHardware(): Boolean {
        return restoreScanningHardware()
    }
}