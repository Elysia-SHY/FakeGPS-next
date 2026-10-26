package com.mockrun.app.core.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import com.mockrun.app.BuildConfig
import com.mockrun.app.core.data.version.VersionComparator
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * 更新信息。
 *
 * 更新来源为 GitHub 单源，两条互为备份的链路共用这一个结构：
 *   - 静态更新清单 version.json（jsDelivr 多 CDN 节点分发，读取仓库根清单）
 *   - GitHub Releases API（多镜像兜底）
 */
data class RemoteReleaseInfo(
    val tagName: String,
    val versionName: String,
    val remoteVersionCode: Int,
    val releaseNotes: String,
    val publishedAt: String,
    val releaseHtmlUrl: String,
    val apkName: String,
    val cdnDownloadUrl: String,
    val fallbackDownloadUrls: List<String>,
    /** 期望的 APK SHA-256（小写十六进制）；清单或说明中声明时用于强制校验 */
    val expectedSha256: String = "",
    /** 联系邮箱，用于「反馈」入口 */
    val contactEmail: String = VersionSyncManager.CONTACT_EMAIL,
    /** 本次检查的来源标签，展示在 UI 上 */
    val sourceLabel: String = "GitHub Releases"
) {
    val releaseDate: String get() = publishedAt
    val releaseUrl: String get() = releaseHtmlUrl
    /** 浏览器兜底下载地址：清单 / CDN 声明的首选直链 */
    val downloadUrl: String get() = cdnDownloadUrl
}

typealias RemoteVersionInfo = RemoteReleaseInfo

sealed class VersionSyncStatus {
    object Idle : VersionSyncStatus()
    object Checking : VersionSyncStatus()
    data class UpToDate(val info: RemoteReleaseInfo, val checkedAt: Long) : VersionSyncStatus()
    data class HasUpdate(val info: RemoteReleaseInfo, val checkedAt: Long) : VersionSyncStatus()
    data class Error(val message: String, val checkedAt: Long) : VersionSyncStatus()
}

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val currentSource: String = "GitHub Releases"
    ) : DownloadStatus()
    data class Success(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}

object VersionSyncManager {
    private const val TAG = "VersionSync"

    // =========================================================================
    // GitHub 单源 OTA 架构
    //
    // 背景：原官网域名（aqvtc.icu）因未完成 ICP 备案已关闭停用。
    // 现更新链路只剩 GitHub 一个来源：
    //   1) 多 CDN 静态清单 version.json 轮询（次源：jsDelivr Global/Fastly/Gcore + gh-proxy + raw）
    //   2) GitHub Releases API 多镜像兜底（备选源）
    // 下载完成后强制执行 SHA-256（若提供）与 APK 签名证书双重校验。
    // =========================================================================

    private const val GITHUB_OWNER = "Elysia-SHY"
    private const val GITHUB_REPO = "FakeGPS-next"

    /** 项目发布与说明入口（GitHub 仓库主页） */
    const val HOMEPAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
    const val CHANGELOG_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/blob/main/CHANGELOG.md"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    /** 联系邮箱 */
    const val CONTACT_EMAIL = "2224958076@qq.com"

    /**
     * 静态 OTA 清单多节点列表（与仓库根目录 version.json 同源）。
     *
     * ### 为什么带查询串
     *
     * jsDelivr 的 `@main` 分支资源默认 `s-maxage=43200`（12 小时）。发版后如果用户的网络
     * 落到某个还没刷新的边缘节点（国内最常见的是 cdn / fastly 不通、最后落到 gcore），
     * 就会读到**旧清单**，表现为「应用内收不到更新推送」，而 GitHub 上其实已经发了新版。
     *
     * 这里按「版本号 + 小时」拼一个查询串：既是新的 CDN 缓存键，又不会改变文件内容。
     * 代价是每小时最多回源一次，最坏陈旧窗口从 12 小时压到 1 小时。
     */
    private fun manifestCacheBuster(): String {
        val hourBucket = System.currentTimeMillis() / 3_600_000L
        return "?v=${BuildConfig.VERSION_CODE}-$hourBucket"
    }

    internal val STATIC_MANIFEST_URLS: List<String>
        get() {
            val bust = manifestCacheBuster()
            // 顺序即优先级：raw / gh-proxy 只有 5 分钟缓存，jsDelivr 的 @main 是 12 小时。
            // 实测 jsDelivr 按**路径**做缓存键，查询串并不能穿透它（带不带 ?v= 都返回同一份旧内容），
            // 所以只能把它排到最后，当兜底用。
            return listOf(
                "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/version.json$bust",
                "https://gh-proxy.com/https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/version.json$bust",
                "https://ghproxy.net/https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/version.json$bust",
                "https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@main/version.json$bust",
                "https://fastly.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@main/version.json$bust",
                "https://gcore.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@main/version.json$bust"
            )
        }

    private const val GITHUB_RELEASE_API =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    internal val GITHUB_RELEASE_API_ENDPOINTS = listOf(
        GITHUB_RELEASE_API,
        "https://gh-proxy.com/https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest",
        "https://ghproxy.net/https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest",
        "https://gh.llkk.cc/https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    )

    internal const val UNKNOWN_VERSION_CODE = -1

    private val VERSION_CODE_REGEX = Regex("(?i)versionCode\\s*[:=]\\s*(\\d+)")
    private val SHA256_REGEX = Regex("(?i)(?:sha-?256\\s*[:=]\\s*|\\b)([0-9a-f]{64})\\b")
    private val APK_VERSION_TAG_REGEX = Regex("(?i)(?:FakeGPS(?:-next)?[-_])?(v\\d+\\.\\d+(?:\\.\\d+)?)|(\\d+\\.\\d+(?:\\.\\d+)?)")

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile; FakeGPS-next) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /**
     * 下载链路（jsDelivr / Release 资产 / 加速镜像）统一使用的桌面端 User-Agent：
     * 部分节点对移动端 UA 只下发缩减页或直接拒绝，桌面端 UA 可稳定拿到完整的
     * 重定向链与二进制流。
     */
    internal const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val _status = mutableStateOf<VersionSyncStatus>(VersionSyncStatus.Idle)
    val status: State<VersionSyncStatus> = _status

    private val _downloadStatus = mutableStateOf<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: State<DownloadStatus> = _downloadStatus

    private val _showUpdatePrompt = mutableStateOf(false)
    val showUpdatePrompt: State<Boolean> = _showUpdatePrompt

    private var downloadJob: Job? = null
    private var lastCheckTime: Long = 0L

    /**
     * 判断 URL 是否指向已停用的旧官网域名（aqvtc.icu）。
     */
    internal fun isClosedLegacyDomain(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.isEmpty() || trimmed.contains("aqvtc.icu")
    }

    fun dismissUpdatePrompt() {
        _showUpdatePrompt.value = false
    }

    fun openUpdatePrompt() {
        if (_status.value is VersionSyncStatus.HasUpdate) {
            _showUpdatePrompt.value = true
        }
    }

    /**
     * 检查云端新版本（GitHub 单源：静态清单优先，GitHub Release API 兜底）。
     */
    suspend fun checkForUpdates(force: Boolean = false): VersionSyncStatus {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckTime < 10_000L &&
            _status.value !is VersionSyncStatus.Idle &&
            _status.value !is VersionSyncStatus.Error
        ) {
            return _status.value
        }

        withContext(Dispatchers.Main) {
            _status.value = VersionSyncStatus.Checking
        }

        val result = withContext(Dispatchers.IO) {
            fetchRelease()
        }

        lastCheckTime = now
        withContext(Dispatchers.Main) {
            _status.value = result
            if (result is VersionSyncStatus.HasUpdate) {
                _showUpdatePrompt.value = true
            }
        }

        return result
    }

    /**
     * 拉取更新信息（GitHub 单源）：
     *  1) 先尝试多 CDN 静态清单 version.json（若可达，包含完整日志与 SHA-256）
     *  2) 再来 GitHub Release API 及其镜像兜底
     */
    private fun fetchRelease(): VersionSyncStatus {
        val now = System.currentTimeMillis()

        // 1) 静态清单多源轮询
        fetchStaticManifest(now)?.let { return it }

        // 2) GitHub Releases API 多源轮询（备选）
        return fetchReleaseFromGitHub(now)
    }

    internal fun extractVersionTagFromFileName(text: String): String? {
        val m = APK_VERSION_TAG_REGEX.find(text) ?: return null
        val raw = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
        if (raw.isEmpty()) return null
        return if (raw.startsWith("v", ignoreCase = true)) "v${raw.drop(1)}" else "v$raw"
    }

    /**
     * 从多 CDN 静态节点读取 version.json 清单。
     */
    private fun fetchStaticManifest(now: Long): VersionSyncStatus? {
        for (manifestUrl in STATIC_MANIFEST_URLS) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                    setRequestProperty("Cache-Control", "no-cache")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val sourceLabel = resolveSourceLabel(manifestUrl)
                    parseOwnManifest(body, now, sourceLabel)?.let { return it }
                }
            } catch (e: Exception) {
                Diag.d(TAG, "静态清单节点不可用 ($manifestUrl)：${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    /**
     * 解析 version.json 清单。
     */
    internal fun parseOwnManifest(
        jsonString: String,
        checkedAt: Long,
        sourceLabel: String = "云端清单"
    ): VersionSyncStatus? {
        return try {
            val json = JSONObject(jsonString)
            val rawVersionName = json.optString("versionName", "").trim()
            if (rawVersionName.isEmpty()) return null
            val tagName = if (rawVersionName.startsWith("v", ignoreCase = true)) {
                rawVersionName
            } else {
                "v$rawVersionName"
            }

            val remoteCode = json.optInt("versionCode", UNKNOWN_VERSION_CODE)
            val notes = json.optString("releaseNotes", "暂无更新日志说明。")
            val publishedAt = json.optString("releaseDate", "")
            val expectedSha = json.optString("sha256", "").trim().lowercase()

            val rawReleaseUrl = json.optString("releaseUrl", "").trim()
            val releaseHtmlUrl = if (!isClosedLegacyDomain(rawReleaseUrl)) {
                rawReleaseUrl
            } else {
                "$RELEASES_PAGE_URL/tag/$tagName"
            }

            val versionedUrl = json.optString("downloadUrlVersioned", "").trim()
            val latestUrl = json.optString("downloadUrl", "").trim()
            val apkName = versionedUrl.substringAfterLast('/')
                .takeIf { it.endsWith(".apk", ignoreCase = true) }
                ?: "FakeGPS-next-$tagName-release.apk"

            val mirrorList = mutableListOf<String>()
            json.optJSONArray("mirrorUrls")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i, "").trim()
                    if (u.isNotBlank() && !isClosedLegacyDomain(u)) {
                        mirrorList.add(u)
                    }
                }
            }

            val urls = buildFallbackDownloadUrls(
                tagName = tagName,
                apkName = apkName,
                versionedUrl = versionedUrl,
                latestUrl = latestUrl,
                extraMirrors = mirrorList
            )
            if (urls.isEmpty()) return null

            val info = RemoteReleaseInfo(
                tagName = tagName,
                versionName = rawVersionName,
                remoteVersionCode = remoteCode,
                releaseNotes = notes,
                publishedAt = publishedAt,
                releaseHtmlUrl = releaseHtmlUrl,
                apkName = apkName,
                cdnDownloadUrl = urls.first(),
                fallbackDownloadUrls = urls,
                expectedSha256 = expectedSha,
                contactEmail = json.optString("contact", CONTACT_EMAIL).ifBlank { CONTACT_EMAIL },
                sourceLabel = sourceLabel
            )

            if (isRemoteNewer(info)) {
                VersionSyncStatus.HasUpdate(info, checkedAt)
            } else {
                VersionSyncStatus.UpToDate(info, checkedAt)
            }
        } catch (e: Exception) {
            Diag.w(TAG, "静态清单解析失败，回退备用源", e)
            null
        }
    }

    /**
     * 组装去重且剔除旧关闭域名后的多源下载列表。
     */
    internal fun buildFallbackDownloadUrls(
        tagName: String,
        apkName: String,
        versionedUrl: String = "",
        latestUrl: String = "",
        extraMirrors: List<String> = emptyList(),
        githubBrowserDownloadUrl: String = ""
    ): List<String> {
        val result = LinkedHashSet<String>()

        fun addIfValid(url: String) {
            val trimmed = url.trim()
            if (trimmed.isNotEmpty() && !isClosedLegacyDomain(trimmed)) {
                result.add(trimmed)
            }
        }

        // 1. 清单显式指定的 GitHub 下载直链与自定义镜像
        addIfValid(versionedUrl)
        addIfValid(latestUrl)
        extraMirrors.forEach { addIfValid(it) }

        // 2. 免备案多 CDN 节点（jsDelivr Global / Fastly / Gcore）
        if (tagName.isNotBlank() && apkName.isNotBlank()) {
            addIfValid("https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@$tagName/$apkName")
            addIfValid("https://fastly.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@$tagName/$apkName")
            addIfValid("https://gcore.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@$tagName/$apkName")
        }

        // 3. GitHub Release 资产加速镜像与官方源
        val releaseAssetUrl = githubBrowserDownloadUrl.ifBlank {
            if (tagName.isNotBlank() && apkName.isNotBlank()) {
                "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/$tagName/$apkName"
            } else ""
        }
        if (releaseAssetUrl.isNotBlank()) {
            addIfValid("https://gh-proxy.com/$releaseAssetUrl")
            addIfValid("https://ghproxy.net/$releaseAssetUrl")
            addIfValid("https://gh.llkk.cc/$releaseAssetUrl")
            addIfValid(releaseAssetUrl)
        }

        return result.toList()
    }

    private fun fetchReleaseFromGitHub(now: Long = System.currentTimeMillis()): VersionSyncStatus {
        var lastErrorMessage = "未知网络错误"

        for (endpoint in GITHUB_RELEASE_API_ENDPOINTS) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    return parseReleaseJson(body, now, resolveSourceLabel(endpoint))
                } else if (responseCode == 403) {
                    lastErrorMessage = "云端接口访问受限或触发频率限制 (HTTP 403)"
                } else if (responseCode == 404) {
                    lastErrorMessage = "未找到已发布的 Release 版本 (HTTP 404)"
                } else {
                    lastErrorMessage = "更新服务请求失败 (HTTP $responseCode)"
                }
            } catch (e: SocketTimeoutException) {
                lastErrorMessage = "连接更新节点超时，请检查网络环境"
            } catch (e: UnknownHostException) {
                lastErrorMessage = "无法解析更新节点域名，当前网络不可达"
            } catch (e: Exception) {
                lastErrorMessage = "更新检查失败: " + (e.message ?: "未知异常")
            } finally {
                conn?.disconnect()
            }
        }

        return VersionSyncStatus.Error(lastErrorMessage, now)
    }

    internal fun parseReleaseJson(
        jsonString: String,
        checkedAt: Long,
        sourceLabel: String = "GitHub"
    ): VersionSyncStatus {
        return try {
            val json = JSONObject(jsonString)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) {
                return VersionSyncStatus.Error("Release 响应缺少版本标签 (tag_name)", checkedAt)
            }

            val body = json.optString("body", "暂无更新日志说明。")
            val publishedAt = json.optString("published_at", "").take(10)
            val htmlUrl = json.optString("html_url", "$RELEASES_PAGE_URL/tag/$tagName")

            val assets = json.optJSONArray("assets")
            if (assets == null || assets.length() == 0) {
                return VersionSyncStatus.Error("最新 Release 尚未上传 APK 安装包", checkedAt)
            }

            var apkName = ""
            var browserDownloadUrl = ""

            // 优先精确匹配 CI 产出名 FakeGPS-next-<tag>-release.apk
            // （见 .github/workflows/release.yml），避免多 ABI / split 资产并存时选错包。
            // 精确匹配不到时，再回落到下面的通用挑选规则。
            val preferredApkName = "FakeGPS-next-$tagName-release.apk"
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.equals(preferredApkName, ignoreCase = true)) {
                    apkName = name
                    browserDownloadUrl = asset.optString("browser_download_url", "")
                    break
                }
            }

            if (apkName.isEmpty()) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        if (!name.contains("debug", ignoreCase = true) || apkName.isEmpty()) {
                            apkName = name
                            browserDownloadUrl = asset.optString("browser_download_url", "")
                        }
                    }
                }
            }

            if (apkName.isEmpty()) {
                return VersionSyncStatus.Error("Release 中未发现可用的 APK 安装包", checkedAt)
            }

            val fallbackUrls = buildFallbackDownloadUrls(
                tagName = tagName,
                apkName = apkName,
                githubBrowserDownloadUrl = browserDownloadUrl
            )

            val remoteCode = extractVersionCode(body)
            val expectedSha = extractSha256(body)
            val releaseInfo = RemoteReleaseInfo(
                tagName = tagName,
                versionName = tagName,
                remoteVersionCode = remoteCode,
                releaseNotes = body,
                publishedAt = publishedAt,
                releaseHtmlUrl = htmlUrl,
                apkName = apkName,
                cdnDownloadUrl = fallbackUrls.first(),
                fallbackDownloadUrls = fallbackUrls,
                expectedSha256 = expectedSha,
                contactEmail = CONTACT_EMAIL,
                sourceLabel = sourceLabel
            )

            if (isRemoteNewer(releaseInfo)) {
                VersionSyncStatus.HasUpdate(releaseInfo, checkedAt)
            } else {
                VersionSyncStatus.UpToDate(releaseInfo, checkedAt)
            }
        } catch (e: Exception) {
            VersionSyncStatus.Error("JSON 解析失败: " + (e.message ?: "数据格式无效"), checkedAt)
        }
    }

    internal fun resolveSourceLabel(urlStr: String): String = when {
        urlStr.contains("fastly.jsdelivr") -> "jsDelivr (Fastly)"
        urlStr.contains("gcore.jsdelivr") -> "jsDelivr (Gcore)"
        urlStr.contains("jsdelivr") -> "jsDelivr CDN"
        urlStr.contains("ghproxy") || urlStr.contains("gh-proxy") || urlStr.contains("gh.llkk") -> "加速镜像"
        else -> "GitHub 官方源"
    }

    // =========================================================================
    // In-App APK Download & Installation
    // =========================================================================

    fun startDownload(context: Context, info: RemoteReleaseInfo) {
        if (_downloadStatus.value is DownloadStatus.Downloading) return

        downloadJob?.cancel()
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            _downloadStatus.value = DownloadStatus.Downloading(0f, 0L, 0L, 0L, info.sourceLabel)

            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val safeTag = info.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(targetDir, "FakeGPS-$safeTag.apk")
            val tempFile = File(targetDir, "FakeGPS-$safeTag.apk.tmp")

            if (tempFile.exists()) tempFile.delete()

            var downloadSuccess = false
            var lastError = "下载失败：所有节点均不可用"

            val candidateUrls = info.fallbackDownloadUrls.filterNot { isClosedLegacyDomain(it) }

            for (rawUrlStr in candidateUrls) {
                if (!isActive) break
                val sourceLabel = resolveSourceLabel(rawUrlStr)

                _downloadStatus.value = DownloadStatus.Downloading(0f, 0L, 0L, 0L, sourceLabel)

                try {
                    val (conn, inputStream) = openConnectionWithRedirects(rawUrlStr, 15_000)
                    val totalBytes = conn.contentLengthLong

                    inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(32768)
                            var bytesRead: Int
                            var totalDownloaded = 0L
                            var lastReportTime = System.currentTimeMillis()
                            var bytesSinceLastReport = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!isActive) {
                                    tempFile.delete()
                                    _downloadStatus.value = DownloadStatus.Idle
                                    return@launch
                                }
                                output.write(buffer, 0, bytesRead)
                                totalDownloaded += bytesRead
                                bytesSinceLastReport += bytesRead

                                val currentTime = System.currentTimeMillis()
                                val timeDiff = currentTime - lastReportTime
                                if (timeDiff >= 300L) {
                                    val speed = (bytesSinceLastReport * 1000L) / timeDiff.coerceAtLeast(1L)
                                    val progress = if (totalBytes > 0) {
                                        (totalDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    } else 0f

                                    _downloadStatus.value = DownloadStatus.Downloading(
                                        progress = progress,
                                        downloadedBytes = totalDownloaded,
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = speed,
                                        currentSource = sourceLabel
                                    )
                                    lastReportTime = currentTime
                                    bytesSinceLastReport = 0L
                                }
                            }
                        }
                    }

                    if (tempFile.exists() && tempFile.length() > 1_000_000L) {
                        // 1) 核对清单声明的 SHA-256（若存在）。
                        val expected = info.expectedSha256.trim().lowercase()
                        if (expected.length == 64) {
                            val actual = sha256Of(tempFile)
                            if (actual.isBlank() || actual != expected) {
                                tempFile.delete()
                                lastError = "$sourceLabel 文件 SHA-256 校验不一致，已拒绝该节点"
                                continue
                            }
                        }

                        // 2) 核对签名：只接受本应用签名密钥签发的 APK。
                        if (!isApkSignedBySameCertificate(context, tempFile)) {
                            tempFile.delete()
                            _downloadStatus.value = DownloadStatus.Error(
                                "安装包签名校验失败：该文件并非由本应用的签名密钥签发，已拒绝安装"
                            )
                            return@launch
                        }

                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        downloadSuccess = true
                        _downloadStatus.value = DownloadStatus.Success(targetFile)
                        break
                    } else {
                        tempFile.delete()
                        lastError = "$sourceLabel 返回数据不完整，正在自动切换备用节点..."
                    }
                } catch (e: CancellationException) {
                    tempFile.delete()
                    _downloadStatus.value = DownloadStatus.Idle
                    return@launch
                } catch (e: Exception) {
                    tempFile.delete()
                    lastError = sourceLabel + "下载异常: " + (e.message ?: "连接中断")
                }
            }

            if (!downloadSuccess && isActive) {
                _downloadStatus.value = DownloadStatus.Error(lastError)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadStatus.value = DownloadStatus.Idle
    }

    private fun isApkSignedBySameCertificate(context: Context, apkFile: File): Boolean = runCatching {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES

        val installed = pm.getPackageInfo(context.packageName, flags)
        val archive = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)

        if (archive == null || archive.packageName != context.packageName) {
            return@runCatching false
        }

        val installedSigners = installed.signingInfo?.apkContentsSigners?.toSet()
        val archiveSigners = archive.signingInfo?.apkContentsSigners?.toSet()

        if (installedSigners.isNullOrEmpty() || archiveSigners.isNullOrEmpty()) {
            return@runCatching false
        }

        installedSigners == archiveSigners
    }.logFailure("VersionSync", "isApkSignedBySameCertificate (fails closed)")
        .getOrDefault(false)

    /**
     * Launch Android PackageInstaller with FileProvider and Unknown Sources permission handling.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() < 1000L) {
                Toast.makeText(context, "安装包不存在或已损坏，请重新下载", Toast.LENGTH_SHORT).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:" + context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "请在设置中授予「允许安装未知应用」权限，返回后即可自动安装", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "拉起系统安装器失败: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    internal fun openConnectionWithRedirects(initialUrl: String, timeoutMs: Int): Pair<HttpURLConnection, InputStream> {
        var currentUrl = initialUrl
        var redirectCount = 0
        val maxRedirects = 10

        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }

            val code = conn.responseCode
            if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER, 307, 308)) {
                val location = conn.getHeaderField("Location") ?: throw Exception("Redirect missing Location header")
                conn.disconnect()
                currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                redirectCount++
                continue
            }

            if (code in 200..299) {
                return Pair(conn, conn.inputStream)
            } else {
                conn.disconnect()
                throw Exception("HTTP $code from $currentUrl")
            }
        }
        throw Exception("Too many redirects: $redirectCount")
    }

    internal fun isRemoteNewer(remote: RemoteReleaseInfo): Boolean {
        val remoteCode = remote.remoteVersionCode
        if (remoteCode > 0 && BuildConfig.VERSION_CODE > 0) {
            if (remoteCode > BuildConfig.VERSION_CODE) return true
            if (remoteCode < BuildConfig.VERSION_CODE) return false
        }
        return compareSemver(remote.tagName, BuildConfig.VERSION_NAME) > 0
    }

    private fun compareSemver(v1: String, v2: String): Int =
        VersionComparator.compareSemver(v1, v2)

    internal fun extractVersionCode(body: String): Int {
        val match = VERSION_CODE_REGEX.find(body) ?: return UNKNOWN_VERSION_CODE
        return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: UNKNOWN_VERSION_CODE
    }

    internal fun extractSha256(body: String): String {
        val match = SHA256_REGEX.find(body) ?: return ""
        return match.groupValues[1].lowercase()
    }

    internal fun sha256Of(file: File): String = runCatching {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.logFailure(TAG, "计算 SHA-256 失败").getOrDefault("")
}
