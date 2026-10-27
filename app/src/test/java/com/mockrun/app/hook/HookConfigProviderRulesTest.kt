package com.mockrun.app.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 导出 Provider 的分流规则必须按调用方裁剪。 */
@RunWith(RobolectricTestRunner::class)
class HookConfigProviderRulesTest {

    private val provider = HookConfigProvider()

    private val json = """
        [
          {"packageName":"com.example.self","userId":0,"latitude":39.9,"longitude":116.4,"mode":"STATIONARY","isEnabled":true},
          {"packageName":"com.example.other","userId":0,"latitude":31.2,"longitude":121.5,"mode":"STATIONARY","isEnabled":true},
          {"packageName":"com.example.self","userId":999,"latitude":30.0,"longitude":120.0,"mode":"STATIONARY","isEnabled":true}
        ]
    """.trimIndent()

    @Test
    fun `untrusted caller sees only its own package and user`() {
        val out = provider.filterMultiTargetRules(json, setOf("com.example.self"), 0)
        assertTrue(out.contains("com.example.self"))
        assertTrue(out.contains("39.9"))
        assertFalse(out.contains("com.example.other"))
        assertFalse(out.contains("121.5"))
        assertFalse(out.contains("120.0"))
        assertEquals(1, com.google.gson.JsonParser.parseString(out).asJsonArray.size())
    }

    @Test
    fun `no package match returns empty json`() {
        assertEquals("[]", provider.filterMultiTargetRules(json, setOf("com.example.unknown"), 0))
        assertEquals("[]", provider.filterMultiTargetRules(json, emptySet(), 0))
    }

    @Test
    fun `work profile caller gets only its user rule`() {
        val out = provider.filterMultiTargetRules(json, setOf("com.example.self"), 999)
        assertTrue(out.contains("120.0"))
        assertFalse(out.contains("39.9"))
    }

    @Test
    fun `malformed json never leaks the original payload`() {
        assertEquals("[]", provider.filterMultiTargetRules("{not json", setOf("com.example.self"), 0))
    }
}
