package com.mockrun.app.hook

import com.mockrun.app.util.HookChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「当前到底靠什么在伪造」的判定测试。
 *
 * 这段逻辑存在的唯一目的：让 UI 永远说真话 —— 用户不能"以为在伪造、实际是真值"。
 * 所以测试的重点不是文案，而是四类组合下 active / warn 是否与事实一致。
 */
class HookChannelStatusTest {

    private fun resolve(
        preference: HookChannel,
        lsposedAlive: Boolean,
        mockActive: Boolean,
        configActive: Boolean = lsposedAlive
    ) = HookChannelStatus.resolve(preference, lsposedAlive, configActive, mockActive)

    @Test
    fun `lsposed alive is reported as the effective channel`() {
        val s = resolve(HookChannel.AUTO, lsposedAlive = true, mockActive = true)
        assertEquals(ActiveChannel.LSPOSED, s.active)
        assertFalse("框架层活着时不该报警", s.warn)
        assertEquals("LSPosed", s.shortLabel)
    }

    @Test
    fun `auto without lsposed degrades to provider and says so`() {
        val s = resolve(HookChannel.AUTO, lsposedAlive = false, mockActive = true)
        assertEquals(ActiveChannel.PROVIDER_ONLY, s.active)
        assertFalse(s.warn)
        assertEquals("仅 Provider", s.shortLabel)
        // 必须明确写出"SDK 可能仍显示真实位置"，这是 AUTO 降级的关键风险提示
        assertTrue(s.detail.contains("真实位置"))
    }

    @Test
    fun `explicit lsposed without the module is a warning, never a silent pass`() {
        val s = resolve(HookChannel.LSPOSED, lsposedAlive = false, mockActive = true)
        assertEquals(ActiveChannel.NONE, s.active)
        assertTrue("选了 LSPosed 但模块没起来，必须报警", s.warn)
        assertEquals("未生效", s.shortLabel)
    }

    @Test
    fun `provider preference ignores a live hook because it publishes no config`() {
        // 用户明确只要 Provider：我们不再发布配置，hook 拿不到坐标，
        // 所以即使框架层进程还活着，也不能对外宣称"LSPosed 在改写"。
        val s = resolve(HookChannel.PROVIDER, lsposedAlive = true, mockActive = true)
        assertEquals(ActiveChannel.PROVIDER_ONLY, s.active)
        assertFalse(s.warn)
    }

    @Test
    fun `live framework without an active config still reports provider only`() {
        // 真机实测场景：system_server 里模块在跑，但 debug.fakegps.active=0（没拿到坐标）。
        // 此时若报 LSPosed 就是撒谎 —— 必须报"仅 Provider"。
        val s = HookChannelStatus.resolve(
            preference = HookChannel.AUTO,
            isLsposedAlive = true,
            hookConfigActive = false,
            isMockActive = true
        )
        assertEquals(ActiveChannel.PROVIDER_ONLY, s.active)
        assertEquals("仅 Provider", s.shortLabel)
    }

    @Test
    fun `explicit lsposed with live framework but no config warns about the missing config`() {
        val s = HookChannelStatus.resolve(
            preference = HookChannel.LSPOSED,
            isLsposedAlive = true,
            hookConfigActive = false,
            isMockActive = true
        )
        assertEquals(ActiveChannel.NONE, s.active)
        assertTrue("必须报警并说明是配置没同步", s.warn)
        assertTrue(s.detail.contains("没有拿到生效配置"))
    }

    @Test
    fun `nothing running reports none without crying wolf`() {
        for (pref in HookChannel.values()) {
            val s = resolve(pref, lsposedAlive = false, mockActive = false)
            assertEquals("pref=$pref", ActiveChannel.NONE, s.active)
            assertFalse("没有在模拟时不应报警（pref=$pref）", s.warn)
        }
    }

    @Test
    fun `only explicit lsposed choice warns among all combinations`() {
        val combos = mutableListOf<Triple<HookChannel, Boolean, Boolean>>()
        for (pref in HookChannel.values()) {
            for (alive in listOf(true, false)) {
                for (active in listOf(true, false)) {
                    combos.add(Triple(pref, alive, active))
                }
            }
        }
        for ((pref, alive, active) in combos) {
            val s = resolve(pref, alive, active)
            val expectedWarn = active && pref == HookChannel.LSPOSED && !alive
            assertEquals(
                "warn 预期不一致：pref=$pref alive=$alive active=$active",
                expectedWarn, s.warn
            )
        }
    }
}
