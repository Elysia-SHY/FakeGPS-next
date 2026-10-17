package com.mockrun.app.feature.map

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
//     1. 底板统一 IosColors.SecondaryGroupedBackground
//     2. 标题 IosTypography.Headline + Bold + IosColors.Label；副标题 IosTypography.Caption1
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
        color = IosColors.SystemGray.copy(alpha = 0.4f)
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
                style = IosTypography.Headline,
                color = IosColors.Label,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = IosTypography.Caption1,
                    color = IosColors.SecondaryLabel
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .minTouchTarget()
                .clip(CircleShape)
                .background(IosColors.SystemGray.copy(alpha = 0.15f))
                .bouncyClickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                tint = IosColors.SecondaryLabel,
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
            .bouncyClickable(onClick = onClick),
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
                style = IosTypography.Caption1,
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
        containerColor = IosColors.SecondaryGroupedBackground,
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
                                .bouncyClickable {
                                    textInput = ""
                                    mapViewModel.clearSearch()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "清空",
                                tint = IosColors.SecondaryLabel,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )

            // Quick Popular Shortcut Chips
            Text("热门地点直达：", style = IosTypography.Caption1, color = IosColors.SecondaryLabel)
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
                        color = IosInfo,
                        outlined = true,
                        onClick = {
                            textInput = name
                            onSelectLocation(coords.first, coords.second, name)
                        }
                    )
                }
            }

            HorizontalDivider(color = IosColors.Separator.copy(alpha = 0.4f))

            // Search Results or Loading
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = IosColors.SystemBlue, strokeWidth = 2.5.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("正在联网检索地点信息...", style = IosTypography.Caption1, color = IosColors.SecondaryLabel)
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
                            color = IosColors.TertiaryGroupedBackground,
                            border = BorderStroke(0.5.dp, IosHairlineBorder),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = IosInfo, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(item.name, fontWeight = FontWeight.Bold, style = IosTypography.Subheadline, color = IosColors.Label)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.address,
                                    style = IosTypography.Caption1,
                                    color = IosColors.SecondaryLabel,
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
                                        color = IosInfo,
                                        onClick = { onSelectLocation(item.latitude, item.longitude, item.name) }
                                    )
                                    TappableChip(
                                        text = "设为起点",
                                        color = IosSuccess,
                                        onClick = { onSetAsOrigin(item.latitude, item.longitude) }
                                    )
                                    TappableChip(
                                        text = "设为终点",
                                        color = IosWarning,
                                        onClick = { onSetAsDestination(item.latitude, item.longitude) }
                                    )
                                    TappableChip(
                                        text = "即刻伪装",
                                        color = IosDanger,
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
                        style = IosTypography.Caption1,
                        color = IosColors.SecondaryLabel
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "输入地名或直接粘贴经纬度即可全网智能联想定位",
                        style = IosTypography.Caption1,
                        color = IosColors.SecondaryLabel
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
        containerColor = IosColors.SecondaryGroupedBackground,
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
                color = IosColors.TertiaryGroupedBackground,
                border = BorderStroke(0.5.dp, IosHairlineBorder),
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
                                modifier = Modifier.size(10.dp).clip(CircleShape).background(IosSuccess)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("起点 (Origin)", fontWeight = FontWeight.Bold, style = IosTypography.Subheadline, color = IosColors.Label)
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
                                        .bouncyClickable {
                                            onNavigateToOrigin(origin.latitude, origin.longitude)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("定位", color = IosInfo, style = IosTypography.Caption1)
                                }
                                Box(
                                    modifier = Modifier
                                        .minTouchTarget()
                                        .bouncyClickable { mapViewModel.setRoadOrigin(null) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("清除", color = IosDanger, style = IosTypography.Caption1)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = roadOrigin?.let { "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" } ?: "尚未设置起点",
                        style = IosTypography.Body,
                        fontWeight = if (roadOrigin != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (roadOrigin != null) IosColors.Label else IosColors.SecondaryLabel
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (selectedTapPoint != null) {
                            TappableChip(
                                text = "使用当前选点",
                                color = IosSuccess,
                                onClick = { mapViewModel.setRoadOrigin(WayPoint(selectedTapPoint.first, selectedTapPoint.second)) }
                            )
                        }
                        if (pointMockLocation != null) {
                            TappableChip(
                                text = "使用模拟点",
                                color = IosWarning,
                                onClick = { mapViewModel.setRoadOrigin(pointMockLocation) }
                            )
                        }
                        if (realLocation != null) {
                            TappableChip(
                                text = "使用物理位置",
                                color = IosInfo,
                                onClick = { mapViewModel.setRoadOrigin(WayPoint(realLocation.first, realLocation.second)) }
                            )
                        }
                    }
                }
            }

            // 2. Destination Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IosColors.TertiaryGroupedBackground,
                border = BorderStroke(0.5.dp, IosHairlineBorder),
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
                                modifier = Modifier.size(10.dp).clip(CircleShape).background(IosWarning)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("终点 (Destination)", fontWeight = FontWeight.Bold, style = IosTypography.Subheadline, color = IosColors.Label)
                        }
                        if (roadDestination != null) {
                            Box(
                                modifier = Modifier
                                    .minTouchTarget()
                                    .bouncyClickable { mapViewModel.setRoadDestination(null) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("清除", color = IosDanger, style = IosTypography.Caption1)
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = roadDestination?.let { "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" } ?: "尚未设置终点",
                        style = IosTypography.Body,
                        fontWeight = if (roadDestination != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (roadDestination != null) IosColors.Label else IosColors.SecondaryLabel
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (selectedTapPoint != null) {
                            TappableChip(
                                text = "使用当前选点",
                                color = IosWarning,
                                onClick = { mapViewModel.setRoadDestination(WayPoint(selectedTapPoint.first, selectedTapPoint.second)) }
                            )
                        }
                        if (roadOrigin != null && roadDestination != null) {
                            TappableChip(
                                text = "互换起终点",
                                color = IosColors.SystemGray,
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
            Text("出行方式 (道路拓扑权重)：", style = IosTypography.Caption1, color = IosColors.SecondaryLabel)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = IosColors.TertiarySystemFill
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
                                .bouncyClickable { mapViewModel.setRoadMode(mode) },
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
                                        color = if (isSelected) activeTextColor else IosColors.SecondaryLabel,
                                        style = IosTypography.Caption1
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
                Text("模拟移动速度", style = IosTypography.Caption1, color = IosColors.SecondaryLabel)
                Text("${speedSliderValue.toInt()} km/h", fontWeight = FontWeight.Bold, color = IosInfo, style = IosTypography.Caption1)
            }
            Slider(
                value = speedSliderValue,
                onValueChange = { speedSliderValue = it },
                valueRange = 1f..120f,
                steps = 119,
                colors = SliderDefaults.colors(
                    thumbColor = IosColors.SystemBlue,
                    activeTrackColor = IosColors.SystemBlue,
                    inactiveTrackColor = IosGlassTrack
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
                    color = IosSuccess.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, IosSuccess.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IosSuccess, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("路线已绘制至底图", fontWeight = FontWeight.Bold, color = IosSuccess, style = IosTypography.Subheadline)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "距离: ${"%.2f".format(result.distanceKm)} km · 预计耗时: ${"%.0f".format(result.durationMinutes)} 分钟 · 折点数: ${result.waypoints.size}",
                            style = IosTypography.Caption1,
                            fontWeight = FontWeight.SemiBold,
                            color = IosColors.Label
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
                            containerColor = IosSuccess,
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
