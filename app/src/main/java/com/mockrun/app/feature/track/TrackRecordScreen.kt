package com.mockrun.app.feature.track

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    Box(modifier = Modifier.fillMaxSize().background(IosColors.SystemGroupedBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .minTouchTarget()
                        .clip(CircleShape)
                        .bouncyClickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = IosColors.Label,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "轨迹录制",
                    style = IosTypography.Headline,
                    fontWeight = FontWeight.Bold,
                    color = IosColors.Label,
                    modifier = Modifier.weight(1f)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    IosInsetGroupCard(isLiquidGlass = false) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                if (isRecording) "● 录制中" else "未在录制",
                                style = IosTypography.Subheadline,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRecording) IosDanger else IosColors.SecondaryLabel
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${"%.2f".format(distance / 1000.0)} km · $pointCount 点 · ${formatDuration(elapsedSec)}",
                                style = IosTypography.Title2,
                                fontWeight = FontWeight.Bold,
                                color = IosColors.Label
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "不需要 Root。屏幕可以关，手机放口袋即可；从最近任务划掉不会丢数据。",
                                style = IosTypography.Caption1,
                                color = IosColors.SecondaryLabel
                            )
                            Spacer(Modifier.height(12.dp))
                            IosPrimaryButton(
                                text = if (isRecording) "停止并保存" else "开始录制",
                                onClick = {
                                    if (isRecording) {
                                        trackViewModel.stop(context)
                                    } else {
                                        trackViewModel.start(context) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    Text(
                        "已录轨迹（${tracks.size}）",
                        style = IosTypography.Footnote,
                        color = IosColors.SecondaryLabel,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                if (tracks.isEmpty()) {
                    item {
                        Text(
                            "还没有录制过轨迹。走一遍没有路网的地方（操场、小区），回来就能把它变成巡航路线。",
                            style = IosTypography.Caption1,
                            color = IosColors.SecondaryLabel,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                items(tracks, key = { it.id }) { track ->
                    IosInsetGroupCard(isLiquidGlass = false) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    track.name,
                                    style = IosTypography.Subheadline,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IosColors.Label,
                                    modifier = Modifier.weight(1f)
                                )
                                if (track.isFavorite) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "已收藏",
                                        tint = IosWarning,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${"%.2f".format(track.distanceMeters / 1000.0)} km · ${formatDuration(track.durationMs / 1000)} · " +
                                    "${"%.1f".format(track.avgSpeedKmh)} km/h" +
                                    if (track.avgCadenceSpm > 0) " · ${track.avgCadenceSpm} SPM" else " · 步频估算",
                                style = IosTypography.Caption1,
                                color = IosColors.SecondaryLabel
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${dateFormatter.format(Date(track.startedAt))} · ${track.pointCount} 点" +
                                    if (track.isClosed) " · 闭合" else "",
                                style = IosTypography.Footnote,
                                color = IosColors.SecondaryLabel
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { trackViewModel.toggleFavorite(track) }) {
                                    Text(if (track.isFavorite) "取消收藏" else "收藏", style = IosTypography.Caption1)
                                }
                                OutlinedButton(onClick = {
                                    trackViewModel.convertToRoute(track) { route ->
                                        if (route != null) {
                                            Toast.makeText(context, "已生成路线「${route.name}」", Toast.LENGTH_SHORT).show()
                                            onRouteCreated(route)
                                        } else {
                                            Toast.makeText(context, "轨迹点数不足，无法生成路线", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Text("转为巡航路线", style = IosTypography.Caption1)
                                }
                                OutlinedButton(onClick = { trackViewModel.delete(track.id) }) {
                                    Text("删除", style = IosTypography.Caption1, color = IosDanger)
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
