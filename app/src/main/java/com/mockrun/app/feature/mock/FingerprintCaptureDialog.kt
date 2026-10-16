package com.mockrun.app.feature.mock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.IosColors
import com.mockrun.app.core.designsystem.IosTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 采集环境指纹的对话框。
 *
 * 坐标默认优先取真机真实物理定位，避免将虚拟定位/选定点误当作采集点。
 */
@Composable
fun FingerprintCaptureDialog(
    defaultLat: Double,
    defaultLon: Double,
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, lat: Double, lon: Double) -> Unit,
    /** 默认坐标的来源说明，用于提示用户这个值是不是自己实际站的位置。 */
    defaultCoordSource: String? = null,
    /** 当前是否正在虚拟定位。为 true 时采集会读到模块自己的伪造值。 */
    isMockActive: Boolean = false,
    /** 实时获取当前真实物理定位（真机 GPS/基站），成功返回 Pair(lat, lon)，无定位返回 null */
    onRequestRealLocation: (() -> Pair<Double, Double>?)? = null,
    /** 停止模拟定位回调 */
    onStopMock: (() -> Unit)? = null
) {
    // 原实现只精确到分钟，同一分钟内重复采集会生成同名 id，
    // 覆盖旧文件后列表文件名不变、摘要不刷新，看起来像"采了没反应"。
    // 加秒并把连字符统一，降低撞名概率。
    val defaultName = remember {
        "fp-" + SimpleDateFormat("MMdd-HHmmss", Locale.US).format(Date())
    }
    var name by remember { mutableStateOf(defaultName) }
    var latText by remember { mutableStateOf("%.6f".format(Locale.US, defaultLat)) }
    var lonText by remember { mutableStateOf("%.6f".format(Locale.US, defaultLon)) }
    var currentSourceHint by remember(defaultCoordSource) {
        mutableStateOf(defaultCoordSource ?: "真机物理定位")
    }
    var localMockActive by remember(isMockActive) {
        mutableStateOf(isMockActive)
    }

    val lat = latText.toDoubleOrNull()
    val lon = lonText.toDoubleOrNull()
    val nameOk = name.isNotBlank() && name.matches(Regex("^[A-Za-z0-9_\\-]+$"))
    val latOk = lat != null && lat in -90.0..90.0
    val lonOk = lon != null && lon in -180.0..180.0
    val coordOk = latOk && lonOk
    val canConfirm = nameOk && coordOk

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("采集环境指纹", style = IosTypography.Headline) },
        text = {
            // 原实现没有滚动也没有 IME 避让：小屏或大字号下，
            // 键盘弹出会顶掉底部按钮，取消/开始采集点不到。
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                Text(
                    text = "请在目标地点、且已关闭虚拟定位的状态下采集。" +
                        "采集会扫描 Wi-Fi、基站与蓝牙，约需 12–15 秒。",
                    style = IosTypography.Footnote,
                    color = IosColors.SecondaryLabel
                )

                if (localMockActive) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(IosColors.SystemOrange.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = IosColors.SystemOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "虚拟定位正在运行，此时采集会记录伪造环境。",
                                style = IosTypography.Caption1,
                                color = IosColors.SystemOrange
                            )
                        }
                        if (onStopMock != null) {
                            Spacer(Modifier.width(6.dp))
                            TextButton(
                                onClick = {
                                    onStopMock()
                                    localMockActive = false
                                    onRequestRealLocation?.invoke()?.let { real ->
                                        latText = "%.6f".format(Locale.US, real.first)
                                        lonText = "%.6f".format(Locale.US, real.second)
                                        currentSourceHint = "真机物理定位（已停止模拟并重置）"
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    "停止模拟",
                                    style = IosTypography.Caption1,
                                    color = IosColors.SystemOrange
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("指纹名称（文件名标识）") },
                    singleLine = true,
                    isError = !nameOk,
                    supportingText = {
                        if (!nameOk) {
                            Text("只能包含字母、数字、下划线和连字符")
                        } else {
                            Text("同名采集会覆盖已有指纹")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "采集点坐标（WGS-84）",
                        style = IosTypography.Caption1,
                        color = IosColors.SecondaryLabel
                    )
                    if (onRequestRealLocation != null) {
                        TextButton(
                            onClick = {
                                val real = onRequestRealLocation()
                                if (real != null) {
                                    latText = "%.6f".format(Locale.US, real.first)
                                    lonText = "%.6f".format(Locale.US, real.second)
                                    currentSourceHint = "真机物理定位（最新校准）"
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = IosColors.SystemBlue
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "获取本机真实位置",
                                style = IosTypography.Caption2,
                                color = IosColors.SystemBlue
                            )
                        }
                    }
                }

                val isRealLocationSource = currentSourceHint.contains("真机") || currentSourceHint.contains("真实")
                if (currentSourceHint.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isRealLocationSource) {
                            "来源：$currentSourceHint（已自动匹配当前真实物理位置）"
                        } else {
                            "来源：$currentSourceHint（建议点击右上角获取本机真实位置）"
                        },
                        style = IosTypography.Caption2,
                        color = if (isRealLocationSource) IosColors.SystemGreen else IosColors.SystemOrange
                    )
                }

                Spacer(Modifier.height(6.dp))

                // 窄屏或大字号下两个输入框并排会把内容压扁、错误文案换行错位，
                // 因此在可用宽度不足时改为纵向堆叠。
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stackVertically = maxWidth < 300.dp
                    if (stackVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LatitudeField(latText, { latText = it }, latOk)
                            LongitudeField(lonText, { lonText = it }, lonOk)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                LatitudeField(latText, { latText = it }, latOk)
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                LongitudeField(lonText, { lonText = it }, lonOk)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "采集期间请留在本页，避免切换页面导致采集中断。",
                    style = IosTypography.Caption2,
                    color = IosColors.TertiaryLabel
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onConfirm(name, lat!!, lon!!) },
                enabled = canConfirm
            ) { Text("开始采集") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("取消") }
        }
    )
}

/** 纬度输入。范围校验与错误原因都直接显示在字段下方，不靠红框让人猜。 */
@Composable
private fun LatitudeField(
    value: String,
    onValueChange: (String) -> Unit,
    isValid: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("纬度") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = !isValid,
        supportingText = { if (!isValid) Text("范围 -90 ~ 90") },
        modifier = Modifier.fillMaxWidth()
    )
}

/** 经度输入。 */
@Composable
private fun LongitudeField(
    value: String,
    onValueChange: (String) -> Unit,
    isValid: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("经度") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = !isValid,
        supportingText = { if (!isValid) Text("范围 -180 ~ 180") },
        modifier = Modifier.fillMaxWidth()
    )
}
