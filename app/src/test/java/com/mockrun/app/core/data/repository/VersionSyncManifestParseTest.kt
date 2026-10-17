package com.mockrun.app.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VersionSyncManifestParseTest {

    @Test
    fun `parseOwnManifest strips closed aqvtc_icu urls and builds multi-cdn fallback queue`() {
        val sampleJson = """
            {
              "versionName": "v9.9.0",
              "versionCode": 990,
              "releaseDate": "2026-10-13",
              "releaseNotes": "迁移在线更新服务与毛玻璃优化",
              "releaseUrl": "https://aqvtc.icu/#changelog",
              "downloadUrl": "https://aqvtc.icu/downloads/latest.apk",
              "downloadUrlVersioned": "https://aqvtc.icu/downloads/FakeGPS-next-v9.9.0-release.apk",
              "lanzouUrl": "https://wwbto.lanzouv.com/i4Fbv4bsiced",
              "lanzouPassword": "cnvh",
              "mirrorUrls": [
                "https://aqvtc.icu/dead.apk",
                "https://cdn.jsdelivr.net/gh/Elysia-SHY/FakeGPS-next@v9.9.0/FakeGPS-next-v9.9.0-release.apk"
              ],
              "sha256": "AE7F59D42356E85DCDC2EAF3BF58A35BEA526DBC529691EA25996E9CC9C148D7",
              "homepage": "https://aqvtc.icu/"
            }
        """.trimIndent()

        val status = VersionSyncManager.parseOwnManifest(sampleJson, 1000L, "jsDelivr CDN")
        assertNotNull("清单解析不应返回 null", status)
        assertTrue("v9.9.0 (code 990) 应判定为 HasUpdate", status is VersionSyncStatus.HasUpdate)

        val info = (status as VersionSyncStatus.HasUpdate).info
        assertEquals("v9.9.0", info.tagName)
        assertEquals(990, info.remoteVersionCode)
        assertEquals(
            "ae7f59d42356e85dcdc2eaf3bf58a35bea526dbc529691ea25996e9cc9c148d7",
            info.expectedSha256
        )
        assertFalse(
            "releaseHtmlUrl 不得指向已关闭的 aqvtc.icu",
            info.releaseHtmlUrl.contains("aqvtc.icu")
        )
        assertTrue(
            "fallbackDownloadUrls 不得包含任何 aqvtc.icu 地址",
            info.fallbackDownloadUrls.none { it.contains("aqvtc.icu") }
        )
        assertEquals(
            "https://cdn.jsdelivr.net/gh/Elysia-SHY/FakeGPS-next@v9.9.0/FakeGPS-next-v9.9.0-release.apk",
            info.fallbackDownloadUrls.first()
        )
        assertTrue(
            "旧清单里残留的蓝奏云地址不得进入下载队列",
            info.fallbackDownloadUrls.none { it.contains("lanzouv") }
        )
    }

    @Test
    fun `parseReleaseJson extracts versionCode and sha256 without legacy domain pollution`() {
        val releaseApiJson = """
            {
              "tag_name": "v9.9.1",
              "published_at": "2026-10-13T08:00:00Z",
              "html_url": "https://github.com/Elysia-SHY/FakeGPS-next/releases/tag/v9.9.1",
              "body": "Release notes\n\n---\n- `versionCode: 991`\n- `SHA-256: 116670144e8ddb3d46fb768b58cddb7420a3bd3a26d540109bd680100f1cfe8f`",
              "assets": [
                {
                  "name": "FakeGPS-next-v9.9.1-release.apk",
                  "browser_download_url": "https://github.com/Elysia-SHY/FakeGPS-next/releases/download/v9.9.1/FakeGPS-next-v9.9.1-release.apk"
                }
              ]
            }
        """.trimIndent()

        val status = VersionSyncManager.parseReleaseJson(releaseApiJson, 2000L, "GitHub")
        assertTrue("v9.9.1 应判定为 HasUpdate", status is VersionSyncStatus.HasUpdate)

        val info = (status as VersionSyncStatus.HasUpdate).info
        assertEquals(991, info.remoteVersionCode)
        assertEquals(
            "116670144e8ddb3d46fb768b58cddb7420a3bd3a26d540109bd680100f1cfe8f",
            info.expectedSha256
        )
        assertTrue(
            "GitHub Release 解析结果绝不能混入 aqvtc.icu",
            info.fallbackDownloadUrls.none { it.contains("aqvtc.icu") }
        )
        assertTrue(
            "首选 CDN 应为 jsDelivr",
            info.cdnDownloadUrl.startsWith("https://cdn.jsdelivr.net/gh/")
        )
    }

    @Test
    fun `repository version_json file is valid and free of closed domain`() {
        val candidates = listOf(
            File("../version.json"),
            File("version.json")
        )
        val versionFile = candidates.firstOrNull { it.exists() }
        assertNotNull("仓库根目录 version.json 必须存在", versionFile)

        val content = versionFile!!.readText(Charsets.UTF_8)
        assertFalse("version.json 不得再包含已关闭的 aqvtc.icu 域名", content.contains("aqvtc.icu"))

        val status = VersionSyncManager.parseOwnManifest(content, 3000L, "LocalRepo")
        assertNotNull("仓库 version.json 必须能被 VersionSyncManager 正常解析", status)
        assertTrue("当前版本解析应为 UpToDate", status is VersionSyncStatus.UpToDate)

        val info = (status as VersionSyncStatus.UpToDate).info
        assertEquals("v2.1.2", info.tagName)
        assertEquals(52, info.remoteVersionCode)
        assertEquals(
            "https://github.com/Elysia-SHY/FakeGPS-next/releases/tag/v2.1.2",
            info.releaseHtmlUrl
        )
        // 2026-10-16 发版时按 CHANGELOG 把 version.json 的下载地址从
        // `cdn.jsdelivr.net/gh/...@tag/...` 改成了 GitHub Release 资产地址
        // （`releases/download/{tag}/{file}.apk`），这里同步断言新的地址形态。
        // 旧断言停留在 jsDelivr 形式，自那次发版起一直是红的。
        assertEquals(
            "https://github.com/Elysia-SHY/FakeGPS-next/releases/download/v2.1.2/FakeGPS-next-v2.1.2-release.apk",
            info.fallbackDownloadUrls.first()
        )
        assertTrue(
            "必须保留多镜像回落列表（下载容灾）",
            info.fallbackDownloadUrls.isNotEmpty()
        )
        assertTrue("version.json 不得再包含蓝奏云字段", !content.contains("lanzou", ignoreCase = true))
    }

    @Test
    fun `version tag extraction from apk file name is exact`() {
        assertEquals("v2.1", VersionSyncManager.extractVersionTagFromFileName("FakeGPS-next-v2.1-release.apk"))
        assertEquals("v2.1.3", VersionSyncManager.extractVersionTagFromFileName("FakeGPS_2.1.3.apk"))
    }

    @Test
    fun `sha256Of computes exact digest for downloaded apk payload`() {
        val fakeApkPayload = ByteArray(1_200_000) { (it % 251).toByte() }
        val expectedSha = MessageDigest.getInstance("SHA-256")
            .digest(fakeApkPayload)
            .joinToString("") { "%02x".format(it) }

        val tmpFile = File.createTempFile("fakegps_verify_", ".apk")
        try {
            tmpFile.writeBytes(fakeApkPayload)
            val actualSha = VersionSyncManager.sha256Of(tmpFile)
            assertEquals("sha256Of 计算结果必须与标准 SHA-256 完全一致", expectedSha, actualSha)
        } finally {
            tmpFile.delete()
        }
    }
}
