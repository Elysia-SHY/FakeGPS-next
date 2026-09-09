package com.mockrun.app.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.BuildConfig
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.SimulationStatus
import com.mockrun.app.location.CadenceMode
import com.mockrun.app.location.RootSuBridge
import com.mockrun.app.location.SensorMockEngine
import com.mockrun.app.ui.theme.*
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel
import java.util.*

@Composable
fun RouteSimulationScreen(
    simulationViewModel: SimulationViewModel,
    mapViewModel: MapViewModel,
    onNavigateToMap: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    rootBridge: RootSuBridge = remember { RootSuBridge() },
    sensorEngine: SensorMockEngine = simulationViewModel.sensorEngine
) {
    val context = LocalContext.current
    val simState by simulationViewModel.state.collectAsState()
    val selectedRoute by mapViewModel.selectedRoute.collectAsState()
    val savedRoutes by mapViewModel.savedRoutes.collectAsState()
    val sensorState by simulationViewModel.sensorState.collectAsState()

    var selectedSpeed by remember { mutableFloatStateOf(8f) }
    var customSpeedInput by remember { mutableStateOf("8") }
    var customCadenceInput by remember { mutableStateOf("165") }
    var showRoutePickerDialog by remember { mutableStateOf(false) }
    var isRootActive by remember { mutableStateOf<Boolean?>(null) }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    mapViewModel.importGpx(inputStream, "导入GPX路线")
                    Toast.makeText(context, "GPX 路线导入成功并已载入", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        isRootActive = rootBridge.isRootAvailable()
    }

    fun applySpeed(speed: Float) {
        selectedSpeed = speed
        customSpeedInput = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else "%.1f".format(speed)
        if (simState.status is SimulationStatus.Running) {
            simulationViewModel.setSpeed(context, speed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosColors.SystemGroupedBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =====================================================================
        // 1. Apple Large Title Navigation Header
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "拟真配速与多点巡航漫游",
                    style = IosTypography.Caption1,
                    color = IosColors.SecondaryLabel,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "路线模拟",
                    style = IosTypography.LargeTitle,
                    color = IosColors.Label
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(simState.status)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = IosColors.SystemGreen.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        color = IosColors.SystemGreen,
                        style = IosTypography.Caption1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // =====================================================================
        // 2. Current Route (iOS Inset Group Card)
        // =====================================================================
        IosSectionHeader("当前规划路线")

        IosInsetGroupCard {
            if (selectedRoute != null && (selectedRoute?.waypoints?.size ?: 0) >= 2) {
                val r = selectedRoute!!
                val totalDistanceKm = r.totalDistanceKm
                val estTimeMinutes = (totalDistanceKm / selectedSpeed * 60).toInt()

                IosListRow(
                    title = r.name,
                    subtitle = "共 ${r.waypoints.size} 个折点 · 总里程 ${"%.2f".format(totalDistanceKm)} km · 预计耗时约 $estTimeMinutes 分钟",
                    icon = Icons.Default.Place,
                    iconBackground = IosColors.SystemBlue
                )

                IosHairlineDivider(startIndent = 58.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .bouncyClickable { onNavigateToMap() },
                        shape = RoundedCornerShape(10.dp),
                        color = IosColors.SystemBlue.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = IosColors.SystemBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("地图查看", color = IosColors.SystemBlue, style = IosTypography.Subheadline, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .bouncyClickable { showRoutePickerDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = IosColors.SystemBlue.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = IosColors.SystemBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("切换路线", color = IosColors.SystemBlue, style = IosTypography.Subheadline, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "尚未载入模拟路线",
                        style = IosTypography.Headline,
                        color = IosColors.Label
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "请从路线库选择历史路线，或直接导入 GPX 轨迹文件",
                        style = IosTypography.Footnote,
                        color = IosColors.SecondaryLabel
                    )
                }

                IosHairlineDivider(startIndent = 16.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .bouncyClickable { showRoutePickerDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = IosColors.SystemBlue
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("选择已有路线 (${savedRoutes.size})", color = Color.White, style = IosTypography.Subheadline, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(0.9f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .bouncyClickable { gpxPickerLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(10.dp),
                        color = IosColors.SystemBlue.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = IosColors.SystemBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导入 GPX", color = IosColors.SystemBlue, style = IosTypography.Subheadline, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 3. Cruise Controller & Metrics (iOS Inset Group Card)
        // =====================================================================
        IosSectionHeader("巡航控制与看板")

        IosInsetGroupCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                // Metric stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricItem("已用时间", simState.formattedElapsedTime)
                    MetricItem("已跑里程", "${"%.2f".format(simState.distanceTraveledMeters / 1000.0)} km")
                    MetricItem("当前配速", "${"%.1f".format(simState.speedKmh)} km/h")
                }

                Spacer(Modifier.height(14.dp))

                // Progress Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("路线巡航进度", style = IosTypography.Footnote, color = IosColors.SecondaryLabel)
                    Text(
                        text = "${(simState.progressPercent * 100).toInt()}%",
                        style = IosTypography.Subheadline,
                        fontWeight = FontWeight.Bold,
                        color = IosColors.SystemBlue
                    )
                }
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = simState.progressPercent,
                    onValueChange = { percent ->
                        simulationViewModel.seekTo(context, percent)
                    },
                    valueRange = 0f..1f,
                    enabled = simState.status is SimulationStatus.Running || simState.status is SimulationStatus.Paused,
                    colors = SliderDefaults.colors(thumbColor = IosColors.SystemBlue, activeTrackColor = IosColors.SystemBlue)
                )

                Spacer(Modifier.height(12.dp))

                // Primary Simulation Master Buttons
                val hasValidRoute = selectedRoute != null && (selectedRoute?.waypoints?.size ?: 0) >= 2
                when (simState.status) {
                    is SimulationStatus.Idle, is SimulationStatus.Completed -> {
                        IosPrimaryButton(
                            text = if (hasValidRoute) "开始模拟路线" else "请先选定路线",
                            containerColor = if (hasValidRoute) IosColors.SystemGreen else IosColors.SystemGray,
                            icon = Icons.Default.PlayArrow,
                            onClick = {
                                if (hasValidRoute) {
                                    simulationViewModel.startSimulation(context, selectedRoute!!, selectedSpeed)
                                    Toast.makeText(context, "路线模拟已开启", Toast.LENGTH_SHORT).show()
                                } else {
                                    showRoutePickerDialog = true
                                }
                            }
                        )
                    }
                    is SimulationStatus.Running -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IosPrimaryButton(
                                text = "暂停模拟",
                                containerColor = IosColors.SystemOrange,
                                icon = Icons.Default.PlayArrow,
                                modifier = Modifier.weight(1f),
                                onClick = { simulationViewModel.pauseSimulation(context) }
                            )
                            IosPrimaryButton(
                                text = "停止模拟",
                                containerColor = IosColors.SystemRed,
                                icon = Icons.Default.Close,
                                modifier = Modifier.weight(1f),
                                onClick = { simulationViewModel.stopSimulation(context) }
                            )
                        }
                    }
                    is SimulationStatus.Paused -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IosPrimaryButton(
                                text = "继续模拟",
                                containerColor = IosColors.SystemGreen,
                                icon = Icons.Default.PlayArrow,
                                modifier = Modifier.weight(1f),
                                onClick = { simulationViewModel.resumeSimulation(context) }
                            )
                            IosPrimaryButton(
                                text = "停止模拟",
                                containerColor = IosColors.SystemRed,
                                icon = Icons.Default.Close,
                                modifier = Modifier.weight(1f),
                                onClick = { simulationViewModel.stopSimulation(context) }
                            )
                        }
                    }
                    is SimulationStatus.Error -> {
                        IosPrimaryButton(
                            text = "重置状态",
                            containerColor = IosColors.SystemRed,
                            onClick = { simulationViewModel.stopSimulation(context) }
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 4. Speed Controller (Modern Borderless Chips + Slider)
        // =====================================================================
        IosSectionHeader("巡航配速控制")

        IosInsetGroupCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("当前配速", style = IosTypography.Headline, color = IosColors.Label)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = IosColors.SystemBlue.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${"%.1f".format(selectedSpeed)} km/h",
                            style = IosTypography.Headline,
                            fontWeight = FontWeight.Bold,
                            color = IosColors.SystemBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Modern Borderless iOS Speed Preset Chips (彻底去除外层容器包边与死板线框)
                val presetSpeeds = listOf(
                    Triple(5f, "🚶", "5"),
                    Triple(8f, "🏃", "8"),
                    Triple(20f, "🚴", "20"),
                    Triple(60f, "🚗", "60"),
                    Triple(100f, "🏎️", "100")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetSpeeds.forEach { (speedVal, emoji, numStr) ->
                        val isSelected = kotlin.math.abs(selectedSpeed - speedVal) < 0.5f
                        val chipBg by animateColorAsState(
                            targetValue = if (isSelected) IosColors.SystemBlue else IosColors.TertiarySystemFill.copy(alpha = 0.55f),
                            animationSpec = tween(180),
                            label = "speedChipBg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else IosColors.Label,
                            animationSpec = tween(180),
                            label = "speedChipTextColor"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(chipBg)
                                .bouncyClickable { applySpeed(speedVal) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(text = emoji, fontSize = 13.sp)
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = numStr,
                                    style = IosTypography.Caption1,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Slider(
                    value = selectedSpeed.coerceIn(1f, 120f),
                    onValueChange = { applySpeed(it) },
                    valueRange = 1f..120f,
                    colors = SliderDefaults.colors(
                        thumbColor = IosColors.SystemBlue,
                        activeTrackColor = IosColors.SystemBlue,
                        inactiveTrackColor = IosColors.TertiarySystemFill.copy(alpha = 0.6f)
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 km/h", style = IosTypography.Caption2, color = IosColors.SecondaryLabel)
                    Text("60 km/h", style = IosTypography.Caption2, color = IosColors.SecondaryLabel)
                    Text("120 km/h", style = IosTypography.Caption2, color = IosColors.SecondaryLabel)
                }

                Spacer(Modifier.height(12.dp))

                // Custom Numerical Speed Input in clean seamless pill style (无多余外包边)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = IosColors.TertiarySystemFill.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = IosColors.SystemBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextField(
                            value = customSpeedInput,
                            onValueChange = {
                                customSpeedInput = it
                                it.toFloatOrNull()?.let { spd ->
                                    if (spd > 0) applySpeed(spd)
                                }
                            },
                            placeholder = {
                                Text(
                                    "输入任意精确配速 (如 12.5)",
                                    style = IosTypography.Footnote,
                                    color = IosColors.SecondaryLabel
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IosColors.SystemBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "km/h",
                                style = IosTypography.Caption1,
                                color = IosColors.SystemBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 5. Cadence & Step Simulation (iOS Inset Group Card, Root Required)
        // =====================================================================
        if (isRootActive == true) {
            IosSectionHeader("运动步频与计步仿真 (Root 专享)")

            IosInsetGroupCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                // Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用步频与计步仿真", style = IosTypography.Headline, color = IosColors.Label)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "随路线模拟速度自动计算步频，注入传感器颠簸起伏与累计步数",
                            style = IosTypography.Caption1,
                            color = IosColors.SecondaryLabel
                        )
                    }
                    Switch(
                        checked = sensorState.isEnabled,
                        onCheckedChange = { simulationViewModel.setCadenceEnabled(it, selectedSpeed) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = IosColors.SystemGreen
                        )
                    )
                }

                if (sensorState.isEnabled) {
                    val currentDisplaySpeed = if (simState.status is SimulationStatus.Running) simState.speedKmh else selectedSpeed
                    val effectiveCadence = remember(currentDisplaySpeed, sensorState.cadenceMode, sensorState.customCadence, sensorState.isEnabled, simState.status, sensorState.cadenceStepsPerMin) {
                        if (!sensorState.isEnabled) 0
                        else if (simState.status is SimulationStatus.Running && sensorState.cadenceStepsPerMin > 0) {
                            sensorState.cadenceStepsPerMin
                        } else {
                            sensorEngine.calculateCadence(currentDisplaySpeed, sensorState.cadenceMode, sensorState.customCadence)
                        }
                    }
                    val effectiveStride = remember(currentDisplaySpeed, effectiveCadence) {
                        sensorEngine.calculateStride(currentDisplaySpeed, effectiveCadence)
                    }

                    Spacer(Modifier.height(14.dp))
                    IosHairlineDivider()
                    Spacer(Modifier.height(14.dp))

                    // 4-item Metrics Grid (Real-time dynamic reaction to speed and cadence mode)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MetricItem(
                            "实时步频",
                            if (!sensorState.isEnabled) "-- SPM"
                            else if (simState.status is SimulationStatus.Running) "${sensorState.cadenceStepsPerMin} SPM"
                            else "$effectiveCadence SPM"
                        )
                        MetricItem("累计步数", "${sensorState.stepCount} 步")
                        MetricItem(
                            "预估步幅",
                            if (!sensorState.isEnabled) "-- m"
                            else "${"%.2f".format(effectiveStride)} m"
                        )
                        MetricItem(
                            "垂直加速度",
                            if (simState.status is SimulationStatus.Running) "${"%.1f".format(sensorState.accelZ)} m/s²"
                            else "9.8 m/s²"
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    IosHairlineDivider()
                    Spacer(Modifier.height(14.dp))

                    Text("步频拟真模式", style = IosTypography.Subheadline, fontWeight = FontWeight.SemiBold, color = IosColors.Label)
                    Spacer(Modifier.height(8.dp))

                    val modes = listOf(
                        CadenceMode.AUTO to "自适应",
                        CadenceMode.WALK to "健步 110",
                        CadenceMode.JOG to "慢跑 160",
                        CadenceMode.RUN to "跑马 180",
                        CadenceMode.CUSTOM to "自定义"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modes.forEach { (mode, label) ->
                            val isSelected = sensorState.cadenceMode == mode
                            val chipBg by animateColorAsState(
                                targetValue = if (isSelected) IosColors.SystemGreen else IosColors.TertiarySystemFill.copy(alpha = 0.55f),
                                label = "cadenceChipBg"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else IosColors.Label,
                                label = "cadenceChipTextColor"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(chipBg)
                                    .bouncyClickable {
                                        val targetCadence = when (mode) {
                                            CadenceMode.WALK -> 110
                                            CadenceMode.JOG -> 160
                                            CadenceMode.RUN -> 180
                                            CadenceMode.CUSTOM -> customCadenceInput.toIntOrNull() ?: 165
                                            CadenceMode.AUTO -> 165
                                        }
                                        simulationViewModel.setCadenceMode(mode, targetCadence, selectedSpeed)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = IosTypography.Caption1,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }
                    }

                    if (sensorState.cadenceMode == CadenceMode.CUSTOM) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = customCadenceInput,
                                onValueChange = {
                                    customCadenceInput = it
                                    it.toIntOrNull()?.let { spm ->
                                        if (spm in 40..300) {
                                            simulationViewModel.setCadenceMode(CadenceMode.CUSTOM, spm, selectedSpeed)
                                        }
                                    }
                                },
                                placeholder = { Text("输入步频 (如 172)", style = IosTypography.Footnote) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(IosColors.TertiarySystemFill.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("SPM (步/分)", style = IosTypography.Caption1, color = IosColors.SecondaryLabel)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { simulationViewModel.resetSteps() }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = IosColors.SecondaryLabel)
                            Spacer(Modifier.width(4.dp))
                            Text("清零步数", color = IosColors.SecondaryLabel, style = IosTypography.Footnote)
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 6. GPS Realism & Anti-Detection (iOS Inset Group Card, Root Required)
        // =====================================================================
        if (isRootActive == true) {
            IosSectionHeader("GPS 底层拟真与抗检测 (Root 专享)")

            IosInsetGroupCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                // Realism Engine Status Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(IosColors.SystemGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = IosColors.SystemGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("全链路 GPS 真实感注入中", style = IosTypography.Headline, color = IosColors.Label)
                        Spacer(Modifier.height(2.dp))
                        Text("多星定位协议 · 高斯微漂移 · 自然配速呼吸抖动", style = IosTypography.Caption1, color = IosColors.SecondaryLabel)
                    }
                }

                Spacer(Modifier.height(14.dp))
                IosHairlineDivider()
                Spacer(Modifier.height(14.dp))

                // GPS Realism Indicator Grid (2 columns)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GpsFeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "多星系统搜星",
                        value = "21 颗",
                        subtitle = "GPS+北斗+Galileo",
                        icon = Icons.Default.Star,
                        tint = IosColors.SystemOrange
                    )
                    GpsFeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "拟真水平精度",
                        value = "±1.8 m",
                        subtitle = "动态 1.5~2.5m 真实浮动",
                        icon = Icons.Default.LocationOn,
                        tint = IosColors.SystemBlue
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GpsFeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "高斯微漂移",
                        value = "±0.3 m",
                        subtitle = "Box-Muller 消除静态死点",
                        icon = Icons.Default.Refresh,
                        tint = IosColors.SystemIndigo
                    )
                    GpsFeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "配速弹性波动",
                        value = "±5%",
                        subtitle = "模拟真实跑步体力起伏",
                        icon = Icons.Default.PlayArrow,
                        tint = IosColors.SystemGreen
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GpsFeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "动态高程海拔",
                        value = "${"%.1f".format(simState.currentWayPoint?.altitude ?: 25.0)} m",
                        subtitle = "真实路网地形起伏仿真",
                        icon = Icons.Default.Place,
                        tint = IosColors.SystemTeal
                    )
                    GpsFeatureCard(
                        modifier = Modifier.weight(1f),
                        title = "航向角切线",
                        value = "${"%.0f".format(simState.bearing)}°",
                        subtitle = "路线转弯平滑平角过渡",
                        icon = Icons.Default.Send,
                        tint = IosColors.SystemPurple
                    )
                }
            }
        }
    }
    }

    // iOS Style Route Picker Dialog
    if (showRoutePickerDialog) {
        AlertDialog(
            onDismissRequest = { showRoutePickerDialog = false },
            title = {
                Text("选择要模拟的路线", style = IosTypography.Title3, fontWeight = FontWeight.Bold, color = IosColors.Label)
            },
            text = {
                if (savedRoutes.isEmpty()) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("路线库暂无存档", style = IosTypography.Body, color = IosColors.SecondaryLabel)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            showRoutePickerDialog = false
                            onNavigateToMap()
                        }) {
                            Text("前往地图绘制路线", color = IosColors.SystemBlue, style = IosTypography.Headline)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(savedRoutes) { route ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .bouncyClickable {
                                        mapViewModel.selectRoute(route)
                                        showRoutePickerDialog = false
                                        Toast.makeText(context, "已载入路线: ${route.name}", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedRoute?.id == route.id) IosColors.SystemBlue.copy(alpha = 0.12f) else IosColors.TertiarySystemFill,
                                border = BorderStroke(0.5.dp, if (selectedRoute?.id == route.id) IosColors.SystemBlue else IosColors.Separator.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(route.name, fontWeight = FontWeight.Bold, style = IosTypography.Headline, color = IosColors.Label)
                                        Text("${route.waypoints.size} 个折点 · ${"%.2f".format(route.totalDistanceKm)} km", style = IosTypography.Footnote, color = IosColors.SecondaryLabel)
                                    }
                                    if (selectedRoute?.id == route.id) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = IosColors.SystemBlue, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showRoutePickerDialog = false
                        onNavigateToLibrary()
                    }) {
                        Text("路线库管理", color = IosColors.SystemBlue, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { showRoutePickerDialog = false }) {
                        Text("取消", color = IosColors.SecondaryLabel)
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            containerColor = Color.White
        )
    }
}
}



@Composable
private fun StatusBadge(status: SimulationStatus) {
    val (text, color) = when (status) {
        is SimulationStatus.Idle -> "待机" to IosColors.SecondaryLabel
        is SimulationStatus.Running -> "模拟运行中" to IosColors.SystemGreen
        is SimulationStatus.Paused -> "已暂停" to IosColors.SystemOrange
        is SimulationStatus.Completed -> "模拟完成" to IosColors.SystemBlue
        is SimulationStatus.Error -> "异常" to IosColors.SystemRed
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = IosTypography.Caption1,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = IosTypography.Title2, fontWeight = FontWeight.Bold, color = IosColors.Label)
        Spacer(Modifier.height(2.dp))
        Text(label, style = IosTypography.Caption1, color = IosColors.SecondaryLabel)
    }
}

@Composable
private fun GpsFeatureCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = IosColors.TertiarySystemFill.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(title, style = IosTypography.Caption2, color = IosColors.SecondaryLabel)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = IosTypography.Subheadline, fontWeight = FontWeight.Bold, color = IosColors.Label)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = IosTypography.Caption2, color = IosColors.SecondaryLabel.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
