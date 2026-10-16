package com.mockrun.app.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 环境指纹 RSSI 重标定与 NR（5G）回放的回归测试。
 *
 * 必须跑在 Robolectric 下（SDK 34），有两个实测原因：
 *  1. 纯 JVM 测试的 classpath 上是 android.jar 的**桩实现**：`org.json` 的
 *     `optInt/optLong/has` 一律返回 0/false，解析类断言会全部假失败。
 *  2. NR 构造器分派测的是 `android.telephony.CellIdentityNr` 等真实框架类，
 *     桩实现里没有这些方法体。
 *
 * 注意 `XposedHelpers` 是 compileOnly 依赖，测试 classpath 上不存在，
 * 因此依赖它的 `buildCellInfoList` 整体不在这里测；但 `newCellInfoNr` /
 * `buildCellIdentityNr` / `buildCellSignalStrengthNr` 的构造器分派是纯反射，可以真跑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    // ------------------------------------------------------------ JSON 解析

    /**
     * 36-bit NCI 的上限：3GPP TS 38.331 规定 NCI 为 36 bit，
     * AOSP 的 `CellIdentityNr.MAX_NCI` 同样是 68719476735。
     * 这个值比 Int.MAX_VALUE(2147483647) 大 32 倍，正是「不能用 Int 装」的证据。
     */
    private val maxNci = 68719476735L

    private fun fingerprintJson(cellsJson: String): String = """
        {
          "originLat": $originLat,
          "originLon": $originLon,
          "capturedAt": 1700000000000,
          "capturedElapsedNanos": 123456789,
          "wifi": [],
          "cells": [$cellsJson],
          "ble": [],
          "bonded": []
        }
    """.trimIndent()

    @Test
    fun `nr nci above Int MAX_VALUE survives parse without truncation`() {
        val fp = FingerprintReplayer.parse(
            fingerprintJson(
                """{"type":"NR","mcc":460,"mnc":0,"cid":-1,"pci":123,"tac":456,
                   "earfcn":627264,"rssi":-80,"rsrp":-95,"rsrq":-12,"rssnr":15,
                   "isRegistered":true,"connectionStatus":1,"nci":$maxNci}"""
            )
        )

        val nr = fp.cells.single()
        assertEquals("NR", nr.type)
        assertEquals(maxNci, nr.nci)
        // 截断成 Int 的典型症状：高位丢失后变成另一个数或负数
        assertTrue("NCI 必须大于 Int.MAX_VALUE，实测 ${nr.nci}", nr.nci!! > Int.MAX_VALUE.toLong())
        assertTrue("NCI 不得为负，实测 ${nr.nci}", nr.nci!! >= 0L)
    }

    @Test
    fun `nr nci roundtrip is exact for every 36-bit boundary`() {
        // 低位、Int 边界、Int 边界 +1、36-bit 上限 —— 覆盖截断会暴露的四个位置
        val cases = listOf(0L, 1L, Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong() + 1L, maxNci)
        for (v in cases) {
            val fp = FingerprintReplayer.parse(
                fingerprintJson("""{"type":"NR","cid":0,"nci":$v}""")
            )
            assertEquals("NCI=$v 必须原样往返", v, fp.cells.single().nci)
        }
    }

    @Test
    fun `old json without new fields parses and does not crash`() {
        // 旧指纹包：cells 里没有 nci 键，也没有任何新增诊断字段
        val fp = FingerprintReplayer.parse(
            """{
              "originLat": 39.9, "originLon": 116.4,
              "wifi": [{"ssid":"a","bssid":"aa:bb:cc:dd:ee:ff","rssi":-50,"frequency":2412}],
              "cells": [{"type":"LTE","mcc":460,"mnc":0,"cid":123,"pci":1,"tac":2}],
              "ble": [], "bonded": []
            }"""
        )

        assertEquals(1, fp.wifi.size)
        assertEquals(1, fp.cells.size)
        // 关键：缺 nci 必须是 null，不能是 0 —— 0 会被当成一个有效 NCI 回放出去
        assertNull("旧 JSON 缺 nci 键时必须为 null", fp.cells.single().nci)
        assertEquals("LTE", fp.cells.single().type)
        assertEquals(123, fp.cells.single().cid)
    }

    @Test
    fun `explicit null nci is treated as absent`() {
        val fp = FingerprintReplayer.parse(
            fingerprintJson("""{"type":"NR","cid":5,"nci":null}""")
        )
        assertNull(fp.cells.single().nci)
    }

    @Test
    fun `mixed lte and nr list keeps both and only nr carries nci`() {
        val fp = FingerprintReplayer.parse(
            fingerprintJson(
                """{"type":"LTE","mcc":460,"mnc":0,"cid":111,"pci":10,"tac":20,"isRegistered":true},
                   {"type":"NR","mcc":460,"mnc":0,"cid":-1,"pci":30,"tac":40,"nci":$maxNci,"isRegistered":false}"""
            )
        )

        assertEquals(2, fp.cells.size)
        val lte = fp.cells.first { it.type == "LTE" }
        val nr = fp.cells.first { it.type == "NR" }
        assertEquals(111, lte.cid)
        assertNull("LTE 不应带 NCI", lte.nci)
        assertEquals(maxNci, nr.nci)
        assertTrue(nr.isRegistered.not())
        assertTrue(lte.isRegistered)
    }

    // ------------------------------------------------------------ NR 构造器

    /**
     * 用真实的 android.telephony 类（Robolectric 提供的 API 34 框架）
     * 走一遍 NR 构造，验证 NCI 不在构造环节被截断。
     *
     * 这一层必须用 Robolectric：`XposedHelpers` 是 compileOnly 依赖，
     * 不在测试 classpath 上，所以 buildCellInfoList 里依赖它的部分无法直接跑；
     * 但 `newCellInfoNr` / `buildCellIdentityNr` 的构造器分派是纯反射，可以真跑。
     */
    @Test
    fun `buildCellIdentityNr keeps 36-bit nci on real framework class`() {
        val clazz = Class.forName("android.telephony.CellIdentityNr")
        val t = cell(type = "NR", cid = -1, pci = 123, tac = 456, earfcn = 627264, nci = maxNci)

        val id = FingerprintReplayer.buildCellIdentityNr(clazz, t)
        assertNotNull("CellIdentityNr 应能在 API 34 上构造出来", id)

        val nci = id!!.javaClass.getMethod("getNci").invoke(id) as Long
        assertEquals("NCI 在构造环节不得被截断", maxNci, nci)

        val pci = id.javaClass.getMethod("getPci").invoke(id) as Int
        assertEquals(123, pci)
    }

    @Test
    fun `buildCellSignalStrengthNr preserves ss values`() {
        val clazz = Class.forName("android.telephony.CellSignalStrengthNr")
        val t = cell(type = "NR", cid = 1, pci = 1, tac = 1, earfcn = 627264, rssi = -80, rsrp = -95, rsrq = -12, rssnr = 15)

        val sig = FingerprintReplayer.buildCellSignalStrengthNr(
            clazz, t, originLat, originLon, originLat, originLon
        )
        assertNotNull(sig)

        // 采集点 == 目标点 → delta = 0 → ss 值应原样保留
        assertEquals(-95, sig!!.javaClass.getMethod("getSsRsrp").invoke(sig) as Int)
        assertEquals(-12, sig.javaClass.getMethod("getSsRsrq").invoke(sig) as Int)
        assertEquals(15, sig.javaClass.getMethod("getSsSinr").invoke(sig) as Int)
    }

    @Test
    fun `newCellInfoNr produces an object exposing the 36-bit nci`() {
        val idClazz = Class.forName("android.telephony.CellIdentityNr")
        val sigClazz = Class.forName("android.telephony.CellSignalStrengthNr")
        val infoClazz = Class.forName("android.telephony.CellInfoNr")

        val t = cell(type = "NR", cid = -1, pci = 7, tac = 8, earfcn = 627264, nci = maxNci, isRegistered = true)
        val id = FingerprintReplayer.buildCellIdentityNr(idClazz, t)!!
        val sig = FingerprintReplayer.buildCellSignalStrengthNr(sigClazz, t, originLat, originLon, originLat, originLon)!!

        val info = FingerprintReplayer.newCellInfoNr(infoClazz, id, sig, t)
        assertNotNull("CellInfoNr 应能在 API 34 上构造出来（5 参构造）", info)

        val gotId = info!!.javaClass.getMethod("getCellIdentity").invoke(info)
        val nci = gotId.javaClass.getMethod("getNci").invoke(gotId) as Long
        assertEquals("回放出来的 NCI 必须是完整的 36 bit", maxNci, nci)

        val gotSig = info.javaClass.getMethod("getCellSignalStrength").invoke(info)
        assertNotNull("信号强度必须被写进去（NR 没有 setter，只能走构造器）", gotSig)
        assertEquals(-95, gotSig.javaClass.getMethod("getSsRsrp").invoke(gotSig) as Int)
    }

    private fun cell(
        type: String,
        cid: Int,
        pci: Int,
        tac: Int,
        earfcn: Int,
        nci: Long? = null,
        rssi: Int = -80,
        rsrp: Int = -95,
        rsrq: Int = -12,
        rssnr: Int = 15,
        isRegistered: Boolean = true
    ) = FingerprintReplayer.ParsedCell(
        type = type, mcc = 460, mnc = 0, cid = cid, pci = pci, tac = tac,
        earfcn = earfcn, rssi = rssi, rsrp = rsrp, rsrq = rsrq, rssnr = rssnr,
        isRegistered = isRegistered, connectionStatus = 1, nci = nci
    )
}
