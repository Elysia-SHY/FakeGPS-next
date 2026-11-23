package com.mockrun.app.feature.map
import androidx.compose.material3.MaterialTheme
import com.mockrun.app.core.designsystem.AppSemanticColors

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.mockrun.app.domain.model.Route
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.core.location.CoordinateConverter
import com.mockrun.app.core.location.RoadMode
import com.mockrun.app.core.location.RoadRouteResult
import com.mockrun.app.core.location.SearchResultItem
import com.mockrun.app.util.GeoTransform
import com.mockrun.app.core.designsystem.*
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel

// =============================================================================
// 弹窗 / 抽屉统一规范（全 App）
// =============================================================================
//   A. 短确认 / 短输入（重命名、删除确认、手动包名）→ AlertDialog，圆角 24dp
//   B. 长表单 / 长列表（搜索、道路规划、收藏、应用选择）→ ModalBottomSheet，顶角 32dp
//   C. 品牌更新对话框 → Dialog，圆角 28dp（唯一例外，见 AppUpdateDialog）
//
//   三条共用的细则：
//     1. 底板统一 MaterialTheme.colorScheme.surfaceContainer
//     2. 标题 MaterialTheme.typography.titleMedium + Bold + MaterialTheme.colorScheme.onSurface；副标题 MaterialTheme.typography.labelMedium
//     3. 所有可点击元素 ≥48dp 触区（Modifier.minTouchTarget() 放在 bouncyClickable 外层）
//     4. 内容超出可视区一律可滚动，输入区补 imePadding()
// =============================================================================

/** 长表单 / 长列表 sheet 的顶角圆角 —— 规范 B 的唯一取值。 */
private val SheetCorner = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/** 统一拖拽把手 —— 四个 sheet 共用同一几何。 */
@Composable
private fun SheetDragHandle() {
    Surface(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 5.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    ) {}
}

/** sheet 标题行：左标题 + 右关闭（48dp 触区）。 */
@Composable
private fun SheetHeader(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .minTouchTarget()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 可点击小胶囊 —— 视觉仍是紧凑 pill，热区由外层 Box 撑到 48dp。
 *
 * 若把 minTouchTarget 直接加在 pill 上，胶囊底板会被一起拉高到 48dp，
 * 观感变粗；这里把 bouncyClickable 放在外层 Box，视觉与热区解耦。
 */
@Composable
private fun TappableChip(
    text: String,
    color: Color,
    onClick: () -> Unit,
    outlined: Boolean = false
) {
    Box(
        modifier = Modifier
            .minTouchTarget()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = color.copy(alpha = 0.12f),
            border = if (outlined) BorderStroke(0.5.dp, color.copy(alpha = 0.25f)) else null
        ) {
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchLocationDialog(
    mapViewModel: MapViewModel,
    onDismissRequest: () -> Unit,
    onSelectLocation: (Double, Double, String) -> Unit,
    onSetAsOrigin: (Double, Double) -> Unit,
    onSetAsDestination: (Double, Double) -> Unit,
    onDirectMock: (Double, Double) -> Unit
) {
    val searchQuery by mapViewModel.searchQuery.collectAsState()
    val searchResults by mapViewModel.searchResults.collectAsState()
    val isSearching by mapViewModel.isSearching.collectAsState()

    var textInput by remember { mutableStateOf(searchQuery) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetCorner,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SheetHeader(
                title = "搜索地点与经纬度",
                subtitle = "支持地名 / POI 关键词，也可直接粘贴经纬度",
                onClose = onDismissRequest
            )

            // Search Input Field
            OutlinedTextField(
                value = textInput,
                onValueChange = {
                    textInput = it
                    mapViewModel.performSearch(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入地名/POI或经纬度(如 39.9, 116.4)", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (textInput.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .minTouchTarget()
                                .clip(CircleShape)
                                .clickable {
                                    textInput = ""
                                    mapViewModel.clearSearch()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "清空",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )

            // Quick Popular Shortcut Chips
            Text("热门地点直达：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 这些常量是**高德/腾讯地图上读到的 GCJ-02 值**（例：天安门 GCJ-02 ≈ 39.9087,116.3975，
                // 同一地点的 WGS-84 ≈ 39.9074,116.3913）。旧实现把它们直接当 WGS-84 消费：
                // 显示时又转一次 GCJ、注入时按 WGS-84 下发，于是「点热门地点」选出来的点
                // 和标签上的地方相差一个 GCJ 偏移（约 500m 东北）。
                // 这里在定义处显式声明来源坐标系，并转成内部统一的 WGS-84。
                val quickPois = listOf(
                    "北京天安门" to GeoTransform.toWgs84(39.9087, 116.3975, GeoTransform.CoordSystem.GCJ02),
                    "上海外滩" to GeoTransform.toWgs84(31.2397, 121.4904, GeoTransform.CoordSystem.GCJ02),
                    "广州塔" to GeoTransform.toWgs84(23.1064, 113.3245, GeoTransform.CoordSystem.GCJ02),
                    "深圳湾" to GeoTransform.toWgs84(22.5025, 113.9510, GeoTransform.CoordSystem.GCJ02),
                    "成都春熙路" to GeoTransform.toWgs84(30.6558, 104.0818, GeoTransform.CoordSystem.GCJ02),
                    "杭州西湖" to GeoTransform.toWgs84(30.2460, 120.1490, GeoTransform.CoordSystem.GCJ02)
                )
                items(quickPois) { (name, coords) ->
                    TappableChip(
                        text = name,
                        color = MaterialTheme.colorScheme.primary,
                        outlined = true,
                        onClick = {
                            textInput = name
                            onSelectLocation(coords.first, coords.second, name)
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Search Results or Loading
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("正在联网检索地点信息...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.address,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                                Spacer(Modifier.height(4.dp))
                                // 四个动作胶囊在窄屏 + 大字号下会超出一行（最小 48dp 触区
                                // 把每个胶囊的下限抬到 48dp），用 FlowRow 折行保证都可达。
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    TappableChip(
                                        text = "定位到此",
                                        color = MaterialTheme.colorScheme.primary,
                                        onClick = { onSelectLocation(item.latitude, item.longitude, item.name) }
                                    )
                                    TappableChip(
                                        text = "设为起点",
                                        color = AppSemanticColors.current.success,
                                        onClick = { onSetAsOrigin(item.latitude, item.longitude) }
                                    )
                                    TappableChip(
                                        text = "设为终点",
                                        color = AppSemanticColors.current.warning,
                                        onClick = { onSetAsDestination(item.latitude, item.longitude) }
                                    )
                                    TappableChip(
                                        text = "即刻伪装",
                                        color = MaterialTheme.colorScheme.error,
                                        onClick = { onDirectMock(item.latitude, item.longitude) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (textInput.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "未找到相关地点，请尝试更通用的地名或输入坐标",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "输入地名或直接粘贴经纬度即可全网智能联想定位",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadRouteDialog(
    mapViewModel: MapViewModel,
    simulationViewModel: SimulationViewModel,
    realLocation: Pair<Double, Double>?,
    selectedTapPoint: Pair<Double, Double>?,
    pointMockLocation: WayPoint?,
    onDismissRequest: () -> Unit,
    onNavigateToOrigin: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val roadOrigin by mapViewModel.roadOrigin.collectAsState()
    val roadDestination by mapViewModel.roadDestination.collectAsState()
    val roadMode by mapViewModel.roadMode.collectAsState()
    val isCalculatingRoute by mapViewModel.isCalculatingRoute.collectAsState()
    val calculatedRoadResult by mapViewModel.calculatedRoadResult.collectAsState()

    var speedSliderValue by remember(roadMode) {
        mutableFloatStateOf(
            when (roadMode) {
                RoadMode.DRIVING -> 45f
                RoadMode.CYCLING -> 18f
                RoadMode.WALKING -> 5f
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canPlan = roadOrigin != null && roadDestination != null

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetCorner,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(
                title = "真实道路路线规划",
                subtitle = "基于真实道路网拓扑，沿车道与街道折线生成模拟轨迹",
                onClose = onDismissRequest
            )

            // 1. Origin Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(10.dp).clip(CircleShape).background(AppSemanticColors.current.success)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("起点 (Origin)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        // 组合期取快照：roadOrigin 是 collectAsState() 委托，
                        // 「清除」按钮会在点击时把它置为 null，
                        // 而本行的 bouncyClickable lambda 稍后才重新读取 ——
                        // 用 roadOrigin!! 会在这种情况下抛 KotlinNullPointerException。
                        val origin = roadOrigin
                        if (origin != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                Box(
                                    modifier = Modifier
                                        .minTouchTarget()
                                        .clickable {
                                            onNavigateToOrigin(origin.latitude, origin.longitude)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("定位", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                }
                                Box(
                                    modifier = Modifier
                                        .minTouchTarget()
                                        .clickable { mapViewModel.setRoadOrigin(null) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("清除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = roadOrigin?.let { "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" } ?: "尚未设置起点",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (roadOrigin != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (roadOrigin != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (selectedTapPoint != null) {
                            TappableChip(
                                text = "使用当前选点",
                                color = AppSemanticColors.current.success,
                                onClick = { mapViewModel.setRoadOrigin(WayPoint(selectedTapPoint.first, selectedTapPoint.second)) }
                            )
                        }
                        if (pointMockLocation != null) {
                            TappableChip(
                                text = "使用模拟点",
                                color = AppSemanticColors.current.warning,
                                onClick = { mapViewModel.setRoadOrigin(pointMockLocation) }
                            )
                        }
                        if (realLocation != null) {
                            TappableChip(
                                text = "使用物理位置",
                                color = MaterialTheme.colorScheme.primary,
                                onClick = { mapViewModel.setRoadOrigin(WayPoint(realLocation.first, realLocation.second)) }
                            )
                        }
                    }
                }
            }

            // 2. Destination Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(10.dp).clip(CircleShape).background(AppSemanticColors.current.warning)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("终点 (Destination)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (roadDestination != null) {
                            Box(
                                modifier = Modifier
                                    .minTouchTarget()
                                    .clickable { mapViewModel.setRoadDestination(null) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("清除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = roadDestination?.let { "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" } ?: "尚未设置终点",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (roadDestination != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (roadDestination != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (selectedTapPoint != null) {
                            TappableChip(
                                text = "使用当前选点",
                                color = AppSemanticColors.current.warning,
                                onClick = { mapViewModel.setRoadDestination(WayPoint(selectedTapPoint.first, selectedTapPoint.second)) }
                            )
                        }
                        if (roadOrigin != null && roadDestination != null) {
                            TappableChip(
                                text = "互换起终点",
                                color = MaterialTheme.colorScheme.outline,
                                onClick = {
                                    val temp = roadOrigin
                                    mapViewModel.setRoadOrigin(roadDestination)
                                    mapViewModel.setRoadDestination(temp)
                                }
                            )
                        }
                    }
                }
            }

            // 3. Transport Mode Selector (Segmented Control)
            Text("出行方式 (道路拓扑权重)：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RoadMode.values().forEach { mode ->
                        val isSelected = mode == roadMode
                        val isDark = LocalIosColors.current.isDark
                        val activePillColor = if (isDark) Color(0xFF636366) else Color.White
                        val activeTextColor = if (isDark) Color.White else Color.Black
                        // 每段自带 48dp 热区：分段控件是主交互控件，
                        // 34dp 的紧凑高度在拇指下命中率很差。
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minTouchTarget()
                                .clip(RoundedCornerShape(13.dp))
                                .clickable { mapViewModel.setRoadMode(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(13.dp),
                                color = if (isSelected) activePillColor else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "${mode.emoji} ${mode.label}",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) activeTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Speed Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("模拟移动速度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${speedSliderValue.toInt()} km/h", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = speedSliderValue,
                onValueChange = { speedSliderValue = it },
                valueRange = 1f..120f,
                steps = 119,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            // 5. Calculate Button
            IosPrimaryButton(
                text = "沿真实道路智能规划路线",
                onClick = {
                    if (canPlan) {
                        mapViewModel.calculateRoadRoute { res ->
                            Toast.makeText(context, "规划成功：全程 ${"%.2f".format(res.distanceKm)} km，共 ${res.waypoints.size} 个道路折点", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "请先设置起点与终点！", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Route,
                height = 50.dp,
                enabled = canPlan && !isCalculatingRoute,
                loading = isCalculatingRoute,
                loadingText = "正在计算沿路折点..."
            )

            // 6. Calculated Result Card & Start Simulation Action
            if (calculatedRoadResult != null) {
                val result = calculatedRoadResult!!
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppSemanticColors.current.success.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, AppSemanticColors.current.success.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppSemanticColors.current.success, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("路线已绘制至底图", fontWeight = FontWeight.Bold, color = AppSemanticColors.current.success, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "距离: ${"%.2f".format(result.distanceKm)} km · 预计耗时: ${"%.0f".format(result.durationMinutes)} 分钟 · 折点数: ${result.waypoints.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        IosPrimaryButton(
                            text = "立即开始沿路模拟",
                            onClick = {
                                val route = Route(
                                    name = "沿路规划 (${result.mode.label} ${"%.1f".format(result.distanceKm)}km)",
                                    waypoints = result.waypoints
                                )
                                simulationViewModel.startSimulation(context, route, speedSliderValue)
                                onDismissRequest()
                                Toast.makeText(context, "已开启沿真实道路模拟运动！", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = AppSemanticColors.current.success,
                            icon = Icons.AutoMirrored.Filled.DirectionsRun,
                            height = 50.dp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
