package com.mockrun.app.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
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
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val candidateProviders = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )
    private val activeProviders = Collections.synchronizedSet(mutableSetOf<String>())

    private var isRegistered = false

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
            } catch (_: Exception) {}

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
            }

            activeProviders.add(provider)
            Log.d(TAG, "Successfully registered $provider test provider")
            true
        }.onFailure { e ->
            Log.w(TAG, "Failed to register $provider test provider: ${e.message}")
        }.getOrDefault(false)
    }

    @Synchronized
    fun unregister() {
        val allToClean = activeProviders.toSet() + candidateProviders + setOf("fused")
        for (p in allToClean) {
            runCatching {
                locationManager.setTestProviderEnabled(p, false)
                locationManager.removeTestProvider(p)
            }
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
            Log.w(TAG, "Ignored invalid coordinates: lat=$latitude, lon=$longitude")
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
                    val totalSats = kotlin.random.Random.nextInt(18, 25)
                    bundle.putInt("satellites", totalSats)
                    bundle.putInt("beidou_satellites", kotlin.random.Random.nextInt(8, 13))
                    bundle.putInt("gps_satellites", kotlin.random.Random.nextInt(8, 12))
                    this.extras = bundle
                }
                locationManager.setTestProviderLocation(p, loc)
            }.onFailure { e ->
                Log.w(TAG, "Inject into $p failed: ${e.message}")
            }
        }
    }
}
