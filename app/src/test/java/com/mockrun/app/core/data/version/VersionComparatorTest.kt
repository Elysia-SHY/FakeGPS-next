package com.mockrun.app.core.data.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 语义化版本比较的回归测试。
 *
 * 该逻辑的输入是 GitHub Release 的 tag 名（外部数据），
 * 判断结果直接决定「是否提示用户有新版本」——
 * 误报会让用户看到一个并不存在的更新，漏报会让用户错过真实更新。
 */
class VersionComparatorTest {

    // ------------------------------------------------------------ 基本比较

    @Test
    fun `equal versions compare equal`() {
        assertEquals(0, VersionComparator.compareSemver("1.2.3", "1.2.3"))
    }

    @Test
    fun `patch bump is newer`() {
        assertTrue(VersionComparator.compareSemver("1.2.4", "1.2.3") > 0)
        assertTrue(VersionComparator.compareSemver("1.2.3", "1.2.4") < 0)
    }

    @Test
    fun `minor bump beats patch bump`() {
        assertTrue(VersionComparator.compareSemver("1.3.0", "1.2.99") > 0)
    }

    @Test
    fun `major bump beats minor bump`() {
        assertTrue(VersionComparator.compareSemver("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun `numeric comparison not lexicographic`() {
        // 字符串比较会认为 "1.9.0" > "1.10.0"，数值比较才是对的
        assertTrue(VersionComparator.compareSemver("1.10.0", "1.9.0") > 0)
        assertTrue(VersionComparator.compareSemver("1.100.0", "1.99.0") > 0)
    }

    // ------------------------------------------------------------ v 前缀

    @Test
    fun `leading v is ignored`() {
        assertEquals(0, VersionComparator.compareSemver("v1.2.3", "1.2.3"))
        assertEquals(0, VersionComparator.compareSemver("v1.2.3", "v1.2.3"))
    }

    @Test
    fun `project tag format compares correctly`() {
        // 本项目实际使用的 tag 格式
        assertTrue(VersionComparator.compareSemver("v2.0.0", "v1.6.1") > 0)
        assertTrue(VersionComparator.compareSemver("v1.6.1", "v1.6.0") > 0)
        assertFalse(VersionComparator.compareSemver("v1.6.1", "v2.0.0") > 0)
    }

    // ------------------------------------------------------------ 段数不等

    @Test
    fun `missing segments treated as zero`() {
        assertEquals(0, VersionComparator.compareSemver("1.2", "1.2.0"))
        assertEquals(0, VersionComparator.compareSemver("1.2.0.0", "1.2"))
        assertTrue(VersionComparator.compareSemver("1.2.1", "1.2") > 0)
    }

    @Test
    fun `two segment tag against three segment local`() {
        assertTrue(VersionComparator.compareSemver("v2.1", "v2.0.0") > 0)
        assertFalse(VersionComparator.compareSemver("v2.0", "v2.0.5") > 0)
    }

    // ------------------------------------------------------------ 畸形输入

    @Test
    fun `prerelease suffix keeps its version segment`() {
        // 关键回归：原实现用 toIntOrNull()，整段非数字就整段丢弃，
        // "1.2.3-beta" 会被解析成 [1,2]（少了第三段），
        // 于是被判为比 1.2.3 更旧。改为取数字前缀后应与 1.2.3 相等。
        assertEquals(0, VersionComparator.compareSemver("1.2.3-beta", "1.2.3"))
        assertEquals(0, VersionComparator.compareSemver("v1.2.3-rc1", "1.2.3"))
    }

    @Test
    fun `build metadata does not become a version segment`() {
        // 关键回归：原实现把 "1.2.3+build.7" 解析成 [1,2,3,7]，
        // 多出的第 4 段让它被判为更新。build metadata 不应参与比较。
        assertEquals(0, VersionComparator.compareSemver("1.2.3+build.7", "1.2.3"))
        assertEquals(0, VersionComparator.compareSemver("v2.0.0+build.1234", "v2.0.0"))
    }

    @Test
    fun `prerelease is not treated as newer than release`() {
        assertFalse(VersionComparator.isNewer("v2.0.0-beta", "v2.0.0"))
        assertFalse(VersionComparator.isNewer("v2.0.0-rc1", "v1.9.0".let { "v2.0.0" }))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals(0, VersionComparator.compareSemver("  1.2.3  ", "1.2.3"))
        assertEquals(0, VersionComparator.compareSemver("v 1.2.3".replace("v ", "v"), "1.2.3"))
    }

    @Test
    fun `fully non numeric input degrades to zero`() {
        // 不应抛异常，也不应返回不可预期的结果
        assertEquals(0, VersionComparator.compareSemver("latest", "0.0.0"))
        assertTrue(VersionComparator.compareSemver("1.0.0", "latest") > 0)
    }

    @Test
    fun `empty strings do not crash`() {
        assertEquals(0, VersionComparator.compareSemver("", ""))
        assertEquals(0, VersionComparator.compareSemver("v", ""))
        assertEquals(0, VersionComparator.compareSemver("", "0.0.0"))
    }

    @Test
    fun `interior non numeric segment is dropped`() {
        // "1.x.3" -> x 段无数字前缀被丢弃 -> [1, 3]，落在第 2 段
        // 这里只固定「不崩溃且可比较」，避免上游改动悄悄改变语义
        val r = VersionComparator.compareSemver("1.x.3", "1.0.0")
        assertTrue("实测 $r，应判定为更新", r > 0)
    }

    // ------------------------------------------------------------ isNewer

    @Test
    fun `isNewer mirrors compareSemver sign`() {
        assertTrue(VersionComparator.isNewer("v2.0.0", "v1.6.1"))
        assertFalse(VersionComparator.isNewer("v1.6.1", "v2.0.0"))
        assertFalse(VersionComparator.isNewer("v2.0.0", "v2.0.0"))
    }

    @Test
    fun `current release is not newer than itself`() {
        // 关键回归：当前版本 v2.0.0 对自身不应报「有新版本」
        assertFalse(VersionComparator.isNewer("v2.0.0", "v2.0.0"))
    }

    @Test
    fun `isNewer is antisymmetric`() {
        val a = "v1.6.1"
        val b = "v2.0.0"
        assertTrue(VersionComparator.isNewer(b, a))
        assertFalse(VersionComparator.isNewer(a, b))
    }
}
