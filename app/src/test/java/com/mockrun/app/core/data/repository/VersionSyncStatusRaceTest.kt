package com.mockrun.app.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：更新状态在「判定」与「强转」之间被改写时不得抛 ClassCastException。
 *
 * ## 背景
 *
 * 崩溃现场（AppNavigation.kt，已修复）：
 *
 * ```kotlin
 * if (showUpdatePrompt && updateStatus is VersionSyncStatus.HasUpdate) {
 *     val info = (updateStatus as VersionSyncStatus.HasUpdate).info   // ← 崩在这里
 * ```
 *
 * `updateStatus` 是 `by` 委托的 Compose `State`，由
 * [VersionSyncManager.checkForUpdates] 写入。`is` 判定与 `as` 强转是**两次
 * 独立读取**：判定通过后、强转之前若状态被改写（例如用户点了「检查更新」，
 * 状态回到 `Checking`），强转即抛 `ClassCastException`，且发生在组合期 ——
 * 整个界面挂掉。
 *
 * 该缺陷长期存在，但原先更新检查走 GitHub API，国内 10 秒超时后状态停在
 * `Error` 分支，很难命中 `HasUpdate`；改用响应更快的国内源后（该源已随更新回到
 * GitHub 单源一并移除），`Idle -> Checking -> HasUpdate` 快速连续翻转，才稳定触发。
 *
 * ## 修复
 *
 * 先取一次快照，之后判定与取值都只用这份快照：
 *
 * ```kotlin
 * val statusSnapshot = updateStatus
 * if (showUpdatePrompt && statusSnapshot is VersionSyncStatus.HasUpdate) {
 *     AppUpdateDialog(info = statusSnapshot.info, ...)
 * ```
 *
 * 下面的用例模拟「两次读取之间状态翻转」这一时序，验证快照写法不再崩。
 */
class VersionSyncStatusRaceTest {

    /** 模拟一个可被后台改写的状态持有者（等价于 Compose 的 mutableStateOf）。 */
    private class StateHolder(initial: VersionSyncStatus) {
        @Volatile
        var value: VersionSyncStatus = initial

        /** 模拟一次完整的更新检查：Idle -> Checking -> 结果。 */
        fun runCheck(result: VersionSyncStatus) {
            value = VersionSyncStatus.Checking
            value = result
        }
    }

    private fun hasUpdateInfo(): RemoteReleaseInfo = RemoteReleaseInfo(
        tagName = "v9.9.9",
        versionName = "v9.9.9",
        remoteVersionCode = 999,
        releaseNotes = "notes",
        publishedAt = "2026-10-10",
        releaseHtmlUrl = "https://github.com/Elysia-SHY/FakeGPS-next/releases/tag/v9.9.9",
        apkName = "FakeGPS-next-v9.9.9-release.apk",
        cdnDownloadUrl = "https://cdn.jsdelivr.net/gh/Elysia-SHY/FakeGPS-next@v9.9.9/FakeGPS-next-v9.9.9-release.apk",
        fallbackDownloadUrls = listOf("https://cdn.jsdelivr.net/gh/Elysia-SHY/FakeGPS-next@v9.9.9/FakeGPS-next-v9.9.9-release.apk")
    )

    // -------------------------------------------------------------------------
    // 缺陷复现：两次独立读取
    // -------------------------------------------------------------------------

    /**
     * 证明「先 is 判定、再 as 强转」在状态翻转时确实会抛。
     *
     * 这里用一个可注入的读取器模拟两次读取之间发生写入 —— 这正是真实场景：
     * Compose 的 `by` 委托每次访问都读当前值，两次访问之间允许其他协程写入。
     */
    @Test
    fun `two independent reads can throw ClassCastException when state flips`() {
        val holder = StateHolder(VersionSyncStatus.Idle)
        var reads = 0
        // 第一次读取（is 判定）看到 HasUpdate；第二次读取（as 强转）看到 Checking
        fun read(): VersionSyncStatus {
            reads++
            return if (reads == 1) {
                VersionSyncStatus.HasUpdate(hasUpdateInfo(), 0L)
            } else {
                VersionSyncStatus.Checking
            }
        }

        var threw = false
        try {
            if (read() is VersionSyncStatus.HasUpdate) {
                @Suppress("UNCHECKED_CAST")
                (read() as VersionSyncStatus.HasUpdate).info
            }
        } catch (e: ClassCastException) {
            threw = true
        }

        assertTrue("两次独立读取在状态翻转时应当抛 ClassCastException", threw)
        assertFalse("测试自身不应把状态写坏", holder.value is VersionSyncStatus.HasUpdate)
    }

    // -------------------------------------------------------------------------
    // 修复验证：单次快照
    // -------------------------------------------------------------------------

    @Test
    fun `snapshot pattern never throws and yields the info`() {
        val holder = StateHolder(VersionSyncStatus.Idle)
        val expected = hasUpdateInfo()
        holder.value = VersionSyncStatus.HasUpdate(expected, 123L)

        // 修复后的写法：取一次快照，判定与取值同源
        val statusSnapshot = holder.value
        var captured: RemoteReleaseInfo? = null
        if (statusSnapshot is VersionSyncStatus.HasUpdate) {
            captured = statusSnapshot.info
        }

        assertEquals(expected, captured)
        assertEquals(123L, (statusSnapshot as VersionSyncStatus.HasUpdate).checkedAt)
    }

    @Test
    fun `snapshot taken before a concurrent flip still uses the captured value`() {
        val holder = StateHolder(VersionSyncStatus.Idle)
        val expected = hasUpdateInfo()
        holder.value = VersionSyncStatus.HasUpdate(expected, 1L)

        // 快照先落地
        val statusSnapshot = holder.value
        // 随后后台把状态改回 Checking（模拟用户点「检查更新」）
        holder.runCheck(VersionSyncStatus.Checking)

        // 快照不受影响，判定与取值都走同一份，不会抛
        var captured: RemoteReleaseInfo? = null
        if (statusSnapshot is VersionSyncStatus.HasUpdate) {
            captured = statusSnapshot.info
        }
        assertEquals(expected, captured)
        assertTrue(holder.value is VersionSyncStatus.Checking)
    }

    // -------------------------------------------------------------------------
    // 覆盖所有状态分支，确认快照写法在每个分支都不抛
    // -------------------------------------------------------------------------

    @Test
    fun `snapshot pattern is safe across every status branch`() {
        val info = hasUpdateInfo()
        val all = listOf(
            VersionSyncStatus.Idle,
            VersionSyncStatus.Checking,
            VersionSyncStatus.UpToDate(info, 2L),
            VersionSyncStatus.HasUpdate(info, 3L),
            VersionSyncStatus.Error("boom", 4L)
        )

        for (status in all) {
            val snapshot = status
            // 与 AboutScreen 中的 when 分支同构
            val label = when (snapshot) {
                is VersionSyncStatus.Checking -> "checking"
                is VersionSyncStatus.UpToDate -> snapshot.info.versionName
                is VersionSyncStatus.HasUpdate -> snapshot.info.versionName
                is VersionSyncStatus.Error -> snapshot.message
                is VersionSyncStatus.Idle -> "idle"
            }
            assertTrue("分支 $status 应当产出非空文案", label.isNotEmpty())
        }
    }

    @Test
    fun `error branch exposes its message via snapshot`() {
        val snapshot: VersionSyncStatus = VersionSyncStatus.Error("网络不可达", 5L)
        val message = when (snapshot) {
            is VersionSyncStatus.Error -> (snapshot as VersionSyncStatus.Error).message
            else -> ""
        }
        assertEquals("网络不可达", message)
    }

    @Test
    fun `nullable cast on snapshot yields null instead of throwing`() {
        val snapshot: VersionSyncStatus = VersionSyncStatus.Checking
        // AboutScreen 用的是 as? —— 必须安全返回 null
        val checkedAt = (snapshot as? VersionSyncStatus.UpToDate)?.checkedAt
        assertEquals(null, checkedAt)
    }

    // -------------------------------------------------------------------------
    // OTA 清单解析：字段缺失时不得抛
    // -------------------------------------------------------------------------

    @Test
    fun `own manifest urls keep server order and drop duplicates`() {
        // 复刻 VersionSyncManager.parseOwnManifest 里的下载源构造
        fun buildUrls(versioned: String, latest: String): List<String> {
            val urls = mutableListOf<String>()
            if (versioned.isNotBlank()) urls.add(versioned)
            if (latest.isNotBlank() && latest != versioned) urls.add(latest)
            return urls
        }

        val v = "https://cdn.jsdelivr.net/gh/Elysia-SHY/FakeGPS-next@v2.1/FakeGPS-next-v2.1-release.apk"
        val l = "https://fastly.jsdelivr.net/gh/Elysia-SHY/FakeGPS-next@v2.1/FakeGPS-next-v2.1-release.apk"

        assertEquals(listOf(v, l), buildUrls(v, l))
        assertEquals(listOf(v), buildUrls(v, v))
        assertEquals(listOf(l), buildUrls("", l))
        assertEquals(emptyList<String>(), buildUrls("", ""))
    }

    @Test
    fun `closed legacy domain aqvtc_icu is strictly filtered out from fallback urls`() {
        val urls = VersionSyncManager.buildFallbackDownloadUrls(
            tagName = "v2.1",
            apkName = "FakeGPS-next-v2.1-release.apk",
            versionedUrl = "https://aqvtc.icu/downloads/FakeGPS-next-v2.1-release.apk",
            latestUrl = "https://aqvtc.icu/downloads/latest.apk",
            extraMirrors = listOf(
                "https://aqvtc.icu/mirror.apk",
                "https://mirror.example.com/FakeGPS-next-v2.1-release.apk"
            )
        )

        assertTrue("必须剔除所有已关闭的 aqvtc.icu 域名", urls.none { it.contains("aqvtc.icu") })
        assertTrue("不得再出现蓝奏云地址", urls.none { it.contains("lanzou") })
        assertEquals(
            "剔除关闭域名后，队列首项应为清单提供的有效镜像",
            "https://mirror.example.com/FakeGPS-next-v2.1-release.apk",
            urls.first()
        )
        assertTrue("自定义有效镜像应保留", urls.contains("https://mirror.example.com/FakeGPS-next-v2.1-release.apk"))
        assertTrue("jsDelivr CDN 节点应包含在内", urls.any { it.contains("cdn.jsdelivr.net") })
        assertTrue("Fastly jsDelivr 节点应包含在内", urls.any { it.contains("fastly.jsdelivr.net") })
    }

    @Test
    fun `extractVersionCode and extractSha256 parse release body metadata`() {
        val body = """
            FakeGPS-next v2.1 更新说明
            ---
            - `versionCode: 50`
            - `SHA-256: ae7f59d42356e85dcdc2eaf3bf58a35bea526dbc529691ea25996e9cc9c148d7`
        """.trimIndent()

        assertEquals(50, VersionSyncManager.extractVersionCode(body))
        assertEquals(
            "ae7f59d42356e85dcdc2eaf3bf58a35bea526dbc529691ea25996e9cc9c148d7",
            VersionSyncManager.extractSha256(body)
        )
    }

    @Test
    fun `sha256 comparison rejects mismatch and short input`() {
        // 复刻 VersionSyncManager.startDownload 的校验判定
        fun verify(expected: String, actual: String): Boolean {
            val e = expected.trim().lowercase()
            if (e.length != 64) return true // 未提供哈希 -> 跳过（交由签名校验）
            return actual.isNotBlank() && actual == e
        }

        val good = "77c865eaa8c95455af8d1622f136081bff62e16955aea41bffb61b5f806dceda"
        assertTrue(verify(good, good))
        assertFalse(verify(good, good.dropLast(1) + "0"))
        assertFalse(verify(good, ""))
        assertTrue("清单未提供 sha256 时应跳过哈希校验", verify("", "anything"))
    }
}
