package com.mockrun.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.AppSemanticColors
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.AtlasSurface
import com.mockrun.app.core.designsystem.GlassDialogWindowEffect
import com.mockrun.app.core.designsystem.LocalLiquidGlassEnabled
import com.mockrun.app.core.designsystem.minTouchTarget
import com.mockrun.app.domain.model.Route

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
 * 弹窗规格：长列表使用 Material 3 ModalBottomSheet。
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
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    var pendingDelete by remember { mutableStateOf<Route?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color.Transparent,
        shape = AppShapes.sheet,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f)
    ) {
        AtlasSurface(
            modifier = Modifier.fillMaxWidth(),
            isLiquidGlass = isLiquidGlass,
            shape = AppShapes.sheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            elevation = if (isLiquidGlass) 22.dp else 8.dp
        ) {
            GlassDialogWindowEffect(isLiquidGlass)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLocation) "地点收藏" else "航线收藏",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isLocation) "收藏本中的地点坐标，点按即可跳转定位" else "收藏本中的模拟航线，点按即可载入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                val badgeContainer = if (items.isEmpty()) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
                val badgeContent = if (items.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
                Surface(
                    shape = CircleShape,
                    color = badgeContainer,
                    contentColor = badgeContent
                ) {
                    Text(
                        text = "${items.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1
                    )
                }
            }

            if (items.isEmpty()) {
                // 空态按类型区分 —— 两种收藏的引导动作不同，不合并文案。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isLocation) Icons.Default.Place else Icons.Default.Route,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (isLocation) "还没有收藏地点" else "还没有收藏航线",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isLocation) "在地图上选好位置后点「收藏此点」" else "在路线标签画好航线后点「收藏」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            onClick = { onSelect(route) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (isLocation) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        AppSemanticColors.current.successContainer
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = if (isLocation) Icons.Default.Place else Icons.Default.Route,
                                            contentDescription = null,
                                            tint = if (isLocation) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                AppSemanticColors.current.onSuccessContainer
                                            },
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = route.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
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
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "当前载入",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }

                                IconButton(onClick = { pendingDelete = route }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "删除收藏",
                                        tint = MaterialTheme.colorScheme.error,
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
    }

    // 短确认使用 Material 3 AlertDialog。
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    text = if (isLocation) "删除该收藏地点？" else "删除该收藏航线？",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "「${target.name}」将从收藏本中移除，此操作无法撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target)
                        pendingDelete = null
                    },
                    modifier = Modifier.minTouchTarget()
                ) {
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.minTouchTarget()
                ) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}
