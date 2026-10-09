package com.mockrun.app.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 环境指纹 RSSI 重标定的回归测试。
 *
 * 这是新功能的核心计算：把采集点的信号强度按距离折算到虚拟点。
 * FingerprintReplayer 里除解析与反射构造之外的部分是纯函数，
 * 可以在 JVM 上直接测。
 */
class FingerprintReplayerTest {

    // 采集点：北京天安门
    private val originLat = 39.904202
    private val originLon = 116.407394

    // ------------------------------------------------------------ haversine

    @Test
    fun `haversineMeters zero for same point`() {
        assertEquals(
            0.0,
            FingerprintReplayer.haversineMeters(originLat, originLon, originLat, originLon),
            1e-6
        )
    }

    @Test
    fun `haversineMeters about 111 km per degree latitude`() {
        val d = FingerprintReplayer.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue("实测 $d m", d in 111_100.0..111_400.0)
    }

    @Test
    fun `haversineMeters matches known short distance`() {
        // 天安门 -> 约 1 km 正北
        val d = FingerprintReplayer.haversineMeters(originLat, originLon, originLat + 0.008993, originLon)
        assertTrue("实测 $d m，应接近 1000 m", d in 990.0..1010.0)
    }

    // ------------------------------------------------------------ rescale

    @Test
    fun `rescale is identity when target equals origin`() {
        val r = FingerprintReplayer.rescaleRssi(-60, originLat, originLon, originLat, originLon)
        assertEquals(-60, r)
    }

    @Test
    fun `rescale is identity for sub-meter displacement`() {
        // d < 1 m 时直接返回原值，避免浮点噪声造成抖动
        val r = FingerprintReplayer.rescaleRssi(-60, originLat, originLon, originLat + 0.000001, originLon)
        assertEquals(-60, r)
    }

    @Test
    fun `rescale attenuates signal with distance`() {
        // 移到 3 km 外：20*log10(3000/30) = 40 dB，但上限 35 dB
        val r = FingerprintReplayer.rescaleRssi(-55, originLat, originLon, originLat + 0.027, originLon)
        assertTrue("远端信号必须更弱，实测 $r", r < -55)
        assertTrue("衰减不得超过 35 dB 上限，实测 $r", r >= -55 - 35)
    }

    @Test
    fun `rescale attenuation is capped at 35 dB`() {
        val near = FingerprintReplayer.rescaleRssi(-55, originLat, originLon, originLat + 0.027, originLon)
        // 移到 1000 km 外，衰减仍应被限制在 35 dB
        val far = FingerprintReplayer.rescaleRssi(-55, originLat, originLon, originLat + 9.0, originLon)
        assertEquals(near, far)
    }

    @Test
    fun `rescale never exceeds -28 dBm`() {
        // 极强信号 + 极近距离，仍不得突破物理上限
        val r = FingerprintReplayer.rescaleRssi(-20, originLat, originLon, originLat + 0.00002, originLon)
        assertTrue("实测 $r 不应高于 -28", r <= -28)
    }

    @Test
    fun `rescale never drops below -95 dBm`() {
        val r = FingerprintReplayer.rescaleRssi(-90, originLat, originLon, originLat + 9.0, originLon)
        assertTrue("实测 $r 不应低于 -95", r >= -95)
    }

    @Test
    fun `connected ap keeps a stronger floor than ambient ap`() {
        val ambient = FingerprintReplayer.rescaleRssi(
            -90, originLat, originLon, originLat + 0.5, originLon, isConnected = false
        )
        val connected = FingerprintReplayer.rescaleRssi(
            -90, originLat, originLon, originLat + 0.5, originLon, isConnected = true
        )
        assertTrue("已连接的 AP 底噪更高：ambient=$ambient connected=$connected", connected > ambient)
        assertTrue("已连接 AP 不得弱于 -55 dBm，实测 $connected", connected >= -55)
    }

    @Test
    fun `rescale is deterministic for identical inputs`() {
        val a = FingerprintReplayer.rescaleRssi(-70, originLat, originLon, originLat + 0.01, originLon)
        val b = FingerprintReplayer.rescaleRssi(-70, originLat, originLon, originLat + 0.01, originLon)
        assertEquals(a, b)
    }
}
