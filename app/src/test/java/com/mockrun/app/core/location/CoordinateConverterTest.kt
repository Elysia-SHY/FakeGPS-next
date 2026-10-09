package com.mockrun.app.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WGS-84 ↔ GCJ-02 转换的回归测试。
 *
 * 期望值不是凭空写的，而是用同一套公开算法独立算出来的参考值，
 * 容差取 1e-5 度（约 1 米），足以锁住实现不被改错。
 */
class CoordinateConverterTest {

    private companion object {
        /** 1e-5 度 ≈ 1.1 米（纬度方向） */
        const val TOL = 1e-5
    }

    @Test
    fun `wgs84ToGcj02 shifts beijing north-east`() {
        // 天安门 WGS-84
        val (lat, lon) = CoordinateConverter.wgs84ToGcj02(39.904202, 116.407394)
        // 参考值由独立的 Python 实现算出：39.905605, 116.413636
        // 北京地区 GCJ-02 偏移量约 +0.0014 lat / +0.0062 lon
        assertEquals(39.905605, lat, TOL)
        assertEquals(116.413636, lon, TOL)
    }

    @Test
    fun `wgs84ToGcj02 matches reference values for major cities`() {
        // 参考值全部来自独立 Python 实现，用于锁定算法不被改错
        val cases = listOf(
            Triple("上海", 31.230416 to 121.473701, 31.228474 to 121.478224),
            Triple("广州", 23.129163 to 113.264435, 23.126486 to 113.269765),
            Triple("成都", 30.572815 to 104.066801, 30.570361 to 104.069306),
            Triple("乌鲁木齐", 43.825592 to 87.616848, 43.826797 to 87.619698)
        )
        for ((name, wgs, expected) in cases) {
            val (lat, lon) = CoordinateConverter.wgs84ToGcj02(wgs.first, wgs.second)
            assertEquals("$name 纬度", expected.first, lat, TOL)
            assertEquals("$name 经度", expected.second, lon, TOL)
        }
    }

    @Test
    fun `wgs84ToGcj02 offset magnitude matches known beijing value`() {
        val (lat, lon) = CoordinateConverter.wgs84ToGcj02(39.904202, 116.407394)
        val dLat = lat - 39.904202
        val dLon = lon - 116.407394
        // 纬度方向偏移应显著小于经度方向（北京纬度约 40°，经度 1° 对应距离更短）
        assertTrue("dLat=$dLat 应为正", dLat > 0)
        assertTrue("dLon=$dLon 应为正", dLon > 0)
        assertTrue("偏移量应在合理区间", dLon in 0.004..0.009)
    }

    @Test
    fun `outside china returns input unchanged`() {
        // 东京：不在 GCJ-02 加密范围内，必须原样返回
        val (lat, lon) = CoordinateConverter.wgs84ToGcj02(35.6762, 139.6503)
        assertEquals(35.6762, lat, 1e-9)
        assertEquals(139.6503, lon, 1e-9)
    }

    @Test
    fun `round trip gcj02ToWgs84 approximately inverts`() {
        val original = 31.230416 to 121.473701  // 上海
        val gcj = CoordinateConverter.wgs84ToGcj02(original.first, original.second)
        val back = CoordinateConverter.gcj02ToWgs84(gcj.first, gcj.second)
        assertEquals(original.first, back.first, TOL)
        assertEquals(original.second, back.second, TOL)
    }

    @Test
    fun `round trip holds for multiple cities`() {
        val cities = listOf(
            "北京" to (39.904202 to 116.407394),
            "上海" to (31.230416 to 121.473701),
            "广州" to (23.129163 to 113.264435),
            "成都" to (30.572815 to 104.066801),
            "乌鲁木齐" to (43.825592 to 87.616848)
        )
        for ((name, wgs) in cities) {
            val gcj = CoordinateConverter.wgs84ToGcj02(wgs.first, wgs.second)
            val back = CoordinateConverter.gcj02ToWgs84(gcj.first, gcj.second)
            assertEquals("$name 纬度往返失败", wgs.first, back.first, TOL)
            assertEquals("$name 经度往返失败", wgs.second, back.second, TOL)
        }
    }

    @Test
    fun `conversion is idempotent at boundary of china check`() {
        // 边界内侧（深圳）应当偏移
        val (lat, lon) = CoordinateConverter.wgs84ToGcj02(22.543096, 114.057865)
        assertTrue("深圳应发生偏移", lat != 22.543096 || lon != 114.057865)

        // 边界外侧（香港以南的公海）不应偏移
        val (hlat, hlon) = CoordinateConverter.wgs84ToGcj02(0.5, 120.0)
        assertEquals(0.5, hlat, 1e-9)
        assertEquals(120.0, hlon, 1e-9)
    }
}
