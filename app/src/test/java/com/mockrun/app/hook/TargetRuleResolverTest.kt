package com.mockrun.app.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用独立分流「规则匹配」的单元测试 —— 对应验收清单 B 段。
 *
 * 这些断言覆盖的是**唯一**决定"某个应用拿到虚拟位置还是真实位置"的判断点，
 * 它原本埋在 2600 行的 hook 类里、依赖 Xposed 运行时，任何测试都碰不到。
 */
class TargetRuleResolverTest {

    private fun rule(
        pkg: String,
        userId: Int = 0,
        enabled: Boolean = true,
        mode: String = "STATIONARY",
        lat: Double = 31.230416,
        lon: Double = 121.473701
    ) = TargetAppRule(
        packageName = pkg,
        userId = userId,
        isEnabled = enabled,
        mode = mode,
        latitude = lat,
        longitude = lon,
        altitude = 20.0,
        speed = 0f
    )

    /** hook 侧 `loadMultiTargetRules()` 的填充方式：同时登记「包名_用户id」与「包名」两个键。 */
    private fun cacheOf(vararg rules: TargetAppRule): Map<String, TargetAppRule> {
        val map = HashMap<String, TargetAppRule>()
        for (r in rules) {
            map["${r.packageName}_${r.userId}"] = r
            map[r.packageName] = r
        }
        return map
    }

    @Test
    fun `selected app gets its own spoofed point`() {
        val cache = cacheOf(rule("com.example.right", lat = 22.543096, lon = 114.057865))
        val decision = TargetRuleResolver.resolve(cache, "com.example.right", userId = 0)
        assertTrue(decision is RuleDecision.Spoof)
        val spoof = decision as RuleDecision.Spoof
        assertEquals(22.543096, spoof.rule.latitude, 0.0)
        assertEquals(114.057865, spoof.rule.longitude, 0.0)
    }

    @Test
    fun `unselected app has no rule and therefore falls back to global logic`() {
        val cache = cacheOf(rule("com.example.right"))
        assertEquals(RuleDecision.NoRule, TargetRuleResolver.resolve(cache, "com.other.app", userId = 0))
    }

    @Test
    fun `paused rule passes through real location instead of inheriting global spoof`() {
        val cache = cacheOf(rule("com.example.right", enabled = false))
        assertEquals(RuleDecision.PassThrough, TargetRuleResolver.resolve(cache, "com.example.right", 0))
    }

    @Test
    fun `REAL_PASSTHROUGH mode passes through real location`() {
        val cache = cacheOf(rule("com.example.right", mode = "REAL_PASSTHROUGH"))
        assertEquals(RuleDecision.PassThrough, TargetRuleResolver.resolve(cache, "com.example.right", 0))
    }

    @Test
    fun `cloned app instance matches its own userId`() {
        val cache = cacheOf(
            rule("com.example.right", userId = 0, lat = 31.0, lon = 121.0),
            rule("com.example.right", userId = 999, lat = 22.0, lon = 114.0)
        )
        val primary = TargetRuleResolver.resolve(cache, "com.example.right", 0) as RuleDecision.Spoof
        assertEquals(31.0, primary.rule.latitude, 0.0)

        val clone = TargetRuleResolver.resolve(cache, "com.example.right", 999) as RuleDecision.Spoof
        assertEquals(22.0, clone.rule.latitude, 0.0)
    }

    @Test
    fun `unknown userId falls back to the primary user rule`() {
        val cache = cacheOf(rule("com.example.right", userId = 0, lat = 31.0, lon = 121.0))
        val decision = TargetRuleResolver.resolve(cache, "com.example.right", userId = 10) as RuleDecision.Spoof
        assertEquals(31.0, decision.rule.latitude, 0.0)
    }

    @Test
    fun `package only key is honoured for legacy configs`() {
        val legacy = mapOf("com.example.right" to rule("com.example.right"))
        assertTrue(TargetRuleResolver.resolve(legacy, "com.example.right", 0) is RuleDecision.Spoof)
    }

    @Test
    fun `unresolvable package yields NoRule instead of a wrong app's rule`() {
        val cache = cacheOf(rule("com.example.right"))
        assertEquals(RuleDecision.NoRule, TargetRuleResolver.resolve(cache, null, 0))
        assertEquals(RuleDecision.NoRule, TargetRuleResolver.resolve(cache, "", 0))
    }

    @Test
    fun `empty rule set yields NoRule`() {
        assertEquals(RuleDecision.NoRule, TargetRuleResolver.resolve(emptyMap(), "com.example.right", 0))
    }

    @Test
    fun `route mode rule still returns the rule point for per-app routing`() {
        // ROUTE 模式由全局路线模拟接管（hook 里另行判断 is_route），匹配层仍应命中该规则
        val cache = cacheOf(rule("com.example.right", mode = "ROUTE"))
        assertTrue(TargetRuleResolver.resolve(cache, "com.example.right", 0) is RuleDecision.Spoof)
    }
}
