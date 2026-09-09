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
    val createdAt: Long = System.currentTimeMillis()
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