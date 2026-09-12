package com.mockrun.app.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.BuildConfig
import com.mockrun.app.data.repository.RemoteVersionInfo
import com.mockrun.app.data.repository.VersionSyncManager
import com.mockrun.app.data.repository.DownloadStatus
import com.mockrun.app.data.repository.VersionSyncStatus
import com.mockrun.app.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val BUILTIN_CHANGELOG = """【FakeGPS-next v1.3.2 更新日志】

1. 🔄 云端版本与更新自动同步：
   - 采用三级梯级容灾架构（jsdelivr CDN、GitHub Raw 及 Releases API）；
   - 实时智能比对版本，支持一键热同步与应用内更新日志预览。

2. 🎨 介绍页 UI 全景重构：
   - 引入 FlowRow 自适应折行，修复多分辨率下技术标签挤压变形问题；
   - 扩大底部安全边距，彻底避免底部浮动导航栏遮挡诊断面板；
   - 新增设备环境与运行诊断面板（展示机型、系统版本、ABI 架构及模块建议）。

3. 🔀 系统框架级多应用独立分流：
   - 补全 HookConfigProvider 跨进程通信与底层派发拦截；
   - 消除定点驻留模式下的 AOSP 最小位移丢包问题。"""

@Composable
fun AboutScreen(
    isLiquidGlass: Boolean,
    isTablet: Boolean = false,
    onToggleLiquidGlass: (Boolean) -> Unit,
    onNavigateToLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val gitRepoUrl = "https://github.com/Elysia-SHY/FakeGPS-next"

    val syncState by VersionSyncManager.status
    val downloadState by VersionSyncManager.downloadStatus
    var showChangelogDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    val currentBgPreset by BackgroundThemeManager.currentPreset
    val wallpaperTimestamp by BackgroundThemeManager.customWallpaperTimestamp
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val success = BackgroundThemeManager.setCustomWallpaper(context, uri)
            Toast.makeText(
                context,
                if (success) "🖼️ 自定义壁纸已设置并在全应用生效" else "⚠️ 壁纸读取失败，请重试",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Auto-sync version & background preferences on screen launch
    LaunchedEffect(Unit) {
        BackgroundThemeManager.initialize(context)
        VersionSyncManager.checkForUpdates(force = false)
    }

    // Changelog Dialog
    if (showChangelogDialog != null) {
        val (dialogTitle, dialogContent) = showChangelogDialog!!
        AlertDialog(
            onDismissRequest = { showChangelogDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NewReleases, contentDescription = null, tint = IosColors.SystemBlue)
                    Text(
                        text = dialogTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dialogContent,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = if (isDark) Color(0xFFE0E0E0) else Color(0xFF2C2C2E)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Elysia-SHY/FakeGPS-next/releases"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开发布页面", Toast.LENGTH_SHORT).show()
                        }
                        showChangelogDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemBlue)
                ) {
                    Text("前往 GitHub Release", color = Color.White, fontSize = 12.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangelogDialog = null }) {
                    Text("关闭", color = IosColors.SecondaryLabel, fontSize = 12.5.sp)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White
        )
    }

    val hazeState = LocalHazeState.current ?: remember { HazeState() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AppBackground(hazeState = hazeState)
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = if (isTablet) 48.dp else 160.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // =====================================================================
            // 1. App Header & Emblem Card
            // =====================================================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(24.dp),
                        elevation = 10.dp
                    ),
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Luminous App Logo Badge
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF007AFF),
                                        Color(0xFF5856D6),
                                        Color(0xFF00C7BE)
                                    )
                                )
                            )
                            .border(
                                1.5.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.65f),
                                        Color.White.copy(alpha = 0.15f)
                                    )
                                ),
                                RoundedCornerShape(22.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "FakeGPS-next",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IosColors.SystemBlue.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IosColors.SystemBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "构建时间: ${BuildConfig.BUILD_TIME}",
                            fontSize = 11.5.sp,
                            color = IosColors.SecondaryLabel
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "系统框架底层分流 · 物理运动学拟真 · 动态多星座 GNSS 星历合成",
                        fontSize = 12.5.sp,
                        color = IosColors.SecondaryLabel,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            // =====================================================================
            // 2. Cloud Version Sync & Update Center (自动同步与更新中心)
            // =====================================================================
            Text(
                text = "版本与云端同步",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(start = 6.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(20.dp),
                        elevation = 8.dp
                    ),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    val isChecking = syncState is VersionSyncStatus.Checking

                    val infiniteTransition = rememberInfiniteTransition(label = "spin")
                    val angle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "spinAngle"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when (syncState) {
                                            is VersionSyncStatus.HasUpdate -> Color(0xFFFF9500).copy(0.18f)
                                            is VersionSyncStatus.UpToDate -> Color(0xFF34C759).copy(0.18f)
                                            is VersionSyncStatus.Error -> Color(0xFFFF3B30).copy(0.18f)
                                            else -> IosColors.SystemBlue.copy(0.18f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (syncState) {
                                        is VersionSyncStatus.HasUpdate -> Icons.Default.SystemUpdate
                                        is VersionSyncStatus.UpToDate -> Icons.Default.CheckCircle
                                        is VersionSyncStatus.Error -> Icons.Default.CloudOff
                                        else -> Icons.Default.CloudSync
                                    },
                                    contentDescription = null,
                                    tint = when (syncState) {
                                        is VersionSyncStatus.HasUpdate -> Color(0xFFFF9500)
                                        is VersionSyncStatus.UpToDate -> Color(0xFF34C759)
                                        is VersionSyncStatus.Error -> Color(0xFFFF3B30)
                                        else -> IosColors.SystemBlue
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "云端版本自动同步",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = when (syncState) {
                                        is VersionSyncStatus.Checking -> "正在同步云端最新版本..."
                                        is VersionSyncStatus.UpToDate -> "已同步 · 当前安装即为最新版"
                                        is VersionSyncStatus.HasUpdate -> "检测到新版本可用"
                                        is VersionSyncStatus.Error -> (syncState as VersionSyncStatus.Error).message
                                        is VersionSyncStatus.Idle -> "自动同步已就绪"
                                    },
                                    fontSize = 11.5.sp,
                                    color = when (syncState) {
                                        is VersionSyncStatus.HasUpdate -> Color(0xFFFF9500)
                                        is VersionSyncStatus.UpToDate -> Color(0xFF34C759)
                                        is VersionSyncStatus.Error -> Color(0xFFFF3B30)
                                        else -> IosColors.SecondaryLabel
                                    }
                                )
                            }
                        }

                        // Sync / Refresh Button
                        IconButton(
                            onClick = {
                                if (!isChecking) {
                                    scope.launch {
                                        val res = VersionSyncManager.checkForUpdates(force = true)
                                        when (res) {
                                            is VersionSyncStatus.UpToDate -> Toast.makeText(context, "已是最新版本 (${res.info.versionName})", Toast.LENGTH_SHORT).show()
                                            is VersionSyncStatus.HasUpdate -> Toast.makeText(context, "发现新版本: ${res.info.versionName}", Toast.LENGTH_SHORT).show()
                                            is VersionSyncStatus.Error -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            else -> {}
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新同步",
                                tint = if (isDark) Color.White else Color.Black,
                                modifier = Modifier
                                    .size(18.dp)
                                    .then(if (isChecking) Modifier.rotate(angle) else Modifier)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.07f) else Color.Black.copy(0.05f))
                    Spacer(Modifier.height(12.dp))

                    // Version comparison matrix
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "当前安装版本",
                                fontSize = 11.5.sp,
                                color = IosColors.SecondaryLabel
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isDark) Color.White else Color.Black
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = IosColors.SecondaryLabel.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "云端最新版本",
                                fontSize = 11.5.sp,
                                color = IosColors.SecondaryLabel
                            )
                            Spacer(Modifier.height(2.dp))
                            val remoteName = when (syncState) {
                                is VersionSyncStatus.UpToDate -> (syncState as VersionSyncStatus.UpToDate).info.versionName
                                is VersionSyncStatus.HasUpdate -> (syncState as VersionSyncStatus.HasUpdate).info.versionName
                                is VersionSyncStatus.Checking -> "检测中..."
                                else -> BuildConfig.VERSION_NAME
                            }
                            Text(
                                text = remoteName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = when (syncState) {
                                    is VersionSyncStatus.HasUpdate -> Color(0xFFFF9500)
                                    is VersionSyncStatus.UpToDate -> Color(0xFF34C759)
                                    else -> IosColors.SecondaryLabel
                                }
                            )
                        }
                    }

                    // Has update banner
                    if (syncState is VersionSyncStatus.HasUpdate) {
                        val info = (syncState as VersionSyncStatus.HasUpdate).info
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFF9500).copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Celebration, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp))
                                        Text(
                                            text = "发现新版本 ${info.versionName}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color.White else Color.Black
                                        )
                                    }
                                    if (info.releaseDate.isNotBlank()) {
                                        Text(
                                            text = info.releaseDate,
                                            fontSize = 11.5.sp,
                                            color = IosColors.SecondaryLabel
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                // In-App Download Status & Action Rows
                                when (downloadState) {
                                    is DownloadStatus.Downloading -> {
                                        val dl = downloadState as DownloadStatus.Downloading
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            LinearProgressIndicator(
                                                progress = { dl.progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = Color(0xFFFF9500),
                                                trackColor = Color(0xFFFF9500).copy(alpha = 0.2f)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${(dl.progress * 100).toInt()}% (${formatMb(dl.downloadedBytes)} / ${formatMb(dl.totalBytes)})",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isDark) Color.White.copy(0.9f) else Color.Black
                                                )
                                                Text(
                                                    text = formatSpeed(dl.speedBytesPerSec),
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFFFF9500),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { VersionSyncManager.cancelDownload() },
                                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("取消下载", fontSize = 12.sp, color = IosColors.SystemRed)
                                            }
                                        }
                                    }

                                    is DownloadStatus.Success -> {
                                        val file = (downloadState as DownloadStatus.Success).file
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "新版本已下载就绪 (${formatMb(file.length())})",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF34C759),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        VersionSyncManager.startDownload(context, info)
                                                    },
                                                    modifier = Modifier.weight(1f).height(38.dp),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("重新下载", fontSize = 12.sp)
                                                }
                                                Button(
                                                    onClick = {
                                                        VersionSyncManager.installApk(context, file)
                                                    },
                                                    modifier = Modifier.weight(1.3f).height(38.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("立即安装", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }

                                    is DownloadStatus.Error -> {
                                        val errMsg = (downloadState as DownloadStatus.Error).message
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "下载失败: $errMsg",
                                                fontSize = 11.5.sp,
                                                color = IosColors.SystemRed
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { VersionSyncManager.startDownload(context, info) },
                                                    modifier = Modifier.weight(1f).height(38.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500))
                                                ) {
                                                    Text("重试在线下载", fontSize = 12.sp, color = Color.White)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f).height(38.dp),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("浏览器下载", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }

                                    DownloadStatus.Idle -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    showChangelogDialog = Pair("${info.versionName} 更新说明", info.releaseNotes)
                                                },
                                                modifier = Modifier.weight(1f).height(40.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isDark) Color.White else Color.Black)
                                            ) {
                                                Text("更新说明", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }

                                            Button(
                                                onClick = {
                                                    VersionSyncManager.startDownload(context, info)
                                                },
                                                modifier = Modifier.weight(1.3f).height(40.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500))
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                Spacer(Modifier.width(5.dp))
                                                Text("在线下载更新", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "备用：在浏览器中直接下载",
                                                fontSize = 11.sp,
                                                color = IosColors.SystemBlue,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Up to date banner with view changelog button
                    if (syncState is VersionSyncStatus.UpToDate || syncState is VersionSyncStatus.Idle) {
                        val checkedAt = (syncState as? VersionSyncStatus.UpToDate)?.checkedAt ?: System.currentTimeMillis()
                        val timeStr = remember(checkedAt) {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(checkedAt))
                        }
                        val remoteNotes = (syncState as? VersionSyncStatus.UpToDate)?.info?.releaseNotes?.takeIf { it.isNotBlank() } ?: BUILTIN_CHANGELOG

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF34C759).copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(15.dp))
                                Text(
                                    text = "已是最新版本 · $timeStr 已同步",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF34C759)
                                )
                            }

                            TextButton(
                                onClick = {
                                    showChangelogDialog = Pair("${BuildConfig.VERSION_NAME} 最新特性日志", remoteNotes)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("查看日志", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = IosColors.SystemBlue)
                            }
                        }
                    }

                    // Error banner
                    if (syncState is VersionSyncStatus.Error) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF3B30).copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(15.dp))
                                Text(
                                    text = "网络离线或同步受阻",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFF3B30)
                                )
                            }

                            TextButton(
                                onClick = {
                                    showChangelogDialog = Pair("${BuildConfig.VERSION_NAME} 本地特性说明", BUILTIN_CHANGELOG)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("本地日志", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = IosColors.SystemBlue)
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 3. Core Features & Architectural Capabilities (核心功能与架构特性)
            // =====================================================================
            Text(
                text = "核心功能与架构特性",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(start = 6.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(20.dp),
                        elevation = 8.dp
                    ),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FeatureItem(
                        icon = Icons.Default.AltRoute,
                        iconTint = IosColors.SystemBlue,
                        title = "多应用独立分流路由 (Multi-Target)",
                        tags = listOf("AOSP 8~15 全兼容", "多开分身支持", "零锁高频 IPC", "真实物理透传"),
                        desc = "突破全系统单点模拟限制。可为微信、钉钉、高德等单独指派独立虚拟位置与专属路线，支持多开分身 (User 999) 独立识别绑定，未添加应用自动走真实物理定位。"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    FeatureItem(
                        icon = Icons.Default.Lock,
                        iconTint = IosColors.SystemGreen,
                        title = "系统内核集中拦截 (system_server Hook)",
                        tags = listOf("0 注入特征", "仅勾选系统框架", "派发监听全拦截", "AOSP 丢包抑制"),
                        desc = "在 LSPosed 中仅需勾选「系统框架」，由系统底层集中路由。宿主应用进程内 0 注入代码，彻底免除第三方反作弊扫描与封号风险。"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    FeatureItem(
                        icon = Icons.Default.PlayArrow,
                        iconTint = IosColors.SystemOrange,
                        title = "离线运动学物理仿真 (Kinematics Pro)",
                        tags = listOf("向心加速度减速", "双峰步频微动", "高斯地形海拔起伏"),
                        desc = "结合外接圆过弯向心减速约束 (v <= sqrt(a*R)) 杜绝急转弯超速异常；步频双峰微动模型拟真人体步态；高斯地形模型生成逼真海拔曲线。"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    FeatureItem(
                        icon = Icons.Default.SatelliteAlt,
                        iconTint = IosColors.SystemPurple,
                        title = "动态多星座 GNSS 星历合成 (Synthetic GNSS)",
                        tags = listOf("北斗/GPS/GLONASS", "16~24 动态卫星", "天顶角仰角 C/N0"),
                        desc = "合成北斗、GPS 与 GLONASS 多星座卫星分布与 24~42 dB-Hz 动态信噪比，在遮挡时叠加多径衰减，解决模拟定位开启后搜星数为 0 触发平台秒封的问题。"
                    )
                }
            }

            // =====================================================================
            // 4. Device Environment & Diagnostic Card (设备环境与运行诊断)
            // =====================================================================
            Text(
                text = "设备环境与运行诊断",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(start = 6.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(20.dp),
                        elevation = 8.dp
                    ),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                    val osInfo = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

                    DiagnosticRow(label = "设备型号", value = deviceModel)
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    DiagnosticRow(label = "系统版本", value = osInfo)
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    DiagnosticRow(label = "指令集架构", value = abi)
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    DiagnosticRow(label = "构建变体", value = "${BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }} (R8 混淆优化)")

                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = IosColors.SystemBlue.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = IosColors.SystemBlue, modifier = Modifier.size(16.dp))
                            Text(
                                text = "LSPosed 模块建议仅勾选「系统框架 (system)」，各分流目标应用无需勾选即可获得底层路由支持。",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = IosColors.SecondaryLabel
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // 5. Visual Appearance & Glass Rendering (视觉渲染风格)
            // =====================================================================
            Text(
                text = "视觉渲染风格",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(start = 6.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(20.dp),
                        elevation = 8.dp
                    ),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isLiquidGlass) IosColors.SystemBlue.copy(0.18f) else (if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isLiquidGlass) Icons.Default.WaterDrop else Icons.Default.BlurOff,
                                    contentDescription = null,
                                    tint = if (isLiquidGlass) IosColors.SystemBlue else IosColors.SecondaryLabel,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "液态毛玻璃渲染效果",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isLiquidGlass) "半透明磨砂 · 镜面光泽 · 柔和环境光" else "纯色材质 · 超低 GPU 负荷 · 极致省电",
                                    fontSize = 11.5.sp,
                                    color = IosColors.SecondaryLabel
                                )
                            }
                        }

                        Switch(
                            checked = isLiquidGlass,
                            onCheckedChange = { next ->
                                LiquidGlassDefaults.setEnabled(context, next)
                                onToggleLiquidGlass(next)
                                Toast.makeText(
                                    context,
                                    if (next) "💧 已开启液态毛玻璃视觉效果" else "⬛ 已切换为经典纯色节能模式",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IosColors.SystemBlue
                            )
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f),
                        thickness = 0.8.dp
                    )
                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "💡 视觉设计参考了 LocationSpoofer 与 Orb 的折射高光光泽。在开启状态下，浮动底栏与抽屉将呈现通透细腻的质感。",
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = IosColors.SecondaryLabel
                    )
                }
            }

            // =====================================================================
            // 5.2. Personalization & Background Themes (个性化主题与背景定制)
            // =====================================================================
            Text(
                text = "个性化主题与背景定制",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(start = 6.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(20.dp),
                        elevation = 8.dp
                    ),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(currentBgPreset.accentColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentBgPreset.iconEmoji,
                                    fontSize = 20.sp
                                )
                            }
                            Column {
                                Text(
                                    text = "全局背景氛围风格",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${currentBgPreset.title} · ${currentBgPreset.subtitle}",
                                    fontSize = 11.5.sp,
                                    color = IosColors.SecondaryLabel
                                )
                            }
                        }
                    }

                    // Preset Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            BackgroundPreset.AURORA,
                            BackgroundPreset.CYBERPUNK,
                            BackgroundPreset.SUNSET,
                            BackgroundPreset.GLACIER,
                            BackgroundPreset.DEEP_SPACE
                        ).forEach { preset ->
                            val isSelected = currentBgPreset == preset
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        BackgroundThemeManager.setPreset(context, preset)
                                        Toast.makeText(context, "已切换为「${preset.title}」风格", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) preset.accentColor.copy(alpha = 0.22f) else (if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.04f)),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.2.dp, preset.accentColor) else null
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(preset.iconEmoji, fontSize = 16.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = preset.title,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) preset.accentColor else (if (isDark) Color.White else Color.Black)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))

                    // Custom Wallpaper Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = if (currentBgPreset == BackgroundPreset.CUSTOM) Color(0xFF10B981) else IosColors.SecondaryLabel,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = if (currentBgPreset == BackgroundPreset.CUSTOM) "已应用自选相册壁纸" else "从相册自定义壁纸",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                                Text(
                                    text = "选择照片作为全应用底衬，卡片将通透毛玻璃折射",
                                    fontSize = 11.sp,
                                    color = IosColors.SecondaryLabel
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (currentBgPreset == BackgroundPreset.CUSTOM) {
                                TextButton(
                                    onClick = {
                                        BackgroundThemeManager.clearCustomWallpaper(context)
                                        Toast.makeText(context, "已恢复为默认极光壁纸", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("恢复预设", fontSize = 12.sp, color = IosColors.SystemRed)
                                }
                            }
                            Button(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentBgPreset == BackgroundPreset.CUSTOM) Color(0xFF10B981) else IosColors.SystemBlue
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (currentBgPreset == BackgroundPreset.CUSTOM) "更换图片" else "选取图片",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 6. GitHub Open Source Address & Actions (开源代码仓库)
            // =====================================================================
            Text(
                text = "项目开源与代码仓库",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(start = 6.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(20.dp),
                        elevation = 8.dp
                    ),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = if (isDark) Color.White else Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GitHub 源码仓库",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White else Color.Black
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = gitRepoUrl,
                                fontSize = 12.sp,
                                color = IosColors.SystemBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Copy URL Button
                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("GitHub Repository", gitRepoUrl)
                                cm.setPrimaryClip(clip)
                                Toast.makeText(context, "已复制 GitHub 仓库地址至剪贴板！", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f),
                                contentColor = if (isDark) Color.White else Color.Black
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("复制链接", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Open Browser Button
                        Button(
                            onClick = {
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(gitRepoUrl))
                                    context.startActivity(browserIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IosColors.SystemBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("前往仓库", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // =====================================================================
            // 7. Quick Access: Route Library (数据管理与路线库)
            // =====================================================================
            Text(
                text = "数据管理与路线库",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = IosColors.SecondaryLabel,
                modifier = Modifier.padding(start = 6.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(20.dp),
                        elevation = 8.dp
                    )
                    .clickable { onNavigateToLibrary() },
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(IosColors.SystemPurple.copy(0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = IosColors.SystemPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "已收藏路线与航点库",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White else Color.Black
                            )
                            Text(
                                text = "管理历史规划、导入/导出 GPX 文件",
                                fontSize = 11.5.sp,
                                color = IosColors.SecondaryLabel
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = IosColors.SecondaryLabel,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // =====================================================================
            // 8. Open Source Credits & Acknowledgements (开源致谢与许可协议)
            // =====================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = IosColors.SystemRed, modifier = Modifier.size(15.dp))
                Text(
                    text = "开源致谢与依赖项目 (Acknowledgements)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosColors.SecondaryLabel
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(20.dp),
                        elevation = 8.dp
                    ),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CreditItem(
                        name = "Haze",
                        author = "chrisbanes",
                        desc = "现代化 Jetpack Compose 玻璃拟态 (Glassmorphism) 与实时毛玻璃背景模糊渲染库",
                        url = "https://github.com/chrisbanes/haze"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    CreditItem(
                        name = "OSMDroid",
                        author = "osmdroid",
                        desc = "免 Key 开源 OpenStreetMap 高速瓦片底图渲染引擎与多手势地图控制器",
                        url = "https://github.com/osmdroid/osmdroid"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    CreditItem(
                        name = "LSPosed Framework",
                        author = "LSPosed Developers",
                        desc = "现代化 ART 运行时系统级 Hook 与 Xposed API 挂载注入框架，接管系统服务分发",
                        url = "https://github.com/LSPosed/LSPosed"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    CreditItem(
                        name = "Jetpack Compose",
                        author = "Google / AOSP",
                        desc = "Android 官方现代化声明式响应式 UI 工具包与 Material 3 基础套件",
                        url = "https://github.com/androidx/androidx"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    CreditItem(
                        name = "Kotlin Coroutines & Flow",
                        author = "JetBrains",
                        desc = "高性能异步并发控制与响应式状态流调度框架，驱动网络与位置模拟引擎",
                        url = "https://github.com/Kotlin/kotlinx.coroutines"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    CreditItem(
                        name = "Dagger Hilt",
                        author = "Google",
                        desc = "Android 官方标准编译期静态依赖注入组件，构建松耦合高扩展架构",
                        url = "https://github.com/google/dagger"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    CreditItem(
                        name = "Room Database",
                        author = "Google / Android Jetpack",
                        desc = "类型安全 SQLite 对象关系映射数据库，支持自定义轨迹与路线持久化存储",
                        url = "https://developer.android.com/training/data-storage/room"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    CreditItem(
                        name = "Gson",
                        author = "Google",
                        desc = "轻量级高性能 JSON 数据序列化与反序列化解析库，处理云端版本同步与配置交换",
                        url = "https://github.com/google/gson"
                    )
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                    CreditItem(
                        name = "LocationSpoofer",
                        author = "HuangZhuoRui",
                        desc = "全屏交互地图、抽屉式控制面板与拟真定位模拟交互设计参考",
                        url = "https://github.com/HuangZhuoRui"
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = IosColors.SecondaryLabel)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = if (isDark) Color.White else Color.Black
        )
    }
}

@Composable
private fun CreditItem(
    name: String,
    author: String,
    desc: String,
    url: String
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "无法打开链接: $url", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = IosColors.SystemBlue,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = author,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = IosColors.SystemBlue
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = desc,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = IosColors.SecondaryLabel
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = url,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = IosColors.SecondaryLabel.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    tags: List<String> = emptyList(),
    desc: String
) {
    val isDark = isSystemInDarkTheme()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color.Black
            )

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = iconTint.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = iconTint,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = IosColors.SecondaryLabel
            )
        }
    }
}

private fun formatMb(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "-- KB/s"
    val kb = bytesPerSec / 1024.0
    return if (kb >= 1024.0) {
        "%.1f MB/s".format(kb / 1024.0)
    } else {
        "%.0f KB/s".format(kb)
    }
}
