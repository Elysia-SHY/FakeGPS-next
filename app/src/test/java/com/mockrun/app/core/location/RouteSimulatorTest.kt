package com.mockrun.app.core.location

import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.RouteMode
import com.mockrun.app.domain.model.WayPoint
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 三种巡航模式的状态机回归；虚拟调度器不会真的等待 tick。 */
class RouteSimulatorTest {
    private val simulator = RouteSimulator(KinematicsEngine())

    @Test
    fun `ping pong restores reverse direction after process restart`() = runTest {
        val route = route(RouteMode.PING_PONG, WayPoint(0.0, 0.0), WayPoint(0.0, 0.001))
        val lap = route.totalDistanceMeters
        val points = simulator.simulateRoute(route, 10f, lap * 1.2).take(8).toList()

        assertTrue(points.first().bearing in 269f..271f)
        assertTrue(points.zipWithNext().all { (a, b) -> b.distanceTraveled >= a.distanceTraveled })
        assertTrue("进度必须处于反向圈的 20% 附近，实际=${points.first().progressPercent}", points.first().progressPercent in 0.15f..0.25f)
    }

    @Test
    fun `loop progress resets while total distance stays continuous`() = runTest {
        val route = route(
            RouteMode.LOOP,
            WayPoint(0.0, 0.0), WayPoint(0.0, 0.001), WayPoint(0.001, 0.001), WayPoint(0.0, 0.001)
        )
        val points = simulator.simulateRoute(route, 30f).take(180).toList()

        assertTrue(points.maxOf { it.lapIndex } >= 2)
        assertTrue(points.zipWithNext().all { (a, b) -> b.distanceTraveled >= a.distanceTraveled })
        assertTrue(points.zipWithNext().any { (a, b) -> b.lapIndex == a.lapIndex + 1 && b.progressPercent < a.progressPercent })
        assertTrue(points.zipWithNext().all { (a, b) -> jumpMeters(a.latitude, a.longitude, b.latitude, b.longitude) < 20.0 })
    }

    @Test
    fun `short one way route completes instead of being skipped`() = runTest {
        val route = route(RouteMode.ONE_WAY, WayPoint(0.0, 0.0), WayPoint(0.0, 0.000009))
        val points = simulator.simulateRoute(route, 10f).take(12).toList()

        assertTrue(points.size >= 2)
        assertTrue(points.last().isCompleted)
        assertEquals(1f, points.last().progressPercent, 1e-6f)
    }

    @Test
    fun `short ping pong route still runs and reverses`() = runTest {
        val route = route(RouteMode.PING_PONG, WayPoint(0.0, 0.0), WayPoint(0.0, 0.000009))
        val points = simulator.simulateRoute(route, 10f).take(24).toList()

        assertTrue("至少应采样到折返后的点，实际 ${points.size}", points.size >= 6)
        assertTrue(points.any { it.bearing in 89f..91f })
        assertTrue(points.any { it.bearing in 269f..271f })
        assertTrue(points.zipWithNext().all { (a, b) -> b.distanceTraveled >= a.distanceTraveled })
    }

    @Test
    fun `loop with large gap falls back to ping pong`() = runTest {
        val route = route(RouteMode.LOOP, WayPoint(0.0, 0.0), WayPoint(0.0, 0.001))
        val points = simulator.simulateRoute(route, 20f).take(60).toList()

        assertTrue(points.any { it.bearing in 269f..271f })
        assertTrue(points.zipWithNext().all { (a, b) -> jumpMeters(a.latitude, a.longitude, b.latitude, b.longitude) < 20.0 })
    }

    @Test
    fun `ping pong dwell emits exactly two stationary ticks`() = runTest {
        val route = route(RouteMode.PING_PONG, WayPoint(0.0, 0.0), WayPoint(0.0, 0.001))
        val points = simulator.simulateRoute(route, 20f).take(60).toList()
        val lapChanges = points.indices.filter { it > 0 && points[it].lapIndex > points[it - 1].lapIndex }
        assertTrue(lapChanges.isNotEmpty())
        val change = lapChanges.first()
        val dwell = points.subList(change, change + 2)
        assertEquals(2, dwell.size)
        assertTrue(dwell.all { it.latitude == points[change].latitude && it.longitude == points[change].longitude })
        assertTrue(points[change + 2].bearing in 269f..271f)
    }

    private fun route(mode: RouteMode, vararg waypoints: WayPoint) = Route(name = "测试", waypoints = waypoints.toList(), mode = mode)

    private fun jumpMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double) =
        Route.haversineDistance(WayPoint(aLat, aLon), WayPoint(bLat, bLon))
}
