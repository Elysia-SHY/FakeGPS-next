package com.mockrun.app.core.location

import com.mockrun.app.domain.model.WayPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 运动学引擎的回归测试。
 *
 * 这些是不依赖 Android 框架的纯计算，覆盖几何退化、曲率限速与哈弗辛距离。
 */
class KinematicsEngineTest {

    private fun wp(lat: Double, lon: Double) = WayPoint(lat, lon)

    // ------------------------------------------------------------ haversine

    @Test
    fun `haversine returns zero for identical points`() {
        val e = KinematicsEngine()
        assertEquals(0.0, e.haversineMeters(39.9042, 116.4074, 39.9042, 116.4074), 1e-6)
    }

    @Test
    fun `haversine matches known distance beijing to shanghai`() {
        val e = KinematicsEngine()
        // 北京天安门 -> 上海人民广场，真实大圆距离约 1067 km
        val d = e.haversineMeters(39.9042, 116.4074, 31.2304, 121.4737)
        assertTrue("实测 $d m，应在 1055–1080 km", d in 1_055_000.0..1_080_000.0)
    }

    @Test
    fun `haversine is symmetric`() {
        val e = KinematicsEngine()
        val a = e.haversineMeters(39.9042, 116.4074, 31.2304, 121.4737)
        val b = e.haversineMeters(31.2304, 121.4737, 39.9042, 116.4074)
        assertEquals(a, b, 1e-6)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        val e = KinematicsEngine()
        val d = e.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue("实测 $d m，应在 111.1–111.4 km", d in 111_100.0..111_400.0)
    }

    // ------------------------------------------------------------ circumradius

    @Test
    fun `circumradius of collinear points falls back to straight approximation`() {
        val e = KinematicsEngine()
        // 三点严格共线
        val r = e.calculateCircumradius(
            wp(39.9000, 116.4000),
            wp(39.9010, 116.4000),
            wp(39.9020, 116.4000)
        )
        assertEquals(10000.0, r, 1e-6)
    }

    @Test
    fun `circumradius of degenerate tiny triangle falls back`() {
        val e = KinematicsEngine()
        // 三点几乎重合，任一边 < 0.5 m
        val r = e.calculateCircumradius(
            wp(39.900000, 116.400000),
            wp(39.900001, 116.400001),
            wp(39.900002, 116.400002)
        )
        assertEquals(10000.0, r, 1e-6)
    }

    @Test
    fun `circumradius of right angle triangle matches analytic value`() {
        val e = KinematicsEngine()
        // 等腰直角三角形，直角边 100 m，斜边 100√2 m
        // 外接圆半径 = 斜边 / 2 = 50√2 ≈ 70.71 m
        val r = e.calculateCircumradius(
            wp(39.900000, 116.400000),
            wp(39.900899, 116.400000),   // 正北约 100 m
            wp(39.900000, 116.401176)    // 正东约 100 m
        )
        // 因经纬度换算不是严格等距，允许 5 m 误差
        assertTrue("实测 R=$r，应接近 70.7 m", r in 65.0..76.0)
    }

    @Test
    fun `circumradius is clamped to upper bound`() {
        val e = KinematicsEngine()
        val r = e.calculateCircumradius(
            wp(0.0, 0.0),
            wp(10.0, 10.0),
            wp(20.0, 20.1)
        )
        assertTrue("R 必须被夹在 10000 以内", r <= 10000.0)
    }

    // ------------------------------------------------------------ speed limiting

    @Test
    fun `gentle curvature does not limit speed`() {
        val e = KinematicsEngine()
        // R >= 500 m 时视为直道
        assertEquals(15.0, e.computeCurvatureConstrainedSpeed(15.0, 800.0), 1e-9)
        assertEquals(15.0, e.computeCurvatureConstrainedSpeed(15.0, 500.0), 1e-9)
    }

    @Test
    fun `tight corner limits speed by lateral acceleration`() {
        val e = KinematicsEngine()
        // R = 10 m, a = 2.2 m/s^2 -> v = sqrt(22) ≈ 4.69 m/s
        val v = e.computeCurvatureConstrainedSpeed(15.0, 10.0)
        assertEquals(4.69, v, 0.05)
    }

    @Test
    fun `extremely tight corner never drops below minimum corner speed`() {
        val e = KinematicsEngine()
        // R 极小：sqrt(2.2*0.01) ≈ 0.148，但下限是 MIN_CORNER_SPEED_MS = 1.2
        val v = e.computeCurvatureConstrainedSpeed(15.0, 0.01)
        assertEquals(KinematicsEngine.MIN_CORNER_SPEED_MS, v, 1e-9)
    }

    @Test
    fun `speed is never raised above desired`() {
        val e = KinematicsEngine()
        // 期望速度本身低于安全速度时，不应被抬高
        val v = e.computeCurvatureConstrainedSpeed(1.0, 100.0)
        assertTrue("v=$v 不应超过期望速度 1.0", v <= 1.0 + 1e-9)
    }

    // ------------------------------------------------------------ elevation

    @Test
    fun `elevation stays within plausible band around base`() {
        val e = KinematicsEngine()
        val base = 24.0
        repeat(2000) {
            val alt = e.nextElevation(baseAltitude = base, deltaDistanceMeters = 2.0)
            // OU 过程应围绕基准波动，不会漂移到荒谬值
            assertTrue("altitude=$alt 偏离基准过远", alt in base - 25.0..base + 25.0)
        }
    }

    @Test
    fun `elevation mean reverts toward base after reset to far offset`() {
        val e = KinematicsEngine()
        val base = 24.0
        // 先把高度推离基准
        e.resetElevation(initialAlt = 300.0)
        // 多步之后应明显回落
        var last = 300.0
        repeat(500) { last = e.nextElevation(baseAltitude = base, deltaDistanceMeters = 2.0) }
        assertTrue("500 步后 altitude=$last 应已回落接近 $base", kotlin.math.abs(last - base) < 60.0)
    }

    @Test
    fun `dynamic speed stays positive and bounded`() {
        val e = KinematicsEngine()
        repeat(1000) {
            val s = e.nextDynamicSpeed(baseSpeedMs = 3.0)
            assertTrue("speed=$s 必须为正", s > 0.0)
            assertTrue("speed=$s 不应超过合理上限", s < 60.0)
        }
    }
}
