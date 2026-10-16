package com.mockrun.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route 与 MultiTargetRule 的回归测试。
 *
 * 这两处是纯计算，不依赖 Android 框架，可在 JVM 上直接验证。
 * 重点覆盖边界：空路线、单点路线、重复点、跨经度换日线。
 */
class RouteTest {

    private fun wp(lat: Double, lon: Double, alt: Double = 0.0) = WayPoint(lat, lon, alt)

    // ------------------------------------------------------------ haversineDistance

    @Test
    fun `distance to self is zero`() {
        val p = wp(39.9042, 116.4074)
        assertEquals(0.0, Route.haversineDistance(p, p), 1e-6)
    }

    @Test
    fun `distance is symmetric`() {
        val a = wp(39.9042, 116.4074)
        val b = wp(31.2304, 121.4737)
        assertEquals(
            Route.haversineDistance(a, b),
            Route.haversineDistance(b, a),
            1e-6
        )
    }

    @Test
    fun `one degree latitude is about 111 km`() {
        val d = Route.haversineDistance(wp(0.0, 0.0), wp(1.0, 0.0))
        assertTrue("实测 $d m", d in 111_100.0..111_400.0)
    }

    @Test
    fun `longitude degrees shrink with latitude`() {
        // 赤道 1° 经度 ≈ 111.3 km；北纬 60° 处约减半
        val atEquator = Route.haversineDistance(wp(0.0, 0.0), wp(0.0, 1.0))
        val at60 = Route.haversineDistance(wp(60.0, 0.0), wp(60.0, 1.0))
        assertEquals(0.5, at60 / atEquator, 0.01)
    }

    @Test
    fun `antipodal-ish long distance stays finite`() {
        // 跨换日线：经度 179 -> -179 只差 2°，不应算成 358°
        val d = Route.haversineDistance(wp(0.0, 179.0), wp(0.0, -179.0))
        assertTrue("实测 $d m，应约 222 km 而非 39800 km", d in 220_000.0..224_000.0)
    }

    @Test
    fun `identical points across date line give zero`() {
        val d = Route.haversineDistance(wp(10.0, 180.0), wp(10.0, 180.0))
        assertEquals(0.0, d, 1e-6)
    }

    @Test
    fun `altitude does not affect horizontal distance`() {
        val flat = Route.haversineDistance(wp(39.9, 116.4), wp(39.91, 116.41))
        val hilly = Route.haversineDistance(wp(39.9, 116.4, 0.0), wp(39.91, 116.41, 5000.0))
        assertEquals(flat, hilly, 1e-9)
    }

    // ------------------------------------------------------------ totalDistanceMeters

    @Test
    fun `empty route has zero distance`() {
        assertEquals(0.0, Route(1L, "empty", emptyList()).totalDistanceMeters, 1e-9)
    }

    @Test
    fun `single point route has zero distance`() {
        assertEquals(0.0, Route(1L, "one", listOf(wp(39.9, 116.4))).totalDistanceMeters, 1e-9)
    }

    @Test
    fun `two point route equals direct distance`() {
        val a = wp(39.9042, 116.4074)
        val b = wp(39.9142, 116.4074)
        val route = Route(1L, "two", listOf(a, b))
        assertEquals(Route.haversineDistance(a, b), route.totalDistanceMeters, 1e-6)
    }

    @Test
    fun `multi segment route sums each leg`() {
        val a = wp(39.9000, 116.4000)
        val b = wp(39.9100, 116.4000)
        val c = wp(39.9100, 116.4200)
        val route = Route(1L, "three", listOf(a, b, c))
        val expected = Route.haversineDistance(a, b) + Route.haversineDistance(b, c)
        assertEquals(expected, route.totalDistanceMeters, 1e-6)
    }

    @Test
    fun `route with repeated points adds no distance`() {
        val a = wp(39.9, 116.4)
        val route = Route(1L, "dup", listOf(a, a, a))
        assertEquals(0.0, route.totalDistanceMeters, 1e-6)
    }

    @Test
    fun `totalDistanceKm is meters divided by 1000`() {
        val a = wp(0.0, 0.0)
        val b = wp(1.0, 0.0)
        val route = Route(1L, "km", listOf(a, b))
        assertEquals(route.totalDistanceMeters / 1000.0, route.totalDistanceKm, 1e-9)
    }

    @Test
    fun `beijing to shanghai is about 1067 km`() {
        val route = Route(1L, "bj-sh", listOf(wp(39.9042, 116.4074), wp(31.2304, 121.4737)))
        assertTrue("实测 ${route.totalDistanceKm} km", route.totalDistanceKm in 1055.0..1080.0)
    }

    // ------------------------------------------------------------ MultiTargetRule

    @Test
    fun `key combines package and user id`() {
        val r = MultiTargetRule(packageName = "com.foo.bar", userId = 0, appName = "Foo")
        assertEquals("com.foo.bar_0", r.key)
    }

    @Test
    fun `cloned instance uses different key from primary`() {
        val primary = MultiTargetRule("com.foo.bar", 0, "Foo")
        val clone = MultiTargetRule("com.foo.bar", 999, "Foo (分身)")
        assertTrue("分身与主用户的 key 必须不同，否则规则会互相覆盖", primary.key != clone.key)
        assertTrue(clone.isCloned)
        assertTrue(!primary.isCloned)
    }

    @Test
    fun `defaults are beijing and stationary`() {
        val r = MultiTargetRule("com.foo", 0, "Foo")
        assertEquals(39.9042, r.latitude, 1e-9)
        assertEquals(116.4074, r.longitude, 1e-9)
        assertEquals(TargetMockMode.STATIONARY, r.mode)
        assertTrue(r.isEnabled)
    }

    @Test
    fun `target mock mode has exactly three values`() {
        // 该枚举参与 system_server 侧的 JSON 解析（按 name 字符串匹配），
        // 增减取值都会影响 hook 侧，故固定住数量
        assertEquals(3, TargetMockMode.entries.size)
        assertEquals(
            listOf("STATIONARY", "ROUTE", "REAL_PASSTHROUGH"),
            TargetMockMode.entries.map { it.name }
        )
    }
}
