package com.mockrun.app.feature.map.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.IosColors
import com.mockrun.app.core.designsystem.IosPrimaryButton
import com.mockrun.app.core.designsystem.IosSuccess
import com.mockrun.app.core.designsystem.IosWarning
import com.mockrun.app.core.designsystem.IosTypography
import com.mockrun.app.core.designsystem.bouncyClickable
import com.mockrun.app.core.designsystem.minTouchTarget
import com.mockrun.app.core.location.CadenceMode
import com.mockrun.app.core.location.RouteSimulator
import com.mockrun.app.domain.model.RouteMode
import kotlin.math.roundToInt

data class SpeedPreset(val label: String, val speed: Float, val icon: ImageVector)

/**
 * 巡航参数配置。
 *
 * 弹窗规格：短表单 → AlertDialog 24dp 圆角（规范 A）。
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
    isLiquidGlass: Boolean = true,
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
                style = IosTypography.Headline,
                fontWeight = FontWeight.Bold,
                color = IosColors.Label
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
                    style = IosTypography.Caption1,
                    color = IosColors.SecondaryLabel
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
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .bouncyClickable {
                                    speedVal = p.speed
                                    onSpeedChange(p.speed)
                                },
                            shape = RoundedCornerShape(13.dp),
                            color = if (isSelected) IosColors.SystemBlue else IosColors.TertiarySystemFill
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = p.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else IosColors.SecondaryLabel,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = p.label,
                                    style = IosTypography.Caption2,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else IosColors.Label,
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
                        Text("自定义模拟配速", style = IosTypography.Subheadline, color = IosColors.Label)
                        Text(
                            text = "${"%.1f".format(speedVal)} km/h",
                            fontWeight = FontWeight.Bold,
                            color = IosColors.SystemBlue,
                            style = IosTypography.Subheadline
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
                            thumbColor = IosColors.SystemBlue,
                            activeTrackColor = IosColors.SystemBlue
                        )
                    )
                }

                // ── 运行方式（v2.4.1：从路线库菜单提到「参数设置」首屏）──
                Column {
                    Text("运行方式", style = IosTypography.Caption1, color = IosColors.SecondaryLabel)
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
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .bouncyClickable(enabled = !loopBlocked) {
                                        modeVal = m
                                        onModeChange(m)
                                    },
                                shape = RoundedCornerShape(13.dp),
                                color = when {
                                    selected -> IosColors.SystemBlue
                                    loopBlocked -> IosColors.TertiarySystemFill.copy(alpha = 0.4f)
                                    else -> IosColors.TertiarySystemFill
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
                                        style = IosTypography.Caption1,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color.White else IosColors.Label,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (loopBlocked) "首尾差 ${closingGapMeters.toInt()} m" else when (m) {
                                            RouteMode.ONE_WAY -> "到头自动停"
                                            RouteMode.PING_PONG -> "原路折返"
                                            RouteMode.LOOP -> "首尾相连"
                                        },
                                        style = IosTypography.Caption2,
                                        color = if (selected) Color.White.copy(alpha = 0.85f) else IosColors.SecondaryLabel,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 步频模式（v2.4.1：地图页也能直接设自定义步频）──
                Column {
                    Text("步频模式", style = IosTypography.Caption1, color = IosColors.SecondaryLabel)
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
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .bouncyClickable {
                                        cadenceModeVal = cm
                                        onCadenceConfigChange(cm, customVal.roundToInt())
                                    },
                                shape = RoundedCornerShape(11.dp),
                                color = if (selected) IosColors.SystemBlue else IosColors.TertiarySystemFill
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = when (cm) {
                                            CadenceMode.AUTO -> "自适应"
                                            CadenceMode.WALK -> "健步 110"
                                            CadenceMode.JOG -> "慢跑 160"
                                            CadenceMode.RUN -> "跑马 180"
                                            CadenceMode.CUSTOM -> "自定义"
                                        },
                                        style = IosTypography.Caption1,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else IosColors.Label,
                                        maxLines = 1
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
                            Text("自定义步频", style = IosTypography.Caption1, color = IosColors.SecondaryLabel)
                            Text(
                                text = "${customVal.roundToInt()} SPM",
                                style = IosTypography.Caption1,
                                fontWeight = FontWeight.Bold,
                                color = IosColors.SystemBlue
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
                                thumbColor = IosColors.SystemBlue,
                                activeTrackColor = IosColors.SystemBlue
                            )
                        )
                    }
                }

                HorizontalDivider(color = IosColors.Separator)

                // Cadence Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "步频与计步估算（仅预览）",
                            style = IosTypography.Subheadline,
                            fontWeight = FontWeight.SemiBold,
                            color = IosColors.Label
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "按速度估算步数、加速度；不会写入系统传感器",
                            style = IosTypography.Caption1,
                            color = IosColors.SecondaryLabel
                        )
                    }
                    Box(
                        modifier = Modifier.minTouchTarget(),
                        contentAlignment = Alignment.Center
                    ) {
                        Switch(
                            checked = cadenceVal,
                            enabled = true,
                            onCheckedChange = {
                                cadenceVal = it
                                onCadenceToggle(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IosSuccess
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            IosPrimaryButton(
                text = "确定",
                onClick = onDismiss,
                height = 48.dp
            )
        },
        containerColor = IosColors.SecondaryGroupedBackground,
        shape = RoundedCornerShape(24.dp)
    )
}
