package com.mockrun.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.ui.theme.IosColors
import com.mockrun.app.ui.theme.IosFrostedCapsule
import com.mockrun.app.ui.theme.IosGray
import com.mockrun.app.util.PermissionHelper
import com.mockrun.app.util.PermissionIssueType

@Composable
fun PermissionGuideDialog(
    issueType: PermissionIssueType,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = if (isDark) Color(0xFF1C1C1E) else IosFrostedCapsule,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (issueType) {
                        PermissionIssueType.MOCK_LOCATION_APP_NOT_SET -> "⚙️ 需设置模拟位置应用"
                        PermissionIssueType.LOCATION_PERMISSION_MISSING -> "📍 需授予系统定位权限"
                        else -> "提示"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isDark) Color.White else Color.Black
                )
                IconButton(onClick = onDismissRequest, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = IosGray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (issueType) {
                    PermissionIssueType.MOCK_LOCATION_APP_NOT_SET -> {
                        Text(
                            text = "当前应用未在系统中被设为模拟位置提供者，直接开启会导致服务无法注入系统 GPS：",
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            color = if (isDark) Color.White.copy(0.85f) else Color.Black.copy(0.80f)
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "操作指引（仅需一次）：",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IosColors.SystemBlue
                                )
                                Text(
                                    text = "1. 点击下方按钮前往系统【开发者选项】",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color.White.copy(0.8f) else Color.Black.copy(0.7f)
                                )
                                Text(
                                    text = "2. 找到「选择模拟位置信息应用」项",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color.White.copy(0.8f) else Color.Black.copy(0.7f)
                                )
                                Text(
                                    text = "3. 勾选选择本应用「FakeGPS-next」",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color.White.copy(0.8f) else Color.Black.copy(0.7f)
                                )
                            }
                        }

                        Text(
                            text = "💡 提示：若未开启开发者选项，请在系统设置【关于手机】中连续快速点击「版本号」7次。",
                            fontSize = 11.5.sp,
                            color = IosGray,
                            lineHeight = 16.sp
                        )
                    }
                    PermissionIssueType.LOCATION_PERMISSION_MISSING -> {
                        Text(
                            text = "应用需要基础定位权限以读取当前设备基准坐标并换算地图图层：",
                            fontSize = 13.5.sp,
                            color = if (isDark) Color.White.copy(0.85f) else Color.Black.copy(0.80f)
                        )
                        Text(
                            text = "请点击下方前往应用权限设置，授予「精确位置」权限。",
                            fontSize = 12.5.sp,
                            color = IosColors.SystemBlue
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
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
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemBlue)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when (issueType) {
                        PermissionIssueType.MOCK_LOCATION_APP_NOT_SET -> "前往【开发者选项】"
                        PermissionIssueType.LOCATION_PERMISSION_MISSING -> "前往授权设置"
                        else -> "前往设置"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("暂不设置", color = IosGray, fontSize = 13.sp)
            }
        }
    )
}