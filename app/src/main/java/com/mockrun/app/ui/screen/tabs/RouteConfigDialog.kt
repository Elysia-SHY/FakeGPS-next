package com.mockrun.app.ui.screen.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.ui.theme.IosColors
import com.mockrun.app.ui.theme.LiquidGlassDefaults
import com.mockrun.app.ui.theme.liquidGlass

data class SpeedPreset(val label: String, val speed: Float, val icon: ImageVector)

@Composable
fun RouteConfigDialog(
    currentSpeed: Float,
    isCadenceEnabled: Boolean,
    onSpeedChange: (Float) -> Unit,
    onCadenceToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    isLiquidGlass: Boolean = true
) {
    var speedVal by remember { mutableFloatStateOf(currentSpeed) }
    var cadenceVal by remember { mutableStateOf(isCadenceEnabled) }

    val presets = listOf(
        SpeedPreset("步行", 4.5f, Icons.Default.DirectionsWalk),
        SpeedPreset("慢跑", 8.0f, Icons.Default.DirectionsRun),
        SpeedPreset("快跑", 12.0f, Icons.Default.DirectionsRun),
        SpeedPreset("骑行", 18.0f, Icons.Default.DirectionsBike),
        SpeedPreset("驾车", 40.0f, Icons.Default.DirectionsCar)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⚡ 巡航参数配置",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = IosColors.Label
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Presets
                Text(
                    text = "快速预设配速",
                    style = MaterialTheme.typography.labelMedium,
                    color = IosColors.SecondaryLabel
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { p ->
                        val isSelected = kotlin.math.abs(speedVal - p.speed) < 0.2f
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    speedVal = p.speed
                                    onSpeedChange(p.speed)
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) IosColors.SystemBlue else IosColors.SystemGray6
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
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
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else IosColors.Label
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
                        Text("自定义模拟配速", fontSize = 13.5.sp, color = IosColors.Label)
                        Text(
                            text = "${"%.1f".format(speedVal)} km/h",
                            fontWeight = FontWeight.Bold,
                            color = IosColors.SystemBlue,
                            fontSize = 15.sp
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
                    Column {
                        Text("拟真步频与传感器模拟", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = IosColors.Label)
                        Text("自动生成步数、加速度与传感器震荡", fontSize = 11.sp, color = IosColors.SecondaryLabel)
                    }
                    Switch(
                        checked = cadenceVal,
                        onCheckedChange = {
                            cadenceVal = it
                            onCadenceToggle(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IosColors.SystemGreen)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确定", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
