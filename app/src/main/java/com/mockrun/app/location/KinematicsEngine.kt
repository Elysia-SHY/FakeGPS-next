package com.mockrun.app.location

import com.mockrun.app.domain.model.WayPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*
import kotlin.random.Random

/**
 * Advanced Kinematics & Physical Motion Dynamics Engine.
 *
 * Implements:
 * 1. Three-Point Circumradius Curvature Deceleration:
 *    Calculates path curvature κ = 1/R and limits speed via lateral acceleration:
 *    v_safe = min(v_cruise, sqrt(a_lateral_max * R))
 * 2. Continuous Gaussian Terrain Elevation:
 *    Ornstein-Uhlenbeck mean-reverting process for smooth, realistic road elevation profile.
 * 3. Dynamic Centripetal G-Force & Micro-Jitter Synthesis.
 */
@Singleton
class KinematicsEngine @Inject constructor() {

    companion object {
        const val EARTH_RADIUS_M = 6371000.0
        const val DEFAULT_LATERAL_ACCEL_MAX = 2.2 // m/s^2 (comfortable passenger/runner turn)
        const val MIN_CORNER_SPEED_MS = 1.2       // ~4.3 km/h minimum turn speed
        const val BASE_ALTITUDE_M = 24.0
        const val ALTITUDE_REVERSION_RATE = 0.04
        const val ALTITUDE_VOLATILITY = 0.45
    }

    private var currentAltitude: Double = BASE_ALTITUDE_M
    private val random = Random(System.currentTimeMillis())

    /**
     * Compute the circumradius R (meters) formed by three consecutive waypoints A -> B -> C.
     * Uses Heron's formula for triangle area:
     * Area = sqrt(s * (s - a) * (s - b) * (s - c))
     * R = (a * b * c) / (4 * Area)
     */
    fun calculateCircumradius(
        pA: WayPoint,
        pB: WayPoint,
        pC: WayPoint
    ): Double {
        val distAB = haversineMeters(pA.latitude, pA.longitude, pB.latitude, pB.longitude)
        val distBC = haversineMeters(pB.latitude, pB.longitude, pC.latitude, pC.longitude)
        val distCA = haversineMeters(pC.latitude, pC.longitude, pA.latitude, pA.longitude)

        // Degenerate checks
        if (distAB < 0.5 || distBC < 0.5 || distCA < 0.5) {
            return 10000.0 // Straight line approximation
        }

        val s = (distAB + distBC + distCA) / 2.0
        val areaSq = s * (s - distAB) * (s - distBC) * (s - distCA)
        if (areaSq <= 1e-4) {
            return 10000.0 // Collinear straight path
        }

        val area = sqrt(areaSq)
        val r = (distAB * distBC * distCA) / (4.0 * area)
        return r.coerceIn(2.0, 10000.0)
    }

    /**
     * Calculates the physics-compliant safe turning speed for a given corner radius.
     * v_safe = min(v_desired, sqrt(a_lateral_max * R))
     */
    fun computeCurvatureConstrainedSpeed(
        desiredSpeedMs: Double,
        circumradiusM: Double,
        lateralAccelMax: Double = DEFAULT_LATERAL_ACCEL_MAX
    ): Double {
        if (circumradiusM >= 500.0) {
            return desiredSpeedMs // Negligible curvature on gentle straights
        }
        val maxSafeSpeedMs = sqrt(lateralAccelMax * circumradiusM)
        return desiredSpeedMs.coerceAtMost(max(MIN_CORNER_SPEED_MS, maxSafeSpeedMs))
    }

    /**
     * Generates a continuous, smooth, realistic road elevation step using an Ornstein-Uhlenbeck process.
     * Prevents flat 20.0m altitude detection by anti-cheat systems while avoiding sudden jumps.
     */
    fun nextElevation(baseAltitude: Double = BASE_ALTITUDE_M, deltaDistanceMeters: Double = 2.0): Double {
        val dt = (deltaDistanceMeters / 10.0).coerceIn(0.1, 2.0)
        // Mean reversion toward base terrain elevation
        val drift = ALTITUDE_REVERSION_RATE * (baseAltitude - currentAltitude) * dt
        // Gaussian shock: Box-Muller transform
        val u1 = random.nextDouble().coerceAtLeast(1e-7)
        val u2 = random.nextDouble()
        val z = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
        val shock = ALTITUDE_VOLATILITY * sqrt(dt) * z

        currentAltitude += drift + shock
        // Bounded within natural local terrain (+-20m around base)
        currentAltitude = currentAltitude.coerceIn(baseAltitude - 18.0, baseAltitude + 22.0)
        return (currentAltitude * 10.0).roundToInt() / 10.0
    }

    /**
     * Reset elevation state to base level when a new simulation starts.
     */
    fun resetElevation(initialAlt: Double = BASE_ALTITUDE_M) {
        currentAltitude = if (initialAlt > 0) initialAlt else BASE_ALTITUDE_M
    }

    /**
     * Haversine distance in meters between two WGS-84 coordinates.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val a = sin(dLat / 2.0).pow(2) + cos(phi1) * cos(phi2) * sin(dLon / 2.0).pow(2)
        return EARTH_RADIUS_M * 2.0 * asin(sqrt(a))
    }
}
