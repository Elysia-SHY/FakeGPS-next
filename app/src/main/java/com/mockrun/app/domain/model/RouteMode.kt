package com.mockrun.app.domain.model

/**
 * 路线运行方式。
 *
 * - [ONE_WAY]   起点 → 终点，到站自动停止（与 v2.2.0 行为一致，向后兼容）
 * - [PING_PONG] 到终点原路返回，到起点再掉头，循环往复，只有用户手动停止
 * - [LOOP]      首尾相连绕圈，一圈接一圈，只有用户手动停止
 */
enum class RouteMode(
    val label: String,
    val hint: String,
    val autoStops: Boolean
) {
    ONE_WAY("单程", "走到终点自动停止", true),
    PING_PONG("折返", "到终点原路返回，循环往复", false),
    LOOP("环形", "首尾相连绕圈，无限循环", false);

    companion object {
        fun fromName(raw: String?): RouteMode =
            entries.firstOrNull { it.name == raw } ?: ONE_WAY
    }
}
