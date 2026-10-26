package com.mockrun.app.core.location

import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.RouteMode
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
 * 1. Build cumulative-distance segments from waypoints (Haversine); 环形模式先补上闭环段。
 * 2. Advance distance each tick (default 1 s), 以「绝对距离」记进度，跨轮不清零。
 * 3. Linearly interpolate lat/lon/alt between the surrounding segment endpoints.
 * 4. Add Box-Muller Gaussian noise (~0.3 m sigma) to mimic real GPS jitter.
 * 5. Vary speed with an Ornstein-Uhlenbeck pacing wave + micro jitter.
 *
 * 三种运行方式：
 * - [RouteMode.ONE_WAY]   走到终点停车并发射 `isCompleted`；
 * - [RouteMode.PING_PONG] 到端点原地掉头（2 tick 停顿 + 显式减速 ramp），永不自动结束；
 * - [RouteMode.LOOP]      首尾闭合绕圈，接缝处连续、不减速、不停顿。
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

        /** 环形模式允许的最大首尾缺口；超过即降级为折返，避免每圈「瞬移回起点」。 */
        const val LOOP_MAX_GAP_M = 300.0
        /** 首尾距离小于这个值就认为已经闭合，不再补段。 */
        private const val LOOP_CLOSE_EPS_M = 0.3
        /** 掉头停顿的 tick 数（1 s / tick）。 */
        private const val DWELL_TICKS = 2
        private const val DWELL_SPEED_MS = 0.12f
        /** 停顿期间把噪声压小，避免原地回环自交。 */
        private const val DWELL_SIGMA_M = 0.05
    }

    /**
     * @param startDistanceMeters 从路线第几米开始（绝对距离，跨轮累计；0 表示起点）
     * @param mode 运行方式，默认取路线上保存的值
     */
    fun simulateRoute(
        route: Route,
        speedKmh: Float,
        startDistanceMeters: Double = 0.0,
        mode: RouteMode = route.mode
    ): Flow<SimulatedPoint> = flow {
        // 不用 require()：该异常会在 MockLocationService.serviceScope.launch 内抛出，
        // 而那个作用域没有 CoroutineExceptionHandler，未捕获异常会直接崩掉进程。
        if (route.waypoints.size < 2) {
            Diag.w(TAG, "simulateRoute 收到 ${route.waypoints.size} 个航点（需 >= 2），已跳过本次模拟")
            return@flow
        }

        val gap = haversine(route.waypoints.last(), route.waypoints.first())
        val effectiveMode = if (mode == RouteMode.LOOP && gap > LOOP_MAX_GAP_M) {
            Diag.w(TAG, "首尾缺口 ${gap.toInt()} m 超过 ${LOOP_MAX_GAP_M.toInt()} m，环形降级为折返")
            RouteMode.PING_PONG
        } else {
            mode
        }

        var lapPoints = buildLapWaypoints(route.waypoints, effectiveMode)
        var segments = buildSegments(lapPoints)
        var lapDistance = segments.lastOrNull()?.endCumulative ?: 0.0
        if (lapDistance <= 0.0) return@flow

        val baseSpeedMs = speedKmh * 1000.0 / 3600.0
        // 单圈太短会一 tick 跨两圈以上（40 km/h 时每 tick ≈ 11 m）
        val minLap = max(2.0, 2.0 * max(baseSpeedMs, 0.3) * (UPDATE_INTERVAL_MS / 1000.0))
        if (lapDistance < minLap) {
            Diag.w(TAG, "单圈 ${lapDistance} m 小于下限 $minLap m，已跳过本次模拟")
            return@flow
        }

        val initialAlt = route.waypoints.firstOrNull()?.altitude ?: 24.0
        kinematicsEngine.resetElevation(initialAlt)
        kinematicsEngine.resetSpeedDynamics()

        val start = startDistanceMeters.coerceAtLeast(0.0)
        val lapsPassed = floor(start / lapDistance).toInt()
        var lapIndex = lapsPassed
        var covered = (start - lapsPassed * lapDistance).coerceIn(0.0, lapDistance)
        var totalCovered = start
        var dwellTicks = 0

        var lastLat = lapPoints.first().latitude
        var lastLon = lapPoints.first().longitude
        var lastAlt = lapPoints.first().altitude
        var lastBearing = 0f

        // 单程且已经跑过终点：直接收尾，别进去空转
        if (effectiveMode == RouteMode.ONE_WAY && start >= lapDistance) {
            val last = route.waypoints.last()
            emit(
                SimulatedPoint(
                    latitude = last.latitude, longitude = last.longitude, altitude = last.altitude,
                    bearing = 0f, speed = 0f, progressPercent = 1f,
                    distanceTraveled = lapDistance, isCompleted = true, lapIndex = lapIndex
                )
            )
            return@flow
        }

        while (true) {
            if (dwellTicks > 0) {
                // 掉头停顿：位置不动、速度接近 0。不能走 nextDynamicSpeed()，它出口有 coerceAtLeast(0.3)
                emit(
                    SimulatedPoint(
                        latitude = lastLat, longitude = lastLon, altitude = lastAlt,
                        bearing = lastBearing, speed = DWELL_SPEED_MS,
                        progressPercent = 1f, distanceTraveled = totalCovered,
                        isCompleted = false, lapIndex = lapIndex
                    )
                )
                dwellTicks--
                delay(UPDATE_INTERVAL_MS)
                continue
            }

            val pt = interpolateAt(segments, covered)

            // 端点显式减速 ramp：共线三点算不出曲率（外接圆退化 10000 m），掉头只能显式压速
            val rampDist = max(3.0, baseSpeedMs * 2.0)
            val remaining = lapDistance - covered
            val cruiseMs =
                if (effectiveMode != RouteMode.LOOP && remaining < rampDist) {
                    max(KinematicsEngine.MIN_CORNER_SPEED_MS, baseSpeedMs * (remaining / rampDist))
                } else {
                    baseSpeedMs
                }

            // Lookahead curvature evaluation for physical corner deceleration
            val laNear = lookaheadDistance(covered + max(5.0, baseSpeedMs * 1.5), lapDistance, effectiveMode)
            val laFar = lookaheadDistance(covered + max(12.0, baseSpeedMs * 3.0), lapDistance, effectiveMode)
            val ptNext = interpolateAt(segments, laNear)
            val ptFar = interpolateAt(segments, laFar)

            val radius = kinematicsEngine.calculateCircumradius(
                WayPoint(pt.lat, pt.lon),
                WayPoint(ptNext.lat, ptNext.lon),
                WayPoint(ptFar.lat, ptFar.lon)
            )
            val safeSpeedMs = kinematicsEngine.computeCurvatureConstrainedSpeed(cruiseMs, radius)
            val currentSpeedMs = kinematicsEngine.nextDynamicSpeed(safeSpeedMs)

            // 每 tick 距离必须有下限：取消它会导致 covered 永不推进、while 死循环、24h WakeLock 不释放
            val distThisTick = (currentSpeedMs * (UPDATE_INTERVAL_MS / 1000.0)).coerceAtLeast(0.3)
            val dynAlt = kinematicsEngine.nextElevation(baseAltitude = pt.alt, deltaDistanceMeters = distThisTick)

            val noise = gaussianNoise2D(if (dwellTicks > 0) DWELL_SIGMA_M else GPS_NOISE_SIGMA_M)
            val noiseLat = noise.first / EARTH_RADIUS_M * (180.0 / PI)
            val noiseLon = noise.second / (EARTH_RADIUS_M * cos(Math.toRadians(pt.lat))) * (180.0 / PI)

            lastLat = pt.lat + noiseLat
            lastLon = pt.lon + noiseLon
            lastAlt = dynAlt
            lastBearing = pt.bearing

            emit(
                SimulatedPoint(
                    latitude = lastLat,
                    longitude = lastLon,
                    altitude = dynAlt,
                    bearing = pt.bearing,
                    speed = currentSpeedMs.toFloat(),
                    progressPercent = (covered / lapDistance).toFloat(),
                    distanceTraveled = totalCovered,
                    isCompleted = false,
                    lapIndex = lapIndex
                )
            )

            delay(UPDATE_INTERVAL_MS)
            covered += distThisTick
            totalCovered += distThisTick

            // 必须是 while：高速下一 tick 可能跨过整圈
            while (covered >= lapDistance) {
                when (effectiveMode) {
                    RouteMode.ONE_WAY -> {
                        val last = route.waypoints.last()
                        emit(
                            SimulatedPoint(
                                latitude = last.latitude, longitude = last.longitude, altitude = last.altitude,
                                bearing = 0f, speed = 0f, progressPercent = 1f,
                                distanceTraveled = totalCovered, isCompleted = true, lapIndex = lapIndex
                            )
                        )
                        return@flow
                    }
                    RouteMode.PING_PONG -> {
                        lapPoints = lapPoints.reversed()
                        segments = buildSegments(lapPoints)
                        lapIndex++
                        covered -= lapDistance
                        dwellTicks = DWELL_TICKS
                    }
                    RouteMode.LOOP -> {
                        lapIndex++
                        covered -= lapDistance
                    }
                }
            }
        }
    }

    // ---- Private helpers ----

    /** 环形模式补上闭合段；折返/单程直接用原始航点。 */
    private fun buildLapWaypoints(wps: List<WayPoint>, mode: RouteMode): List<WayPoint> {
        if (mode != RouteMode.LOOP || wps.size < 2) return wps
        val gap = haversine(wps.last(), wps.first())
        return if (gap <= LOOP_CLOSE_EPS_M) wps else wps + wps.first()
    }

    /** 环形模式对圈长取模，让 lookahead 落进下一圈；其余模式夹到终点。 */
    private fun lookaheadDistance(raw: Double, lapDistance: Double, mode: RouteMode): Double =
        if (mode == RouteMode.LOOP) raw % lapDistance else raw.coerceAtMost(lapDistance)

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
    private fun gaussianNoise2D(sigma: Double = GPS_NOISE_SIGMA_M): Pair<Double, Double> {
        val u1 = kotlin.random.Random.nextDouble().coerceAtLeast(1e-10)
        val u2 = kotlin.random.Random.nextDouble()
        val mag = sigma * sqrt(-2.0 * ln(u1))
        val theta = 2.0 * PI * u2
        return (mag * cos(theta)) to (mag * sin(theta))
    }
}
