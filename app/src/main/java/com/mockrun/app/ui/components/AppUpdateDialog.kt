package com.mockrun.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mockrun.app.data.repository.DownloadStatus
import com.mockrun.app.data.repository.RemoteReleaseInfo
import com.mockrun.app.data.repository.VersionSyncManager
import com.mockrun.app.ui.theme.IosColors
import com.mockrun.app.ui.theme.liquidGlass

@Composable
fun AppUpdateDialog(
    info: RemoteReleaseInfo,
    isLiquidGlass: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val downloadStatus by VersionSyncManager.downloadStatus

    Dialog(
        onDismissRequest = {
            if (downloadStatus !is DownloadStatus.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = downloadStatus !is DownloadStatus.Downloading,
            dismissOnClickOutside = downloadStatus !is DownloadStatus.Downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .liquidGlass(
                    isLiquidGlass = isLiquidGlass,
                    shape = RoundedCornerShape(28.dp),
                    elevation = 16.dp
                ),
            shape = RoundedCornerShape(28.dp),
            color = if (isLiquidGlass) Color.Transparent else if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon Badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF007AFF), Color(0xFF5856D6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Title
                Text(
                    text = "发现新版本",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )

                Spacer(Modifier.height(6.dp))

                // Version & Date Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = IosColors.SystemBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = info.tagName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IosColors.SystemBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (info.publishedAt.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color(0x33FFFFFF) else Color(0x14000000)
                        ) {
                            Text(
                                text = info.publishedAt,
                                fontSize = 11.sp,
                                color = IosColors.SecondaryLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Release Notes Card
                Text(
                    text = "更新日志",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IosColors.SecondaryLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0x33000000) else Color(0x0A000000),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (isDark) Color(0x22FFFFFF) else Color(0x15000000)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = info.releaseNotes.ifBlank { "本次更新包含多项稳定性改进与细节体验优化。" },
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = if (isDark) Color(0xFFDDDDDD) else Color(0xFF333333)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Download Progress / Status Section
                when (val state = downloadStatus) {
                    is DownloadStatus.Idle -> {
                        // Action Buttons: Update Now & Remind Later
                        Button(
                            onClick = { VersionSyncManager.startDownload(context, info) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IosColors.SystemBlue
                            )
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("立即在线更新", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseHtmlUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            ) {
                                Text("浏览器下载", fontSize = 12.5.sp, color = IosColors.SecondaryLabel)
                            }

                            TextButton(onClick = onDismiss) {
                                Text("稍后提醒", fontSize = 13.sp, color = IosColors.SecondaryLabel)
                            }
                        }
                    }

                    is DownloadStatus.Downloading -> {
                        val animatedProgress by animateFloatAsState(
                            targetValue = state.progress,
                            label = "downloadProgress"
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.currentSource,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = IosColors.SystemBlue
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color.Black
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = IosColors.SystemBlue,
                                trackColor = if (isDark) Color(0x33FFFFFF) else Color(0x18000000)
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${formatMb(state.downloadedBytes)} / ${formatMb(state.totalBytes)}",
                                    fontSize = 11.5.sp,
                                    color = IosColors.SecondaryLabel
                                )
                                Text(
                                    text = formatSpeed(state.speedBytesPerSec),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IosColors.SystemGreen
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            OutlinedButton(
                                onClick = { VersionSyncManager.cancelDownload() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("取消下载", fontSize = 13.5.sp, color = IosColors.SecondaryLabel)
                            }
                        }
                    }

                    is DownloadStatus.Success -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = IosColors.SystemGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "下载完成，正在调起安装...",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IosColors.SystemGreen
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = { VersionSyncManager.installApk(context, state.file) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IosColors.SystemGreen
                                )
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("立即安装", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }

                    is DownloadStatus.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = IosColors.SystemRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = state.message,
                                    fontSize = 12.5.sp,
                                    color = IosColors.SystemRed,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("关闭", fontSize = 13.5.sp)
                                }

                                Button(
                                    onClick = { VersionSyncManager.startDownload(context, info) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = IosColors.SystemBlue
                                    )
                                ) {
                                    Text("重试下载", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
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
