package com.mockrun.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mockrun.app.core.data.repository.DownloadStatus
import com.mockrun.app.core.data.repository.RemoteReleaseInfo
import com.mockrun.app.core.data.repository.VersionSyncManager
import com.mockrun.app.core.designsystem.AppSemanticColors
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.GlassDialogWindowEffect
import com.mockrun.app.core.designsystem.LocalLiquidGlassEnabled
import com.mockrun.app.util.Diag

/**
 * Atlas update dialog.
 *
 * Downloading intentionally disables both back-press and outside-tap dismissal.
 * The user must explicitly choose 「取消下载」 before the dialog can be dismissed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateDialog(
    info: RemoteReleaseInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val downloadStatus by VersionSyncManager.downloadStatus
    val dismissAllowed = downloadStatus !is DownloadStatus.Downloading

    AlertDialog(
        onDismissRequest = {
            if (dismissAllowed) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = dismissAllowed,
            dismissOnClickOutside = dismissAllowed
        ),
        shape = AppShapes.sheet,
        containerColor = if (isLiquidGlass) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = 0.dp,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Column {
                GlassDialogWindowEffect(isLiquidGlass)
                Text(
                    text = "发现新版本",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            UpdateDialogBody(
                info = info,
                downloadStatus = downloadStatus
            )
        },
        confirmButton = {
            UpdateDialogActions(
                context = context,
                info = info,
                downloadStatus = downloadStatus,
                onDismiss = onDismiss
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateDialogBody(
    info: RemoteReleaseInfo,
    downloadStatus: DownloadStatus
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = AppSemanticColors.current
    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.52f).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxBodyHeight)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = 8.dp,
                alignment = Alignment.CenterHorizontally
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer
            ) {
                Text(
                    text = info.tagName,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (info.publishedAt.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurfaceVariant
                ) {
                    Text(
                        text = info.publishedAt,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "更新日志",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp),
            shape = MaterialTheme.shapes.medium,
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            border = BorderStroke(1.dp, scheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = info.releaseNotes.ifBlank {
                        "本次更新包含多项稳定性改进与细节体验优化。"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (downloadStatus !is DownloadStatus.Idle) {
            Spacer(Modifier.height(16.dp))
        }

        when (val state = downloadStatus) {
            is DownloadStatus.Idle -> Unit

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
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = scheme.primary,
                        trackColor = scheme.surfaceContainerHighest
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatMb(state.downloadedBytes)} / ${formatMb(state.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = formatSpeed(state.speedBytesPerSec),
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.success
                        )
                    }
                }
            }

            is DownloadStatus.Success -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = semantic.success,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "安装包下载完成并已通过安全校验",
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantic.success,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            is DownloadStatus.Error -> {
                var errorExpanded by remember(state.message) { mutableStateOf(false) }

                TextButton(
                    onClick = { errorExpanded = !errorExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = scheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                        maxLines = if (errorExpanded) Int.MAX_VALUE else 2,
                        overflow = if (errorExpanded) {
                            TextOverflow.Visible
                        } else {
                            TextOverflow.Ellipsis
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (errorExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (errorExpanded) "收起错误详情" else "展开错误详情",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateDialogActions(
    context: Context,
    info: RemoteReleaseInfo,
    downloadStatus: DownloadStatus,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (val state = downloadStatus) {
            is DownloadStatus.Idle -> {
                Button(
                    onClick = { VersionSyncManager.startDownload(context, info) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "立即在线更新",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            try {
                                val targetUrl = info.releaseHtmlUrl.ifBlank { info.downloadUrl }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Diag.d(
                                    "AppUpdateDialog",
                                    "open browser for release page failed: ${e.message}"
                                )
                                Toast.makeText(
                                    context,
                                    "无法打开浏览器",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "浏览器下载",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "稍后提醒",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            is DownloadStatus.Downloading -> {
                OutlinedButton(
                    onClick = { VersionSyncManager.cancelDownload() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "取消下载",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            is DownloadStatus.Success -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "稍后",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = { VersionSyncManager.installApk(context, state.file) },
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "立即安装",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            is DownloadStatus.Error -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = { VersionSyncManager.startDownload(context, info) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "重试下载",
                            style = MaterialTheme.typography.labelLarge
                        )
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
