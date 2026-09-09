package com.mockrun.app.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

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

    fun recordSystemHookHeartbeat() {
        lastSystemHookHeartbeat = System.currentTimeMillis()
    }

    fun isSystemHookAlive(): Boolean {
        return (System.currentTimeMillis() - lastSystemHookHeartbeat) < 60_000L
    }

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
            // 1. SharedPreferences update
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
                android.provider.Settings.Global.putString(ctx.contentResolver, "fake_gps_config", jsonStr)
            }

            // 3. Local file writes (cacheDir & /data/local/tmp if writable)
            runCatching {
                val tmpFile = java.io.File("/data/local/tmp/fake_gps_hook.json")
                tmpFile.writeText(jsonStr)
                tmpFile.setReadable(true, false)
                tmpFile.setWritable(true, false)
            }

            runCatching {
                val cacheDir = ctx.cacheDir
                val tmpFile = java.io.File(cacheDir, "current_hook.json")
                tmpFile.writeText(
                    """{"active":$active,"lat":$lat,"lon":$lon,"alt":$alt,"bearing":$bear,"speed":$spd,"time":$updateTimestamp}"""
                )
                tmpFile.setReadable(true, false)
            }

            // 4. Multi-Channel System Sync via Root (SystemProperties, /data/system/, Settings.Global, DAC permissions)
            // Throttled to max 1 execution every 1200ms unless active state toggles
            val now = System.currentTimeMillis()
            val stateChanged = (lastSyncedActive != active)
            if (stateChanged || (now - lastSuSyncTime > 1200L)) {
                lastSuSyncTime = now
                lastSyncedActive = active

                runCatching {
                    val activeInt = if (active) 1 else 0
                    val pkgDir = ctx.applicationInfo.dataDir
                    val suCmd = buildString {
                        append("setprop debug.fakegps.active $activeInt; ")
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
                    Runtime.getRuntime().exec(arrayOf("su", "-c", suCmd))
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
