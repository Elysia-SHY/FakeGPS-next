package com.mockrun.app.data.repository

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.mockrun.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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

object VersionSyncManager {
    private val _status = mutableStateOf<VersionSyncStatus>(VersionSyncStatus.Idle)
    val status: State<VersionSyncStatus> = _status

    private var lastCheckTime: Long = 0L

    suspend fun checkForUpdates(force: Boolean = false): VersionSyncStatus {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckTime < 15_000L && _status.value !is VersionSyncStatus.Idle && _status.value !is VersionSyncStatus.Error) {
            return _status.value
        }

        _status.value = VersionSyncStatus.Checking

        return withContext(Dispatchers.IO) {
            val candidateEndpoints = listOf(
                "https://cdn.jsdelivr.net/gh/Elysia-SHY/FakeGPS-next@main/version.json?t=$now",
                "https://raw.githubusercontent.com/Elysia-SHY/FakeGPS-next/main/version.json?t=$now",
                "https://api.github.com/repos/Elysia-SHY/FakeGPS-next/releases/latest"
            )

            var parsedInfo: RemoteVersionInfo? = null

            for (endpoint in candidateEndpoints) {
                try {
                    val url = URL(endpoint)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4500
                        readTimeout = 4500
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "FakeGPS-App/${BuildConfig.VERSION_NAME}")
                        setRequestProperty("Accept", "application/json, text/plain, */*")
                    }

                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)

                        if (endpoint.contains("version.json")) {
                            parsedInfo = RemoteVersionInfo(
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
                            parsedInfo = RemoteVersionInfo(
                                versionName = tag,
                                versionCode = extractVersionCode(tag),
                                releaseDate = pubAt,
                                releaseNotes = bodyText,
                                releaseUrl = htmlUrl,
                                downloadUrl = apkUrl
                            )
                        }

                        if (parsedInfo.versionName.isNotBlank()) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    // Try next candidate
                }
            }

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
        if (parts.size >= 3) {
            return parts[0] * 10000 + parts[1] * 100 + parts[2]
        }
        return 0
    }
}
