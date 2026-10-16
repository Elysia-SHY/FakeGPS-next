package com.mockrun.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.IosColors
import com.mockrun.app.core.designsystem.IosPrimaryButton
import com.mockrun.app.core.designsystem.IosTypography
import com.mockrun.app.core.designsystem.bouncyClickable
import com.mockrun.app.core.designsystem.minTouchTarget
import com.mockrun.app.util.PermissionHelper
import com.mockrun.app.util.PermissionIssueType

/**
 * 权限引导对话框。
 *
 * 弹窗规格：短确认 → AlertDialog 24dp 圆角（规范 A）。
 *
 * 原实现用手写的 `Color(0xFF1C1C1E)` / `Color.White` / `Color.Black` 作为
 * 底板与文字色，与设计系统的 `IosColors.SecondaryGroupedBackground` /
 * `IosColors.Label` 在浅色档下色差可见；现统一走语义色，
 * 深浅色双分支行为不变（语义 palette 自身按 isDark 取值）。
 */
@Composable
fun PermissionGuideDialog(
    issueType: PermissionIssueType,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = IosColors.SecondaryGroupedBackground,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (issueType) {
                        PermissionIssueType.MOCK_LOCATION_APP_NOT_SET -> "需设置模拟位置应用"
                        PermissionIssueType.LOCATION_PERMISSION_MISSING -> "需授予系统定位权限"
                        else -> "提示"
                    },
                    style = IosTypography.Headline,
                    fontWeight = FontWeight.Bold,
                    color = IosColors.Label
                )
                Box(
                    modifier = Modifier
                        .minTouchTarget()
                        .clip(CircleShape)
                        .bouncyClickable(onClick = onDismissRequest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = IosColors.SecondaryLabel,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (issueType) {
                    PermissionIssueType.MOCK_LOCATION_APP_NOT_SET -> {
                        Text(
                            text = "当前应用未在系统中被设为模拟位置提供者，直接开启会导致服务无法注入系统 GPS：",
                            style = IosTypography.Subheadline,
                            color = IosColors.Label
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = IosColors.TertiarySystemFill,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "操作指引（仅需一次）：",
                                    style = IosTypography.Footnote,
                                    fontWeight = FontWeight.Bold,
                                    color = IosColors.SystemBlue
                                )
                                Text(
                                    text = "1. 点击下方按钮前往系统【开发者选项】",
                                    style = IosTypography.Caption1,
                                    color = IosColors.Label
                                )
                                Text(
                                    text = "2. 找到「选择模拟位置信息应用」项",
                                    style = IosTypography.Caption1,
                                    color = IosColors.Label
                                )
                                Text(
                                    text = "3. 勾选选择本应用「FakeGPS-next」",
                                    style = IosTypography.Caption1,
                                    color = IosColors.Label
                                )
                            }
                        }

                        Text(
                            text = "提示：若未开启开发者选项，请在系统设置【关于手机】中连续快速点击「版本号」7 次。",
                            style = IosTypography.Caption1,
                            color = IosColors.SecondaryLabel
                        )
                    }
                    PermissionIssueType.LOCATION_PERMISSION_MISSING -> {
                        Text(
                            text = "应用需要基础定位权限以读取当前设备基准坐标并换算地图图层：",
                            style = IosTypography.Subheadline,
                            color = IosColors.Label
                        )
                        Text(
                            text = "请点击下方前往应用权限设置，授予「精确位置」权限。",
                            style = IosTypography.Caption1,
                            color = IosColors.SystemBlue
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            IosPrimaryButton(
                text = when (issueType) {
                    PermissionIssueType.MOCK_LOCATION_APP_NOT_SET -> "前往【开发者选项】"
                    PermissionIssueType.LOCATION_PERMISSION_MISSING -> "前往授权设置"
                    else -> "前往设置"
                },
                onClick = {
                    onDismissRequest()
                    when (issueType) {
                        PermissionIssueType.MOCK_LOCATION_APP_NOT_SET -> {
                            PermissionHelper.openDevelopmentSettings(context)
                        }
                        PermissionIssueType.LOCATION_PERMISSION_MISSING -> {
                            PermissionHelper.openAppSettings(context)
                        }
                        else -> {}
                    }
                },
                icon = Icons.Default.Settings,
                height = 48.dp
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.minTouchTarget(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("暂不设置", color = IosColors.SecondaryLabel, style = IosTypography.Subheadline)
            }
        }
    )
}