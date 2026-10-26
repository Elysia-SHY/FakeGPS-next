package com.mockrun.app.domain.model

import java.io.Serializable
import kotlin.math.*

/**
 * A named GPS route consisting of an ordered list of WayPoints.
 */
data class Route(
    val id: Long = 0L,
    val name: String,
    val waypoints: List<WayPoint>,
    val createdAt: Long = System.currentTimeMillis(),
    /** 运行方式：单程 / 折返 / 环形。默认单程，保证旧数据与旧调用点行为不变。 */
    val mode: RouteMode = RouteMode.ONE_WAY,
    /** 由录制轨迹转成的路线会带上来源轨迹 id，列表里显示「轨迹」角标。 */
    val sourceTrackId: Long? = null
) : Serializable {
    val totalDistanceMeters: Double
        get() {
            if (waypoints.size < 2) return 0.0
            var total = 0.0
            for (i in 0 until waypoints.size - 1) {
                total += haversineDistance(waypoints[i], waypoints[i + 1])
            }
            return total
        }

    val totalDistanceKm: Double get() = totalDistanceMeters / 1000.0

    /** 首尾缺口（米）。环形模式用它判断能不能闭合。 */
    val closingGapMeters: Double
        get() = if (waypoints.size < 2) 0.0 else haversineDistance(waypoints.first(), waypoints.last())

    companion object {
        fun haversineDistance(p1: WayPoint, p2: WayPoint): Double {
            val R = 6371000.0
            val lat1 = Math.toRadians(p1.latitude)
            val lat2 = Math.toRadians(p2.latitude)
            val dLat = Math.toRadians(p2.latitude - p1.latitude)
            val dLon = Math.toRadians(p2.longitude - p1.longitude)
            val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
            return R * 2 * asin(sqrt(a))
        }
    }
}
