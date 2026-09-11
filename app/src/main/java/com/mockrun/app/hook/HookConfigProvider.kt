package com.mockrun.app.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.*

object HookStateBridge {
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
            }

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
            }

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
            }

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
                                append("chmod 666 /data/system/fake_gps_hook.json 2>/dev/null; ")
                                append("echo '$jsonStr' > /data/local/tmp/fake_gps_hook.json 2>/dev/null; ")
                                append("chmod 666 /data/local/tmp/fake_gps_hook.json 2>/dev/null; ")
                                append("settings put global fake_gps_config '$jsonStr' 2>/dev/null; ")
                                append("chmod 755 $pkgDir 2>/dev/null; ")
                                append("chmod 755 $pkgDir/shared_prefs 2>/dev/null; ")
                                append("chmod 666 $pkgDir/shared_prefs/hook_config.xml 2>/dev/null")
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
                            runCatching { process?.inputStream?.close() }
                            runCatching { process?.errorStream?.close() }
                            runCatching { process?.destroy() }
                        }
                    }
                }
            }
        }
    }
}

class HookConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
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

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
