package com.mockrun.app.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import kotlinx.coroutines.*

object HookStateBridge {
    private const val TAG = "HookState"

    @Volatile
    var isHookActive: Boolean = false
        private set

    @Volatile
    var latitude: Double = 39.9042
        private set

    @Volatile
    var longitude: Double = 116.4074
        private set

    @Volatile
    var altitude: Double = 50.0
        private set

    @Volatile
    var bearing: Float = 0f
        private set

    @Volatile
    var speed: Float = 0f
        private set

    @Volatile
    var updateTimestamp: Long = 0L
        private set

    @Volatile
    var lastSystemHookHeartbeat: Long = 0L
        private set

    @Volatile
    private var lastSuSyncTime: Long = 0L

    @Volatile
    private var lastSyncedActive: Boolean? = null

    @Volatile
    var multiTargetRulesJson: String = ""
        private set

    fun setMultiTargetRules(json: String) {
        multiTargetRulesJson = json
    }

    fun recordSystemHookHeartbeat() {
        lastSystemHookHeartbeat = System.currentTimeMillis()
    }

    fun isSystemHookAlive(): Boolean {
        return (System.currentTimeMillis() - lastSystemHookHeartbeat) < 60_000L
    }

    @Volatile
    var isRouteSimulation: Boolean = false
        private set

    fun setRouteSimulationMode(isRoute: Boolean) {
        isRouteSimulation = isRoute
        asyncScope.launch {
            com.mockrun.app.location.RootSuBridge().executeCommand("setprop debug.fakegps.is_route ${if (isRoute) "1" else "0"}")
        }
    }

    private val asyncScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun update(
        context: Context?,
        active: Boolean,
        lat: Double = latitude,
        lon: Double = longitude,
        alt: Double = 50.0,
        bear: Float = 0f,
        spd: Float = 0f
    ) {
        isHookActive = active
        latitude = lat
        longitude = lon
        altitude = alt
        bearing = bear
        speed = spd
        updateTimestamp = System.currentTimeMillis()

        context?.let { ctx ->
            // 1. SharedPreferences update (Immediate)
            runCatching {
                val sp = ctx.getSharedPreferences("hook_config", Context.MODE_PRIVATE)
                sp.edit()
                    .putBoolean("is_active", active)
                    .putString("latitude", lat.toString())
                    .putString("longitude", lon.toString())
                    .putFloat("bearing", bear)
                    .putFloat("speed", spd)
                    .putLong("timestamp", updateTimestamp)
                    .apply()

                val prefsFile = java.io.File(ctx.applicationInfo.dataDir, "shared_prefs/hook_config.xml")
                if (prefsFile.exists()) {
                    prefsFile.setReadable(true, false)
                }
            }.logFailure(TAG, "channel 1: write hook_config SharedPreferences", Diag.Level.DEBUG)

            val jsonStr = """{"isActive":$active,"latitude":$lat,"longitude":$lon,"altitude":$alt,"bearing":$bear,"speed":$spd,"time":$updateTimestamp}"""

            // 2. Settings.Global update (zero IPC overhead in system_server, readable by all apps)
            runCatching {
                if (active) {
                    android.provider.Settings.Global.putString(ctx.contentResolver, "fake_gps_config", jsonStr)
                } else {
                    android.provider.Settings.Global.putString(
                        ctx.contentResolver,
                        "fake_gps_config",
                        """{"isActive":false,"latitude":$lat,"longitude":$lon,"time":$updateTimestamp}"""
                    )
                }
            }.logFailure(TAG, "channel 2: write Settings.Global fake_gps_config", Diag.Level.DEBUG)

            // 3. Local file writes (cacheDir)
            runCatching {
                val cacheDir = ctx.cacheDir
                val tmpFile = java.io.File(cacheDir, "current_hook.json")
                if (active) {
                    tmpFile.writeText(
                        """{"active":$active,"lat":$lat,"lon":$lon,"alt":$alt,"bearing":$bear,"speed":$spd,"time":$updateTimestamp}"""
                    )
                    tmpFile.setReadable(true, false)
                } else {
                    if (tmpFile.exists()) {
                        tmpFile.delete()
                    }
                }
                Unit
            }.logFailure(TAG, "channel 3: write cacheDir/current_hook.json", Diag.Level.DEBUG)

            // 4. Multi-Channel System Sync via Root (Asynchronous, NEVER blocks UI thread)
            val now = System.currentTimeMillis()
            val stateChanged = (lastSyncedActive != active)
            if (stateChanged || (now - lastSuSyncTime > 1200L)) {
                lastSuSyncTime = now
                lastSyncedActive = active

                asyncScope.launch {
                    runCatching {
                        val activeInt = if (active) 1 else 0
                        val pkgDir = ctx.applicationInfo.dataDir
                        val suCmd = if (active) {
                            buildString {
                                append("setprop debug.fakegps.active $activeInt; ")
                                append("setprop debug.fakegps.time $updateTimestamp; ")
                                append("setprop debug.fakegps.lat $lat; ")
                                append("setprop debug.fakegps.lon $lon; ")
                                append("setprop debug.fakegps.alt $alt; ")
                                append("setprop debug.fakegps.bearing $bear; ")
                                append("setprop debug.fakegps.speed $spd; ")
                                append("echo '$jsonStr' > /data/system/fake_gps_hook.json 2>/dev/null; ")
                                // 0644 rather than 0666. The hook has to be able to READ these from
                                // any process, so world-readable is load-bearing — but world-WRITABLE
                                // is not. With 0666 any app on the device could rewrite the
                                // coordinates this hook serves.
                                append("chmod 644 /data/system/fake_gps_hook.json 2>/dev/null; ")
                                append("echo '$jsonStr' > /data/local/tmp/fake_gps_hook.json 2>/dev/null; ")
                                append("chmod 644 /data/local/tmp/fake_gps_hook.json 2>/dev/null; ")
                                append("settings put global fake_gps_config '$jsonStr' 2>/dev/null; ")
                                append("chmod 755 $pkgDir 2>/dev/null; ")
                                append("chmod 755 $pkgDir/shared_prefs 2>/dev/null; ")
                                // Owner is this app (it is the one writing the prefs), so 0644 still
                                // lets it write while keeping other apps read-only.
                                append("chmod 644 $pkgDir/shared_prefs/hook_config.xml 2>/dev/null")
                            }
                        } else {
                            buildString {
                                append("setprop debug.fakegps.active 0; ")
                                append("setprop debug.fakegps.time 0; ")
                                append("rm -f /data/system/fake_gps_hook.json 2>/dev/null; ")
                                append("rm -f /data/local/tmp/fake_gps_hook.json 2>/dev/null; ")
                                append("settings delete global fake_gps_config 2>/dev/null; ")
                                append("settings put global fake_gps_config '{\"isActive\":false,\"latitude\":$lat,\"longitude\":$lon}' 2>/dev/null; ")
                                append("settings put secure location_mode 3 2>/dev/null; ")
                                append("settings put global wifi_scan_always_enabled 1 2>/dev/null; ")
                                append("settings put global ble_scan_always_enabled 1 2>/dev/null; ")
                                append("settings put global assisted_gps_enabled 1 2>/dev/null")
                            }
                        }
                        var process: Process? = null
                        try {
                            process = Runtime.getRuntime().exec(arrayOf("su", "-c", suCmd))
                            process.outputStream.close()
                            process.waitFor()
                        } finally {
                            // Best-effort resource cleanup — deliberately not logged, same
                            // reasoning as RootSuBridge: failures here are harmless.
                            runCatching { process?.inputStream?.close() }
                            runCatching { process?.errorStream?.close() }
                            runCatching { process?.destroy() }
                        }
                    }.logFailure(
                        TAG,
                        "root sync of hook state (SystemProperties + /data files + Settings.Global) — " +
                            "hook will fall back to the non-root channels"
                    )
                }
            }
        }
    }
}

class HookConfigProvider : ContentProvider() {

    private companion object {
        const val TAG = "HookProvider"
    }

    override fun onCreate(): Boolean = true

    /**
     * Gate every inbound call on the caller's uid.
     *
     * ## Why not a permission
     *
     * This provider is deliberately `exported` because the hook runs inside `system_server`,
     * which cannot hold an app-signature permission — a `protectionLevel="signature"` guard
     * would lock the framework out of its own config channel.
     *
     * ## Why uid gating
     *
     * Before this check **any installed app** could call `getLocation` on
     * `content://com.mockrun.app.hook.provider` and read the current spoofed coordinates plus
     * the hook-active flag. That leaks the user's spoofed position and reveals that this app is
     * installed and active.
     *
     * Allowed callers: this app itself, the system uid, root and shell. Everything else is
     * refused and recorded.
     *
     * ## Accepted trade-off
     *
     * The hook's ContentProvider channels (used as a fallback in `loadMultiTargetRules()` and
     * `getGlobalActiveLocation()`) stop working when the hook runs inside a third-party app
     * process, because that process's uid is not on the list. Those channels are already
     * last-resort — SystemProperties, the `/data/system` and `/data/local/tmp` files,
     * `Settings.Global` and XSharedPreferences are all tried first — and a refusal is now
     * logged instead of passing silently. If this turns out to matter on a real device, the
     * fix is to have the app push state into XSharedPreferences rather than relaxing this check.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!isTrustedCaller()) {
            Diag.w(TAG, "refused call '$method' from untrusted uid ${Binder.getCallingUid()}")
            return null
        }
        return when (method) {
            "getLocation" -> {
                HookStateBridge.recordSystemHookHeartbeat()
                Bundle().apply {
                    putBoolean("is_active", HookStateBridge.isHookActive)
                    putDouble("latitude", HookStateBridge.latitude)
                    putDouble("longitude", HookStateBridge.longitude)
                    putDouble("altitude", HookStateBridge.altitude)
                    putFloat("bearing", HookStateBridge.bearing)
                    putFloat("speed", HookStateBridge.speed)
                    putLong("timestamp", HookStateBridge.updateTimestamp)
                }
            }
            "pingSystemServer" -> {
                HookStateBridge.recordSystemHookHeartbeat()
                Bundle().apply {
                    putBoolean("ack", true)
                }
            }
            "getMultiTargetRules" -> {
                Bundle().apply {
                    putString("json", HookStateBridge.multiTargetRulesJson)
                }
            }
            "isHookActive" -> {
                Bundle().apply {
                    putBoolean("is_active", HookStateBridge.isSystemHookAlive() || XposedStatusHelper.isModuleActive())
                }
            }
            "ping" -> {
                Bundle().apply {
                    putBoolean("available", true)
                    putString("version", com.mockrun.app.BuildConfig.VERSION_NAME)
                }
            }
            else -> null
        }
    }

    /** See [call] for why gating happens here rather than via a manifest permission. */
    private fun isTrustedCaller(): Boolean {
        val uid = Binder.getCallingUid()
        // Fully qualified on purpose: this file also uses java.lang.Process for `su` execution,
        // so importing android.os.Process here would shadow it and break those call sites.
        return uid == android.os.Process.myUid() ||
            uid == android.os.Process.SYSTEM_UID ||
            uid == android.os.Process.ROOT_UID ||
            uid == android.os.Process.SHELL_UID
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
