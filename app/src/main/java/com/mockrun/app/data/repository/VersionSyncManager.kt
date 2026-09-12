package com.mockrun.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import com.mockrun.app.BuildConfig
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
 * Information extracted from GitHub Release API.
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
    val fallbackDownloadUrls: List<String>
) {
    val releaseDate: String get() = publishedAt
    val releaseUrl: String get() = releaseHtmlUrl
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
        val currentSource: String = "jsDelivr CDN"
    ) : DownloadStatus()
    data class Success(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}

object VersionSyncManager {
    private const val GITHUB_OWNER = "Elysia-SHY"
    private const val GITHUB_REPO = "FakeGPS-next"

    private const val GITHUB_RELEASE_API =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    // Fallback mirror if GitHub API is throttled or blocked by firewall/proxy
    private const val GITHUB_RELEASE_API_MIRROR =
        "https://ghproxy.net/https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile; FakeGPS-next) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val _status = mutableStateOf<VersionSyncStatus>(VersionSyncStatus.Idle)
    val status: State<VersionSyncStatus> = _status

    private val _downloadStatus = mutableStateOf<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: State<DownloadStatus> = _downloadStatus

    private val _showUpdatePrompt = mutableStateOf(false)
    val showUpdatePrompt: State<Boolean> = _showUpdatePrompt

    private var downloadJob: Job? = null
    private var lastCheckTime: Long = 0L

    fun dismissUpdatePrompt() {
        _showUpdatePrompt.value = false
    }

    fun openUpdatePrompt() {
        if (_status.value is VersionSyncStatus.HasUpdate) {
            _showUpdatePrompt.value = true
        }
    }

    /**
     * Check for latest release via GitHub Release API.
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
            fetchReleaseFromGitHub()
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

    private fun fetchReleaseFromGitHub(): VersionSyncStatus {
        val now = System.currentTimeMillis()
        val endpoints = listOf(
            GITHUB_RELEASE_API,
            "https://gh-proxy.com/https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest",
            "https://ghproxy.net/https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest",
            "https://gh.llkk.cc/https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        )
        var lastErrorMessage = "未知网络错误"

        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    return parseReleaseJson(body, now)
                } else if (responseCode == 403) {
                    lastErrorMessage = "GitHub API 触发访问频率限制 (403)"
                } else if (responseCode == 404) {
                    lastErrorMessage = "未找到 Release 发布版本 (404)"
                } else {
                    lastErrorMessage = "GitHub API 请求失败 (HTTP $responseCode)"
                }
            } catch (e: SocketTimeoutException) {
                lastErrorMessage = "网络连接超时 (10s)，请检查加速器或网络环境"
            } catch (e: UnknownHostException) {
                lastErrorMessage = "无法解析 GitHub 服务器地址，网络不可达"
            } catch (e: Exception) {
                lastErrorMessage = "GitHub API 请求异常: " + (e.message ?: "未知异常")
            } finally {
                conn?.disconnect()
            }
        }

        return VersionSyncStatus.Error(lastErrorMessage, now)
    }

    private fun parseReleaseJson(jsonString: String, checkedAt: Long): VersionSyncStatus {
        return try {
            val json = JSONObject(jsonString)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) {
                return VersionSyncStatus.Error("Release 响应缺少版本标签 (tag_name)", checkedAt)
            }

            val body = json.optString("body", "暂无更新日志说明。")
            val publishedAt = json.optString("published_at", "").take(10)
            val htmlUrl = json.optString("html_url", "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases")

            val assets = json.optJSONArray("assets")
            if (assets == null || assets.length() == 0) {
                return VersionSyncStatus.Error("最新 Release 尚未上传 APK 安装包", checkedAt)
            }

            var apkName = ""
            var browserDownloadUrl = ""

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

            if (apkName.isEmpty()) {
                return VersionSyncStatus.Error("Release 中未发现可用的 APK 安装包", checkedAt)
            }

            // jsDelivr CDN address format: https://cdn.jsdelivr.net/gh/{owner}/{repo}@{tag}/文件名.apk
            val cdnDownloadUrl = "https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@$tagName/$apkName"

            val fallbackUrls = mutableListOf<String>().apply {
                add(cdnDownloadUrl) // jsDelivr CDN primary
                if (browserDownloadUrl.isNotBlank()) {
                    add("https://ghproxy.net/$browserDownloadUrl") // High-speed asset mirror
                    add("https://gh-proxy.com/$browserDownloadUrl")
                    add("https://gh.llkk.cc/$browserDownloadUrl")
                    add(browserDownloadUrl) // GitHub direct asset URL
                }
            }

            val remoteCode = extractVersionCode(tagName, body)
            val releaseInfo = RemoteReleaseInfo(
                tagName = tagName,
                versionName = tagName,
                remoteVersionCode = remoteCode,
                releaseNotes = body,
                publishedAt = publishedAt,
                releaseHtmlUrl = htmlUrl,
                apkName = apkName,
                cdnDownloadUrl = cdnDownloadUrl,
                fallbackDownloadUrls = fallbackUrls
            )

            val isNewer = isRemoteNewer(releaseInfo)
            if (isNewer) {
                VersionSyncStatus.HasUpdate(releaseInfo, checkedAt)
            } else {
                VersionSyncStatus.UpToDate(releaseInfo, checkedAt)
            }
        } catch (e: Exception) {
            VersionSyncStatus.Error("JSON 解析失败: " + (e.message ?: "数据格式无效"), checkedAt)
        }
    }

    // =========================================================================
    // In-App APK Download & Installation
    // =========================================================================

    fun startDownload(context: Context, info: RemoteReleaseInfo) {
        if (_downloadStatus.value is DownloadStatus.Downloading) return

        downloadJob?.cancel()
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            _downloadStatus.value = DownloadStatus.Downloading(0f, 0L, 0L, 0L, "jsDelivr CDN")

            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val targetFile = File(targetDir, "FakeGPS-${info.tagName}.apk")
            val tempFile = File(targetDir, "FakeGPS-${info.tagName}.apk.tmp")

            if (tempFile.exists()) tempFile.delete()

            var downloadSuccess = false
            var lastError = "下载失败：所有节点均不可用"

            for (urlStr in info.fallbackDownloadUrls) {
                if (!isActive) break
                val sourceLabel = when {
                    urlStr.contains("jsdelivr") -> "jsDelivr CDN"
                    urlStr.contains("ghproxy") || urlStr.contains("gh-proxy") -> "加速镜像"
                    else -> "GitHub 官方源"
                }

                _downloadStatus.value = DownloadStatus.Downloading(0f, 0L, 0L, 0L, sourceLabel)

                try {
                    val (conn, inputStream) = openConnectionWithRedirects(urlStr, 15_000)
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
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        downloadSuccess = true
                        _downloadStatus.value = DownloadStatus.Success(targetFile)

                        withContext(Dispatchers.Main) {
                            installApk(context, targetFile)
                        }
                        break
                    } else {
                        tempFile.delete()
                        lastError = "节点返回数据不完整，正在自动切换备用节点..."
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

    /**
     * Launch Android PackageInstaller with FileProvider and Unknown Sources permission handling.
     * Compatible with Android 8.0 through Android 14/15.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() < 1000L) {
                Toast.makeText(context, "安装包不存在或已损坏，请重新下载", Toast.LENGTH_SHORT).show()
                return
            }

            // Android 8.0+ Unknown App Sources Permission check
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

    private fun openConnectionWithRedirects(initialUrl: String, timeoutMs: Int): Pair<HttpURLConnection, InputStream> {
        var currentUrl = initialUrl
        var redirectCount = 0
        val maxRedirects = 10

        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", BROWSER_USER_AGENT)
                setRequestProperty("Accept", "*/*")
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

    private fun isRemoteNewer(remote: RemoteReleaseInfo): Boolean {
        if (remote.remoteVersionCode > 0 && BuildConfig.VERSION_CODE > 0) {
            if (remote.remoteVersionCode > BuildConfig.VERSION_CODE) return true
            if (remote.remoteVersionCode < BuildConfig.VERSION_CODE) return false
        }
        return compareSemver(remote.tagName, BuildConfig.VERSION_NAME) > 0
    }

    private fun compareSemver(v1: String, v2: String): Int {
        val s1 = v1.trim().removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val s2 = v2.trim().removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(s1.size, s2.size)
        for (i in 0 until maxLen) {
            val p1 = s1.getOrElse(i) { 0 }
            val p2 = s2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    private fun extractVersionCode(tag: String, body: String): Int {
        val bodyRegex = Regex("(?i)versionCode\\s*[:=]\\s*(\\d+)")
        val match = bodyRegex.find(body)
        if (match != null) {
            return match.groupValues[1].toIntOrNull() ?: 0
        }

        val parts = tag.trim().removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        var code = 0
        for (p in parts) {
            code = code * 10 + p
        }
        return code
    }
}
