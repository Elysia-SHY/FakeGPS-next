package com.mockrun.app.feature.mock


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
import com.mockrun.app.core.location.AddressResolver
import com.mockrun.app.core.location.CoordinateConverter
import com.mockrun.app.core.location.FloatingJoystickService
import com.mockrun.app.core.location.KeepAliveHelper
import com.mockrun.app.core.location.RootSuBridge
import com.mockrun.app.ui.components.PermissionGuideDialog
import com.mockrun.app.util.Diag
import com.mockrun.app.util.InjectionMode
import com.mockrun.app.util.InjectionModePrefs
import com.mockrun.app.util.PermissionIssueType
import com.mockrun.app.util.logFailure
import androidx.compose.ui.text.style.TextAlign
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.domain.model.TargetMockMode
import com.mockrun.app.ui.components.AppPickerBottomSheet
import com.mockrun.app.core.designsystem.*
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
    val multiTargetRules by simulationViewModel.multiTargetRules.collectAsState()
    var showAppPickerSheet by remember { mutableStateOf(false) }

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
    var isRootMode by remember {
        mutableStateOf(InjectionModePrefs.isRootMode(context))
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
        isRootMode = InjectionModePrefs.isRootMode(context)
        if (isRootAvailable && isRootMode) {
            rootBridge.grantMockLocation(context.packageName)
        }
    }

    var showHelpSheet by remember { mutableStateOf(false) }
    var showWeChatGuideSheet by remember { mutableStateOf(false) }

    val isLiquidGlass = LocalLiquidGlassEnabled.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AppBackground()

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 140.dp),
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
                .bouncyClickable { showWeChatGuideSheet = true }
                .liquidGlass(
                    isLiquidGlass = isLiquidGlass,
                    shape = RoundedCornerShape(16.dp),
                    elevation = 6.dp,
                    containerColor = IosColors.SystemOrange.copy(alpha = 0.16f)
                ),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent
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
                        text = "Root 一键关闭蓝牙 / Wi‑Fi 与背景扫描，阻止定位反查",
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
                            // 统一走 ViewModel：Root 模式先自动授权再校验，免 Root 模式维持手动勾选校验
                            coroutineScope.launch {
                                val issue = simulationViewModel.resolveInjectionPermission(context)
                                if (issue != PermissionIssueType.NONE) {
                                    permissionIssueDialogType = issue
                                } else {
                                    simulationViewModel.startPointMock(context, activeLat, activeLon)
                                    simulationViewModel.updateSelectedTarget(activeLat, activeLon)
                                    Toast.makeText(context, "虚拟定位已开启！", Toast.LENGTH_SHORT).show()
                                }
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
                    CoordinateConverter.clearSavedRealLocation(context)
                    com.mockrun.app.core.location.MockLocationEngine.forceCleanAllTestProviders(context)
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
        // 2.5 Multi-Target Per-App Virtualization Routing (iOS Inset Group Card)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "多应用独立分流 (${multiTargetRules.size})".uppercase(),
                style = IosTypography.Footnote,
                color = IosColors.SecondaryLabel,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "+ 添加分流应用",
                style = IosTypography.Footnote,
                color = IosColors.SystemBlue,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.bouncyClickable { showAppPickerSheet = true }
            )
        }

        IosInsetGroupCard {
            if (multiTargetRules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "暂无独立分流应用",
                        style = IosTypography.Headline,
                        color = IosColors.Label
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "所有调用定位的应用默认走上方的主控模拟位置。添加应用后可让钉钉、微信等分别驻留不同位置，未添加的应用可选择使用硬件真实定位。",
                        style = IosTypography.Footnote,
                        color = IosColors.SecondaryLabel,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showAppPickerSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+ 添加分流应用", style = IosTypography.Subheadline, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                multiTargetRules.forEachIndexed { index, rule ->
                    if (index > 0) {
                        IosHairlineDivider(startIndent = 58.dp)
                    }
                    MultiTargetRuleItemRow(
                        rule = rule,
                        onToggle = { isChecked ->
                            simulationViewModel.toggleMultiTargetRule(rule.key, isChecked)
                        },
                        onSetLocationOnMap = {
                            simulationViewModel.setActiveTargetKey(rule.key)
                            simulationViewModel.updateSelectedTarget(rule.latitude, rule.longitude)
                            onNavigateToMap()
                        },
                        onModeChange = { newMode ->
                            simulationViewModel.setMultiTargetRuleMode(rule.key, newMode)
                        },
                        onDelete = {
                            simulationViewModel.removeMultiTargetRule(rule.key)
                        }
                    )
                }
            }
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
                subtitle = when {
                    isRootMode && isDevMockLocationEnabled -> "Root 已自动授权 · 本应用为系统模拟位置信息应用"
                    isRootMode -> "Root 模式将自动授权，无需手动勾选"
                    isDevMockLocationEnabled -> "已将本应用勾选为系统模拟位置信息应用"
                    else -> "未勾选 · 点击前往【开发者选项】勾选"
                },
                icon = Icons.Default.Settings,
                iconBackground = if (isDevMockLocationEnabled) IosColors.SystemGreen else IosColors.SystemOrange,
                trailingText = if (isDevMockLocationEnabled) "已勾选" else "未勾选",
                showChevron = !isDevMockLocationEnabled && !isRootMode,
                onClick = {
                    if (isRootMode && isRootAvailable) {
                        coroutineScope.launch {
                            rootBridge.grantMockLocation(context.packageName)
                            isDevMockLocationEnabled = XposedStatusHelper.isMockLocationAppSelected(context)
                            Toast.makeText(context, "Root 已自动配置模拟位置权限", Toast.LENGTH_SHORT).show()
                        }
                    } else {
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
                }
            )

            IosHairlineDivider(startIndent = 58.dp)

            // Item 0.5: Injection Mode Switch (Root vs No-Root)
            IosListRow(
                title = "Root 注入模式",
                subtitle = if (isRootMode) "已开启：自动配置模拟权限与硬件高精度定位" else "免 Root：仅依赖开发者选项模拟位置，不调用 Root",
                icon = Icons.Default.Lock,
                iconBackground = if (isRootMode) IosColors.SystemGreen else IosColors.SystemOrange,
                trailingContent = {
                    IosSwitch(
                        checked = isRootMode,
                        onCheckedChange = { enabled ->
                            isRootMode = enabled
                            InjectionModePrefs.setMode(
                                context,
                                if (enabled) InjectionMode.ROOT else InjectionMode.NO_ROOT
                            )
                            if (enabled && isRootAvailable) {
                                // 切到 Root：立即自动授予模拟权限（含全局开发者选项开关），无需手动打开开发者选项
                                coroutineScope.launch { rootBridge.grantMockLocation(context.packageName) }
                            } else if (!enabled && isRootAvailable) {
                                // 切到免 Root：把硬件恢复为正常高精度，避免定位卡在异常状态
                                coroutineScope.launch { rootBridge.restoreScanningHardware() }
                            }
                        }
                    )
                }
            )

            IosHairlineDivider(startIndent = 58.dp)

            // Item 1: LSPosed
            IosListRow(
                title = "LSPosed 系统框架",
                subtitle = if (isLsposedHookActive) "system_server 框架拦截 · 运行中" else "模块未激活 · 请在 LSPosed 中勾选并重启手机",
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
                subtitle = when {
                    isRootMode && isRootAvailable -> "已授权 · 硬件高精度模式"
                    isRootMode && !isRootAvailable -> "Root 模式已选 · 未检测到 Root 权限"
                    else -> "免 Root 模式 · 仅开发者选项模拟位置"
                },
                icon = Icons.Default.CheckCircle,
                iconBackground = if (isRootMode && isRootAvailable) IosColors.SystemGreen else IosColors.SystemOrange,
                trailingText = when {
                    isRootMode && isRootAvailable -> "已授权"
                    isRootMode && !isRootAvailable -> "未检测到"
                    else -> "已关闭"
                },
                showChevron = true,
                onClick = {
                    if (isRootMode && isRootAvailable) {
                        coroutineScope.launch {
                            rootBridge.restoreScanningHardware()
                            rootBridge.grantMockLocation(context.packageName)
                            Toast.makeText(context, "已配置底层模拟权限与高精度定位", Toast.LENGTH_SHORT).show()
                        }
                    } else if (isRootAvailable) {
                        Toast.makeText(context, "已切换为免 Root 模式，本应用不会调用 Root 注入", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "免 Root 模式下可通过系统设置优化", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            IosHairlineDivider(startIndent = 58.dp)

            // Item 3: Notification
            IosListRow(
                title = "常驻前台通知",
                subtitle = if (areNotificationsEnabled) "前台服务运行中 · 提升后台留存" else "未开启通知保护 (易被系统清理)",
                icon = Icons.Default.Notifications,
                iconBackground = if (areNotificationsEnabled) IosColors.SystemBlue else IosColors.SystemRed,
                trailingText = if (areNotificationsEnabled) "运行中" else "去开启",
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
                trailingText = if (isBatteryIgnoring) "已加入白名单" else "申请豁免",
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
                title = "恢复系统高精度定位",
                subtitle = if (isRootMode && isRootAvailable) "配置模拟权限与 Wi-Fi/蓝牙辅助定位" else "免 Root 模式：可在系统设置中管理扫描与模拟位置",
                icon = Icons.Default.Build,
                iconBackground = if (isRootMode && isRootAvailable) IosColors.SystemGreen else IosColors.SystemBlue,
                trailingText = if (isRootMode && isRootAvailable) "立即配置" else "使用指南",
                showChevron = true,
                onClick = {
                    if (isRootMode && isRootAvailable) {
                        coroutineScope.launch {
                            rootBridge.restoreScanningHardware()
                            rootBridge.grantMockLocation(context.packageName)
                            Toast.makeText(context, "已配置模拟权限与高精度模式", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "免 Root 模式下可通过系统设置优化", Toast.LENGTH_SHORT).show()
                        showHelpSheet = true
                    }
                }
            )
        }

        Text(
            text = "提示：在 LSPosed 管理器中开启模块并勾选【系统框架 (Android)】，由系统底层分发虚拟坐标，减少闪回真实位置现象。",
            style = IosTypography.Footnote,
            color = IosColors.SecondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}

    if (showHelpSheet) {
        UsageGuideDialog(onDismissRequest = { showHelpSheet = false })
    }

    if (showWeChatGuideSheet) {
        WeChatTroubleshootDialog(
            context = context,
            coroutineScope = coroutineScope,
            rootBridge = rootBridge,
            isRootMode = isRootMode,
            isRootAvailable = isRootAvailable,
            onDismissRequest = { showWeChatGuideSheet = false }
        )
    }

    if (showKeepAliveSheet) {
        KeepAliveGuideDialog(
            context = context,
            isIgnoringBattery = isBatteryIgnoring,
            onDismissRequest = { showKeepAliveSheet = false }
        )
    }

    if (showAppPickerSheet) {
        AppPickerBottomSheet(
            onDismissRequest = { showAppPickerSheet = false },
            existingRules = multiTargetRules,
            initialLatitude = activeLat,
            initialLongitude = activeLon,
            onAppSelected = { newRule ->
                simulationViewModel.addOrUpdateMultiTargetRule(newRule)
                Toast.makeText(context, "已为 ${newRule.appName} 添加独立分流规则", Toast.LENGTH_SHORT).show()
            },
            loadInstalledApps = { simulationViewModel.getInstalledUserApps() }
        )
    }

    permissionIssueDialogType?.let { issue ->
        PermissionGuideDialog(
            issueType = issue,
            onDismissRequest = { permissionIssueDialogType = null }
        )
    }
}
