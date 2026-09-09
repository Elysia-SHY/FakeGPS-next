package com.mockrun.app.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.mockrun.app.BuildConfig
import com.mockrun.app.hook.XposedStatusHelper
import com.mockrun.app.location.AddressResolver
import com.mockrun.app.location.CoordinateConverter
import com.mockrun.app.location.FloatingJoystickService
import com.mockrun.app.location.KeepAliveHelper
import com.mockrun.app.location.RootSuBridge
import com.mockrun.app.ui.components.PermissionGuideDialog
import com.mockrun.app.util.PermissionHelper
import com.mockrun.app.util.PermissionIssueType
import com.mockrun.app.ui.theme.*
import com.mockrun.app.ui.viewmodel.SimulationViewModel
import kotlinx.coroutines.launch

@Composable
fun LocationMockScreen(
    simulationViewModel: SimulationViewModel,
    onNavigateToMap: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val rootBridge = remember { RootSuBridge() }

    // State from ViewModel
    val isJoystickRunning by simulationViewModel.isJoystickActive.collectAsState()
    val isPointMockActive by simulationViewModel.isPointMockActive.collectAsState()
    val pointMockLocation by simulationViewModel.pointMockLocation.collectAsState()
    val joystickLocation by simulationViewModel.joystickLocation.collectAsState()
    val selectedTargetLocation by simulationViewModel.selectedTargetLocation.collectAsState()

    var joystickSizeDp by remember { mutableFloatStateOf(140f) }
    var realLocationCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var permissionIssueDialogType by remember { mutableStateOf<PermissionIssueType?>(null) }

    LaunchedEffect(Unit) {
        realLocationCoord = CoordinateConverter.getRealDeviceLocation(context)
    }

    val isMockOn = isPointMockActive || isJoystickRunning

    // Resolved Coordinates
    val activeLat = pointMockLocation?.latitude
        ?: joystickLocation?.latitude
        ?: selectedTargetLocation?.latitude
        ?: realLocationCoord?.first
        ?: 39.9042

    val activeLon = pointMockLocation?.longitude
        ?: joystickLocation?.longitude
        ?: selectedTargetLocation?.longitude
        ?: realLocationCoord?.second
        ?: 116.4074

    var currentAddress by remember { mutableStateOf("正在解析地址...") }

    LaunchedEffect(activeLat, activeLon) {
        currentAddress = AddressResolver.resolveAddress(context, activeLat, activeLon)
    }

    // System Environment & Statuses
    var isRootAvailable by remember { mutableStateOf(false) }
    var isDevMockLocationEnabled by remember {
        mutableStateOf(XposedStatusHelper.isMockLocationAppSelected(context))
    }
    var isLsposedHookActive by remember {
        mutableStateOf(XposedStatusHelper.isLsposedHookReallyActive(context))
    }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isBatteryIgnoring by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }
    var areNotificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var showKeepAliveSheet by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                isDevMockLocationEnabled = XposedStatusHelper.isMockLocationAppSelected(context)
                isLsposedHookActive = XposedStatusHelper.isLsposedHookReallyActive(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                    isBatteryIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }
                CoordinateConverter.getRealDeviceLocation(context)?.let {
                    realLocationCoord = it
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        isRootAvailable = rootBridge.isRootAvailable()
        isDevMockLocationEnabled = XposedStatusHelper.isMockLocationAppSelected(context)
        isLsposedHookActive = XposedStatusHelper.isLsposedHookReallyActive(context)
        if (isRootAvailable) {
            rootBridge.grantMockLocation(context.packageName)
        }
    }

    var showHelpSheet by remember { mutableStateOf(false) }
    var showWeChatGuideSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IosColors.SystemGroupedBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =====================================================================
        // 1. Apple Large Title Navigation Header
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "系统底层虚拟定位与全向漫游",
                    style = IosTypography.Caption1,
                    color = IosColors.SecondaryLabel,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "虚拟定位",
                    style = IosTypography.LargeTitle,
                    color = IosColors.Label
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isMockOn) IosColors.SystemGreen.copy(alpha = 0.15f) else IosColors.SystemGray.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isMockOn) "🟢 模拟生效中" else "⚪ 待机中",
                        color = if (isMockOn) IosColors.SystemGreen else IosColors.SecondaryLabel,
                        style = IosTypography.Caption1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = IosColors.SystemBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        color = IosColors.SystemBlue,
                        style = IosTypography.Caption1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // =====================================================================
        // WeChat / Anti-Detection Troubleshooter Banner
        // =====================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .bouncyClickable { showWeChatGuideSheet = true },
            shape = RoundedCornerShape(14.dp),
            color = IosColors.SystemOrange.copy(alpha = 0.12f),
            border = BorderStroke(0.5.dp, IosColors.SystemOrange.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = IosColors.SystemOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "微信/打卡软件仍显示真实位置？",
                        style = IosTypography.Subheadline,
                        fontWeight = FontWeight.Bold,
                        color = IosColors.SystemOrange
                    )
                    Text(
                        text = "关闭系统 WLAN 扫描 / 检查精确位置与强停微信",
                        style = IosTypography.Caption1,
                        color = IosColors.SecondaryLabel
                    )
                }
                Text(
                    text = "排查解决 ▾",
                    style = IosTypography.Caption1,
                    fontWeight = FontWeight.SemiBold,
                    color = IosColors.SystemOrange
                )
            }
        }

        // =====================================================================
        // 2. Target Location & Primary Master Controller (iOS Inset Group Card)
        // =====================================================================
        IosSectionHeader("目标位置与主控")

        IosInsetGroupCard {
            // Address & Coordinate Detail Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = IosColors.SystemRed
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentAddress,
                        style = IosTypography.Headline,
                        color = IosColors.Label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "WGS-84: ${"%.6f".format(activeLat)}, ${"%.6f".format(activeLon)}",
                        style = IosTypography.Footnote,
                        color = IosColors.SecondaryLabel
                    )
                }
                IconButton(
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cb?.setPrimaryClip(ClipData.newPlainText("Coordinates", "$activeLat, $activeLon"))
                        Toast.makeText(context, "已复制经纬度坐标", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "复制", tint = IosColors.SystemBlue, modifier = Modifier.size(18.dp))
                }
            }

            IosHairlineDivider(startIndent = 16.dp)

            // Master Control Button
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                IosPrimaryButton(
                    text = if (isMockOn) "停止虚拟定位" else "开启虚拟定位",
                    containerColor = if (isMockOn) IosColors.SystemRed else IosColors.SystemBlue,
                    icon = if (isMockOn) Icons.Default.Close else Icons.Default.PlayArrow,
                    onClick = {
                        if (isMockOn) {
                            simulationViewModel.stopPointMock(context)
                            if (isJoystickRunning) {
                                context.stopService(Intent(context, FloatingJoystickService::class.java))
                            }
                            Toast.makeText(context, "已停止虚拟定位", Toast.LENGTH_SHORT).show()
                        } else {
                            val issue = PermissionHelper.checkPrimaryPermissions(context)
                            if (issue != PermissionIssueType.NONE) {
                                permissionIssueDialogType = issue
                            } else {
                                simulationViewModel.startPointMock(context, activeLat, activeLon)
                                simulationViewModel.updateSelectedTarget(activeLat, activeLon)
                                Toast.makeText(context, "虚拟定位已开启！", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            IosHairlineDivider(startIndent = 16.dp)

            // Map Picker Row
            IosListRow(
                title = "地图精准选点",
                subtitle = "在交互式底图上任意点选或手绘连线",
                icon = Icons.Default.LocationOn,
                iconBackground = IosColors.SystemBlue,
                showChevron = true,
                onClick = onNavigateToMap
            )

            IosHairlineDivider(startIndent = 58.dp)

            // Reset to Real Physical Location Row
            IosListRow(
                title = "重置为真机物理位置",
                subtitle = "复位并跳转至地图显示当前真实 GPS 定位",
                icon = Icons.Default.Refresh,
                iconBackground = IosColors.SystemBlue,
                showChevron = true,
                onClick = {
                    if (isPointMockActive) {
                        simulationViewModel.stopPointMock(context)
                    }
                    simulationViewModel.stopSimulation(context)
                    runCatching {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused").forEach { p ->
                            runCatching { lm?.removeTestProvider(p) }
                        }
                    }
                    CoordinateConverter.requestFreshLocation(context) { freshLat, freshLon ->
                        realLocationCoord = freshLat to freshLon
                        simulationViewModel.updateRealPhysicalLocation(freshLat, freshLon)
                    }
                    val real = CoordinateConverter.getRealDeviceLocation(context) ?: realLocationCoord
                    if (real != null) {
                        realLocationCoord = real
                        simulationViewModel.updateRealPhysicalLocation(real.first, real.second)
                        simulationViewModel.updateSelectedTarget(real.first, real.second)
                        Toast.makeText(context, "已重置为真实位置，正在地图中呈现", Toast.LENGTH_SHORT).show()
                        onNavigateToMap()
                    } else {
                        Toast.makeText(context, "未能获取到真机定位，请检查系统定位权限", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // =====================================================================
        // 3. Floating Joystick Controller (iOS Inset Group Card)
        // =====================================================================
        IosSectionHeader("悬浮摇杆漫游")

        IosInsetGroupCard {
            IosListRow(
                title = "桌面万向悬浮摇杆",
                subtitle = "常驻桌面浮窗，八方向实时移动",
                icon = Icons.Default.ThumbUp,
                iconBackground = IosColors.SystemOrange,
                trailingContent = {
                    IosSwitch(
                        checked = isJoystickRunning,
                        onCheckedChange = { checked ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                if (checked) {
                                    val intent = Intent(context, FloatingJoystickService::class.java)
                                    ContextCompat.startForegroundService(context, intent)
                                    Toast.makeText(context, "万向悬浮摇杆已启动", Toast.LENGTH_SHORT).show()
                                } else {
                                    context.stopService(Intent(context, FloatingJoystickService::class.java))
                                    Toast.makeText(context, "万向悬浮摇杆已关闭", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            )

            AnimatedVisibility(visible = isJoystickRunning) {
                Column {
                    IosHairlineDivider(startIndent = 58.dp)
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("摇杆盘体尺寸", style = IosTypography.Body, color = IosColors.Label)
                            Text(
                                text = "${joystickSizeDp.toInt()} dp",
                                style = IosTypography.Headline,
                                color = IosColors.SystemBlue
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = joystickSizeDp,
                            onValueChange = {
                                joystickSizeDp = it
                                simulationViewModel.setJoystickSize(context, it.toInt())
                            },
                            valueRange = 80f..220f,
                            colors = SliderDefaults.colors(thumbColor = IosColors.SystemBlue, activeTrackColor = IosColors.SystemBlue)
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 4. System Shield & Health Matrix (iOS Inset Grouped Table)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "系统防护与保活状态".uppercase(),
                style = IosTypography.Footnote,
                color = IosColors.SecondaryLabel,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "使用指南",
                style = IosTypography.Footnote,
                color = IosColors.SystemBlue,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.bouncyClickable { showHelpSheet = true }
            )
        }

        IosInsetGroupCard {
            // Item 0: Developer Options Mock Location App Check
            IosListRow(
                title = "开发者选项模拟位置",
                subtitle = if (isDevMockLocationEnabled) "已将本应用勾选为系统模拟位置信息应用" else "未勾选 · 点击前往【开发者选项】勾选",
                icon = Icons.Default.Settings,
                iconBackground = if (isDevMockLocationEnabled) IosColors.SystemGreen else IosColors.SystemOrange,
                trailingText = if (isDevMockLocationEnabled) "已勾选" else "未勾选",
                showChevron = !isDevMockLocationEnabled,
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (ex: Exception) {
                            Toast.makeText(context, "请在系统设置中开启开发者选项并选择模拟位置应用", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )

            IosHairlineDivider(startIndent = 58.dp)

            // Item 1: LSPosed
            IosListRow(
                title = "LSPosed 系统框架",
                subtitle = if (isLsposedHookActive) "system_server 核心拦截 · 0 闪回" else "模块未激活 · 请在 LSPosed 中勾选并重启手机",
                icon = Icons.Default.Build,
                iconBackground = if (isLsposedHookActive) IosColors.SystemPurple else IosColors.SystemGray,
                trailingText = if (isLsposedHookActive) "已接管系统框架" else "未勾选",
                showChevron = true,
                onClick = {
                    val lsposedIntent = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
                    if (lsposedIntent != null) {
                        context.startActivity(lsposedIntent)
                    } else {
                        Toast.makeText(context, "请在 LSPosed 管理器中勾选本模块与【系统框架】并软重启", Toast.LENGTH_LONG).show()
                    }
                }
            )

            IosHairlineDivider(startIndent = 58.dp)

            // Item 2: Root
            IosListRow(
                title = "Root 权限状态",
                subtitle = if (isRootAvailable) "底层打通 · 硬件探针已压制" else "免 Root 模式",
                icon = Icons.Default.CheckCircle,
                iconBackground = if (isRootAvailable) IosColors.SystemGreen else IosColors.SystemOrange,
                trailingText = if (isRootAvailable) "已授权底层打通" else "未检测到",
                showChevron = true,
                onClick = {
                    if (isRootAvailable) {
                        coroutineScope.launch {
                            rootBridge.disableScanningHardware()
                            rootBridge.grantMockLocation(context.packageName)
                            Toast.makeText(context, "⚡ 已优化硬件探针与底层模拟权限", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "免 Root 模式下可通过系统设置优化", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            IosHairlineDivider(startIndent = 58.dp)

            // Item 3: Notification
            IosListRow(
                title = "常驻前台通知",
                subtitle = if (areNotificationsEnabled) "服务前台守护中 · 杜绝后台杀进程" else "未开启通知保护 (易被查杀)",
                icon = Icons.Default.Notifications,
                iconBackground = if (areNotificationsEnabled) IosColors.SystemBlue else IosColors.SystemRed,
                trailingText = if (areNotificationsEnabled) "保活运行中" else "去开启",
                showChevron = !areNotificationsEnabled,
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            )

            IosHairlineDivider(startIndent = 58.dp)

            // Item 4: Battery Optimization
            IosListRow(
                title = "电池优化策略",
                subtitle = if (isBatteryIgnoring) "已豁免系统省电策略，保障持续注入" else "建议加入白名单避免被系统挂起",
                icon = Icons.Default.Favorite,
                iconBackground = if (isBatteryIgnoring) IosColors.SystemGreen else IosColors.SystemYellow,
                trailingText = if (isBatteryIgnoring) "已无限制后台" else "申请豁免",
                showChevron = !isBatteryIgnoring,
                onClick = {
                    KeepAliveHelper.requestIgnoreBatteryOptimization(context)
                }
            )
        }

        // Background Keep-Alive & Anti-Kill Management
        Text(
            text = "后台保活与防杀配置 · ${KeepAliveHelper.getDeviceBrandName()}".uppercase(),
            style = IosTypography.Footnote,
            color = IosColors.SecondaryLabel,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        IosInsetGroupCard {
            IosListRow(
                title = "应用自启动管理",
                subtitle = "跳转当前系统启动管理，允许 Fake GPS 自启动与关联启动",
                icon = Icons.Default.PlayArrow,
                iconBackground = IosColors.SystemIndigo,
                trailingText = "去设置",
                showChevron = true,
                onClick = {
                    val opened = KeepAliveHelper.openAutoStartSettings(context)
                    if (!opened) {
                        Toast.makeText(context, "请在系统设置中允许 Fake GPS 自启动", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            IosHairlineDivider(startIndent = 58.dp)

            IosListRow(
                title = "多任务卡片加锁保护",
                subtitle = "防止在多任务列表划掉或一键清理时被系统误杀",
                icon = Icons.Default.Lock,
                iconBackground = IosColors.SystemTeal,
                trailingText = "查看指南",
                showChevron = true,
                onClick = {
                    showKeepAliveSheet = true
                }
            )

            IosHairlineDivider(startIndent = 58.dp)

            IosListRow(
                title = "系统应用详情与权限",
                subtitle = "管理悬浮窗、通知与后台高耗电权限",
                icon = Icons.Default.Settings,
                iconBackground = IosColors.SystemGray,
                trailingText = "打开设置",
                showChevron = true,
                onClick = {
                    KeepAliveHelper.openAppDetailsSettings(context)
                }
            )
        }

        // Root One-Click Anti-Flashback Optimization
        IosInsetGroupCard {
            IosListRow(
                title = "一键免闪回优化 (关闭 Wi-Fi 硬件探针)",
                subtitle = if (isRootAvailable) "掐死 Wi-Fi/蓝牙位置扫描探针并注入模拟权限" else "免 Root 模式：可在系统设置中关闭 Wi-Fi 扫描",
                icon = Icons.Default.Build,
                iconBackground = if (isRootAvailable) IosColors.SystemGreen else IosColors.SystemBlue,
                trailingText = if (isRootAvailable) "立即优化" else "使用指南",
                showChevron = true,
                onClick = {
                    if (isRootAvailable) {
                        coroutineScope.launch {
                            rootBridge.disableScanningHardware()
                            rootBridge.grantMockLocation(context.packageName)
                            Toast.makeText(context, "⚡ 已掐死硬件探针并注入模拟权限", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "免 Root 模式下可通过系统设置优化", Toast.LENGTH_SHORT).show()
                        showHelpSheet = true
                    }
                }
            )
        }

        Text(
            text = "提示：在 LSPosed 管理器中开启模块并勾选【系统框架 (Android)】，即可实现全机所有应用程序无感分发原生虚拟坐标，绝无闪回真实地址现象。",
            style = IosTypography.Footnote,
            color = IosColors.SecondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
    }

    // iOS Style Guide Dialog: 彻底防闪回与使用指南
    if (showHelpSheet) {
        AlertDialog(
            onDismissRequest = { showHelpSheet = false },
            title = {
                Text(
                    text = "🛡️ 彻底防闪回与使用指南",
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
                            "【作用域中勾选【系统框架】并软重启】",
                            style = IosTypography.Headline,
                            color = IosColors.SystemBlue
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "全机所有应用将由系统底层分发虚拟坐标，免除逐个勾选且无法被应用检测。",
                            style = IosTypography.Callout,
                            color = IosColors.SecondaryLabel
                        )
                    }

                    Column {
                        Text(
                            "【关闭系统硬件探针】",
                            style = IosTypography.Headline,
                            color = IosColors.SystemOrange
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "在系统设置中搜索“扫描”，关闭“Wi-Fi扫描”与“蓝牙扫描”，彻底切断高德、微信、外卖软件通过周边邻居路由器 BSSID 反推物理位置的途径。",
                            style = IosTypography.Callout,
                            color = IosColors.SecondaryLabel
                        )
                    }

                    Column {
                        Text(
                            "【前台通知与电池无限制】",
                            style = IosTypography.Headline,
                            color = IosColors.SystemGreen
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "保持前台常驻通知开启并将电池策略设为无限制，防止被各大手机厂商后台清理机制误杀。",
                            style = IosTypography.Callout,
                            color = IosColors.SecondaryLabel
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpSheet = false }) {
                    Text("我知道了", color = IosColors.SystemBlue, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(14.dp),
            containerColor = Color.White
        )
    }

    // WeChat & Anti-Real-Location Troubleshooting Dialog
    if (showWeChatGuideSheet) {
        AlertDialog(
            onDismissRequest = { showWeChatGuideSheet = false },
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
                        Text("【免 ROOT 模式：4 步解决方案】", style = IosTypography.Headline, color = IosColors.SystemBlue)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "① 关闭系统 WLAN 扫描：进入系统设置，彻底关闭「WLAN 扫描」与「蓝牙扫描」（切断后台路由器 MAC 探针）。\n" +
                            "② 检查微信权限：系统设置 -> 微信应用信息 -> 权限 -> 位置信息 -> 务必选【精确位置】（非大致位置）。\n" +
                            "③ 临时改用移动流量：断开家用 Wi-Fi，改用手机蜂窝网络。\n" +
                            "④ 强行停止微信：开启 FakeGPS 虚拟定位后，进入手机设置点击微信的【强行停止】清除物理缓存，再重新打开微信即可！",
                            style = IosTypography.Callout,
                            color = IosColors.SecondaryLabel
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }.onFailure {
                                    Toast.makeText(context, "请在系统设置中搜索「扫描」", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("关闭扫描", style = IosTypography.Caption1, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:com.tencent.mm")
                                    }
                                    context.startActivity(intent)
                                }.onFailure {
                                    Toast.makeText(context, "未能直接打开微信应用信息", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("微信强停/权限", style = IosTypography.Caption1, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isRootAvailable) {
                        Column {
                            Text("【ROOT / LSPosed 模式建议】", style = IosTypography.Headline, color = IosColors.SystemPurple)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "① 打开 LSPosed 管理器，在 FakeGPS 模块的作用域中务必勾选【微信 (com.tencent.mm)】与【系统框架】；\n" +
                                "② 点击下方一键通过 Root 禁用底层硬件扫描：",
                                style = IosTypography.Callout,
                                color = IosColors.SecondaryLabel
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val ok = rootBridge.disableScanningHardware()
                                        if (ok) {
                                            Toast.makeText(context, "已通过 Root 禁用硬件扫描探针", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Root 命令执行未完成", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemPurple),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🛡️ ROOT 一键关闭硬件扫描", style = IosTypography.Caption1, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeChatGuideSheet = false }) {
                    Text("我知道了", color = IosColors.SystemBlue, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(14.dp),
            containerColor = Color.White
        )
    }

    // Keep-Alive & Recents Lock Guide Dialog
    if (showKeepAliveSheet) {
        AlertDialog(
            onDismissRequest = { showKeepAliveSheet = false },
            title = {
                Text(
                    text = "🔒 多任务加锁与防杀指南",
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "识别到当前机型：${KeepAliveHelper.getDeviceBrandName()}",
                        style = IosTypography.Subheadline,
                        color = IosColors.SystemBlue,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column {
                        Text(
                            "① 多任务列表卡片加锁（核心必做）：",
                            style = IosTypography.Headline,
                            color = IosColors.Label
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            KeepAliveHelper.getRecentsLockGuide(),
                            style = IosTypography.Callout,
                            color = IosColors.SecondaryLabel
                        )
                    }

                    Column {
                        Text(
                            "② 电池省电策略设为【无限制】：",
                            style = IosTypography.Headline,
                            color = IosColors.Label
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "部分系统锁屏后会进入深度 Doze 休眠冻结 CPU。请在电池设置中将 Fake GPS 设为「无限制」，不限制后台活动。",
                            style = IosTypography.Callout,
                            color = IosColors.SecondaryLabel
                        )
                    }

                    Column {
                        Text(
                            "③ 允许自启动与关联启动：",
                            style = IosTypography.Headline,
                            color = IosColors.Label
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "确保手机管家未拦截 Fake GPS 的后台常驻与快速自愈复活广播。",
                            style = IosTypography.Callout,
                            color = IosColors.SecondaryLabel
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKeepAliveSheet = false }) {
                    Text("我知道了", color = IosColors.SystemBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showKeepAliveSheet = false
                    KeepAliveHelper.openAutoStartSettings(context)
                }) {
                    Text("前往设置", color = IosColors.SecondaryLabel)
                }
            },
            shape = RoundedCornerShape(14.dp),
            containerColor = Color.White
        )
    }

    permissionIssueDialogType?.let { issue ->
        PermissionGuideDialog(
            issueType = issue,
            onDismissRequest = { permissionIssueDialogType = null }
        )
    }
}
