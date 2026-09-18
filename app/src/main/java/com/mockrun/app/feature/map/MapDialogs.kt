package com.mockrun.app.feature.map

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.mockrun.app.core.designsystem.*
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = IosColors.SecondaryGroupedBackground,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍 搜索地点与经纬度", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = IosColors.Label)
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = IosGray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                            IconButton(onClick = {
                                textInput = ""
                                mapViewModel.clearSearch()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "清空", tint = IosGray)
                            }
                        }
                    }
                )

                // Quick Popular Shortcut Chips
                Text("热门地点直达：", style = MaterialTheme.typography.labelSmall, color = IosGray)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val quickPois = listOf(
                        "北京天安门" to (39.9087 to 116.3975),
                        "上海外滩" to (31.2397 to 121.4904),
                        "广州塔" to (23.1064 to 113.3245),
                        "深圳湾" to (22.5025 to 113.9510),
                        "成都春熙路" to (30.6558 to 104.0818),
                        "杭州西湖" to (30.2460 to 120.1490)
                    )
                    items(quickPois) { (name, coords) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IosBlue.copy(alpha = 0.08f),
                            border = BorderStroke(0.5.dp, IosBlue.copy(alpha = 0.2f)),
                            modifier = Modifier.bouncyClickable {
                                textInput = name
                                onSelectLocation(coords.first, coords.second, name)
                            }
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = IosBlue,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = IosSeparator.copy(alpha = 0.4f))

                // Search Results or Loading
                if (isSearching) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = IosBlue, strokeWidth = 2.5.dp)
                            Spacer(Modifier.height(8.dp))
                            Text("正在联网检索地点信息...", style = MaterialTheme.typography.bodySmall, color = IosGray)
                        }
                    }
                } else if (searchResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { item ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (LocalIosColors.current.isDark) Color(0xFF2C2C2E) else Color.White,
                                border = BorderStroke(0.5.dp, IosHairlineBorder),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = IosBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = IosColors.Label)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = item.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IosGray,
                                        maxLines = 2
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = IosBlue,
                                            modifier = Modifier.bouncyClickable {
                                                onSelectLocation(item.latitude, item.longitude, item.name)
                                            }
                                        ) {
                                            Text(
                                                "定位到此",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = IosGreen.copy(alpha = 0.15f),
                                            modifier = Modifier.bouncyClickable {
                                                onSetAsOrigin(item.latitude, item.longitude)
                                            }
                                        ) {
                                            Text(
                                                "设为起点",
                                                color = IosGreen,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = IosOrange.copy(alpha = 0.15f),
                                            modifier = Modifier.bouncyClickable {
                                                onSetAsDestination(item.latitude, item.longitude)
                                            }
                                        ) {
                                            Text(
                                                "设为终点",
                                                color = IosOrange,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = IosRed.copy(alpha = 0.15f),
                                            modifier = Modifier.bouncyClickable {
                                                onDirectMock(item.latitude, item.longitude)
                                            }
                                        ) {
                                            Text(
                                                "即刻伪装",
                                                color = IosRed,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (textInput.isNotBlank()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("未找到相关地点，请尝试更通用的地名或输入坐标", style = MaterialTheme.typography.bodySmall, color = IosGray)
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("输入地名或直接粘贴经纬度即可全网智能联想定位", style = MaterialTheme.typography.bodySmall, color = IosGray)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

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

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = IosColors.SecondaryGroupedBackground,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛣️ 真实道路路线规划", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = IosColors.Label)
                IconButton(onClick = onDismissRequest, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = IosGray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "基于真实道路网拓扑算法，精准沿车道与街道折线生成模拟轨迹",
                    style = MaterialTheme.typography.labelSmall,
                    color = IosGray
                )

                // 1. Origin Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (LocalIosColors.current.isDark) Color(0xFF2C2C2E) else Color.White,
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
                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(IosGreen)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("起点 (Origin)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = IosColors.Label)
                            }
                            if (roadOrigin != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "定位",
                                        color = IosBlue,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.bouncyClickable {
                                            onNavigateToOrigin(roadOrigin!!.latitude, roadOrigin!!.longitude)
                                        }
                                    )
                                    Text(
                                        text = "清除",
                                        color = IosRed,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.bouncyClickable { mapViewModel.setRoadOrigin(null) }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = roadOrigin?.let { "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" } ?: "尚未设置起点",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (roadOrigin != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (roadOrigin != null) IosColors.Label else IosGray
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (selectedTapPoint != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosGreen.copy(alpha = 0.12f),
                                    modifier = Modifier.bouncyClickable {
                                        mapViewModel.setRoadOrigin(WayPoint(selectedTapPoint.first, selectedTapPoint.second))
                                    }
                                ) {
                                    Text("使用当前选点", color = IosGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (pointMockLocation != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosOrange.copy(alpha = 0.12f),
                                    modifier = Modifier.bouncyClickable {
                                        mapViewModel.setRoadOrigin(pointMockLocation)
                                    }
                                ) {
                                    Text("使用模拟点", color = IosOrange, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (realLocation != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosBlue.copy(alpha = 0.12f),
                                    modifier = Modifier.bouncyClickable {
                                        mapViewModel.setRoadOrigin(WayPoint(realLocation.first, realLocation.second))
                                    }
                                ) {
                                    Text("使用物理位置", color = IosBlue, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // 2. Destination Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (LocalIosColors.current.isDark) Color(0xFF2C2C2E) else Color.White,
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
                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(IosOrange)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("终点 (Destination)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = IosColors.Label)
                            }
                            if (roadDestination != null) {
                                Text(
                                    text = "清除",
                                    color = IosRed,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.bouncyClickable { mapViewModel.setRoadDestination(null) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = roadDestination?.let { "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" } ?: "尚未设置终点",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (roadDestination != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (roadDestination != null) IosColors.Label else IosGray
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (selectedTapPoint != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosOrange.copy(alpha = 0.12f),
                                    modifier = Modifier.bouncyClickable {
                                        mapViewModel.setRoadDestination(WayPoint(selectedTapPoint.first, selectedTapPoint.second))
                                    }
                                ) {
                                    Text("使用当前选点", color = IosOrange, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (roadOrigin != null && roadDestination != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosGray.copy(alpha = 0.12f),
                                    modifier = Modifier.bouncyClickable {
                                        val temp = roadOrigin
                                        mapViewModel.setRoadOrigin(roadDestination)
                                        mapViewModel.setRoadDestination(temp)
                                    }
                                ) {
                                    Text("互换起终点", color = IosGray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // 3. Transport Mode Selector (Segmented Control)
                Text("出行方式 (道路拓扑权重)：", style = MaterialTheme.typography.labelSmall, color = IosGray)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IosColors.TertiarySystemFill)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RoadMode.values().forEach { mode ->
                        val isSelected = mode == roadMode
                        val isDark = LocalIosColors.current.isDark
                        val activePillColor = if (isDark) Color(0xFF636366) else Color.White
                        val activeTextColor = if (isDark) Color.White else Color.Black
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .bouncyClickable {
                                    mapViewModel.setRoadMode(mode)
                                },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) activePillColor else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "${mode.emoji} ${mode.label}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) activeTextColor else IosGray,
                                    style = MaterialTheme.typography.labelMedium
                                )
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
                    Text("模拟移动速度", style = MaterialTheme.typography.labelSmall, color = IosGray)
                    Text("${speedSliderValue.toInt()} km/h", fontWeight = FontWeight.Bold, color = IosBlue, style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = speedSliderValue,
                    onValueChange = { speedSliderValue = it },
                    valueRange = 1f..120f,
                    steps = 119,
                    colors = SliderDefaults.colors(
                        thumbColor = IosBlue,
                        activeTrackColor = IosBlue,
                        inactiveTrackColor = IosGlassTrack
                    )
                )

                // 5. Calculate Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .bouncyClickable {
                            if (roadOrigin != null && roadDestination != null) {
                                mapViewModel.calculateRoadRoute { res ->
                                    Toast.makeText(context, "规划成功：全程 ${"%.2f".format(res.distanceKm)} km，共 ${res.waypoints.size} 个道路折点", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "请先设置起点与终点！", Toast.LENGTH_SHORT).show()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = if (roadOrigin != null && roadDestination != null && !isCalculatingRoute) IosBlue else IosGray.copy(alpha = 0.3f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                        if (isCalculatingRoute) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在计算沿路折点...", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Text("⚡ 沿真实道路智能规划路线", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // 6. Calculated Result Card & Start Simulation Action
                if (calculatedRoadResult != null) {
                    val result = calculatedRoadResult!!
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = IosGreen.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, IosGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IosGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("路线已绘制至底图", fontWeight = FontWeight.Bold, color = IosGreen, style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "距离: ${"%.2f".format(result.distanceKm)} km · 预计耗时: ${"%.0f".format(result.durationMinutes)} 分钟 · 折点数: ${result.waypoints.size}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .bouncyClickable {
                                        val route = Route(
                                            name = "沿路规划 (${result.mode.label} ${"%.1f".format(result.distanceKm)}km)",
                                            waypoints = result.waypoints
                                        )
                                        simulationViewModel.startSimulation(context, route, speedSliderValue)
                                        onDismissRequest()
                                        Toast.makeText(context, "已开启沿真实道路模拟运动！", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = IosGreen
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                    Text("🏃 立即开始沿路模拟", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}