package com.mockrun.app.feature.route

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mockrun.app.BuildConfig
import com.mockrun.app.core.designsystem.*
import com.mockrun.app.core.location.CadenceMode
import com.mockrun.app.core.location.RootSuBridge
import com.mockrun.app.core.location.SensorMockEngine
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.RouteMode
import com.mockrun.app.domain.model.SimulationStatus
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel
import com.mockrun.app.util.Diag
import com.mockrun.app.util.InjectionModePrefs

@OptIn(ExperimentalLayoutApi::class)
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
    var customCadenceInput by remember {
        mutableStateOf(
            sensorEngine.sensorState.value.customCadence
                .takeIf { it > 0 }?.toString() ?: "165"
        )
    }
    var showRoutePickerDialog by remember { mutableStateOf(false) }
    var isRootActive by remember { mutableStateOf<Boolean?>(null) }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 解析与流的打开/关闭都在 ViewModel 的 IO 任务内完成。
            runCatching {
                mapViewModel.importGpx(context, it, "导入GPX路线") { count ->
                    // 只有真的解析出 >= 2 个点才算成功。
                    if (count >= 2) {
                        Toast.makeText(context, "GPX 路线导入成功并已载入（$count 个点）", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            "该 GPX 只解析出 $count 个点，至少需要 2 个点才能构成路线",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure { e ->
                Diag.w("RouteSimulation", "GPX 导入失败", e)
                Toast.makeText(context, "导入失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        val rootOk = rootBridge.isRootAvailable()
        val isRoot = InjectionModePrefs.isRootMode(context, rootOk) && rootOk
        isRootActive = isRoot
        if (!isRoot && sensorState.isEnabled) {
            simulationViewModel.setCadenceEnabled(false, selectedSpeed)
        }
    }

    /**
     * 应用配速。
     *
     * @param syncInputField 是否把格式化后的值写回输入框。
     *   必须为 false 用于「用户在输入框里打字」这条路径 ——
     *   否则会在输入过程中覆盖用户的文本：
     *   输入 "12.5" 时，打到 "12." 这一步 `toFloatOrNull()` 失败、
     *   但前一次成功的 "12" 会被格式化成 "12" 写回，光标与文本被打乱，
     *   最终可能变成 "125"。预设档位按钮与滑块才需要回写。
     */
    fun applySpeed(speed: Float, syncInputField: Boolean = true) {
        selectedSpeed = speed
        if (syncInputField) {
            customSpeedInput = if (speed == speed.toInt().toFloat()) {
                speed.toInt().toString()
            } else {
                "%.1f".format(speed)
            }
        }
        if (simState.status is SimulationStatus.Running) {
            simulationViewModel.setSpeed(context, speed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =====================================================================
        // 1. Page Head
        // =====================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppSpacing.pageH,
                    end = AppSpacing.pageH,
                    top = 12.dp,
                    bottom = 20.dp
                )
        ) {
            Text(
                text = "拟真配速与多点巡航漫游",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "路线模拟",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(simState.status)
                VersionBadge(text = BuildConfig.VERSION_NAME)
            }
        }

        // =====================================================================
        // 2. Current Route
        // =====================================================================
        SectionHeader("当前规划路线")

        SectionCard {
            if (selectedRoute != null && (selectedRoute?.waypoints?.size ?: 0) >= 2) {
                val r = selectedRoute!!
                val totalDistanceKm = r.totalDistanceKm
                val estTimeMinutes = (totalDistanceKm / selectedSpeed * 60).toInt()

                ListItem(
                    leadingContent = {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    },
                    headlineContent = {
                        Text(
                            text = r.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "共 ${r.waypoints.size} 个折点 · 总里程 ${"%.2f".format(totalDistanceKm)} km · 预计耗时约 $estTimeMinutes 分钟",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onNavigateToMap,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("地图查看", style = MaterialTheme.typography.labelLarge)
                    }

                    FilledTonalButton(
                        onClick = { showRoutePickerDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("切换路线", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "尚未载入模拟路线",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "请从路线库选择历史路线，或直接导入 GPX 轨迹文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showRoutePickerDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "选择已有路线 (${savedRoutes.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    FilledTonalButton(
                        onClick = { gpxPickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("导入 GPX", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // =====================================================================
        // 3. Cruise Controller & Metrics
        // =====================================================================
        SectionHeader("巡航控制与看板")

        SectionCard {
            Column(modifier = Modifier.padding(16.dp)) {
                MetricGrid(
                    listOf(
                        "已用时间" to simState.formattedElapsedTime,
                        "已跑里程" to "${"%.2f".format(simState.distanceTraveledMeters / 1000.0)} km",
                        "当前配速" to "${"%.1f".format(simState.speedKmh)} km/h",
                        "轮次" to if ((simState.route?.mode ?: RouteMode.ONE_WAY) == RouteMode.ONE_WAY) {
                            "--"
                        } else {
                            "${simState.lapIndex + 1}"
                        }
                    )
                )

                Spacer(Modifier.height(14.dp))

                // Progress Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if ((simState.route?.mode ?: RouteMode.ONE_WAY) == RouteMode.ONE_WAY) "路线巡航进度" else "本轮进度",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(simState.progressPercent * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                // 拖动进度条时用本地状态，松手才真正 seek。
                //
                // 原实现把 onValueChange 直接接到 seekTo：每一次指针移动都会
                // startForegroundService(ACTION_SEEK)，而服务端每次 seek 都会
                // currentSessionId++，从而取消并重启整条模拟 flow ——
                // 拖动一次会触发几十次重启，表现为进度条卡顿、位置跳变。
                var dragProgress by remember { mutableStateOf<Float?>(null) }
                Slider(
                    value = dragProgress ?: simState.progressPercent,
                    onValueChange = { percent -> dragProgress = percent },
                    onValueChangeFinished = {
                        dragProgress?.let { simulationViewModel.seekTo(context, it) }
                        dragProgress = null
                    },
                    valueRange = 0f..1f,
                    enabled = simState.status is SimulationStatus.Running || simState.status is SimulationStatus.Paused,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )

                Spacer(Modifier.height(12.dp))

                // Primary Simulation Master Buttons
                // 先在组合期取局部量：selectedRoute 是 collectAsState() 委托，
                // onClick lambda 会在稍后重新读取它，而 MapViewModel.clearWaypoints() /
                // deleteRoute() 可能在这两次读取之间把状态置空 ——
                // 那样 selectedRoute!! 就会在点击时抛 KotlinNullPointerException。
                val route = selectedRoute
                val hasValidRoute = route != null && route.waypoints.size >= 2
                val semantic = AppSemanticColors.current
                when (simState.status) {
                    is SimulationStatus.Idle, is SimulationStatus.Completed -> {
                        Button(
                            onClick = {
                                if (route != null && route.waypoints.size >= 2) {
                                    simulationViewModel.startSimulation(context, route, selectedSpeed)
                                    Toast.makeText(context, "路线模拟已开启", Toast.LENGTH_SHORT).show()
                                } else {
                                    showRoutePickerDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasValidRoute) {
                                    semantic.success
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                contentColor = if (hasValidRoute) {
                                    semantic.onSuccess
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (hasValidRoute) "开始模拟路线" else "请先选定路线",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    is SimulationStatus.Running -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { simulationViewModel.pauseSimulation(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = semantic.warningContainer,
                                    contentColor = semantic.onWarningContainer
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("暂停模拟", style = MaterialTheme.typography.labelLarge)
                            }
                            Button(
                                onClick = { simulationViewModel.stopSimulation(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("停止模拟", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    is SimulationStatus.Paused -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { simulationViewModel.resumeSimulation(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = semantic.success,
                                    contentColor = semantic.onSuccess
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("继续模拟", style = MaterialTheme.typography.labelLarge)
                            }
                            Button(
                                onClick = { simulationViewModel.stopSimulation(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("停止模拟", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    is SimulationStatus.Error -> {
                        Button(
                            onClick = { simulationViewModel.stopSimulation(context) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("重置状态", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 4. Speed Controller
        // =====================================================================
        SectionHeader("巡航配速控制")

        SectionCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "当前配速",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = "${"%.1f".format(selectedSpeed)} km/h",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                val presetSpeeds = listOf(
                    Triple(5f, Icons.AutoMirrored.Filled.DirectionsWalk, "5"),
                    Triple(8f, Icons.AutoMirrored.Filled.DirectionsRun, "8"),
                    Triple(20f, Icons.AutoMirrored.Filled.DirectionsBike, "20"),
                    Triple(60f, Icons.Default.DirectionsCar, "60"),
                    Triple(100f, Icons.Default.DirectionsCar, "100")
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetSpeeds.forEach { (speedVal, icon, numStr) ->
                        val isSelected = kotlin.math.abs(selectedSpeed - speedVal) < 0.5f
                        FilterChip(
                            selected = isSelected,
                            onClick = { applySpeed(speedVal) },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = numStr,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Slider(
                    value = selectedSpeed.coerceIn(1f, 120f),
                    onValueChange = { applySpeed(it) },
                    valueRange = 1f..120f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "1 km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "60 km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "120 km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = customSpeedInput,
                    onValueChange = {
                        customSpeedInput = it
                        // syncInputField = false：不要覆盖用户正在输入的文本
                        it.toFloatOrNull()?.let { spd ->
                            if (spd > 0) applySpeed(spd, syncInputField = false)
                        }
                    },
                    supportingText = {
                        Text(
                            "输入任意精确配速 (如 12.5)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    },
                    suffix = {
                        Text(
                            "km/h",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
            }
        }

        // =====================================================================
        // 5. Cadence & Step Simulation
        //    v2.4.0：非 Root 也展示（灰度 + 「仅预览」），避免「开关亮着但引擎没注入」的谎报
        // =====================================================================
        run {
            SectionHeader("运动步频与计步仿真")

            SectionCard {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(
                                "启用步频与计步仿真",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                "按路线速度生成步频、步数与加速度，注入 LSPosed 作用域中勾选的目标应用；未勾选的应用收不到",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = sensorState.isEnabled,
                                enabled = isRootActive == true,
                                onCheckedChange = {
                                    simulationViewModel.setCadenceEnabled(it, selectedSpeed)
                                }
                            )
                        }
                    )

                    if (sensorState.isEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Column(modifier = Modifier.padding(16.dp)) {
                            val currentDisplaySpeed =
                                if (simState.status is SimulationStatus.Running) simState.speedKmh else selectedSpeed
                            val effectiveCadence = remember(
                                currentDisplaySpeed,
                                sensorState.cadenceMode,
                                sensorState.customCadence,
                                sensorState.isEnabled,
                                simState.status,
                                sensorState.cadenceStepsPerMin
                            ) {
                                if (!sensorState.isEnabled) 0
                                else if (simState.status is SimulationStatus.Running && sensorState.cadenceStepsPerMin > 0) {
                                    sensorState.cadenceStepsPerMin
                                } else {
                                    sensorEngine.calculateCadence(
                                        currentDisplaySpeed,
                                        sensorState.cadenceMode,
                                        sensorState.customCadence
                                    )
                                }
                            }
                            val effectiveStride = remember(currentDisplaySpeed, effectiveCadence) {
                                sensorEngine.calculateStride(currentDisplaySpeed, effectiveCadence)
                            }

                            MetricGrid(
                                listOf(
                                    "实时步频" to if (!sensorState.isEnabled) {
                                        "-- SPM"
                                    } else if (simState.status is SimulationStatus.Running) {
                                        "${sensorState.cadenceStepsPerMin} SPM"
                                    } else {
                                        "$effectiveCadence SPM"
                                    },
                                    "累计步数" to "${sensorState.stepCount} 步",
                                    "预估步幅" to if (!sensorState.isEnabled) {
                                        "-- m"
                                    } else {
                                        "${"%.2f".format(effectiveStride)} m"
                                    },
                                    "垂直加速度" to if (simState.status is SimulationStatus.Running) {
                                        "${"%.1f".format(sensorState.accelZ)} m/s²"
                                    } else {
                                        "9.8 m/s²"
                                    }
                                )
                            )

                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(14.dp))

                            Text(
                                "步频拟真模式",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))

                            val modes = listOf(
                                CadenceMode.AUTO to "自适应",
                                CadenceMode.WALK to "健步 110",
                                CadenceMode.JOG to "慢跑 160",
                                CadenceMode.RUN to "跑马 180",
                                CadenceMode.CUSTOM to "自定义"
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                modes.forEach { (mode, label) ->
                                    FilterChip(
                                        selected = sensorState.cadenceMode == mode,
                                        onClick = {
                                            val targetCadence = when (mode) {
                                                CadenceMode.WALK -> 110
                                                CadenceMode.JOG -> 160
                                                CadenceMode.RUN -> 180
                                                CadenceMode.CUSTOM ->
                                                    customCadenceInput.toIntOrNull()
                                                        ?: sensorState.customCadence
                                                CadenceMode.AUTO -> sensorState.customCadence
                                            }
                                            simulationViewModel.setCadenceMode(
                                                mode,
                                                targetCadence,
                                                selectedSpeed
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    )
                                }
                            }

                            if (sensorState.cadenceMode == CadenceMode.CUSTOM) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = customCadenceInput,
                                    onValueChange = {
                                        customCadenceInput = it
                                        it.toIntOrNull()?.let { spm ->
                                            if (spm in 40..300) {
                                                simulationViewModel.setCadenceMode(
                                                    CadenceMode.CUSTOM,
                                                    spm,
                                                    selectedSpeed
                                                )
                                            }
                                        }
                                    },
                                    supportingText = {
                                        Text(
                                            "输入步频 (如 172)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    suffix = {
                                        Text(
                                            "SPM (步/分)",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.small
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { simulationViewModel.resetSteps() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "清零步数",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 6. GPS Realism & Anti-Detection (Root Required)
        // =====================================================================
        if (isRootActive == true) {
            SectionHeader("GPS 底层拟真与抗检测 (Root 专享)")

            SectionCard {
                Column {
                    ListItem(
                        leadingContent = {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = AppSemanticColors.current.successContainer,
                                contentColor = AppSemanticColors.current.onSuccessContainer
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        },
                        headlineContent = {
                            Text(
                                "全链路 GPS 真实感注入中",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                "多星定位协议 · 高斯微漂移 · 自然配速呼吸抖动",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GpsFeatureCard(
                                modifier = Modifier.weight(1f),
                                title = "多星系统搜星",
                                value = "21 颗",
                                subtitle = "GPS+北斗+Galileo",
                                icon = Icons.Default.Star,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            GpsFeatureCard(
                                modifier = Modifier.weight(1f),
                                title = "拟真水平精度",
                                value = "±1.8 m",
                                subtitle = "动态 1.5~2.5m 真实浮动",
                                icon = Icons.Default.LocationOn,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GpsFeatureCard(
                                modifier = Modifier.weight(1f),
                                title = "高斯微漂移",
                                value = "±0.3 m",
                                subtitle = "Box-Muller 消除静态死点",
                                icon = Icons.Default.Refresh,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            GpsFeatureCard(
                                modifier = Modifier.weight(1f),
                                title = "配速弹性波动",
                                value = "±5%",
                                subtitle = "模拟真实跑步体力起伏",
                                icon = Icons.Default.PlayArrow,
                                tint = AppSemanticColors.current.success
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GpsFeatureCard(
                                modifier = Modifier.weight(1f),
                                title = "动态高程海拔",
                                value = "${"%.1f".format(simState.currentWayPoint?.altitude ?: 25.0)} m",
                                subtitle = "真实路网地形起伏仿真",
                                icon = Icons.Default.Place,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            GpsFeatureCard(
                                modifier = Modifier.weight(1f),
                                title = "航向角切线",
                                value = "${"%.0f".format(simState.bearing)}°",
                                subtitle = "路线转弯平滑平角过渡",
                                icon = Icons.AutoMirrored.Filled.Send,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }

    // Route picker dialog
    if (showRoutePickerDialog) {
        AlertDialog(
            onDismissRequest = { showRoutePickerDialog = false },
            title = {
                Text(
                    "选择要模拟的路线",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                if (savedRoutes.isEmpty()) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "路线库暂无存档",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            showRoutePickerDialog = false
                            onNavigateToMap()
                        }) {
                            Text(
                                "前往地图绘制路线",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(savedRoutes) { route ->
                            val isSelected = selectedRoute?.id == route.id
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        mapViewModel.selectRoute(route)
                                        showRoutePickerDialog = false
                                        Toast.makeText(context, "已载入路线: ${route.name}", Toast.LENGTH_SHORT).show()
                                    },
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    contentColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            route.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            "${route.waypoints.size} 个折点 · ${"%.2f".format(route.totalDistanceKm)} km",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingContent = if (isSelected) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = {
                        showRoutePickerDialog = false
                        onNavigateToLibrary()
                    }) {
                        Text("路线库管理", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { showRoutePickerDialog = false }) {
                        Text(
                            "取消",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}



@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = AppSpacing.pageH)
    )
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.pageH),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(content = content)
    }
}

@Composable
private fun VersionBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun StatusBadge(status: SimulationStatus) {
    val semantic = AppSemanticColors.current
    val (text, containerColor, contentColor) = when (status) {
        is SimulationStatus.Idle -> Triple(
            "待机",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        is SimulationStatus.Running -> Triple(
            "模拟运行中",
            semantic.successContainer,
            semantic.onSuccessContainer
        )
        is SimulationStatus.Paused -> Triple(
            "已暂停",
            semantic.warningContainer,
            semantic.onWarningContainer
        )
        is SimulationStatus.Completed -> Triple(
            "模拟完成",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        is SimulationStatus.Error -> Triple(
            "异常",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MetricGrid(metrics: List<Pair<String, String>>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowMetrics.forEach { (label, value) ->
                    MetricItem(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowMetrics.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
