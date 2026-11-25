package com.mockrun.app.feature.map.tabs
import androidx.compose.material3.MaterialTheme
import com.mockrun.app.core.designsystem.AppSemanticColors
import androidx.compose.ui.text.font.FontFamily

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mockrun.app.domain.model.SimulationState
import com.mockrun.app.domain.model.SimulationStatus
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.core.location.SearchResultItem
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.LocalLiquidGlassEnabled
import com.mockrun.app.core.designsystem.liquidGlass
import com.mockrun.app.core.designsystem.minTouchTarget
import com.mockrun.app.feature.map.MapSearchField
import com.mockrun.app.feature.map.MapSearchState
import com.mockrun.app.feature.map.MapDrawerStage
import com.mockrun.app.feature.map.ThreeStageMapDrawer

enum class RouteStage {
    SELECTING,
    READY,
    RUNNING
}

/**
 * 巡航流程的三个阶段标识。
 *
 * 纯展示、不可点击（因此不受 48dp 触区约束）。当前阶段用蓝色底 + 白字，
 * 其余用三级文字色 —— 每一段都带文字，不靠颜色单独区分状态。
 */
@Composable
private fun RouteStageIndicator(
    stage: RouteStage,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        RouteStage.SELECTING to "选点",
        RouteStage.READY to "就绪",
        RouteStage.RUNNING to "巡航"
    )
    val currentIndex = steps.indexOfFirst { it.first == stage }
    Row(
        modifier = modifier
            .semantics {
                contentDescription = "路线阶段：${steps[currentIndex.coerceAtLeast(0)].second}"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (_, label) ->
            val isCurrent = index == currentIndex
            val isReached = index <= currentIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isReached -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.onPrimary
                            isReached -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isReached) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (index != steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(top = 11.dp)
                        .width(26.dp)
                        .height(2.dp)
                        .background(
                            if (index < currentIndex) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }
    }
}

/** One-line stage strip used by the half-expanded map-selection view. */
@Composable
private fun CompactRouteStageIndicator(
    stage: RouteStage,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        RouteStage.SELECTING to "选点",
        RouteStage.READY to "就绪",
        RouteStage.RUNNING to "巡航"
    )
    val currentIndex = steps.indexOfFirst { it.first == stage }.coerceAtLeast(0)
    Row(
        modifier = modifier.semantics {
            contentDescription = "路线阶段：${steps[currentIndex].second}"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        steps.forEachIndexed { index, (_, label) ->
            val isCurrent = index == currentIndex
            val isReached = index <= currentIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .background(
                        when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isReached -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isCurrent -> MaterialTheme.colorScheme.onPrimary
                        isReached -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isCurrent -> MaterialTheme.colorScheme.onPrimary
                        isReached -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
            }
            if (index != steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

/**
 * 巡航数字区单元 —— 标签在上，等宽数字在下。
 * 坐标与关键数字统一走 [MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)] 等宽样式，避免数字跳动时宽度抖动。
 */
@Composable
private fun RouteMetric(
    label: String,
    value: String,
    unit: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                color = tint,
                maxLines = 1
            )
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WaypointWorkspace(
    waypoints: List<WayPoint>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.panel,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        if (waypoints.isEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.AddLocationAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "点击地图放置起点，或从上方搜索地点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WaypointEndpoint(
                    label = "起点",
                    waypoint = waypoints.first(),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                WaypointEndpoint(
                    label = if (waypoints.size == 1) "等待终点" else "当前终点",
                    waypoint = waypoints.last(),
                    modifier = Modifier.weight(1f)
                )
                if (waypoints.size > 2) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = AppShapes.pill,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = "+${waypoints.size - 2}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
        }
}

@Composable
private fun WaypointEndpoint(
    label: String,
    waypoint: WayPoint,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${"%.4f".format(waypoint.latitude)}, ${"%.4f".format(waypoint.longitude)}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RouteSelectionActions(
    waypointCount: Int,
    stacked: Boolean,
    onAddWaypoint: () -> Unit,
    onFinishSelecting: () -> Unit,
    modifier: Modifier = Modifier
) {
    val addButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        Button(
            onClick = onAddWaypoint,
            modifier = buttonModifier.height(48.dp),
            shape = AppShapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.AddLocation, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (waypointCount == 0) "添加起点" else "添加第 ${waypointCount + 1} 点",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
        }
    }
    val finishButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        Button(
            onClick = onFinishSelecting,
            enabled = waypointCount >= 2,
            modifier = buttonModifier.height(48.dp),
            shape = AppShapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppSemanticColors.current.success,
                contentColor = AppSemanticColors.current.onSuccess,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "完成规划",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
        }
    }

    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            addButton(Modifier.fillMaxWidth())
            finishButton(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            addButton(Modifier.weight(1.2f))
            finishButton(Modifier.weight(1f))
        }
    }
}

/**
 * 完成 / 异常结果卡。
 *
 * 原实现的 [RouteStage] 只有 SELECTING / READY / RUNNING 三档，MapScreen 的状态
 * 映射只把 Running/Paused 归入 RUNNING，其余一律退回 READY —— 于是
 * [SimulationStatus.Completed] 与 [SimulationStatus.Error] 都被吞掉：
 * 巡航跑完只剩一句「路线已就绪」，看不到「已跑 X km · 用时 Y」，
 * 出错也看不到原因。这里把这两种终态显式承载出来，并提供「重新开始」。
 *
 * 本卡只做呈现，不触碰任何模拟算法。
 */
@Composable
private fun RouteResultCard(
    status: SimulationStatus,
    simState: SimulationState,
    canRestart: Boolean,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isError = status is SimulationStatus.Error
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val distanceKm = simState.distanceTraveledMeters / 1000.0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.card,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isError) "本次巡航异常结束" else "本次巡航已完成",
                    style = MaterialTheme.typography.titleSmall,
                    color = accent
                )
            }

            if (isError) {
                Text(
                    text = (status as SimulationStatus.Error).message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RouteMetric(
                        label = "已跑距离",
                        value = "%.2f".format(distanceKm),
                        unit = "km",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    RouteMetric(
                        label = "用时",
                        value = simState.formattedElapsedTime,
                        unit = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    RouteMetric(
                        label = "巡航配速",
                        value = "%.1f".format(simState.speedKmh),
                        unit = "km/h",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                enabled = canRestart,
                shape = AppShapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("重新开始", style = MaterialTheme.typography.labelLarge)
            }
            if (!canRestart) {
                Text(
                    text = "航点不足 2 个，先在「选点」阶段补齐起终点后再重新开始",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RouteCompactDrawerSummary(
    stage: RouteStage,
    waypoints: List<WayPoint>,
    simState: SimulationState,
    isPaused: Boolean,
    collapsed: Boolean,
    showExpand: Boolean,
    onExpand: () -> Unit,
    onAddWaypoint: (() -> Unit)? = null,
    onFinishSelecting: (() -> Unit)? = null,
    onSearchShortcut: (() -> Unit)? = null
) {
    val title = when (stage) {
        RouteStage.SELECTING -> if (waypoints.isEmpty()) "尚未添加航点" else "${waypoints.size} 个航点"
        RouteStage.READY -> "路线已就绪"
        RouteStage.RUNNING -> if (isPaused) "巡航已暂停" else "巡航中"
    }
    val detail = when (stage) {
        RouteStage.SELECTING -> if (waypoints.isEmpty()) {
            "点击地图放置起点"
        } else {
            "起点与终点已选 · 上拉查看完整规划"
        }
        RouteStage.READY -> "上拉查看路线详情并开启模拟"
        RouteStage.RUNNING -> "${(simState.progressPercent.coerceIn(0f, 1f) * 100).toInt()}% · " +
            "${"%.2f".format(simState.distanceTraveledMeters / 1000.0)} km"
    }
    val accent = if (stage == RouteStage.RUNNING && isPaused) {
        AppSemanticColors.current.warning
    } else {
        MaterialTheme.colorScheme.primary
    }

    if (collapsed) {
        if (stage == RouteStage.SELECTING && onAddWaypoint != null && onFinishSelecting != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clickable(onClick = onExpand),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$title · $detail",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onSearchShortcut != null) {
                        IconButton(
                            onClick = onSearchShortcut,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索航点",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "展开路线规划面板",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAddWaypoint,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = AppShapes.button,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.AddLocation, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (waypoints.isEmpty()) "添加起点" else "添加第 ${waypoints.size + 1} 点",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = onFinishSelecting,
                        enabled = waypoints.size >= 2,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = AppShapes.button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppSemanticColors.current.success,
                            contentColor = AppSemanticColors.current.onSuccess,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "完成规划",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$title · $detail",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "展开路线规划面板",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = showExpand, onClick = onExpand),
            shape = AppShapes.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(AppShapes.icon)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (stage) {
                            RouteStage.RUNNING -> Icons.Default.Navigation
                            else -> Icons.Default.Route
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showExpand) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "展开路线规划面板",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RouteBottomPanel(
    modifier: Modifier = Modifier,
    topChromeHeight: Dp = 0.dp,
    onPanelHeightChanged: (Int) -> Unit = {},
    stage: RouteStage,
    waypoints: List<WayPoint>,
    simState: SimulationState,
    selectedSpeed: Float,
    searchQuery: String = "",
    searchResults: List<SearchResultItem> = emptyList(),
    isSearching: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchResultSelect: (SearchResultItem) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onAddWaypoint: () -> Unit,
    onFinishSelecting: () -> Unit,
    onUndoWaypoint: () -> Unit,
    onClearWaypoints: () -> Unit,
    onReselect: () -> Unit,
    onOpenConfig: () -> Unit,
    onSaveRoute: () -> Unit,
    onStartSimulation: () -> Unit,
    onPauseSimulation: () -> Unit,
    onResumeSimulation: () -> Unit,
    onStopSimulation: () -> Unit
) {
    val isPaused = simState.status is SimulationStatus.Paused
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val configuration = LocalConfiguration.current
    val stackActions = configuration.fontScale >= 1.3f || configuration.screenWidthDp < 380
    // The first screen is directly actionable: search, add-point and finish-planning stay visible
    // without opening the middle or expanded editor.
    var drawerStage by remember { mutableStateOf(MapDrawerStage.COLLAPSED) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && drawerStage == MapDrawerStage.COLLAPSED) {
            drawerStage = MapDrawerStage.HALF_EXPANDED
        }
    }

    ThreeStageMapDrawer(
        modifier = modifier,
        stage = drawerStage,
        onStageChange = { drawerStage = it },
        topChromeHeight = topChromeHeight,
        horizontalMargin = 14.dp,
        onHeightChanged = onPanelHeightChanged
    ) { anchorStage ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = when (anchorStage) {
                        MapDrawerStage.COLLAPSED -> 0.dp
                        MapDrawerStage.HALF_EXPANDED -> 4.dp
                        MapDrawerStage.EXPANDED -> 8.dp
                    },
                    bottom = when (anchorStage) {
                        MapDrawerStage.COLLAPSED -> 4.dp
                        MapDrawerStage.HALF_EXPANDED -> 8.dp
                        MapDrawerStage.EXPANDED -> 16.dp
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                when (anchorStage) {
                    MapDrawerStage.COLLAPSED -> 0.dp
                    MapDrawerStage.HALF_EXPANDED -> 8.dp
                    MapDrawerStage.EXPANDED -> 10.dp
                }
            )
        ) {
        // =====================================================================
        // 0. 完成 / 异常结果卡（终态不属于三阶段之一，独立于 stage 渲染）
        // =====================================================================
        val terminalStatus = simState.status
        if (
            anchorStage != MapDrawerStage.COLLAPSED &&
            (terminalStatus is SimulationStatus.Completed || terminalStatus is SimulationStatus.Error)
        ) {
            RouteResultCard(
                status = terminalStatus,
                simState = simState,
                canRestart = waypoints.size >= 2,
                onRestart = onStartSimulation
            )
        }

        // =====================================================================
        // 1. Search Bar & Search Results (Only in SELECTING stage)
        // =====================================================================
        if (stage == RouteStage.SELECTING && anchorStage != MapDrawerStage.COLLAPSED) {
            Box(modifier = Modifier.fillMaxWidth()) {
                MapSearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = "搜索航点 / 地名 / 道路",
                    onClear = onClearSearch,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Search Results Dropdown List
            if (searchResults.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        items(searchResults) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minTouchTarget(minHeight = 48.dp)
                                    .clickable { onSearchResultSelect(item) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.address.isNotEmpty()) {
                                        Text(
                                            item.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 搜索中 / 无匹配结果 两态承载（旧实现只在数组非空时渲染，两种状态都无反馈）
                MapSearchState(
                    isSearching = isSearching,
                    query = searchQuery,
                    resultCount = searchResults.size,
                    loadingText = "正在检索航点…",
                    emptyText = "未找到匹配的航点，换个关键词或直接在地图上点选"
                )
            }
        }

        if (anchorStage == MapDrawerStage.COLLAPSED) {
            RouteCompactDrawerSummary(
                stage = stage,
                waypoints = waypoints,
                simState = simState,
                isPaused = isPaused,
                collapsed = true,
                showExpand = true,
                onExpand = { drawerStage = MapDrawerStage.HALF_EXPANDED },
                onAddWaypoint = if (stage == RouteStage.SELECTING) {
                    onAddWaypoint
                } else {
                    null
                },
                onFinishSelecting = if (stage == RouteStage.SELECTING) {
                    onFinishSelecting
                } else {
                    null
                },
                onSearchShortcut = if (stage == RouteStage.SELECTING) {
                    { drawerStage = MapDrawerStage.HALF_EXPANDED }
                } else {
                    null
                }
            )
        } else if (anchorStage == MapDrawerStage.HALF_EXPANDED) {
            if (stage == RouteStage.SELECTING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactRouteStageIndicator(stage = stage, modifier = Modifier.weight(1f))
                    Surface(
                        shape = AppShapes.pill,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = "${waypoints.size} 个航点",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                WaypointWorkspace(waypoints = waypoints)
                RouteSelectionActions(
                    waypointCount = waypoints.size,
                    stacked = stackActions,
                    onAddWaypoint = onAddWaypoint,
                    onFinishSelecting = onFinishSelecting
                )
            } else {
                RouteCompactDrawerSummary(
                    stage = stage,
                    waypoints = waypoints,
                    simState = simState,
                    isPaused = isPaused,
                    collapsed = false,
                    showExpand = true,
                    onExpand = { drawerStage = MapDrawerStage.EXPANDED }
                )
            }
        }

        // =====================================================================
        // 2. Main Route Control Surface
        // =====================================================================
        AnimatedVisibility(
            visible = anchorStage == MapDrawerStage.EXPANDED,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    isLiquidGlass = isLiquidGlass,
                    shape = AppShapes.card,
                    elevation = if (isLiquidGlass) 16.dp else 6.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                when (stage) {
                    // ---------------------------------------------------------
                    // 1. SELECTING: 选点阶段
                    // ---------------------------------------------------------
                    RouteStage.SELECTING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 阶段标识 + 航点计数
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RouteStageIndicator(stage = stage, modifier = Modifier.weight(1f))
                                Text(
                                    text = "${waypoints.size} 个航点",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            WaypointWorkspace(waypoints = waypoints)

                            // 撤销 / 清空：独立一行 + 48dp 触区，与下方主按钮之间隔一条分隔线
                            if (waypoints.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = onUndoWaypoint,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = MaterialTheme.shapes.large,
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("撤销上一点", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = onClearWaypoints,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = MaterialTheme.shapes.large,
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            "清空全部",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            }

                            RouteSelectionActions(
                                waypointCount = waypoints.size,
                                stacked = stackActions,
                                onAddWaypoint = onAddWaypoint,
                                onFinishSelecting = onFinishSelecting
                            )

                            // 选点不足 2 点：说明为什么「完成规划」不可用。
                            // 原先只靠按钮禁用表达，用户看不出差在哪一步
                            // （MapScreen 的 routeStage 也会悄悄退回 SELECTING）。
                            if (waypoints.size < 2) {
                                Text(
                                    text = if (waypoints.isEmpty()) {
                                        "「完成规划」需要至少 2 个航点：先在地图上点选起点，再加一个终点"
                                    } else {
                                        "还差 ${2 - waypoints.size} 个航点才能完成规划（至少需要 2 个）"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppSemanticColors.current.warning
                                )
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // 2. READY: 规划完成准备阶段
                    // ---------------------------------------------------------
                    RouteStage.READY -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RouteStageIndicator(stage = stage, modifier = Modifier.weight(1f))

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = onOpenConfig,
                                        modifier = Modifier.height(48.dp),
                                        shape = MaterialTheme.shapes.large,
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "参数设置",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            WaypointWorkspace(waypoints = waypoints)

                            Text(
                                text = "路线已就绪 · ${waypoints.size} 折点 · ${"%.1f".format(selectedSpeed)} km/h",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (stackActions) {
                                Button(
                                    onClick = onStartSimulation,
                                    enabled = waypoints.size >= 2,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = AppShapes.button
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "开启模拟",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onReselect,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = AppShapes.button
                                    ) {
                                        Text("重选", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = onSaveRoute,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = AppShapes.button
                                    ) {
                                        Text("收藏路线", style = MaterialTheme.typography.labelMedium, maxLines = 2)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = onReselect,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = AppShapes.button,
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("重选", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = onSaveRoute,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = AppShapes.button,
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Icon(Icons.Default.BookmarkAdd, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("收藏路线", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                    Button(
                                        onClick = onStartSimulation,
                                        enabled = waypoints.size >= 2,
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .height(48.dp),
                                        shape = AppShapes.button,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "开启模拟",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            if (waypoints.size < 2) {
                                Text(
                                    text = "航点不足 2 个，无法启动巡航：请先回到「选点」阶段补齐起终点",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppSemanticColors.current.warning
                                )
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // 3. RUNNING: 巡航模拟运行中
                    // ---------------------------------------------------------
                    RouteStage.RUNNING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RouteStageIndicator(stage = stage, modifier = Modifier.weight(1f))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPaused) {
                                                    AppSemanticColors.current.warning
                                                } else {
                                                    AppSemanticColors.current.success
                                                }
                                            )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isPaused) "已暂停" else "巡航中",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // 数字区：配速 / 距离 / 时间（等宽数字，暂停时配速转橙）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                RouteMetric(
                                    label = "当前配速",
                                    value = "%.1f".format(simState.speedKmh),
                                    unit = "km/h",
                                    tint = if (isPaused) AppSemanticColors.current.warning else AppSemanticColors.current.success,
                                    modifier = Modifier.weight(1f)
                                )
                                RouteMetric(
                                    label = "已跑距离",
                                    value = "%.2f".format(simState.distanceTraveledMeters / 1000.0),
                                    unit = "km",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                RouteMetric(
                                    label = "已用时间",
                                    value = simState.formattedElapsedTime,
                                    unit = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // 进度（文字 + 数值，不靠颜色单独表意）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinearProgressIndicator(
                                    progress = { simState.progressPercent.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (isPaused) AppSemanticColors.current.warning else AppSemanticColors.current.success,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "${(simState.progressPercent.coerceIn(0f, 1f) * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onOpenConfig,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = MaterialTheme.shapes.large,
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("配速", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }

                                Button(
                                    onClick = {
                                        if (isPaused) onResumeSimulation() else onPauseSimulation()
                                    },
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(48.dp),
                                    shape = MaterialTheme.shapes.large,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPaused) {
                                            AppSemanticColors.current.success
                                        } else {
                                            AppSemanticColors.current.warning
                                        },
                                        contentColor = if (isPaused) {
                                            AppSemanticColors.current.onSuccess
                                        } else {
                                            AppSemanticColors.current.onWarning
                                        }
                                    )
                                ) {
                                    Icon(
                                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (isPaused) "继续" else "暂停",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = onStopSimulation,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(48.dp),
                                    shape = MaterialTheme.shapes.large,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("停止", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
}
