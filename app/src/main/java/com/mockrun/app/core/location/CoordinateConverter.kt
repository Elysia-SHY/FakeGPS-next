package com.mockrun.app.core.location

import kotlin.math.*
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure

/**
 * Converts between WGS-84 (standard GPS / mock location) and GCJ-02 (China national standard).
 *
 * Use cases:
 *  - Injecting mock location: always use WGS-84
 *  - Displaying on Amap/high-de map: convert to GCJ-02 for correct pin placement
 *  - User taps on Amap: convert back to WGS-84 before saving
 *
 * OSMDroid uses WGS-84 natively, so no conversion is needed when using OSMDroid.
 *
 * 算法本体已收敛到 [com.mockrun.app.util.GeoTransform]（纯函数、无 Android 依赖、可单测）。
 * 本对象只保留「需要 Context 的硬件定位查询」这类应用侧职责，以及给既有调用点用的同名 API ——
 * hook 侧（XposedLocationHook）现在也从 GeoTransform 取同一份算法，两处不会再各自漂移。
 */
object CoordinateConverter {

    private const val TAG = "CoordConverter"

    /** WGS-84 -> GCJ-02. Returns (lat, lon). */
    fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> =
        com.mockrun.app.util.GeoTransform.wgs84ToGcj02(lat, lon)

    /** GCJ-02 -> WGS-84 (iterative approximation, ~1 cm accuracy). */
    fun gcj02ToWgs84(lat: Double, lon: Double): Pair<Double, Double> =
        com.mockrun.app.util.GeoTransform.gcj02ToWgs84(lat, lon)

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
            val loc = runCatching { lm.getLastKnownLocation(p) }
                .logFailure(TAG, "getLastKnownLocation($p)", Diag.Level.DEBUG)
                .getOrNull() ?: continue
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
        // No hardware provider yielded a usable location: every one either threw, returned
        // null, or was filtered as mock/spoofed. Callers fall back to the cached value, and
        // if that is missing too the "reset position" UI simply shows nothing - which used
        // to have no explanation attached to it anywhere.
        Diag.d(
            TAG,
            "no usable hardware location across ${providers.size} providers — falling back to saved value"
        )
        return getSavedRealLocation(context)
    }

    /**
     * Actively flushes and wakes up system hardware location providers (Wi-Fi, Cell, GPS)
     * immediately upon stopping mock location. This resets system_server's mLastLocation cache.
     */
    fun flushRealLocation(context: android.content.Context) {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return
        val providers = listOf(
            android.location.LocationManager.NETWORK_PROVIDER,
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.PASSIVE_PROVIDER
        )
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        for (provider in providers) {
            if (!lm.isProviderEnabled(provider)) continue
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val cancellationSignal = android.os.CancellationSignal()
                    handler.postDelayed({
                        runCatching { cancellationSignal.cancel() }
                    }, 15_000L)
                    lm.getCurrentLocation(provider, cancellationSignal, androidx.core.content.ContextCompat.getMainExecutor(context)) { loc ->
                        if (loc != null && !isMockLocation(loc)) {
                            saveRealLocation(context, loc.latitude, loc.longitude)
                        }
                    }
                } else {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(loc: android.location.Location) {
                            if (!isMockLocation(loc)) {
                                saveRealLocation(context, loc.latitude, loc.longitude)
                            }
                            runCatching { lm.removeUpdates(this) }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                        override fun onProviderEnabled(p: String) {}
                        override fun onProviderDisabled(p: String) {}
                    }
                    handler.postDelayed({
                        runCatching { lm.removeUpdates(listener) }
                    }, 15_000L)
                    lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                }
            } catch (e: SecurityException) {
                // Missing location permission — the saved real location goes stale silently
                Diag.w(TAG, "flushRealLocation($provider): no location permission")
            } catch (e: Throwable) {
                Diag.w(TAG, "flushRealLocation($provider) failed", e)
            }
        }
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

        // 2. Query both Network (fast indoor Wi-Fi/Cell fix) and GPS
        val providers = listOf(
            android.location.LocationManager.NETWORK_PROVIDER,
            android.location.LocationManager.GPS_PROVIDER
        )
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        for (provider in providers) {
            if (!lm.isProviderEnabled(provider)) continue
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val cancellationSignal = android.os.CancellationSignal()
                    handler.postDelayed({
                        runCatching { cancellationSignal.cancel() }
                    }, 15_000L)
                    lm.getCurrentLocation(provider, cancellationSignal, androidx.core.content.ContextCompat.getMainExecutor(context)) { loc ->
                        if (loc != null && !isMockLocation(loc)) {
                            if (!com.mockrun.app.hook.HookStateBridge.isHookActive) {
                                saveRealLocation(context, loc.latitude, loc.longitude)
                            }
                            onLocation(loc.latitude, loc.longitude)
                        }
                    }
                } else {
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
                    handler.postDelayed({
                        runCatching { lm.removeUpdates(listener) }
                    }, 15_000L)
                    lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                }
            } catch (e: SecurityException) {
                // Missing location permission — user-visible symptom: location never refreshes
                Diag.w(TAG, "requestFreshLocation($provider): no location permission")
            } catch (e: Throwable) {
                Diag.w(TAG, "requestFreshLocation($provider) failed", e)
            }
        }
    }
}
