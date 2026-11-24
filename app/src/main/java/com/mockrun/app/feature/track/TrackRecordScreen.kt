package com.mockrun.app.feature.track

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.data.db.TrackSummary
import com.mockrun.app.core.designsystem.*
import com.mockrun.app.domain.model.Route
import com.mockrun.app.ui.viewmodel.TrackViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轨迹录制 / 收藏 / 转路线。
 *
 * 作为路线库的叠加层打开，避免再动导航图。屏幕熄灭后录制继续（前台服务 + WakeLock），
 * 所以这里的 UI 只负责开始/停止与结果管理。
 */
@Composable
fun TrackRecordScreen(
    trackViewModel: TrackViewModel,
    onClose: () -> Unit,
    onRouteCreated: (Route) -> Unit
) {
    val context = LocalContext.current
    val tracks by trackViewModel.tracks.collectAsState()
    val isRecording by trackViewModel.isRecording.collectAsState()
    val distance by trackViewModel.distanceMeters.collectAsState()
    val pointCount by trackViewModel.pointCount.collectAsState()
    val startedAt by trackViewModel.startedAt.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val elapsedSec = if (isRecording && startedAt > 0) ((now - startedAt) / 1000).coerceAtLeast(0) else 0L
    val dateFormatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "轨迹录制",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 底部导航是 AppNavigation 画的浮层，会压住内容；留出安全距离（横屏同样有效）
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AtlasGroup(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (isRecording) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AtlasStatusPill(
                                    text = if (isRecording) "录制中" else "待机",
                                    tone = if (isRecording) {
                                        AtlasStatusTone.DANGER
                                    } else {
                                        AtlasStatusTone.NEUTRAL
                                    }
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = if (isRecording) "正在记录真实 GPS" else "准备记录新轨迹",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = formatDuration(elapsedSec),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AtlasMetric(
                                    label = "距离",
                                    value = "%.2f".format(distance / 1000.0),
                                    unit = "km",
                                    modifier = Modifier.weight(1f)
                                )
                                AtlasMetric(
                                    label = "采样点",
                                    value = pointCount.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "不需要 Root。屏幕可以关，手机放口袋即可；从最近任务划掉不会丢数据。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (isRecording) {
                                        trackViewModel.stop(context)
                                    } else {
                                        trackViewModel.start(context) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = AppShapes.button,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    contentColor = if (isRecording) {
                                        MaterialTheme.colorScheme.onError
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = if (isRecording) "停止并保存" else "开始录制",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "已录轨迹（${tracks.size}）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                if (tracks.isEmpty()) {
                    item {
                        Text(
                            "还没有录制过轨迹。走一遍没有路网的地方（操场、小区），回来就能把它变成巡航路线。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                items(tracks, key = { it.id }) { track ->
                    AtlasGroup(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    if (track.isFavorite) {
                                        AppSemanticColors.current.warning
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    }
                                )
                        )
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    track.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (track.isFavorite) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "已收藏",
                                        tint = AppSemanticColors.current.warning,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${"%.2f".format(track.distanceMeters / 1000.0)} km · ${formatDuration(track.durationMs / 1000)} · " +
                                    "${"%.1f".format(track.avgSpeedKmh)} km/h" +
                                    if (track.avgCadenceSpm > 0) " · ${track.avgCadenceSpm} SPM" else " · 步频估算",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${dateFormatter.format(Date(track.startedAt))} · ${track.pointCount} 点" +
                                    if (track.isClosed) " · 闭合" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    trackViewModel.convertToRoute(track) { route ->
                                        if (route != null) {
                                            Toast.makeText(context, "已生成路线「${route.name}」", Toast.LENGTH_SHORT).show()
                                            onRouteCreated(route)
                                        } else {
                                            Toast.makeText(context, "轨迹点数不足，无法生成路线", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = AppShapes.button
                            ) {
                                Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("转成路线", style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { trackViewModel.toggleFavorite(track) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        if (track.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        if (track.isFavorite) "取消收藏" else "收藏",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                                TextButton(
                                    onClick = { trackViewModel.delete(track.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "删除",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
