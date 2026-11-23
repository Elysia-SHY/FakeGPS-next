package com.mockrun.app.feature.map.tabs
import androidx.compose.material3.MaterialTheme
import com.mockrun.app.core.designsystem.AppSemanticColors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.location.CadenceMode
import com.mockrun.app.core.location.RouteSimulator
import com.mockrun.app.domain.model.RouteMode
import kotlin.math.roundToInt

data class SpeedPreset(val label: String, val speed: Float, val icon: ImageVector)

/**
 * 巡航参数配置。
 *
 * 弹窗规格：短表单 → AlertDialog（28dp 圆角）。
 *
 * 两处修复（v1.7.1 精致玻璃风统一）：
 *   1. 五档配速原本是等宽一排（每档 ≈ 55dp），窄屏 + 系统大字号下
 *      中文标签会被压成两行并溢出。现按 [LocalConfiguration] 的
 *      screenWidthDp / fontScale 选择每行档数：宽屏 5 档一排，
 *      窄屏或 fontScale ≥ 1.3 时 3 档一行的 FlowRow。
 *   2. 内容区原本无滚动，大字号下底部「确定」会被挤出屏幕。
 *      现整块 body 走 verticalScroll。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteConfigDialog(
    currentSpeed: Float,
    isCadenceEnabled: Boolean,
    isRootAvailable: Boolean = false,
    onSpeedChange: (Float) -> Unit,
    onCadenceToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    // v2.4.1：运行方式与步频模式放进「参数设置」，用户在这里就能看到，不必去路线库翻菜单
    mode: RouteMode = RouteMode.ONE_WAY,
    closingGapMeters: Double = 0.0,
    onModeChange: (RouteMode) -> Unit = {},
    cadenceMode: CadenceMode = CadenceMode.AUTO,
    customSpm: Int = 165,
    onCadenceConfigChange: (CadenceMode, Int) -> Unit = { _, _ -> }
) {
    // remember 必须带键。
    //
    // isRootAvailable 由 SimulationViewModel 异步探测（首次组合时为 false），
    // 无键的 remember 会把 cadenceVal 永久锁在「false」——
    // root 探测晚于首帧落地时，Switch 显示关闭而步频其实已开启；
    // 同理调用方改速时滑块会回弹到旧值。
    var speedVal by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    var cadenceVal by remember(isCadenceEnabled, isRootAvailable) {
        mutableStateOf(if (isRootAvailable) isCadenceEnabled else false)
    }

    // v2.4.1：运行方式与步频模式的本地状态（键与外部值绑定，Root 探测晚到也不会闪回）
    var modeVal by remember(mode) { mutableStateOf(mode) }
    var cadenceModeVal by remember(cadenceMode) { mutableStateOf(cadenceMode) }
    var customVal by remember(customSpm) {
        mutableFloatStateOf(customSpm.toFloat().coerceIn(40f, 300f))
    }

    val presets = listOf(
        SpeedPreset("步行", 4.5f, Icons.Default.DirectionsWalk),
        SpeedPreset("慢跑", 8.0f, Icons.Default.DirectionsRun),
        SpeedPreset("快跑", 12.0f, Icons.Default.DirectionsRun),
        SpeedPreset("骑行", 18.0f, Icons.Default.DirectionsBike),
        SpeedPreset("驾车", 40.0f, Icons.Default.DirectionsCar)
    )

    // 每行档数：宽屏 5 档一排；窄屏或大字号 3 档一行（FlowRow 自动折行）。
    val configuration = LocalConfiguration.current
    val presetsPerRow = if (configuration.screenWidthDp < 380 || configuration.fontScale >= 1.3f) 3 else 5

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "巡航参数配置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Presets
                Text(
                    text = "快速预设配速",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxItemsInEachRow = presetsPerRow
                ) {
                    presets.forEach { p ->
                        val isSelected = kotlin.math.abs(speedVal - p.speed) < 0.2f
                        Surface(
                            onClick = {
                                speedVal = p.speed
                                onSpeedChange(p.speed)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            tonalElevation = if (isSelected) 2.dp else 1.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = p.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = p.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自定义模拟配速", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "${"%.1f".format(speedVal)} km/h",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Slider(
                        value = speedVal,
                        onValueChange = {
                            speedVal = it
                            onSpeedChange(it)
                        },
                        valueRange = 1f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // ── 运行方式（v2.4.1：从路线库菜单提到「参数设置」首屏）──
                Column {
                    Text("运行方式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxItemsInEachRow = 3
                    ) {
                        RouteMode.entries.forEach { m ->
                            val loopBlocked = m == RouteMode.LOOP &&
                                closingGapMeters > RouteSimulator.LOOP_MAX_GAP_M
                            val selected = m == modeVal
                            Surface(
                                onClick = {
                                    modeVal = m
                                    onModeChange(m)
                                },
                                enabled = !loopBlocked,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 58.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = when {
                                    selected -> MaterialTheme.colorScheme.primaryContainer
                                    loopBlocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = when {
                                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    loopBlocked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = m.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (loopBlocked) "首尾差 ${closingGapMeters.toInt()} m" else when (m) {
                                            RouteMode.ONE_WAY -> "到头自动停"
                                            RouteMode.PING_PONG -> "原路折返"
                                            RouteMode.LOOP -> "首尾相连"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 步频模式（v2.4.1：地图页也能直接设自定义步频）──
                Column {
                    Text("步频模式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxItemsInEachRow = 3
                    ) {
                        CadenceMode.entries.forEach { cm ->
                            val selected = cm == cadenceModeVal
                            Surface(
                                onClick = {
                                    cadenceModeVal = cm
                                    onCadenceConfigChange(cm, customVal.roundToInt())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (cm) {
                                            CadenceMode.AUTO -> "自适应"
                                            CadenceMode.WALK -> "健步 110"
                                            CadenceMode.JOG -> "慢跑 160"
                                            CadenceMode.RUN -> "跑马 180"
                                            CadenceMode.CUSTOM -> "自定义"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    if (cadenceModeVal == CadenceMode.CUSTOM) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("自定义步频", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${customVal.roundToInt()} SPM",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = customVal,
                            onValueChange = {
                                customVal = it
                                onCadenceConfigChange(CadenceMode.CUSTOM, it.roundToInt())
                            },
                            valueRange = 40f..300f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Cadence Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "步频与计步仿真",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRootAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isRootAvailable) {
                                "注入 LSPosed 作用域中勾选的目标应用"
                            } else {
                                "需 Root 授权后生成步数、加速度"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isRootAvailable) MaterialTheme.colorScheme.onSurfaceVariant else AppSemanticColors.current.warning
                        )
                    }
                    Switch(
                        checked = if (isRootAvailable) cadenceVal else false,
                        enabled = isRootAvailable,
                        onCheckedChange = {
                            if (isRootAvailable) {
                                cadenceVal = it
                                onCadenceToggle(it)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text("确定", style = MaterialTheme.typography.labelLarge)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge
    )
}
