package com.mockrun.app.hook

/**
 * 一条应用分流规则（hook 侧从 JSON 解析出来的纯数据）。
 *
 * 字段名必须与 app 侧 `domain/model/MultiTargetRule.kt` 的 Gson 序列化结果一致：
 * packageName / userId / isEnabled / mode / latitude / longitude / altitude / speedKmh。
 * 任何一侧改名都会让规则匹配静默失效 —— `TargetRuleResolverTest` 用同一套键名锁住这层契约。
 */
data class TargetAppRule(
    val packageName: String,
    val userId: Int,
    val isEnabled: Boolean,
    val mode: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float
)

/** [TargetRuleResolver.resolve] 的判定结果。 */
sealed interface RuleDecision {
    /** 命中且启用：用这条规则自己的点伪造。 */
    data class Spoof(val rule: TargetAppRule) : RuleDecision

    /**
     * 命中但"不要伪造"：规则被暂停（`isEnabled=false`）或显式设为透传真实位置
     * （`mode=REAL_PASSTHROUGH`）。此时必须原样透出真实定位，**不能**回落到全局模拟点，
     * 否则"暂停某应用"会把该应用变成全局模拟点。
     */
    data object PassThrough : RuleDecision

    /** 没有任何规则命中：交给全局逻辑（全局模拟开则用全局点，否则透传真实位置）。 */
    data object NoRule : RuleDecision
}

/**
 * 应用独立分流的规则匹配 —— 从 `XposedLocationHook.getActiveLocation()` 里抽出来的纯函数。
 *
 * 抽出来的唯一原因是**可测**：这段判断原本埋在 2600 行的 hook 类里，依赖 Xposed 运行时，
 * 任何单测都碰不到它；而它恰恰是"分流到底生效没有"的唯一决策点。
 *
 * 匹配顺序（与 app 侧 `MultiTargetRule.key = "${'$'}{packageName}_${'$'}{userId}"` 对齐）：
 *   1. `包名_用户id`  —— 精确匹配（分身应用 userId 非 0）
 *   2. `包名_0`       —— 该应用在主用户下的规则
 *   3. `包名`         —— 兼容旧格式/未带 userId 的键
 */
object TargetRuleResolver {

    fun resolve(
        rules: Map<String, TargetAppRule>,
        packageName: String?,
        userId: Int
    ): RuleDecision {
        if (packageName.isNullOrEmpty()) return RuleDecision.NoRule

        val rule = rules["${packageName}_$userId"]
            ?: rules["${packageName}_0"]
            ?: rules[packageName]
            ?: return RuleDecision.NoRule

        if (!rule.isEnabled || rule.mode == "REAL_PASSTHROUGH") {
            return RuleDecision.PassThrough
        }
        return RuleDecision.Spoof(rule)
    }
}
