package com.mockrun.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 坐标转换的回归测试 —— 覆盖正向、反向、BD-09 与边界。
 *
 * 这份测试是**唯一**能覆盖 hook 侧转换算法的测试：hook 里的 `wgs84ToGcj02` / `wgs84ToBd09`
 * 现在只是转发到 [GeoTransform]，所以这里锁住的公式同时就是注入高德/百度/腾讯 SDK 时用的公式。
 *
 * 期望值取自公开的 GCJ-02/BD-09 算法独立实现，容差 1e-5 度（约 1 米）。
 */
class GeoTransformTest {

    private companion object {
        const val TOL = 1e-5
        /** 天安门 WGS-84 */
        const val TIANANMEN_WGS_LAT = 39.904202
        const val TIANANMEN_WGS_LON = 116.407394
    }

    @Test
    fun `wgs84ToGcj02 shifts beijing north-east`() {
        val (lat, lon) = GeoTransform.wgs84ToGcj02(TIANANMEN_WGS_LAT, TIANANMEN_WGS_LON)
        assertEquals(39.905605, lat, TOL)
        assertEquals(116.413636, lon, TOL)
    }

    @Test
    fun `wgs84ToGcj02 matches reference values for major cities`() {
        val cases = listOf(
            Triple("上海", 31.230416 to 121.473701, 31.228474 to 121.478224),
            Triple("广州", 23.129163 to 113.264435, 23.126486 to 113.269765),
            Triple("成都", 30.572815 to 104.066801, 30.570361 to 104.069306),
            Triple("乌鲁木齐", 43.825592 to 87.616848, 43.826797 to 87.619698)
        )
        for ((name, wgs, expected) in cases) {
            val (lat, lon) = GeoTransform.wgs84ToGcj02(wgs.first, wgs.second)
            assertEquals("$name 纬度", expected.first, lat, TOL)
            assertEquals("$name 经度", expected.second, lon, TOL)
        }
    }

    @Test
    fun `gcj02ToWgs84 inverts the forward transform to sub-metre accuracy`() {
        val cities = listOf(
            "北京" to (TIANANMEN_WGS_LAT to TIANANMEN_WGS_LON),
            "上海" to (31.230416 to 121.473701),
            "广州" to (23.129163 to 113.264435),
            "成都" to (30.572815 to 104.066801),
            "乌鲁木齐" to (43.825592 to 87.616848)
        )
        for ((name, wgs) in cities) {
            val (gLat, gLon) = GeoTransform.wgs84ToGcj02(wgs.first, wgs.second)
            val (wLat, wLon) = GeoTransform.gcj02ToWgs84(gLat, gLon)
            assertEquals("$name 纬度往返失败", wgs.first, wLat, TOL)
            assertEquals("$name 经度往返失败", wgs.second, wLon, TOL)
        }
    }

    @Test
    fun `gcj02ToWgs84 removes the offset instead of applying it twice`() {
        // 这是「重复转换」回归测试：把 GCJ 值当 WGS 再转一次会得到 ~500m 的二次偏移。
        val (gLat, gLon) = GeoTransform.wgs84ToGcj02(TIANANMEN_WGS_LAT, TIANANMEN_WGS_LON)
        val (wLat, wLon) = GeoTransform.gcj02ToWgs84(gLat, gLon)
        // 反向结果必须比原 GCJ 值更靠近真实 WGS 值
        val errBefore = Math.abs(gLat - TIANANMEN_WGS_LAT) + Math.abs(gLon - TIANANMEN_WGS_LON)
        val errAfter = Math.abs(wLat - TIANANMEN_WGS_LAT) + Math.abs(wLon - TIANANMEN_WGS_LON)
        assertTrue("反向转换必须显著减小偏差（before=$errBefore after=$errAfter）", errAfter < errBefore / 100)
        // 且偏移量本身在已知量级（北京约 0.0014 lat / 0.0062 lon）
        assertEquals(0.0014, gLat - TIANANMEN_WGS_LAT, 5e-4)
        assertEquals(0.0062, gLon - TIANANMEN_WGS_LON, 5e-4)
    }

    @Test
    fun `bd09 forward and inverse round trip`() {
        val (bLat, bLon) = GeoTransform.wgs84ToBd09(TIANANMEN_WGS_LAT, TIANANMEN_WGS_LON)
        // 百度 BD-09 相对 WGS-84 的偏移比 GCJ 更大（约 0.0074 lat / 0.0126 lon）
        assertTrue("BD-09 纬度应北偏", bLat > TIANANMEN_WGS_LAT)
        assertTrue("BD-09 经度应东偏", bLon > TIANANMEN_WGS_LON)

        val (wLat, wLon) = GeoTransform.bd09ToWgs84(bLat, bLon)
        assertEquals(TIANANMEN_WGS_LAT, wLat, 1e-4)
        assertEquals(TIANANMEN_WGS_LON, wLon, 1e-4)
    }

    @Test
    fun `bd09ToGcj02 inverts gcj02ToBd09`() {
        val (gLat, gLon) = GeoTransform.wgs84ToGcj02(TIANANMEN_WGS_LAT, TIANANMEN_WGS_LON)
        val (bLat, bLon) = GeoTransform.gcj02ToBd09(gLat, gLon)
        val (backLat, backLon) = GeoTransform.bd09ToGcj02(bLat, bLon)
        // BD-09 的反算是公开算法给出的近似逆变换，实测残差约 1e-6 度（≈0.1 m），
        // 因此容差取 5e-6 而不是 1e-9。
        assertEquals(gLat, backLat, 5e-6)
        assertEquals(gLon, backLon, 5e-6)
    }

    @Test
    fun `toWgs84 and fromWgs84 are consistent for every coordinate system`() {
        for (system in GeoTransform.CoordSystem.values()) {
            val (lat, lon) = GeoTransform.fromWgs84(TIANANMEN_WGS_LAT, TIANANMEN_WGS_LON, system)
            val (backLat, backLon) = GeoTransform.toWgs84(lat, lon, system)
            assertEquals("$system 纬度", TIANANMEN_WGS_LAT, backLat, 1e-4)
            assertEquals("$system 经度", TIANANMEN_WGS_LON, backLon, 1e-4)
        }
    }

    @Test
    fun `outside china returns input unchanged for every conversion`() {
        // 东京：不在 GCJ-02 / BD-09 的偏移范围内，三种转换都必须原样返回。
        // （BD-09 是中国大陆基准，百度 SDK 在境外返回的就是 WGS-84；对它硬套偏移会凭空造出 ~500m 误差。）
        val tokyo = 35.6762 to 139.6503
        for ((name, pair) in listOf(
            "wgs84ToGcj02" to GeoTransform.wgs84ToGcj02(tokyo.first, tokyo.second),
            "gcj02ToWgs84" to GeoTransform.gcj02ToWgs84(tokyo.first, tokyo.second),
            "wgs84ToBd09" to GeoTransform.wgs84ToBd09(tokyo.first, tokyo.second),
            "bd09ToWgs84" to GeoTransform.bd09ToWgs84(tokyo.first, tokyo.second)
        )) {
            assertEquals("$name 纬度不应偏移", tokyo.first, pair.first, 1e-9)
            assertEquals("$name 经度不应偏移", tokyo.second, pair.second, 1e-9)
        }
    }

    @Test
    fun `invalid input is passed through instead of producing NaN`() {
        assertEquals(Double.NaN, GeoTransform.wgs84ToGcj02(Double.NaN, 116.4).first, 0.0)
        assertEquals(Double.POSITIVE_INFINITY, GeoTransform.wgs84ToGcj02(Double.POSITIVE_INFINITY, 116.4).first, 0.0)
        // BD-09 的转换是纯代数，没有国界判断，但非法值必须原样返回
        assertTrue(GeoTransform.gcj02ToBd09(Double.NaN, 116.4).first.isNaN())
        assertTrue(GeoTransform.bd09ToGcj02(39.9, Double.NaN).second.isNaN())
    }

    @Test
    fun `latitude and longitude are never swapped`() {
        // 纬度的合法范围是 ±90、经度 ±180：把北京输入按「经度在前」误用会得到越界值。
        val (lat, lon) = GeoTransform.wgs84ToGcj02(TIANANMEN_WGS_LAT, TIANANMEN_WGS_LON)
        assertTrue("输出纬度必须在 ±90 内：$lat", lat in -90.0..90.0)
        assertTrue("输出经度必须在 ±180 内：$lon", lon in -180.0..180.0)
        // 纬度方向的偏移量级必须小于经度方向（北京纬度约 40°，经度 1° 对应的地面距离更短）
        assertTrue(lat - TIANANMEN_WGS_LAT < lon - TIANANMEN_WGS_LON)
    }

    @Test
    fun `china bounding box guard behaves at the edges`() {
        // 边界内侧（深圳）应当发生偏移
        val (lat, lon) = GeoTransform.wgs84ToGcj02(22.543096, 114.057865)
        assertTrue(lat != 22.543096 || lon != 114.057865)
        // 边界外侧（南海以外）不应偏移
        assertEquals(0.5, GeoTransform.wgs84ToGcj02(0.5, 120.0).first, 1e-9)
        assertFalse(GeoTransform.outOfChina(39.9, 116.4))
        assertTrue(GeoTransform.outOfChina(35.6, 139.6))
    }
}
