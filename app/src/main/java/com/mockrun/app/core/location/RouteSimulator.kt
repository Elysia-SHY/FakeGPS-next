package com.mockrun.app.core.location

import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.SimulatedPoint
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.util.Diag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Core interpolation engine. Converts a [Route] + speed into a cold [Flow] of [SimulatedPoint].
 *
 * Algorithm:
 * 1. Build cumulative-distance segments from waypoints (Haversine).
 * 2. Advance [distancePerInterval] meters each tick (default 1 s).
 * 3. Linearly interpolate lat/lon/alt between the surrounding segment endpoints.
 * 4. Add Box-Muller Gaussian noise (~0.3 m sigma) to mimic real GPS jitter.
 * 5. Vary speed ±5 % each tick for realism.
 */
@Singleton
class RouteSimulator @Inject constructor(
    private val kinematicsEngine: KinematicsEngine
) {

    companion object {
        private const val TAG = "RouteSimulator"
        const val UPDATE_INTERVAL_MS = 1000L   // position update cadence
        const val EARTH_RADIUS_M = 6371000.0
        const val GPS_NOISE_SIGMA_M = 0.3      // ~0.3 m standard deviation
    }

    fun simulateRoute(
        route: Route,
        speedKmh: Float,
        startProgress: Float = 0f
    ): Flow<SimulatedPoint> = flow {
        // 不用 require()：该异常会在 MockLocationService.serviceScope.launch 内抛出，
        // 而那个作用域没有 CoroutineExceptionHandler，未捕获异常会直接崩掉进程。
        // 调用方（UI 层）虽然已加了按钮可用性判断，但模拟也可能由
        // ADB 广播、路线删除竞态等路径触发，这里必须自己兜住 ——
        // 返回空 flow 让模拟静默结束，比崩溃好。
        if (route.waypoints.size < 2) {
            Diag.w(TAG, "simulateRoute 收到 ${route.waypoints.size} 个航点（需 >= 2），已跳过本次模拟")
            return@flow
        }

        val segments = buildSegments(route.waypoints)
        val totalDistance = segments.last().endCumulative
        if (totalDistance <= 0.0) return@flow

        val baseSpeedMs = speedKmh * 1000.0 / 3600.0
        var covered = totalDistance * startProgress.toDouble()
        val initialAlt = route.waypoints.firstOrNull()?.altitude ?: 24.0
        kinematicsEngine.resetElevation(initialAlt)
        kinematicsEngine.resetSpeedDynamics()

        while (covered < totalDistance) {
            val pt = interpolateAt(segments, covered)

            // Lookahead curvature evaluation for physical corner deceleration
            val lookahead1 = (covered + max(5.0, baseSpeedMs * 1.5)).coerceAtMost(totalDistance)
            val lookahead2 = (covered + max(12.0, baseSpeedMs * 3.0)).coerceAtMost(totalDistance)
            val ptNext = interpolateAt(segments, lookahead1)
            val ptFar = interpolateAt(segments, lookahead2)

            val radius = kinematicsEngine.calculateCircumradius(
                WayPoint(pt.lat, pt.lon),
                WayPoint(ptNext.lat, ptNext.lon),
                WayPoint(ptFar.lat, ptFar.lon)
            )
            val safeSpeedMs = kinematicsEngine.computeCurvatureConstrainedSpeed(baseSpeedMs, radius)

            // Dynamic realistic speed combining macro pacing wave (±12%) and micro stride jitter (±3%)
            val currentSpeedMs = kinematicsEngine.nextDynamicSpeed(safeSpeedMs)
            // 每 tick 距离必须有下限。
            // nextDynamicSpeed 对 baseSpeedMs <= 0.1 直接返回 0.0（即速度 ≤ 0.36 km/h），
            // 那样 distThisTick = 0，covered 永不推进，外层 while 永不退出：
            // 每秒发射一个完全相同的点，isCompleted 永不触发，前台服务永不结束，
            // 24 小时 PARTIAL_WAKE_LOCK 一直持有。
            // 触发路径是 AdbCommandReceiver 文档里写明的
            // `am broadcast -a com.mockrun.app.ACTION_SET_SPEED --ef extra_speed 0`。
            val distThisTick = (currentSpeedMs * (UPDATE_INTERVAL_MS / 1000.0)).coerceAtLeast(0.3)

            val dynAlt = kinematicsEngine.nextElevation(baseAltitude = pt.alt, deltaDistanceMeters = distThisTick)

            val noise = gaussianNoise2D()

            // Convert meter-level noise to degree offsets
            val noiseLat = noise.first / EARTH_RADIUS_M * (180.0 / PI)
            val noiseLon = noise.second / (EARTH_RADIUS_M * cos(Math.toRadians(pt.lat))) * (180.0 / PI)

            emit(SimulatedPoint(
                latitude  = pt.lat + noiseLat,
                longitude = pt.lon + noiseLon,
                altitude  = dynAlt,
                bearing   = pt.bearing,
                speed     = currentSpeedMs.toFloat(),
                progressPercent = (covered / totalDistance).toFloat(),
                distanceTraveled = covered
            ))

            delay(UPDATE_INTERVAL_MS)
            covered += distThisTick
        }

        // Final point exactly at destination
        val last = route.waypoints.last()
        emit(SimulatedPoint(
            latitude = last.latitude, longitude = last.longitude, altitude = last.altitude,
            bearing = 0f, speed = 0f,
            progressPercent = 1f, distanceTraveled = totalDistance,
            isCompleted = true
        ))
    }

    // ---- Private helpers ----

    private data class Segment(
        val start: WayPoint, val end: WayPoint,
        val distance: Double,       // segment length in meters
        val endCumulative: Double,  // cumulative distance at segment end
        val bearing: Float
    )

    private data class InterpPoint(val lat: Double, val lon: Double, val alt: Double, val bearing: Float)

    private fun buildSegments(wps: List<WayPoint>): List<Segment> {
        var cum = 0.0
        return wps.zipWithNext().map { (a, b) ->
            val d = haversine(a, b)
            cum += d
            Segment(a, b, d, cum, bearing(a, b))
        }
    }

    private fun interpolateAt(segments: List<Segment>, dist: Double): InterpPoint {
        val seg = segments.firstOrNull { it.endCumulative >= dist } ?: segments.last()
        val segStart = seg.endCumulative - seg.distance
        val t = if (seg.distance == 0.0) 0.0 else ((dist - segStart) / seg.distance).coerceIn(0.0, 1.0)
        return InterpPoint(
            lat = seg.start.latitude  + t * (seg.end.latitude  - seg.start.latitude),
            lon = seg.start.longitude + t * (seg.end.longitude - seg.start.longitude),
            alt = seg.start.altitude  + t * (seg.end.altitude  - seg.start.altitude),
            bearing = seg.bearing
        )
    }

    private fun haversine(a: WayPoint, b: WayPoint): Double {
        val dLat = Math.toRadians(b.latitude  - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val x = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_M * 2 * asin(sqrt(x))
    }

    private fun bearing(a: WayPoint, b: WayPoint): Float {
        val lat1 = Math.toRadians(a.latitude);  val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val x = sin(dLon) * cos(lat2)
        val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return ((Math.toDegrees(atan2(x, y)) + 360) % 360).toFloat()
    }

    /** Box-Muller transform for 2-D Gaussian noise (returns meters). */
    private fun gaussianNoise2D(): Pair<Double, Double> {
        val u1 = kotlin.random.Random.nextDouble().coerceAtLeast(1e-10)
        val u2 = kotlin.random.Random.nextDouble()
        val mag = GPS_NOISE_SIGMA_M * sqrt(-2.0 * ln(u1))
        val theta = 2.0 * PI * u2
        return (mag * cos(theta)) to (mag * sin(theta))
    }
}
