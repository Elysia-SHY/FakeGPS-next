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
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class RemoteVersionInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val downloadUrl: String
)

sealed class VersionSyncStatus {
    object Idle : VersionSyncStatus()
    object Checking : VersionSyncStatus()
    data class UpToDate(val info: RemoteVersionInfo, val checkedAt: Long) : VersionSyncStatus()
    data class HasUpdate(val info: RemoteVersionInfo, val checkedAt: Long) : VersionSyncStatus()
    data class Error(val message: String, val checkedAt: Long) : VersionSyncStatus()
}

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : DownloadStatus()
    data class Success(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}

object VersionSyncManager {
    private val _status = mutableStateOf<VersionSyncStatus>(VersionSyncStatus.Idle)
    val status: State<VersionSyncStatus> = _status

    private val _downloadStatus = mutableStateOf<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: State<DownloadStatus> = _downloadStatus

    private var downloadJob: Job? = null
    private var lastCheckTime: Long = 0L

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    suspend fun checkForUpdates(force: Boolean = false): VersionSyncStatus {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckTime < 15_000L && _status.value !is VersionSyncStatus.Idle && _status.value !is VersionSyncStatus.Error) {
            return _status.value
        }

        _status.value = VersionSyncStatus.Checking

        return withContext(Dispatchers.IO) {
            // Diverse multi-mirror candidates to conquer proxy, VPN and CDN restrictions
            val candidateEndpoints = listOf(
                "https://raw.githubusercontent.com/Elysia-SHY/FakeGPS-next/main/version.json?_t=$now",
                "https://ghproxy.net/https://raw.githubusercontent.com/Elysia-SHY/FakeGPS-next/main/version.json?_t=$now",
                "https://fastly.jsdelivr.net/gh/Elysia-SHY/FakeGPS-next@main/version.json?_t=$now",
                "https://gh.llkk.cc/https://raw.githubusercontent.com/Elysia-SHY/FakeGPS-next/main/version.json?_t=$now",
                "https://cdn.jsdelivr.net/gh/Elysia-SHY/FakeGPS-next@main/version.json?_t=$now",
                "https://api.github.com/repos/Elysia-SHY/FakeGPS-next/releases/latest"
            )

            var parsedInfo: RemoteVersionInfo? = null

            // Parallel race: launch coroutines for all endpoints and pick the fastest valid response!
            val raceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val channel = Channel<RemoteVersionInfo?>(candidateEndpoints.size)

            val jobs = candidateEndpoints.map { endpoint ->
                raceScope.launch {
                    val info = fetchFromEndpoint(endpoint)
                    channel.send(info)
                }
            }

            var attempts = 0
            while (attempts < candidateEndpoints.size) {
                val res = channel.receive()
                attempts++
                if (res != null && res.versionName.isNotBlank()) {
                    parsedInfo = res
                    break
                }
            }

            jobs.forEach { it.cancel() }
            raceScope.cancel()

            val resultStatus: VersionSyncStatus = if (parsedInfo != null && parsedInfo.versionName.isNotBlank()) {
                val hasUpdate = isRemoteNewer(parsedInfo)
                if (hasUpdate) {
                    VersionSyncStatus.HasUpdate(parsedInfo, now)
                } else {
                    VersionSyncStatus.UpToDate(parsedInfo, now)
                }
            } else {
                VersionSyncStatus.Error("云端同步暂不可用或网络受限", now)
            }

            lastCheckTime = now
            _status.value = resultStatus
            resultStatus
        }
    }

    private fun fetchFromEndpoint(endpoint: String): RemoteVersionInfo? {
        return try {
            val (conn, stream) = openConnectionWithRedirects(endpoint, 8000)
            val body = stream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)

            if (endpoint.contains("version.json")) {
                RemoteVersionInfo(
                    versionName = json.optString("versionName", ""),
                    versionCode = json.optInt("versionCode", 0),
                    releaseDate = json.optString("releaseDate", ""),
                    releaseNotes = json.optString("releaseNotes", ""),
                    releaseUrl = json.optString("releaseUrl", "https://github.com/Elysia-SHY/FakeGPS-next/releases"),
                    downloadUrl = json.optString("downloadUrl", "https://github.com/Elysia-SHY/FakeGPS-next/releases")
                )
            } else {
                val tag = json.optString("tag_name", "")
                val bodyText = json.optString("body", "")
                val htmlUrl = json.optString("html_url", "https://github.com/Elysia-SHY/FakeGPS-next/releases")
                val pubAt = json.optString("published_at", "").take(10)
                var apkUrl = htmlUrl
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk") && !name.contains("debug")) {
                            apkUrl = asset.optString("browser_download_url", htmlUrl)
                            break
                        }
                    }
                }
                RemoteVersionInfo(
                    versionName = tag,
                    versionCode = extractVersionCode(tag),
                    releaseDate = pubAt,
                    releaseNotes = bodyText,
                    releaseUrl = htmlUrl,
                    downloadUrl = apkUrl
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================================
    // In-App APK Download & Installation
    // =========================================================================

    fun startDownload(context: Context, info: RemoteVersionInfo) {
        if (_downloadStatus.value is DownloadStatus.Downloading) return

        downloadJob?.cancel()
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            _downloadStatus.value = DownloadStatus.Downloading(0f, 0L, 0L, 0L)

            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val targetFile = File(targetDir, "FakeGPS-${info.versionName}.apk")
            val tempFile = File(targetDir, "FakeGPS-${info.versionName}.apk.tmp")

            if (tempFile.exists()) tempFile.delete()

            val originalUrl = info.downloadUrl
            val downloadMirrors = listOf(
                "https://ghproxy.net/$originalUrl",
                "https://gh.llkk.cc/$originalUrl",
                "https://gh-proxy.com/$originalUrl",
                originalUrl
            )

            var downloadSuccess = false
            var lastError = "下载失败"

            for (mirrorUrl in downloadMirrors) {
                if (!isActive) break
                try {
                    val (conn, inputStream) = openConnectionWithRedirects(mirrorUrl, 12000)
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
                                if (timeDiff >= 350L) {
                                    val speed = (bytesSinceLastReport * 1000L) / timeDiff.coerceAtLeast(1L)
                                    val progress = if (totalBytes > 0) (totalDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                                    _downloadStatus.value = DownloadStatus.Downloading(
                                        progress = progress,
                                        downloadedBytes = totalDownloaded,
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = speed
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

                        // Launch PackageInstaller automatically!
                        withContext(Dispatchers.Main) {
                            installApk(context, targetFile)
                        }
                        break
                    } else {
                        tempFile.delete()
                        lastError = "下载文件不完整，正在切换镜像重试..."
                    }
                } catch (e: CancellationException) {
                    tempFile.delete()
                    _downloadStatus.value = DownloadStatus.Idle
                    return@launch
                } catch (e: Exception) {
                    tempFile.delete()
                    lastError = e.message ?: "下载异常"
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

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Toast.makeText(context, "安装包不存在，请重新下载", Toast.LENGTH_SHORT).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "请开启「允许安装未知来源应用」权限后返回", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "启动安装器失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openConnectionWithRedirects(initialUrl: String, timeoutMs: Int): Pair<HttpURLConnection, InputStream> {
        var currentUrl = initialUrl
        var redirectCount = 0
        val maxRedirects = 8

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
                val location = conn.getHeaderField("Location") ?: throw Exception("Redirect missing Location")
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

    private fun isRemoteNewer(remote: RemoteVersionInfo): Boolean {
        if (remote.versionCode > 0 && BuildConfig.VERSION_CODE > 0) {
            if (remote.versionCode > BuildConfig.VERSION_CODE) return true
            if (remote.versionCode < BuildConfig.VERSION_CODE) return false
        }
        return compareSemver(remote.versionName, BuildConfig.VERSION_NAME) > 0
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

    private fun extractVersionCode(tag: String): Int {
        val parts = tag.trim().removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        var code = 0
        for (p in parts) {
            code = code * 10 + p
        }
        return code
    }
}
