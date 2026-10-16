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
import com.mockrun.app.core.location.fingerprint.FingerprintCoordinator
import com.mockrun.app.core.location.fingerprint.FingerprintStore
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

    // 环境指纹状态
    //
    // 原来 activeFingerprintId 只 remember(null)，页面切走再回来就显示"未启用"，
    // 但 hook 侧发布的 id 其实还在 —— 界面状态与实际回放状态不一致。
    // 现在启动时从 Coordinator 读回真实发布的 id。
    var fingerprintIds by remember { mutableStateOf(FingerprintStore.list(context)) }
    var fingerprintSummaries by remember { mutableStateOf(emptyMap<String, String>()) }
    var activeFingerprintId by remember {
        mutableStateOf(FingerprintCoordinator.readPublishedId(context).takeIf { it.isNotBlank() })
    }
    var isCapturingFingerprint by remember { mutableStateOf(false) }
    var showFingerprintCaptureDialog by remember { mutableStateOf(false) }
    // 采集结果反馈：失败/部分成功不能只闪一个 Toast 就消失
    var captureResult by remember { mutableStateOf<CaptureFeedback?>(null) }
    // 待确认删除的指纹 id（删除是不可逆操作，原实现点一下就删）
    var pendingDeleteFingerprintId by remember { mutableStateOf<String?>(null) }

    // 用 fingerprintIds + 一个自增的刷新计数当 key。
    //
    // 原实现只用 fingerprintIds 当 key：同名覆盖后文件名列表完全相等，
    // Compose 的结构性相等判断不触发重组，列表里会一直显示旧摘要，
    // 看起来像"采了没反应"。加 reloadToken 后每次采集成功都强制重载。
    var reloadToken by remember { mutableIntStateOf(0) }

    // 指纹详情（时间、坐标）与摘要一起在副作用里加载。
    // 不在 forEach 里用 remember 读文件：列表变化时 remember 的槽位会错位，
    // 显示的可能不是当前这一行的指纹。
    var fingerprintDetails by remember {
        mutableStateOf(emptyMap<String, com.mockrun.app.core.location.fingerprint.EnvironmentFingerprint>())
    }

    LaunchedEffect(fingerprintIds, reloadToken) {
        val loaded = fingerprintIds.associateWith { fid ->
            FingerprintStore.load(context, fid)
        }
        fingerprintSummaries = loaded.mapValues { (_, fp) ->
            fp?.let { FingerprintStore.summary(it) } ?: ""
        }
        fingerprintDetails = loaded.filterValues { it != null }
            .mapValues { (_, fp) -> fp!! }
        // 列表刷新后同步一次真实的启用状态，避免显示与实际回放不符
        activeFingerprintId = FingerprintCoordinator.readPublishedId(context)
            .takeIf { it.isNotBlank() && it in fingerprintIds }
    }

    // 初值必须与 FloatingJoystickService 的默认值一致（130 dp）。
    // 原实现写死 140f，导致界面显示「140 dp」而悬浮窗实际渲染 130 dp ——
    // 两者可见地不一致，且只有用户拖动滑块后才会同步。
    // 这里直接读该服务使用的同一份 SharedPreferences，避免再写一份常量。
    var joystickSizeDp by remember {
        mutableFloatStateOf(
            context.getSharedPreferences(
                FloatingJoystickService.PREFS_NAME, Context.MODE_PRIVATE
            ).getInt(FloatingJoystickService.KEY_JOYSTICK_SIZE, 130)
                .coerceIn(80, 240)
                .toFloat()
        )
    }
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

    // 当前主控坐标的来源，供采集对话框提示用户"这个默认值是不是你实际站的位置"。
    // 采集坐标是回放时重标定 RSSI 的输入，默认值取错会让整包指纹失真。
    val activeCoordSourceLabel = when {
        pointMockLocation != null -> "主控虚拟点"
        joystickLocation != null -> "摇杆漫游点"
        selectedTargetLocation != null -> "地图选定点"
        realLocationCoord != null -> "真机物理定位"
        else -> "默认坐标（未获取到任何定位）"
    }
    // 同上，落库时用枚举名而不是中文短语，便于回放端与后续分析按值判断。
    val coordSourceTag = when {
        pointMockLocation != null -> "MOCK_TARGET"
        joystickLocation != null -> "MOCK_TARGET"
        selectedTargetLocation != null -> "MOCK_TARGET"
        realLocationCoord != null -> "DEVICE"
        else -> "MANUAL"
    }

    // 环境指纹采集专用坐标（实地采集优先直取真机物理真实定位，杜绝误用选定模拟点）
    val captureRealCoord = realLocationCoord ?: CoordinateConverter.getRealDeviceLocation(context)
    val captureDefaultLat = captureRealCoord?.first ?: activeLat
    val captureDefaultLon = captureRealCoord?.second ?: activeLon
    val captureCoordSourceLabel = when {
        captureRealCoord != null -> "真机物理定位"
        pointMockLocation != null -> "主控虚拟点"
        joystickLocation != null -> "摇杆漫游点"
        selectedTargetLocation != null -> "地图选定点"
        else -> "默认坐标（未获取到任何定位）"
    }

    val onTriggerFingerprintCapture: () -> Unit = {
        // 打开弹窗前优先抓取最新物理硬件定位并触发刷新
        val fresh = CoordinateConverter.getRealDeviceLocation(context)
        if (fresh != null) {
            realLocationCoord = fresh
        }
        CoordinateConverter.requestFreshLocation(context) { freshLat, freshLon ->
            realLocationCoord = freshLat to freshLon
        }
        showFingerprintCaptureDialog = true
    }

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
        isRootMode = InjectionModePrefs.validateOrDowngrade(context, isRootAvailable) == InjectionMode.ROOT
        isDevMockLocationEnabled = XposedStatusHelper.isMockLocationAppSelected(context)
        isLsposedHookActive = XposedStatusHelper.isLsposedHookReallyActive(context)
        if (isRootAvailable && isRootMode) {
            rootBridge.grantMockLocation(context.packageName)
        }
    }

    var showHelpSheet by remember { mutableStateOf(false) }
    var showWeChatGuideSheet by remember { mutableStateOf(false) }

    val isLiquidGlass = LocalLiquidGlassEnabled.current

    // 本页专属模糊源：AppBackground 铺底当源层，卡片玻璃采样它 ——
    // 两者是同一个 Box 下的兄弟节点，不会自引用。
    val pageHaze = remember { dev.chrisbanes.haze.HazeState() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AppBackground(hazeState = pageHaze)

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = ScreenInsets.bottomInset(extra = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // =====================================================================
        // 1. 页面头（设计稿 .page-head：kicker + 30/800 大标题 + 状态徽标 + 版本芯片）
        // =====================================================================
        IosPageHead(
            kicker = "系统底层虚拟定位与全向漫游",
            title = "虚拟定位",
            badgeText = if (isMockOn) "模拟生效中" else "待机中",
            badgeRunning = isMockOn,
            chipText = BuildConfig.VERSION_NAME
        )

        // =====================================================================
        // 反检测排查入口（原为常显橙渐变警告条，压过主控操作）。
        //
        // 现在默认折叠为一行次级入口：日常操作（选点、开关虚拟定位）不再被
        // 橙色警示抢占视觉重心，遇到「仍显示真实位置」时点开即可看到完整说明。
        // 功能与文案一字未删，只是默认收起。
        // =====================================================================
        var showTroubleshootHint by remember { mutableStateOf(false) }

        IosInsetGroupCard(hazeState = pageHaze) {
            IosListRow(
                title = "微信/打卡软件仍显示真实位置？",
                subtitle = "Root 一键关闭蓝牙 / Wi‑Fi 与背景扫描，阻止定位反查",
                icon = Icons.Default.Info,
                iconBackground = IosColors.SystemOrange,
                trailingText = if (showTroubleshootHint) "收起" else "排查",
                showChevron = !showTroubleshootHint,
                onClick = { showTroubleshootHint = !showTroubleshootHint }
            )

            AnimatedVisibility(visible = showTroubleshootHint) {
                Column {
                    IosHairlineDivider(startIndent = 58.dp)
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "若目标应用仍读取到真实位置，通常是系统级 Wi‑Fi / 蓝牙背景扫描" +
                                "仍在回传真实周边环境。可在下方「系统防护与保活状态」中开启 " +
                                "Root 注入模式，由系统底层关闭扫描并分发虚拟坐标。",
                            style = IosTypography.Footnote,
                            color = IosColors.SecondaryLabel
                        )
                        Spacer(Modifier.height(10.dp))
                        IosPrimaryButton(
                            text = "查看完整排查指南",
                            containerColor = IosColors.SystemOrange,
                            icon = Icons.Default.Build,
                            onClick = { showWeChatGuideSheet = true }
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 2. Target Location & Primary Master Controller (iOS Inset Group Card)
        // =====================================================================
        IosSectionHeader("目标位置与主控")

        IosInsetGroupCard(hazeState = pageHaze) {
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
                        style = IosTypography.Mono13,
                        color = IosColors.SecondaryLabel
                    )
                    Spacer(Modifier.height(2.dp))
                    // 标明这个坐标从哪来。它不是装饰性元数据 —— 环境指纹回放时
                    // 用它重标定 RSSI，用户必须知道当前值是自己选的点还是真机位置。
                    Text(
                        text = when {
                            pointMockLocation != null -> "来源：主控虚拟点"
                            joystickLocation != null -> "来源：摇杆漫游点"
                            selectedTargetLocation != null -> "来源：地图选定点"
                            realLocationCoord != null -> "来源：真机物理定位"
                            else -> "来源：默认坐标（未获取到定位）"
                        },
                        style = IosTypography.Caption2,
                        color = if (realLocationCoord == null && pointMockLocation == null &&
                            joystickLocation == null && selectedTargetLocation == null
                        ) {
                            IosColors.SystemOrange
                        } else {
                            IosColors.TertiaryLabel
                        }
                    )
                }
                IconButton(
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cb?.setPrimaryClip(ClipData.newPlainText("Coordinates", "$activeLat, $activeLon"))
                        Toast.makeText(context, "已复制经纬度坐标", Toast.LENGTH_SHORT).show()
                    },
                    // 原为 30dp，低于 48dp 最小触区
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "复制坐标", tint = IosColors.SystemBlue, modifier = Modifier.size(18.dp))
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
        // 2.5 环境指纹（实地采集 Wi-Fi / 基站 / 蓝牙，之后在虚拟点回放）
        // =====================================================================
        IosGroupHeader(
            title = "环境指纹",
            count = fingerprintIds.size.takeIf { it > 0 },
            linkText = "实地采集",
            onLinkClick = onTriggerFingerprintCapture
        )

        IosInsetGroupCard(hazeState = pageHaze) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "在目标地点实地采集周边的 Wi-Fi、基站、蓝牙快照，" +
                        "之后在这些地点启用虚拟定位时按距离回放，" +
                        "让周边环境与虚拟坐标自洽。",
                    style = IosTypography.Footnote,
                    color = IosColors.SecondaryLabel
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "采集时请先关闭虚拟定位，并站在目标地点。",
                    style = IosTypography.Caption1,
                    color = IosColors.SystemOrange
                )

                Spacer(Modifier.height(12.dp))

                IosPrimaryButton(
                    text = "采集当前地点",
                    containerColor = IosColors.SystemBlue,
                    icon = Icons.Filled.Wifi,
                    loading = isCapturingFingerprint,
                    loadingText = "采集中…",
                    onClick = onTriggerFingerprintCapture
                )

                // 采集结果反馈：失败或部分成功必须留在页面上，
                // 不能只闪一个 Toast —— 用户看不到原因就无法判断该怎么办。
                captureResult?.let { result ->
                    Spacer(Modifier.height(12.dp))
                    FingerprintResultCard(
                        result = result,
                        onDismiss = { captureResult = null },
                        onRetry = {
                            captureResult = null
                            onTriggerFingerprintCapture()
                        }
                    )
                }

                if (fingerprintIds.isEmpty()) {
                    // 原实现只在非空时渲染列表，没有指纹时页面上只有一段说明，
                    // 用户看不出"还没有采集过"还是"采集失败了"。
                    Spacer(Modifier.height(14.dp))
                    IosHairlineDivider(startIndent = 0.dp)
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "还没有采集过环境指纹",
                            style = IosTypography.Headline,
                            color = IosColors.Label
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "站在目标地点、关闭虚拟定位后点上方按钮开始采集。",
                            style = IosTypography.Footnote,
                            color = IosColors.SecondaryLabel,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    IosHairlineDivider(startIndent = 0.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "已保存 (${fingerprintIds.size})".uppercase(),
                        style = IosTypography.Caption1,
                        color = IosColors.SecondaryLabel,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))

                    fingerprintIds.forEach { fid ->
                        val isActiveFp = fid == activeFingerprintId
                        val detail = fingerprintDetails[fid]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isActiveFp) IosColors.SystemBlue.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fid,
                                        style = IosTypography.Footnote,
                                        color = IosColors.Label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // 采集时间与坐标让用户能认出"这是哪个地点采的"。
                                    // 坐标是回放时重标定 RSSI 的输入，不是装饰元数据。
                                    detail?.let { fp ->
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = formatCapturedAt(fp.capturedAt) +
                                                " · ${"%.5f".format(fp.originLat)}, ${"%.5f".format(fp.originLon)}",
                                            style = IosTypography.Mono13,
                                            color = IosColors.TertiaryLabel,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    fingerprintSummaries[fid]?.takeIf { it.isNotBlank() }?.let {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = it,
                                            style = IosTypography.Caption2,
                                            color = IosColors.SecondaryLabel
                                        )
                                    }
                                }

                                if (isActiveFp) {
                                    IosStatusBadge(
                                        text = "已启用",
                                        running = true,
                                        tone = IosBadgeTone.RUNNING
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 「已启用」不再是一个既能看状态又能反向停用的按钮，
                                // 状态归状态徽章，动作归动作按钮，避免误读与误触。
                                IosGhostButton(
                                    text = if (isActiveFp) "停用" else "启用",
                                    modifier = Modifier.weight(1f),
                                    height = 48.dp,
                                    onClick = {
                                        if (isActiveFp) {
                                            FingerprintCoordinator.deactivate(context)
                                            activeFingerprintId = null
                                            Toast.makeText(context, "已停用环境指纹", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val ok = FingerprintCoordinator.activate(context, fid)
                                            activeFingerprintId = if (ok) fid else null
                                            Toast.makeText(
                                                context,
                                                if (ok) "已启用 $fid"
                                                else "导出失败：需要 root 写入 /data/local/tmp",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )

                                IosGhostButton(
                                    text = "删除",
                                    contentColor = IosColors.SystemRed,
                                    modifier = Modifier.weight(1f),
                                    height = 48.dp,
                                    onClick = {
                                        pendingDeleteFingerprintId = fid
                                    }
                                )
                            }
                        }

                        if (fid != fingerprintIds.last()) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // =====================================================================
        // 3. Multi-Target Per-App Virtualization Routing (iOS Inset Group Card)
        // =====================================================================
        IosGroupHeader(
            title = "多应用独立分流",
            count = multiTargetRules.size,
            linkText = "添加分流应用",
            onLinkClick = { showAppPickerSheet = true }
        )

        IosInsetGroupCard(hazeState = pageHaze) {
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
                        shape = RoundedCornerShape(13.dp)
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

        IosInsetGroupCard(hazeState = pageHaze) {
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
                            // 与服务端 FloatingJoystickService 的 coerceIn(80, 240) 对齐。
                            // 原上限 220 会让存过 240 的用户在界面上被卡在 220，拖不回原值。
                            valueRange = 80f..240f,
                            colors = SliderDefaults.colors(thumbColor = IosColors.SystemBlue, activeTrackColor = IosColors.SystemBlue)
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 4. System Shield & Health Matrix (iOS Inset Grouped Table)
        // =====================================================================
        IosGroupHeader(
            title = "系统防护与保活状态",
            linkText = "使用指南",
            onLinkClick = { showHelpSheet = true },
            // 「使用指南」不是新增动作，去掉默认的加号图标
            linkIcon = null
        )

        IosInsetGroupCard(hazeState = pageHaze) {
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
                            if (enabled) {
                                coroutineScope.launch {
                                    val hasRoot = rootBridge.isRootAvailable()
                                    if (!hasRoot) {
                                        isRootAvailable = false
                                        isRootMode = false
                                        InjectionModePrefs.setMode(context, InjectionMode.NO_ROOT)
                                        Toast.makeText(context, "未检测到 Root 权限，无法开启 Root 注入模式", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isRootAvailable = true
                                        isRootMode = true
                                        InjectionModePrefs.setMode(context, InjectionMode.ROOT)
                                        rootBridge.grantMockLocation(context.packageName)
                                        Toast.makeText(context, "已开启 Root 注入模式并自动配置权限", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                isRootMode = false
                                InjectionModePrefs.setMode(context, InjectionMode.NO_ROOT)
                                if (isRootAvailable) {
                                    coroutineScope.launch { rootBridge.restoreScanningHardware() }
                                }
                                Toast.makeText(context, "已切换为免 Root 注入模式", Toast.LENGTH_SHORT).show()
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
            // 注意：这里只能证明「通知权限已允许」，不能证明前台服务正在运行。
            // 原文案用「运行中」把权限状态说成了服务状态，属于过度承诺，已改正。
            IosListRow(
                title = "常驻前台通知",
                subtitle = if (areNotificationsEnabled) "通知已允许 · 可提升后台留存" else "未开启通知保护 (易被系统清理)",
                icon = Icons.Default.Notifications,
                iconBackground = if (areNotificationsEnabled) IosColors.SystemBlue else IosColors.SystemRed,
                trailingText = if (areNotificationsEnabled) "已允许" else "去开启",
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
        IosInsetGroupCard(hazeState = pageHaze) {
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
        IosInsetGroupCard(hazeState = pageHaze) {
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

    if (showFingerprintCaptureDialog) {
        FingerprintCaptureDialog(
            defaultLat = captureDefaultLat,
            defaultLon = captureDefaultLon,
            defaultCoordSource = captureCoordSourceLabel,
            // 污染路径不止单点/摇杆：多应用分流规则可以独立于主控生效，
            // 同样会让采集读到模块自己伪造的环境。任一处于启用即警告。
            isMockActive = isMockOn || multiTargetRules.any { it.isEnabled },
            onRequestRealLocation = {
                val fresh = CoordinateConverter.getRealDeviceLocation(context)
                if (fresh != null) {
                    realLocationCoord = fresh
                    fresh
                } else {
                    realLocationCoord
                }
            },
            onStopMock = {
                if (isPointMockActive) {
                    simulationViewModel.stopPointMock(context)
                }
                simulationViewModel.stopSimulation(context)
                CoordinateConverter.clearSavedRealLocation(context)
                com.mockrun.app.core.location.MockLocationEngine.forceCleanAllTestProviders(context)
                val fresh = CoordinateConverter.getRealDeviceLocation(context)
                if (fresh != null) {
                    realLocationCoord = fresh
                }
                CoordinateConverter.requestFreshLocation(context) { freshLat, freshLon ->
                    realLocationCoord = freshLat to freshLon
                    simulationViewModel.updateRealPhysicalLocation(freshLat, freshLon)
                }
            },
            onDismissRequest = { showFingerprintCaptureDialog = false },
            onConfirm = { name, lat, lon ->
                showFingerprintCaptureDialog = false
                coroutineScope.launch {
                    isCapturingFingerprint = true
                    captureResult = null
                    runCatching {
                        // 精准判定坐标来源：优先对比真机真实物理定位，避免将真机物理位置误标为虚拟点
                        val finalSourceTag = if (realLocationCoord != null &&
                            Math.abs(lat - realLocationCoord!!.first) < 0.005 &&
                            Math.abs(lon - realLocationCoord!!.second) < 0.005
                        ) {
                            "DEVICE"
                        } else if ((pointMockLocation != null && Math.abs(lat - pointMockLocation!!.latitude) < 0.0001 && Math.abs(lon - pointMockLocation!!.longitude) < 0.0001) ||
                            (selectedTargetLocation != null && Math.abs(lat - selectedTargetLocation!!.latitude) < 0.0001 && Math.abs(lon - selectedTargetLocation!!.longitude) < 0.0001)
                        ) {
                            "MOCK_TARGET"
                        } else {
                            "MANUAL"
                        }
                        FingerprintCoordinator.captureAndSave(
                            context, name, lat, lon,
                            originSource = finalSourceTag
                        )
                    }.onSuccess { outcome ->
                        // 采集完成 ≠ 采集成功。原来的实现只要不抛异常就报"已采集"，
                        // 但保存失败、四类数据全空、扫描被系统限流都可能是"完成"的。
                        captureResult = CaptureFeedback.from(outcome, name)
                        // 强制重载列表：同名覆盖时文件名列表不变，
                        // 只靠 fingerprintIds 当 key 不会触发摘要刷新。
                        fingerprintIds = FingerprintStore.list(context)
                        reloadToken++
                    }.onFailure { e ->
                        Diag.w("LocationMockScreen", "fingerprint capture failed", e)
                        captureResult = CaptureFeedback(
                            id = name,
                            severity = FeedbackSeverity.ERROR,
                            title = "采集失败",
                            detail = e.message ?: "采集过程抛出异常，未产生指纹文件。",
                            saved = false
                        )
                    }
                    isCapturingFingerprint = false
                }
            }
        )
    }

    // 删除确认：原实现点一下"删除"就立即抹掉文件，且没有撤销。
    pendingDeleteFingerprintId?.let { fid ->
        AlertDialog(
            onDismissRequest = { pendingDeleteFingerprintId = null },
            title = { Text("删除环境指纹", style = IosTypography.Headline) },
            text = {
                Text(
                    text = "确定删除「$fid」吗？删除后该地点的 Wi-Fi / 基站 / 蓝牙快照" +
                        "会一并移除，无法撤销。",
                    style = IosTypography.Footnote,
                    color = IosColors.SecondaryLabel
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    FingerprintStore.delete(context, fid)
                    if (activeFingerprintId == fid) {
                        FingerprintCoordinator.deactivate(context)
                        activeFingerprintId = null
                    }
                    fingerprintIds = FingerprintStore.list(context)
                    fingerprintSummaries = fingerprintSummaries - fid
                    if (captureResult?.id == fid) captureResult = null
                    pendingDeleteFingerprintId = null
                    Toast.makeText(context, "已删除 $fid", Toast.LENGTH_SHORT).show()
                }) {
                    Text("删除", color = IosColors.SystemRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFingerprintId = null }) {
                    Text("取消", color = IosColors.SecondaryLabel)
                }
            }
        )
    }
}
