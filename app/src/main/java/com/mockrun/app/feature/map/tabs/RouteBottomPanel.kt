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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mockrun.app.domain.model.SimulationState
import com.mockrun.app.domain.model.SimulationStatus
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.core.location.SearchResultItem
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.ScreenInsets
import com.mockrun.app.core.designsystem.minTouchTarget
import com.mockrun.app.feature.map.MapSearchField
import com.mockrun.app.feature.map.MapSearchState

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
        RouteStage.SELECTING to "① 选点",
        RouteStage.READY to "② 就绪",
        RouteStage.RUNNING to "③ 巡航"
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEach { (step, label) ->
            val active = step == stage
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
fun RouteBottomPanel(
    modifier: Modifier = Modifier,
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
    // 底部安全区统一由 ScreenInsets 推导（系统导航栏 + 底栏 72 + 上下外距 12 + 呼吸 8）。
    // 不再依赖调用方传入的 navBar + 94 硬编码值。
    val bottomInset = ScreenInsets.bottomInset(extra = 8.dp)

    // 仅在巡航运行中且未暂停时启动脉冲动画，避免在选点/就绪阶段每帧空转触发重组
    val pulseAlpha = if (stage == RouteStage.RUNNING && !isPaused) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        anim
    } else {
        1.0f
    }

    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = bottomInset.coerceAtLeast(0.dp))
            // 与定位页抽屉同一套「地图可用区」约束：调用方的 heightIn(max = …) 把面板压在
            // 地图净空以内后，超出的内容在这里滚动查看，而不是把面板顶到顶部控件上或裁掉内容。
            .verticalScroll(rememberScrollState())
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // =====================================================================
        // 0. 完成 / 异常结果卡（终态不属于三阶段之一，独立于 stage 渲染）
        // =====================================================================
        val terminalStatus = simState.status
        if (terminalStatus is SimulationStatus.Completed || terminalStatus is SimulationStatus.Error) {
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
        if (stage == RouteStage.SELECTING) {
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

        // =====================================================================
        // 2. Main Route Control Surface
        // =====================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = AppShapes.card,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                RouteStageIndicator(stage = stage)
                                Text(
                                    text = "${waypoints.size} 个航点",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

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
                                        Icon(Icons.Default.Undo, null, modifier = Modifier.size(16.dp))
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

                            // Main action buttons: +添加路点 与 完成规划
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onAddWaypoint,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(48.dp),
                                    shape = MaterialTheme.shapes.large,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.AddLocation, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (waypoints.isEmpty()) "添加起点" else "添加第 ${waypoints.size + 1} 点",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = onFinishSelecting,
                                    enabled = waypoints.size >= 2,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = MaterialTheme.shapes.large,
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
                                        maxLines = 1
                                    )
                                }
                            }

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
                                RouteStageIndicator(stage = stage)

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

                            Text(
                                text = "✅ 路线已就绪 · ${waypoints.size} 折点 · ${"%.1f".format(selectedSpeed)} km/h",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

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
                                    shape = MaterialTheme.shapes.large,
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
                                    shape = MaterialTheme.shapes.large,
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Default.BookmarkAdd, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("收藏路线", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }

                                Button(
                                    onClick = onStartSimulation,
                                    // 第三层防护：waypoints 不足 2 个时禁用。
                                    // 正常情况下 MapScreen 的 routeStage 会先退回 SELECTING，
                                    // 但这个面板也可能在状态短暂不一致时被渲染 ——
                                    // 一旦以空航点启动，RouteSimulator 会走 require() 崩溃路径
                                    // （该异常在无 CoroutineExceptionHandler 的作用域内抛出）。
                                    enabled = waypoints.size >= 2,
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(48.dp),
                                    shape = MaterialTheme.shapes.large,
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
                                RouteStageIndicator(stage = stage)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPaused) AppSemanticColors.current.warning else AppSemanticColors.current.success.copy(alpha = pulseAlpha)
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
