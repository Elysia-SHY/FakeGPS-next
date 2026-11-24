package com.mockrun.app.feature.map

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.AppSemanticColors
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.AtlasSurface
import com.mockrun.app.core.designsystem.GlassDialogWindowEffect
import com.mockrun.app.core.designsystem.LocalLiquidGlassEnabled
import com.mockrun.app.core.location.RoadMode
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel
import com.mockrun.app.util.GeoTransform

// Long-form dialogs use M3 modal bottom sheets and theme-owned surfaces.
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TappableChip(
    text: String,
    color: Color,
    onClick: () -> Unit,
    outlined: Boolean = false
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        },
        shape = MaterialTheme.shapes.small,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (outlined) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                color.copy(alpha = 0.12f)
            },
            labelColor = color
        ),
        border = if (outlined) {
            BorderStroke(1.dp, color.copy(alpha = 0.4f))
        } else {
            null
        }
    )
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
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val searchQuery by mapViewModel.searchQuery.collectAsState()
    val searchResults by mapViewModel.searchResults.collectAsState()
    val isSearching by mapViewModel.isSearching.collectAsState()

    var textInput by remember { mutableStateOf(searchQuery) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        shape = AppShapes.sheet,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f)
    ) {
        AtlasSurface(
            modifier = Modifier.fillMaxWidth(),
            isLiquidGlass = isLiquidGlass,
            shape = AppShapes.sheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            elevation = if (isLiquidGlass) 22.dp else 8.dp
        ) {
            GlassDialogWindowEffect(isLiquidGlass)
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
            TextField(
                value = textInput,
                onValueChange = {
                    textInput = it
                    mapViewModel.performSearch(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "输入地名/POI或经纬度(如 39.9, 116.4)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = AppShapes.panel,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                trailingIcon = {
                    if (textInput.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                textInput = ""
                                mapViewModel.clearSearch()
                            }
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
            Text(
                "热门地点直达：",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Search Results or Loading
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "正在联网检索地点信息...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { item ->
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = item.address,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "输入地名或直接粘贴经纬度即可全网智能联想定位",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val isLiquidGlass = LocalLiquidGlassEnabled.current
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
        containerColor = Color.Transparent,
        shape = AppShapes.sheet,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f)
    ) {
        AtlasSurface(
            modifier = Modifier.fillMaxWidth(),
            isLiquidGlass = isLiquidGlass,
            shape = AppShapes.sheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            elevation = if (isLiquidGlass) 22.dp else 8.dp
        ) {
            GlassDialogWindowEffect(isLiquidGlass)
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
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(AppSemanticColors.current.success)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "起点 (Origin)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Snapshot the origin before the clear button can update the state.
                    val origin = roadOrigin
                    if (origin != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    onNavigateToOrigin(origin.latitude, origin.longitude)
                                }
                            ) {
                                Text(
                                    text = "定位",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            TextButton(
                                onClick = { mapViewModel.setRoadOrigin(null) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(
                                    text = "清除",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = roadOrigin?.let { "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" } ?: "尚未设置起点",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (roadOrigin != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(AppSemanticColors.current.warning)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "终点 (Destination)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (roadDestination != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { mapViewModel.setRoadDestination(null) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(
                                    text = "清除",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = roadDestination?.let { "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" } ?: "尚未设置终点",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (roadDestination != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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

            // 3. Transport Mode Selector
            Text(
                text = "出行方式 (道路拓扑权重)：",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoadMode.values().forEach { mode ->
                    FilterChip(
                        selected = mode == roadMode,
                        onClick = { mapViewModel.setRoadMode(mode) },
                        label = {
                            Text(
                                text = "${mode.emoji} ${mode.label}",
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }

            // 4. Speed Slider
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "模拟移动速度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${speedSliderValue.toInt()} km/h",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
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
            Button(
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
                enabled = canPlan && !isCalculatingRoute,
            ) {
                if (isCalculatingRoute) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在计算沿路折点...",
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "沿真实道路智能规划路线",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // 6. Calculated Result Card & Start Simulation Action
            if (calculatedRoadResult != null) {
                val result = calculatedRoadResult!!
                val semantic = AppSemanticColors.current
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = semantic.successContainer,
                        contentColor = semantic.onSuccessContainer
                    ),
                    border = BorderStroke(
                        1.dp,
                        semantic.success.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = semantic.onSuccessContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "路线已绘制至底图",
                                style = MaterialTheme.typography.titleSmall,
                                color = semantic.onSuccessContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "距离: ${"%.2f".format(result.distanceKm)} km · 预计耗时: ${"%.0f".format(result.durationMinutes)} 分钟 · 折点数: ${result.waypoints.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = semantic.onSuccessContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = semantic.success,
                                contentColor = semantic.onSuccess
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "立即开始沿路模拟",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            }
        }
    }
}
