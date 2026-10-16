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
    isLiquidGlass: Boolean = true
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

                HorizontalDivider(color = IosColors.Separator)

                // Cadence Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRootAvailable) "拟真步频与传感器模拟" else "拟真步频与传感器模拟 (Root 专享)",
                            style = IosTypography.Subheadline,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRootAvailable) IosColors.Label else IosColors.SecondaryLabel
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isRootAvailable) "自动生成步数、加速度与传感器震荡" else "需 Root 注入环境支持（当前设备未获得 Root）",
                            style = IosTypography.Caption1,
                            color = if (isRootAvailable) IosColors.SecondaryLabel else IosWarning
                        )
                    }
                    Box(
                        modifier = Modifier.minTouchTarget(),
                        contentAlignment = Alignment.Center
                    ) {
                        Switch(
                            checked = if (isRootAvailable) cadenceVal else false,
                            enabled = isRootAvailable,
                            onCheckedChange = {
                                if (isRootAvailable) {
                                    cadenceVal = it
                                    onCadenceToggle(it)
                                }
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
