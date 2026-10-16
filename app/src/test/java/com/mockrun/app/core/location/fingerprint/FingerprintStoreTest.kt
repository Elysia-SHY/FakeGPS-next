package com.mockrun.app.core.location.fingerprint

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * FingerprintStore 的容错回归测试。
 *
 * 这组用例锁的是两条容易静默失效的契约：
 *
 *  1. **Gson 不走 Kotlin 构造器**（Unsafe 分配），所以「Kotlin 默认值」在反序列化时
 *     不生效——旧指纹包里缺失的新增字段读出来是 null/0，而不是声明处的默认值。
 *     因此新增字段必须是可空类型，并且 `load()` 之后要把 null 解释成「未知」而不是 0。
 *  2. **save() 必须是原子的且如实返回成败**。旧实现吞掉异常返回 Unit，
 *     于是保存失败照样弹「已采集成功」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FingerprintStoreTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun fp(
        wifi: List<WifiAp> = emptyList(),
        cells: List<CellTower> = emptyList(),
        ble: List<BleBeacon> = emptyList(),
        bonded: List<BondedDevice> = emptyList(),
        wifiDiscovered: Int? = null,
        cellsDiscovered: Int? = null,
        cellsDropped: Int? = null,
        wifiState: String? = null,
        cellsState: String? = null,
        bleState: String? = null,
        bondedState: String? = null,
        bleScanError: Int? = null,
        originSource: String? = null
    ) = EnvironmentFingerprint(
        originLat = 39.904202, originLon = 116.407394,
        capturedAt = 1_700_000_000_000L, capturedElapsedNanos = 123L,
        wifi = wifi, cells = cells, ble = ble, bonded = bonded,
        wifiDiscovered = wifiDiscovered, cellsDiscovered = cellsDiscovered,
        cellsDropped = cellsDropped, wifiState = wifiState, cellsState = cellsState,
        bleState = bleState, bondedState = bondedState, bleScanError = bleScanError,
        originSource = originSource
    )

    private fun writeRaw(id: String, json: String) {
        val dir = File(ctx.filesDir, "fingerprints").apply { mkdirs() }
        File(dir, "$id.json").writeText(json)
    }

    // ------------------------------------------------------------ 往返

    @Test
    fun `save returns true and round-trips all new fields`() {
        val id = "rt"
        val ok = FingerprintStore.save(
            ctx, id,
            fp(
                wifi = listOf(WifiAp("ssid", "aa:bb:cc:dd:ee:ff", "[WPA2]", -50, 2412)),
                wifiDiscovered = 45, wifiState = SourceState.OK.name,
                cellsState = SourceState.OK_EMPTY.name, cellsDiscovered = 0,
                bleState = SourceState.RADIO_OFF.name, bondedState = SourceState.RADIO_OFF.name,
                originSource = "MANUAL"
            )
        )
        assertTrue("save 必须如实返回 true", ok)

        val loaded = FingerprintStore.load(ctx, id)
        assertNotNull(loaded)
        assertEquals(45, loaded!!.wifiDiscovered)
        assertEquals(SourceState.OK.name, loaded.wifiState)
        assertEquals(SourceState.OK_EMPTY.name, loaded.cellsState)
        assertEquals(0, loaded.cellsDiscovered)
        assertEquals(SourceState.RADIO_OFF.name, loaded.bleState)
        assertEquals("MANUAL", loaded.originSource)
    }

    @Test
    fun `save leaves no tmp file behind`() {
        val id = "atomic"
        assertTrue(FingerprintStore.save(ctx, id, fp()))
        val dir = File(ctx.filesDir, "fingerprints")
        val leftovers = dir.listFiles { f -> f.name.endsWith(".tmp") }?.map { it.name } ?: emptyList()
        assertTrue("原子写不得残留临时文件，实测 $leftovers", leftovers.isEmpty())
        assertTrue(File(dir, "$id.json").exists())
    }

    @Test
    fun `save overwrites an existing file`() {
        val id = "overwrite"
        assertTrue(FingerprintStore.save(ctx, id, fp(wifiDiscovered = 1, wifiState = SourceState.OK.name)))
        assertTrue(FingerprintStore.save(ctx, id, fp(wifiDiscovered = 99, wifiState = SourceState.OK_EMPTY.name)))
        assertEquals(99, FingerprintStore.load(ctx, id)!!.wifiDiscovered)
    }

    // ------------------------------------------------------------ 旧 JSON

    @Test
    fun `old json without any new field loads and reports unknown instead of zero`() {
        // 完全按旧版本写出的指纹包：没有 wifiDiscovered / wifiState / originSource 等键
        writeRaw(
            "legacy",
            """
            {
              "originLat": 39.9, "originLon": 116.4,
              "capturedAt": 1700000000000, "capturedElapsedNanos": 5,
              "wifi": [{"ssid":"a","bssid":"aa:bb:cc:dd:ee:ff","capabilities":"","rssi":-50,"frequency":2412}],
              "cells": [{"type":"LTE","mcc":460,"mnc":0,"cid":1,"pci":2,"tac":3}],
              "ble": [], "bonded": []
            }
            """.trimIndent()
        )

        val loaded = FingerprintStore.load(ctx, "legacy")
        assertNotNull("旧指纹包必须能读出来", loaded)

        // 关键：未知必须是 null，不能被当成 0（0 会被 UI 显示成「这里真的没有」）
        assertNull("缺 wifiDiscovered 必须是未知(null)", loaded!!.wifiDiscovered)
        assertNull("缺 cellsDiscovered 必须是未知(null)", loaded.cellsDiscovered)
        assertNull("缺 wifiState 必须是未知(null)", loaded.wifiState)
        assertNull("缺 cellsState 必须是未知(null)", loaded.cellsState)
        assertNull("缺 originSource 必须是未知(null)", loaded.originSource)
        assertNull("缺 bleScanError 必须是未知(null)", loaded.bleScanError)

        // 老数据本身要完好
        assertEquals(1, loaded.wifi.size)
        assertEquals(1, loaded.cells.size)
        assertNull("旧包没有 nci", loaded.cells.single().nci)
    }

    @Test
    fun `corrupt json returns null and does not throw`() {
        writeRaw("broken", "{ this is not json at all ")
        assertNull("损坏文件必须返回 null 而不是抛异常", FingerprintStore.load(ctx, "broken"))
    }

    @Test
    fun `null collections in json do not crash the reader`() {
        // Gson 绕过构造器：JSON 里写 null 会真的塞进非空属性，必须由 normalize 收口
        writeRaw(
            "nulls",
            """
            {"originLat":1.0,"originLon":2.0,"capturedAt":1,"capturedElapsedNanos":1,
             "wifi":null,"cells":null,"ble":null,"bonded":null}
            """.trimIndent()
        )
        val loaded = FingerprintStore.load(ctx, "nulls")
        assertNotNull(loaded)
        assertEquals(0, loaded!!.wifi.size)
        assertEquals(0, loaded.cells.size)
        assertEquals(0, loaded.ble.size)
        assertEquals(0, loaded.bonded.size)
    }

    @Test
    fun `unknown state name is normalized to unknown`() {
        writeRaw(
            "badstate",
            """
            {"originLat":1.0,"originLon":2.0,"capturedAt":1,"capturedElapsedNanos":1,
             "wifi":[],"cells":[],"ble":[],"bonded":[],
             "wifiState":"SOMETHING_ELSE","bleState":"","cellsState":"OK"}
            """.trimIndent()
        )
        val loaded = FingerprintStore.load(ctx, "badstate")!!
        assertNull("未知状态名不得被当成正常", loaded.wifiState)
        assertNull("空串状态名不得被当成正常", loaded.bleState)
        assertEquals(SourceState.OK.name, loaded.cellsState)
    }

    @Test
    fun `nci is cleared for non NR cells on load`() {
        writeRaw(
            "nci",
            """
            {"originLat":1.0,"originLon":2.0,"capturedAt":1,"capturedElapsedNanos":1,
             "wifi":[],"ble":[],"bonded":[],
             "cells":[{"type":"LTE","mcc":460,"mnc":0,"cid":1,"pci":2,"tac":3,"nci":68719476735},
                      {"type":"NR","mcc":460,"mnc":0,"cid":-1,"pci":4,"tac":5,"nci":68719476735}]}
            """.trimIndent()
        )
        val cells = FingerprintStore.load(ctx, "nci")!!.cells
        assertNull("LTE 上的 nci 是噪音，必须清掉", cells.first { it.type == "LTE" }.nci)
        assertEquals("NR 的 36-bit NCI 必须保留", 68719476735L, cells.first { it.type == "NR" }.nci)
    }

    // ------------------------------------------------------------ 摘要

    @Test
    fun `summary shows saved over discovered and attaches state when empty`() {
        val s = FingerprintStore.summary(
            fp(
                wifi = List(20) { WifiAp("s$it", "b$it", "", -50, 2412) },
                wifiDiscovered = 45, wifiState = SourceState.OK.name,
                cellsState = SourceState.OK_EMPTY.name,
                bleState = SourceState.RADIO_OFF.name,
                bondedState = SourceState.RADIO_OFF.name
            )
        )
        // 截断可见：保存 20 / 发现 45
        assertTrue("摘要必须体现截断，实测：$s", s.contains("Wi-Fi 20/45"))
        assertTrue("基站为 0 时必须带状态，实测：$s", s.contains("基站 0（无结果）"))
        assertTrue("蓝牙关闭必须可见，实测：$s", s.contains("蓝牙 0（开关关闭）"))
        assertTrue("配对关闭必须可见，实测：$s", s.contains("配对 0（开关关闭）"))
    }

    @Test
    fun `summary for fully empty capture does not look like success`() {
        val s = FingerprintStore.summary(
            fp(wifiState = SourceState.PERMISSION_DENIED.name, cellsState = SourceState.TIMEOUT.name)
        )
        assertTrue("四类全空时必须明确写出没采到数据，实测：$s", s.contains("未采到任何数据"))
        assertTrue("必须带上失败原因，实测：$s", s.contains("权限被拒"))
        assertTrue("必须带上超时原因，实测：$s", s.contains("超时"))
    }

    @Test
    fun `summary omits discovered count when unknown`() {
        val s = FingerprintStore.summary(fp(wifi = listOf(WifiAp("a", "b", "", -50, 2412))))
        assertTrue("发现数未知时只显示保存数，实测：$s", s.contains("Wi-Fi 1"))
        assertFalse("不得凭空补一个发现数，实测：$s", s.contains("Wi-Fi 1/"))
    }

    // ------------------------------------------------------------ 枚举

    @Test
    fun `every source state has a non blank chinese label`() {
        for (st in SourceState.entries) {
            assertTrue("${st.name} 缺少 label", st.label.isNotBlank())
        }
        assertEquals(8, SourceState.entries.size)
    }

    @Test
    fun `fromName maps names and rejects junk`() {
        for (st in SourceState.entries) {
            assertEquals(st, SourceState.fromName(st.name))
        }
        assertNull(SourceState.fromName(null))
        assertNull(SourceState.fromName(""))
        assertNull(SourceState.fromName("OKAY"))
        assertNull(SourceState.fromName("ok"))
    }

    // ------------------------------------------------------------ 其他

    @Test
    fun `load of missing id returns null`() {
        assertNull(FingerprintStore.load(ctx, "does-not-exist"))
    }

    @Test
    fun `list and delete work on saved entries`() {
        assertTrue(FingerprintStore.save(ctx, "d1", fp()))
        assertTrue(FingerprintStore.list(ctx).contains("d1"))
        FingerprintStore.delete(ctx, "d1")
        assertFalse(FingerprintStore.list(ctx).contains("d1"))
    }
}
