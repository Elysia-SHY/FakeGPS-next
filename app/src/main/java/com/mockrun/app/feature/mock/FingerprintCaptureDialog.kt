package com.mockrun.app.feature.mock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * 坐标默认取当前主控点，允许手工改成「实际站的位置」——
 * 采集必须在真实地点进行，所以这个值经常和虚拟点不同。
 */
@Composable
fun FingerprintCaptureDialog(
    defaultLat: Double,
    defaultLon: Double,
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, lat: Double, lon: Double) -> Unit
) {
    val defaultName = remember {
        "fp-" + SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date())
    }
    var name by remember { mutableStateOf(defaultName) }
    var latText by remember { mutableStateOf("%.6f".format(Locale.US, defaultLat)) }
    var lonText by remember { mutableStateOf("%.6f".format(Locale.US, defaultLon)) }

    val lat = latText.toDoubleOrNull()
    val lon = lonText.toDoubleOrNull()
    val nameOk = name.isNotBlank() && name.matches(Regex("^[A-Za-z0-9_\\-]+$"))
    val coordOk = lat != null && lon != null &&
        lat in -90.0..90.0 && lon in -180.0..180.0
    val canConfirm = nameOk && coordOk

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("采集环境指纹", style = IosTypography.Headline) },
        text = {
            Column {
                Text(
                    text = "请在目标地点、且已关闭虚拟定位的状态下采集。" +
                        "采集会扫描 Wi-Fi、基站与蓝牙，约需 12 秒。",
                    style = IosTypography.Footnote,
                    color = IosColors.SecondaryLabel
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    isError = !nameOk,
                    supportingText = {
                        if (!nameOk) Text("只能包含字母、数字、下划线和连字符")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "采集点坐标（WGS-84）",
                    style = IosTypography.Caption1,
                    color = IosColors.SecondaryLabel
                )

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("纬度") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = latText.toDoubleOrNull()?.let { it !in -90.0..90.0 } ?: true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text("经度") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = lonText.toDoubleOrNull()?.let { it !in -180.0..180.0 } ?: true,
                        modifier = Modifier.weight(1f)
                    )
                }
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
