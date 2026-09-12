package com.mockrun.app.ui.screen.tabs

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.domain.model.SimulationState
import com.mockrun.app.domain.model.SimulationStatus
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.location.SearchResultItem
import com.mockrun.app.ui.theme.IosColors
import com.mockrun.app.ui.theme.liquidGlass

enum class RouteStage {
    SELECTING,
    READY,
    RUNNING
}

@Composable
fun RouteBottomPanel(
    modifier: Modifier = Modifier,
    stage: RouteStage,
    waypoints: List<WayPoint>,
    simState: SimulationState,
    selectedSpeed: Float,
    isLiquidGlass: Boolean,
    bottomBarPadding: Dp,
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
    val isDark = isSystemInDarkTheme()
    val isPaused = simState.status is SimulationStatus.Paused

    // Pulsating animation for running status
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = (bottomBarPadding + 8.dp).coerceAtLeast(0.dp))
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
        // 1. Search Bar & Search Results (Only in SELECTING stage)
        // =====================================================================
        if (stage == RouteStage.SELECTING) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(24.dp),
                        elevation = 6.dp
                    ),
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索航点",
                        tint = if (isDark) Color.White.copy(0.7f) else IosColors.SecondaryLabel,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                "搜索航点 / 地名 / 道路",
                                fontSize = 13.5.sp,
                                color = if (isDark) Color.White.copy(0.5f) else IosColors.SecondaryLabel
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = IosColors.SystemBlue
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = IosColors.SystemBlue
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = onClearSearch,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除",
                                tint = if (isDark) Color.White.copy(0.7f) else IosColors.SecondaryLabel,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Search Results Dropdown List
            if (searchResults.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .liquidGlass(
                            isLiquidGlass = isLiquidGlass,
                            shape = RoundedCornerShape(20.dp),
                            elevation = 10.dp
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent
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
                                    .clickable { onSearchResultSelect(item) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = IosColors.SystemBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = if (isDark) Color.White else Color.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.address.isNotEmpty()) {
                                        Text(
                                            item.address,
                                            fontSize = 11.5.sp,
                                            color = if (isDark) Color.White.copy(0.6f) else IosColors.SecondaryLabel,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 2. Main Route Control Surface
        // =====================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    isLiquidGlass = isLiquidGlass,
                    shape = RoundedCornerShape(22.dp),
                    elevation = 8.dp
                ),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent
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
                            // Top info & actions row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (waypoints.isEmpty()) IosColors.SystemBlue.copy(0.12f) else IosColors.SystemGreen.copy(0.15f)
                                    ) {
                                        Text(
                                            text = if (waypoints.isEmpty()) "📍 待定起点" else "📍 已选 ${waypoints.size} 个折点",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = if (waypoints.isEmpty()) IosColors.SystemBlue else IosColors.SystemGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (waypoints.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = onUndoWaypoint,
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Undo, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("撤销", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = onClearWaypoints,
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(14.dp), tint = IosColors.SystemRed)
                                            Spacer(Modifier.width(3.dp))
                                            Text("清空", fontSize = 12.sp, color = IosColors.SystemRed)
                                        }
                                    }
                                }
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
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemBlue)
                                ) {
                                    Icon(Icons.Default.AddLocation, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (waypoints.isEmpty()) "添加起点" else "添加第 ${waypoints.size + 1} 点",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Button(
                                    onClick = onFinishSelecting,
                                    enabled = waypoints.size >= 2,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = IosColors.SystemGreen,
                                        disabledContainerColor = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
                                    )
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "完成规划",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
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
                                Text(
                                    text = "✅ 路线已就绪 · ${waypoints.size} 折点 · ${"%.1f".format(selectedSpeed)} km/h",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = if (isDark) Color.White else IosColors.Label
                                )

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenConfig() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = IosColors.SystemBlue.copy(0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Tune, null, tint = IosColors.SystemBlue, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("参数设置", fontSize = 12.sp, color = IosColors.SystemBlue, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

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
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("重选", fontSize = 13.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = onSaveRoute,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Default.BookmarkAdd, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("收藏", fontSize = 13.sp, maxLines = 1)
                                }

                                Button(
                                    onClick = onStartSimulation,
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemBlue),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("开启模拟", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // 3. RUNNING: 巡航模拟运行中
                    // ---------------------------------------------------------
                    RouteStage.RUNNING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPaused) IosColors.SystemOrange else IosColors.SystemGreen.copy(alpha = pulseAlpha)
                                            )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isPaused) "已暂停" else "巡航中: ${"%.1f".format(simState.speedKmh)} km/h",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isDark) Color.White else IosColors.Label
                                    )
                                }

                                Text(
                                    text = "已跑 ${"%.2f".format(simState.distanceTraveledMeters / 1000.0)} km · ${simState.formattedElapsedTime}",
                                    fontSize = 12.5.sp,
                                    color = if (isDark) Color.White.copy(0.7f) else IosColors.SecondaryLabel
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
                                        .height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("配速", fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        if (isPaused) onResumeSimulation() else onPauseSimulation()
                                    },
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPaused) IosColors.SystemGreen else IosColors.SystemOrange
                                    )
                                ) {
                                    Icon(
                                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isPaused) "继续" else "暂停", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                }

                                Button(
                                    onClick = onStopSimulation,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemRed)
                                ) {
                                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("停止", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
