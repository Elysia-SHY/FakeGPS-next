package com.mockrun.app.feature.mock

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.location.RootSuBridge
import com.mockrun.app.core.designsystem.IosColors
import com.mockrun.app.core.designsystem.IosTypography
import com.mockrun.app.util.logFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * iOS Style Guide Dialog: 防闪回与使用指南
 */
@Composable
fun UsageGuideDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "防闪回与使用指南",
                style = IosTypography.Title3,
                fontWeight = FontWeight.Bold,
                color = IosColors.Label
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text(
                        "【LSPosed 仅勾选「系统框架」】",
                        style = IosTypography.Headline,
                        color = IosColors.SystemBlue
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "请在 LSPosed 作用域中仅勾选【系统框架 (Android 系统)】，切勿勾选目标软件自身。本模块直接接管系统级 LocationManagerService 并屏蔽扫描硬件，无需注入宿主应用。",
                        style = IosTypography.Callout,
                        color = IosColors.SecondaryLabel
                    )
                }

                Column {
                    Text(
                        "【彻底关闭 Wi-Fi 与蓝牙硬件扫描】",
                        style = IosTypography.Headline,
                        color = IosColors.SystemOrange
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "高版本系统即使关闭 Wi-Fi，仍会允许周边基站与探针进行后台硬件定位扫描。请前往系统【设置 - 定位服务 - 提高精确度】，彻底关闭「Wi-Fi 扫描」和「蓝牙扫描」。",
                        style = IosTypography.Callout,
                        color = IosColors.SecondaryLabel
                    )
                }

                Column {
                    Text(
                        "【电池优化与后台运行无限制】",
                        style = IosTypography.Headline,
                        color = IosColors.SystemGreen
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "请将本应用及目标软件加入电池白名单（无限制后台运行），并在最近任务视图中对本应用执行「应用加锁」，防止底层前台服务被系统杀后台导致复位。",
                        style = IosTypography.Callout,
                        color = IosColors.SecondaryLabel
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("我知道了", color = IosColors.SystemBlue, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(14.dp),
        containerColor = IosColors.SecondaryGroupedBackground
    )
}

/**
 * 微信显示真实位置排查指南弹窗
 */
@Composable
fun WeChatTroubleshootDialog(
    context: Context,
    coroutineScope: CoroutineScope,
    rootBridge: RootSuBridge,
    isRootMode: Boolean,
    isRootAvailable: Boolean,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "💬 微信显示真实位置排查指南",
                style = IosTypography.Title3,
                fontWeight = FontWeight.Bold,
                color = IosColors.Label
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = IosColors.SystemOrange.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "💡 原理说明：微信内嵌腾讯定位 SDK。即便模拟了 GPS，微信默认仍会在后台扫描您周边的 Wi-Fi 路由器 MAC 地址（BSSID）上报给腾讯服务器反查真实经纬度；若检测到 Mock 标记则会直接舍弃 GPS 回退到 Wi-Fi 定位。",
                        style = IosTypography.Footnote,
                        color = IosColors.SystemOrange,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Column {
                    Text("处理方式（Root 功能）", style = IosTypography.Headline, color = IosColors.SystemPurple)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击下方按钮将关闭 WLAN 始终扫描、蓝牙始终扫描，并直接关闭 Wi-Fi 与蓝牙射频，让微信 / 打卡软件无法通过周边路由器 MAC 或蓝牙信标反查真实经纬度。\n" +
                        "未获取 Root 时，按钮会跳转到系统定位设置，请手动关闭「WLAN 扫描」与「蓝牙扫描」开关。",
                        style = IosTypography.Callout,
                        color = IosColors.SecondaryLabel
                    )
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (isRootMode && rootBridge.isRootAvailable()) {
                                val ok = rootBridge.disableWifiBluetoothScan()
                                if (ok) {
                                    Toast.makeText(context, "已关闭蓝牙 / Wi-Fi 与背景扫描", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Root 命令执行失败，请检查授权", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }.logFailure("LocationMockScreen", "open location settings").onFailure {
                                    Toast.makeText(context, "请在系统设置中关闭 WLAN 扫描 / 蓝牙扫描", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemPurple),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isRootMode && isRootAvailable) "一键关闭蓝牙 / Wi-Fi 与背景扫描" else "去系统设置关闭扫描开关",
                        style = IosTypography.Caption1,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("我知道了", color = IosColors.SystemBlue, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(14.dp),
        containerColor = IosColors.SecondaryGroupedBackground
    )
}

/**
 * 后台保活与防掉进程操作指南弹窗
 */
@Composable
fun KeepAliveGuideDialog(
    context: Context,
    isIgnoringBattery: Boolean,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "🛡️ 后台保活与防掉进程指南",
                style = IosTypography.Title3,
                fontWeight = FontWeight.Bold,
                color = IosColors.Label
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text("1. 忽略电池激进优化 (必须)", style = IosTypography.Headline, color = IosColors.SystemGreen)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "系统低电量或熄屏时会冻结未加白名单的后台进程，导致虚拟定位掉线闪回。点击下方快速加入白名单。",
                        style = IosTypography.Callout,
                        color = IosColors.SecondaryLabel
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            com.mockrun.app.core.location.KeepAliveHelper.requestIgnoreBatteryOptimization(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isIgnoringBattery) IosColors.SystemGreen else IosColors.SystemBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isIgnoringBattery) "✓ 已加入电池优化白名单" else "前往设置电池白名单 (无限制)",
                            style = IosTypography.Caption1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column {
                    Text("2. 多任务视图锁定应用 (关键)", style = IosTypography.Headline, color = IosColors.SystemOrange)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "• 从屏幕底部上滑悬停进入「最近任务 (多任务卡片)」视图\n" +
                        "• 长按本应用卡片（或下滑卡片），点击「加锁 (锁定图标)」\n" +
                        "• 锁定后「一键清理后台」时不会误杀虚拟定位常驻服务",
                        style = IosTypography.Callout,
                        color = IosColors.SecondaryLabel
                    )
                }

                Column {
                    Text("3. 自启动与后台关联启动", style = IosTypography.Headline, color = IosColors.SystemBlue)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "在小米/OPPO/vivo/华为等国内深度定制系统中，请在【系统设置 - 应用管理 - 自启动管理】中允许本应用「允许后台自启动」和「允许关联启动」。",
                        style = IosTypography.Callout,
                        color = IosColors.SecondaryLabel
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("我知道了", color = IosColors.SystemBlue, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(14.dp),
        containerColor = IosColors.SecondaryGroupedBackground
    )
}
