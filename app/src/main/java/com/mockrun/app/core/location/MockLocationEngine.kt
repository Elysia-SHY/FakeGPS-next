package com.mockrun.app.core.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import java.util.Collections

/**
 * High-reliability multi-provider mock location engine.
 *
 * Mocks GPS, NETWORK, PASSIVE, and FUSED providers simultaneously to prevent
 * Android OEM ROMs from reverting to real physical location via Wi-Fi/Cellular scans.
 */
class MockLocationEngine(private val context: Context) {

    companion object {
        private const val TAG = "MockLocationEngine"

        // Provider power and accuracy constants matching system standards without deprecated Criteria warnings
        private const val POWER_LOW = 1
        private const val ACCURACY_FINE = 1
        private const val ACCURACY_COARSE = 2

        fun forceCleanAllTestProviders(context: Context) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
                "fused"
            )
            val fusedS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(LocationManager.FUSED_PROVIDER)
            } else emptyList()
            (providers + fusedS).distinct().forEach { p ->
                runCatching { lm.removeTestProvider(p) }
                    .logFailure(TAG, "pre-clean removeTestProvider($p)", Diag.Level.DEBUG)
            }
        }
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val candidateProviders = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )
    // 用 ConcurrentHashMap.newKeySet() 而非 Collections.synchronizedSet：
    // inject() 没有 @Synchronized，在第 198 行对集合做 toList()（即迭代），
    // 而 register()/unregister() 是 @Synchronized 且会被 inject() 自身递归调用、
    // 也会被 MockLocationService / FloatingJoystickService 从其它线程调用。
    // synchronizedSet 的迭代器不受其内部锁保护，会导致
    // ConcurrentModificationException（被 logFailure 吞掉 → 该 tick 静默不注入）
    // 或读到不一致的 provider 列表。并发集合可安全迭代。
    private val activeProviders: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    private var isRegistered = false

    fun isRegistered(): Boolean = isRegistered

    @Synchronized
    fun register(): Boolean {
        activeProviders.clear()

        // 1. GPS Provider
        registerTestProvider(
            provider = LocationManager.GPS_PROVIDER,
            requiresNetwork = false,
            requiresSatellite = true,
            requiresCell = false,
            hasMonetaryCost = false,
            supportsAltitude = true,
            supportsSpeed = true,
            supportsBearing = true,
            powerRequirement = POWER_LOW,
            accuracy = ACCURACY_FINE
        )

        // 2. Network Provider
        registerTestProvider(
            provider = LocationManager.NETWORK_PROVIDER,
            requiresNetwork = true,
            requiresSatellite = false,
            requiresCell = true,
            hasMonetaryCost = false,
            supportsAltitude = false,
            supportsSpeed = false,
            supportsBearing = false,
            powerRequirement = POWER_LOW,
            accuracy = ACCURACY_COARSE
        )

        // 3. Fused Provider (Android 12+ & GMS)
        val fusedProvider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocationManager.FUSED_PROVIDER
        } else {
            "fused"
        }
        registerTestProvider(
            provider = fusedProvider,
            requiresNetwork = true,
            requiresSatellite = true,
            requiresCell = true,
            hasMonetaryCost = false,
            supportsAltitude = true,
            supportsSpeed = true,
            supportsBearing = true,
            powerRequirement = POWER_LOW,
            accuracy = ACCURACY_FINE
        )

        // Registration succeeds if GPS or at least one provider was successfully added
        isRegistered = activeProviders.contains(LocationManager.GPS_PROVIDER) || activeProviders.isNotEmpty()
        return isRegistered
    }

    private fun registerTestProvider(
        provider: String,
        requiresNetwork: Boolean,
        requiresSatellite: Boolean,
        requiresCell: Boolean,
        hasMonetaryCost: Boolean,
        supportsAltitude: Boolean,
        supportsSpeed: Boolean,
        supportsBearing: Boolean,
        powerRequirement: Int,
        accuracy: Int
    ): Boolean {
        return runCatching {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                // Expected when the provider was never registered; the add below is the real step.
                Diag.d(TAG, "pre-clean of $provider skipped: ${e.javaClass.simpleName}")
            }

            locationManager.addTestProvider(
                provider,
                requiresNetwork,
                requiresSatellite,
                requiresCell,
                hasMonetaryCost,
                supportsAltitude,
                supportsSpeed,
                supportsBearing,
                powerRequirement,
                accuracy
            )
            locationManager.setTestProviderEnabled(provider, true)

            @Suppress("DEPRECATION")
            runCatching {
                locationManager.setTestProviderStatus(
                    provider,
                    android.location.LocationProvider.AVAILABLE,
                    null,
                    System.currentTimeMillis()
                )
            }.logFailure(TAG, "setTestProviderStatus($provider)", Diag.Level.DEBUG)

            activeProviders.add(provider)
            Diag.d(TAG, "Successfully registered $provider test provider")
            true
        }.logFailure(TAG, "registerTestProvider($provider)")
            .getOrDefault(false)
    }

    @Synchronized
    fun unregister() {
        val allToClean = (activeProviders.toSet() + candidateProviders + setOf("fused", LocationManager.PASSIVE_PROVIDER)).distinct()
        for (p in allToClean) {
            runCatching {
                locationManager.removeTestProvider(p)
            }.logFailure(TAG, "unregister removeTestProvider($p)", Diag.Level.DEBUG)
        }
        activeProviders.clear()
        isRegistered = false
    }

    fun inject(
        latitude: Double,
        longitude: Double,
        altitude: Double = 20.0,
        speedMps: Float = 0f,
        bearingDeg: Float = 0f,
        accuracyM: Float = 1.0f
    ) {
        // Boundary check: skip invalid coordinates
        if (latitude.isNaN() || longitude.isNaN() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            Diag.w(TAG, "Ignored invalid coordinates: lat=$latitude, lon=$longitude")
            return
        }

        if (!isRegistered || activeProviders.isEmpty()) {
            if (!register()) return
        }

        val now = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()
        val targets = activeProviders.toList()

        for (p in targets) {
            runCatching {
                val loc = Location(p).apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    this.altitude = altitude
                    this.speed = speedMps
                    this.bearing = bearingDeg
                    val dynamicAccuracy = accuracyM + (kotlin.random.Random.nextFloat() * 0.4f - 0.2f)
                    this.accuracy = dynamicAccuracy.coerceAtLeast(1.2f)
                    this.time = now
                    this.elapsedRealtimeNanos = elapsedNanos
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        this.bearingAccuracyDegrees = 0.2f
                        this.verticalAccuracyMeters = 0.3f
                        this.speedAccuracyMetersPerSecond = 0.1f
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        this.elapsedRealtimeUncertaintyNanos = 0.0
                    }
                    val bundle = Bundle()
                    // 只写 AOSP 真实存在的 extras key。
                    // GnssLocationProvider.java:279 -> extras.putInt("satellites", mSvCount)
                    bundle.putInt("satellites", kotlin.random.Random.nextInt(18, 25))
                    // 删除以下内容，它们都是指纹而非伪装：
                    //   bundle.putInt("beidou_satellites", ...)  —— AOSP 不认这个 key
                    //   bundle.putInt("gps_satellites", ...)     —— AOSP 不认这个 key
                    //   bundle.remove("mockLocation") / putBoolean("mockLocation", false)
                    //      —— "mockLocation" 这个 key 在 AOSP 全版本里都不存在（已 grep 验证），
                    //         主动塞进去等于挂了一块「这里有人动过手」的牌子。
                    this.extras = bundle

                    // 不再尝试清 mIsFromMockProvider 字段。
                    //
                    // minSdk = 29，而该字段在 Android 10 (API 29) 已被 mFieldsMask 取代
                    // （Android 9 及以下才是真实字段），所以这行在全部受支持机型上都会抛
                    // NoSuchFieldException 并被 runCatching 吞掉 —— 从未执行过。
                    //
                    // 真正清 flag 的是系统侧 MockProvider.setProviderLocation 里的
                    // setIsFromMockProvider(true)（AOSP MockProvider.java:55），
                    // 由 XposedLocationHook.createSpoofedLocation 覆写。
                    // App 侧改这个字段没有任何作用。
                }
                locationManager.setTestProviderLocation(p, loc)
            }.logFailure(TAG, "inject into $p")
        }
    }
}
