package com.mockrun.app.core.data.version

/**
 * 语义化版本比较。
 *
 * 从 `VersionSyncManager` 中提取为独立的纯对象，原因有二：
 *  1. 原实现是 private 方法，无法被单元测试覆盖 —— 而「误报有新版本」是
 *     用户可见的缺陷，值得用测试固定住行为。
 *  2. 该比较的输入来自 GitHub Release 的 tag 名（外部数据），
 *     解析必须对畸形输入健壮。
 *
 * 比较规则：
 *  - 去掉前缀 `v`，按 `.` 分段，逐段取**数字前缀**后数值比较
 *  - 缺失的段按 0 处理（`1.2` == `1.2.0`）
 *  - 全非数字的输入退化为 [0]
 *
 * ## 为什么取「数字前缀」而不是「整段必须是数字」
 *
 * 原实现用 `toIntOrNull()`，整段非数字就整段丢弃。这会在语义化版本的
 * 预发布/构建元数据上产生错误结果：
 *
 * | tag           | 原解析结果  | 问题                        |
 * |---------------|-------------|-----------------------------|
 * | `1.2.3-beta`  | `[1, 2]`    | 段数少了，被判为比 1.2.3 更旧 |
 * | `1.2.3+build.7` | `[1, 2, 7]` | 元数据段被当成版本段，判为更新 |
 *
 * 改为提取每段的数字前缀后：`1.2.3-beta` -> `[1,2,3]`（与 `1.2.3` 相等），
 * `1.2.3+build.7` -> `[1,2,3]`（同上）。预发布标识在 SemVer 里本就
 * 优先于正式版，但本项目只用 tag 判断「是否更新」，
 * 把 `1.2.3-beta` 视为等于 `1.2.3` 是更安全的取法 —— 不会误报更新。
 */
object VersionComparator {

    /** @return 负数表示 [v1] 更旧，0 表示相等，正数表示 [v1] 更新 */
    fun compareSemver(v1: String, v2: String): Int {
        val s1 = parse(v1)
        val s2 = parse(v2)
        val maxLen = maxOf(s1.size, s2.size)
        for (i in 0 until maxLen) {
            val p1 = s1.getOrElse(i) { 0 }
            val p2 = s2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    /** 远端版本是否严格新于本地版本 */
    fun isNewer(remoteTag: String, localVersionName: String): Boolean =
        compareSemver(remoteTag, localVersionName) > 0

    /**
     * 解析版本字符串为数字段列表。
     *
     * 每段取开头的连续数字；没有数字前缀的段被丢弃。
     * 例：`v1.2.3-beta` -> [1, 2, 3]；`1.2.3+build.7` -> [1, 2, 3]。
     */
    private fun parse(v: String): List<Int> {
        val body = v.trim().removePrefix("v").substringBefore('+')
        val nums = body.split(".").mapNotNull { segment ->
            segment.trim().takeWhile { it.isDigit() }.toIntOrNull()
        }
        // 全非数字（如 "latest"）时视为 0，避免空列表导致比较结果不可预期
        return nums.ifEmpty { listOf(0) }
    }
}
