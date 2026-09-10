package com.mockrun.app.location

import kotlin.math.*

/**
 * Converts between WGS-84 (standard GPS / mock location) and GCJ-02 (China national standard).
 *
 * Use cases:
 *  - Injecting mock location: always use WGS-84
 *  - Displaying on Amap/high-de map: convert to GCJ-02 for correct pin placement
 *  - User taps on Amap: convert back to WGS-84 before saving
 *
 * OSMDroid uses WGS-84 natively, so no conversion is needed when using OSMDroid.
 */
object CoordinateConverter {

    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    private fun outOfChina(lat: Double, lon: Double) =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        r += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        r += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        r += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return r
    }

    private fun transformLon(x: Double, y: Double): Double {
        var r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        r += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        r += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        r += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return r
    }

    /** WGS-84 -> GCJ-02. Returns (lat, lon). */
    fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite() || outOfChina(lat, lon)) {
            return lat to lon
        }
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val rLat = Math.toRadians(lat)
        var magic = sin(rLat); magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / (A * (1 - EE) / (magic * sqrtMagic) * PI)
        dLon = dLon * 180.0 / (A / sqrtMagic * cos(rLat) * PI)
        return (lat + dLat) to (lon + dLon)
    }

    /** GCJ-02 -> WGS-84 (iterative approximation, ~1 cm accuracy). */
    fun gcj02ToWgs84(lat: Double, lon: Double): Pair<Double, Double> {
        if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite() || outOfChina(lat, lon)) {
            return lat to lon
        }
        var wLat = lat; var wLon = lon
        repeat(10) {
            val (gLat, gLon) = wgs84ToGcj02(wLat, wLon)
            wLat -= gLat - lat; wLon -= gLon - lon
        }
        return wLat to wLon
    }

    /**
     * Retrieves the best last known physical location from available system hardware providers.
     */
    private const val PREFS_NAME = "real_physical_location_prefs"
    private const val KEY_REAL_LAT = "real_hardware_lat"
    private const val KEY_REAL_LON = "real_hardware_lon"

    fun isMockLocation(loc: android.location.Location): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            loc.isMock
        } else {
            @Suppress("DEPRECATION")
            loc.isFromMockProvider
        }
    }

    fun saveRealLocation(context: android.content.Context, lat: Double, lon: Double) {
        if (lat.isNaN() || lon.isNaN() || (lat == 0.0 && lon == 0.0)) return
        if (com.mockrun.app.hook.HookStateBridge.isHookActive) return
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REAL_LAT, lat.toString())
            .putString(KEY_REAL_LON, lon.toString())
            .apply()
    }

    fun clearSavedRealLocation(context: android.content.Context) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun getSavedRealLocation(context: android.content.Context): Pair<Double, Double>? {
        val sp = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val latStr = sp.getString(KEY_REAL_LAT, null) ?: return null
        val lonStr = sp.getString(KEY_REAL_LON, null) ?: return null
        val lat = latStr.toDoubleOrNull() ?: return null
        val lon = lonStr.toDoubleOrNull() ?: return null
        return lat to lon
    }

    /**
     * Retrieves the best last known physical location from available system hardware providers.
     * Guaranteed to reject any mock/test provider location.
     */
    fun getRealDeviceLocation(context: android.content.Context): Pair<Double, Double>? {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
            ?: return getSavedRealLocation(context)

        val providers = listOf(
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.NETWORK_PROVIDER,
            android.location.LocationManager.PASSIVE_PROVIDER,
            "fused"
        )
        var best: android.location.Location? = null
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            // Strictly filter out mock/spoofed locations!
            if (isMockLocation(loc)) continue
            // Guard: If HookStateBridge is active, reject coordinates matching the spoofed location
            if (com.mockrun.app.hook.HookStateBridge.isHookActive) {
                val dLat = Math.abs(loc.latitude - com.mockrun.app.hook.HookStateBridge.latitude)
                val dLon = Math.abs(loc.longitude - com.mockrun.app.hook.HookStateBridge.longitude)
                if (dLat < 0.0001 && dLon < 0.0001) continue
            }
            if (best == null || loc.time > best.time) {
                best = loc
            }
        }
        if (best != null) {
            if (!com.mockrun.app.hook.HookStateBridge.isHookActive) {
                saveRealLocation(context, best.latitude, best.longitude)
            }
            return best.latitude to best.longitude
        }
        return getSavedRealLocation(context)
    }

    /**
     * Actively requests the freshest physical hardware location from system GPS or network providers.
     * Strictly filters out mock locations and invokes callback with (lat, lon).
     */
    fun requestFreshLocation(context: android.content.Context, onLocation: (Double, Double) -> Unit) {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return

        // 1. Immediately report best verified non-mock location if available
        getRealDeviceLocation(context)?.let { (lat, lon) ->
            onLocation(lat, lon)
        }

        // 2. Request single active hardware GPS fix for highest accuracy
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val provider = if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                    android.location.LocationManager.GPS_PROVIDER
                } else {
                    android.location.LocationManager.NETWORK_PROVIDER
                }
                lm.getCurrentLocation(provider, null, androidx.core.content.ContextCompat.getMainExecutor(context)) { loc ->
                    if (loc != null && !isMockLocation(loc)) {
                        if (!com.mockrun.app.hook.HookStateBridge.isHookActive) {
                            saveRealLocation(context, loc.latitude, loc.longitude)
                        }
                        onLocation(loc.latitude, loc.longitude)
                    }
                }
            } else {
                val provider = if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                    android.location.LocationManager.GPS_PROVIDER
                } else {
                    android.location.LocationManager.NETWORK_PROVIDER
                }
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(loc: android.location.Location) {
                        if (!isMockLocation(loc)) {
                            if (!com.mockrun.app.hook.HookStateBridge.isHookActive) {
                                saveRealLocation(context, loc.latitude, loc.longitude)
                            }
                            onLocation(loc.latitude, loc.longitude)
                        }
                        runCatching { lm.removeUpdates(this) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }
                lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            // Missing location permission
        } catch (_: Throwable) {
            // Sensor unavailable
        }
    }
}
