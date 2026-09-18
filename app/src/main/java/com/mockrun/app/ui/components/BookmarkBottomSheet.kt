package com.mockrun.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.domain.model.Route
import com.mockrun.app.core.designsystem.IosColors
import com.mockrun.app.core.designsystem.IosTypography
import com.mockrun.app.core.designsystem.bouncyClickable

/**
 * 收藏夹的两种视图。
 *
 * 定位标签放地点收藏（单点），路线标签放航线收藏（多点），二者数据同源，
 * 仅按航点数在 [com.mockrun.app.ui.viewmodel.MapViewModel] 中分流。
 */
enum class BookmarkKind { LOCATION, TRACK }

/**
 * 右上角工具胶囊旁的收藏夹弹层。
 *
 * @param items 当前标签对应的收藏列表，已由调用方过滤。
 * @param selectedRouteId 用于标注「当前载入」的航线，地点收藏不参与该标记。
 * @param onSelect 点按条目：地点收藏用于跳转定位，航线收藏用于载入路线。
 * @param onDelete 确认删除后的回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkBottomSheet(
    kind: BookmarkKind,
    items: List<Route>,
    selectedRouteId: Long?,
    onDismissRequest: () -> Unit,
    onSelect: (Route) -> Unit,
    onDelete: (Route) -> Unit
) {
    val isLocation = kind == BookmarkKind.LOCATION
    var pendingDelete by remember { mutableStateOf<Route?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = IosColors.SecondaryGroupedBackground,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 5.dp),
                shape = CircleShape,
                color = IosColors.SystemGray.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isLocation) "地点收藏" else "航线收藏",
                        style = IosTypography.Headline,
                        color = IosColors.Label,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isLocation) "收藏本中的地点坐标，点按即可跳转定位" else "收藏本中的模拟航线，点按即可载入",
                        style = IosTypography.Footnote,
                        color = IosColors.SecondaryLabel
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = IosColors.SystemBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${items.size} 条",
                        color = IosColors.SystemBlue,
                        style = IosTypography.Caption1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isLocation) Icons.Default.Place else Icons.Default.Route,
                            contentDescription = null,
                            tint = IosColors.SecondaryLabel,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (isLocation) "还没有收藏地点" else "还没有收藏航线",
                            style = IosTypography.Subheadline,
                            color = IosColors.Label,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isLocation) "在地图上选好位置后点「收藏此点」" else "在路线标签画好航线后点「收藏」",
                            style = IosTypography.Footnote,
                            color = IosColors.SecondaryLabel
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { route ->
                        val isCurrent = !isLocation && selectedRouteId == route.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .bouncyClickable { onSelect(route) },
                            shape = RoundedCornerShape(14.dp),
                            color = IosColors.TertiarySystemFill,
                            border = if (isCurrent) BorderStroke(1.dp, IosColors.SystemBlue) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isLocation) {
                                        IosColors.SystemBlue.copy(alpha = 0.14f)
                                    } else {
                                        IosColors.SystemGreen.copy(alpha = 0.16f)
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = if (isLocation) Icons.Default.Place else Icons.Default.Route,
                                            contentDescription = null,
                                            tint = if (isLocation) IosColors.SystemBlue else IosColors.SystemGreen,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = route.name,
                                        style = IosTypography.Subheadline,
                                        color = IosColors.Label,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = route.waypoints.firstOrNull()?.let { wp ->
                                            if (isLocation) {
                                                "WGS-84: %s, %s".format(
                                                    "%.6f".format(wp.latitude),
                                                    "%.6f".format(wp.longitude)
                                                )
                                            } else {
                                                "${route.waypoints.size} 个折点 · %.2f km".format(route.totalDistanceKm)
                                            }
                                        } ?: "坐标缺失",
                                        style = IosTypography.Caption1,
                                        color = IosColors.SecondaryLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "当前载入",
                                        tint = IosColors.SystemBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }

                                IconButton(
                                    onClick = { pendingDelete = route },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "删除收藏",
                                        tint = IosColors.SystemRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    text = if (isLocation) "删除该收藏地点？" else "删除该收藏航线？",
                    style = IosTypography.Headline,
                    color = IosColors.Label,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "「${target.name}」将从收藏本中移除，此操作无法撤销。",
                    style = IosTypography.Subheadline,
                    color = IosColors.SecondaryLabel
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    pendingDelete = null
                }) {
                    Text("删除", color = IosColors.SystemRed, fontWeight = FontWeight.Bold, style = IosTypography.Headline)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = IosColors.SecondaryLabel, style = IosTypography.Headline)
                }
            },
            containerColor = IosColors.SecondaryGroupedBackground,
            shape = RoundedCornerShape(18.dp)
        )
    }
}
