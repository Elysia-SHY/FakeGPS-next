package com.mockrun.app.feature.map

import com.mockrun.app.feature.map.IosVerticalZoomControl
import com.mockrun.app.feature.map.SearchLocationDialog
import com.mockrun.app.feature.map.RoadRouteDialog
import com.mockrun.app.feature.map.MapFloatingActions
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mockrun.app.feature.map.tabs.LocationControlPanel
import com.mockrun.app.feature.map.tabs.RouteBottomPanel
import com.mockrun.app.feature.map.tabs.RouteConfigDialog
import com.mockrun.app.feature.map.tabs.RouteStage
import com.mockrun.app.ui.components.PermissionGuideDialog
import com.mockrun.app.util.Diag
import com.mockrun.app.util.PermissionIssueType
import com.mockrun.app.util.logFailure
import com.mockrun.app.core.designsystem.LiquidGlassDefaults
import com.mockrun.app.core.designsystem.liquidGlass
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import com.mockrun.app.core.location.AddressResolver
import com.mockrun.app.core.location.CoordinateConverter
import com.mockrun.app.core.location.RoadMode
import com.mockrun.app.core.location.RoadRouteResult
import com.mockrun.app.core.location.SearchResultItem
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.ui.components.AppPickerBottomSheet
import com.mockrun.app.ui.components.BookmarkBottomSheet
import com.mockrun.app.ui.components.BookmarkKind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.sp
import com.mockrun.app.BuildConfig
import com.mockrun.app.core.designsystem.*
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

/** 反向地理编码尚未返回时的占位文案；收藏命名据此判断地址是否可用。 */
private const val ADDRESS_PLACEHOLDER = "正在获取当前地址..."

/**
 * 中央准星 Pin 块的固有高度（dp）：
 * 胶囊提示条 ≈ 33（ButtonSmall 13pt + 上下 8dp padding + 描边）
 * + 10 间距 + 水滴 Pin 56 + 3 间距 + 落地阴影 7 ≈ 109dp。
 *
 * 用于按底部面板实测高度反推 Pin 的垂直让位量，替代原先写死的 130dp。
 */
private val CENTER_PIN_BLOCK_HEIGHT = 109.dp

/**
 * 右列悬浮控件（缩放胶囊 + 物理定位 FAB）的几何常量，与实现逐条对齐：
 * - 54dp / 12dp 来自 MapFloatingActions（MapFloatingActions.kt:54「全览路线」FAB、
 *   :80「真机物理定位」FAB，以及它自己 Column 的 spacedBy(12.dp)）。
 *   ROUTE 且航点 >= 2 时两个圆同时在列里，所以那一档 FAB 块高 = 54 + 12 + 54 = 120dp。
 * - 112dp 是缩放胶囊的几何下限：两个 48dp 按钮 + 轨道与内边距 16dp
 *   （IosVerticalZoomControl 的 Column = padding(vertical = 4.dp) + 两个 48dp 按钮 + weight 轨道）。
 * - 56dp 是顶部图标簇高度，用于推出右列顶边不得越过的限制线。
 */
private val RIGHT_FAB_SIZE = 54.dp
private val RIGHT_FAB_GAP = 12.dp
private val RIGHT_FAB_BLOCK_ROUTE = RIGHT_FAB_SIZE * 2 + RIGHT_FAB_GAP
/** 控件列底边与抽屉顶边之间的呼吸距离。 */
private val RIGHT_PANEL_GAP = 12.dp
/**
 * 顶部图标簇的高度：地址条右侧「底图 / 收藏 / 更多」三个 48dp IconButton，
 * 外加那层 Row 的上下各 4dp 内边距（MapScreen.kt:855）= 56dp；
 * 实测占据 y ∈ [statusBarTop + 4, statusBarTop + 60]。
 */
private val TOP_ICON_ROW_HEIGHT = 56.dp
/** 控件列顶边与顶部图标簇之间的呼吸距离。 */
private val TOP_MARGIN = 12.dp
private val RIGHT_MIN_CAPSULE = 112.dp
private val RIGHT_MAX_CAPSULE = 180.dp

/**
 * 抽屉折叠（COLLAPSED）态下卡片部分的固有高度 [估算]。
 *
 * 推导自 LocationControlPanel 的布局（只读参考，本文件不改它）：
 * 卡片内 Column = padding 14 + 拖拽手柄 48（minTouchTarget）+ 间距 12 + 搜索行 52 + padding 14
 * ≈ 140dp；卡片底部那份 ScreenInsets 预留不含在内，由 collapsedPanelFallback 按设备实时求得。
 * 只用于 onGloballyPositioned 首次回填之前的那一帧。
 */
private val COLLAPSED_PANEL_CARD_HEIGHT = 140.dp

/**
 * 右列「胶囊收缩 / 形态切换」的过渡曲线 —— 与底部抽屉自身的展开动画同源。
 *
 * 规格逐字抄自 LocationControlPanel.kt:138-143（只读抄录，未改该文件）：
 *     .animateContentSize(
 *         animationSpec = spring(
 *             dampingRatio = Spring.DampingRatioLowBouncy,
 *             stiffness = Spring.StiffnessMediumLow
 *         )
 *     )
 * 即 dampingRatio = 0.75f、stiffness = 400f；visibilityThreshold 未显式给出，
 * 沿用各动画类型的默认阈值（Dp 为 0.5dp，Float 为 0.01f）。
 */
private val DRAWER_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)
private val DRAWER_SPRING_DP = spring<Dp>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * 右列在「当前抽屉高度」下的形态与胶囊目标高度。
 *
 * @param compact 竖排最小形态也放不下时为 true —— FAB 块移到胶囊左侧，整列高度只剩胶囊。
 * @param capsuleHeight 胶囊目标高度，已扣入顶边限制线（statusBarTop + 图标簇 + 呼吸）。
 * @param compactFloor 形态插值至少要走到这个比例（0..1），列顶才不越限制线。动画的 fraction
 *   取 max(spring 值, 这个下限)：既保证不变量②在过渡的每一帧成立，又因为是 max 而非赋值，
 *   曲线仍连续（不跳变）。
 */
private data class RightColumnMetrics(
    val compact: Boolean,
    val capsuleHeight: Dp,
    val compactFloor: Float
)

/**
 * 右列悬浮控件的两种形态，在同一条 spring 上插值（子节点顺序：0 = 缩放胶囊，1 = FAB 块）。
 *
 * - 竖排（compactFraction = 0）：胶囊在上，FAB 块在下方 RIGHT_FAB_GAP 处（原设计稿 .map-fabs）；
 * - 紧凑（= 1）：FAB 块移到胶囊左侧 RIGHT_FAB_GAP 处，整列高度 = max(胶囊高, FAB 块高)。
 *
 * 为什么不用 Column / Row 互换：两种形态要在同一棵节点树上滑动过渡（FAB 从「胶囊下方」滑到
 * 「胶囊左侧」），互换容器会重建子树、只能靠淡入淡出，既不是同一条 spring，也会在切换瞬间
 * 丢掉 48dp 触区。这里两个子节点全程在位，位置按 fraction 线性插值。
 *
 * 不变量：任何 fraction 下两个子节点都落在上报尺寸内（竖排的上报高度恰为内容高，紧凑按上报
 * 高度居中），所以「底边恒胶着在抽屉顶边上方」（不变量①）在过渡的每一帧都成立。
 */
@Composable
private fun RightFloatingControlsLayout(
    compactFraction: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(modifier = modifier, content = content) { measurables, _ ->
        if (measurables.size < 2) {
            layout(0, 0) { }
        } else {
            val capsule = measurables[0].measure(Constraints())
            val fab = measurables[1].measure(Constraints())
            val gapPx = RIGHT_FAB_GAP.roundToPx()
            val f = compactFraction.coerceIn(0f, 1f)
            fun mix(a: Int, b: Int) = (a + (b - a) * f).roundToInt()

            // 竖排槽位
            val portraitWidth = maxOf(capsule.width, fab.width)
            val portraitHeight = capsule.height + gapPx + fab.height
            val portraitCapsuleX = (portraitWidth - capsule.width) / 2
            val portraitFabX = (portraitWidth - fab.width) / 2
            val portraitFabY = capsule.height + gapPx

            // 紧凑槽位
            val compactWidth = fab.width + gapPx + capsule.width
            val compactHeight = maxOf(capsule.height, fab.height)
            val compactFabY = (compactHeight - fab.height) / 2
            val compactCapsuleX = fab.width + gapPx
            val compactCapsuleY = (compactHeight - capsule.height) / 2

            layout(mix(portraitWidth, compactWidth), mix(portraitHeight, compactHeight)) {
                capsule.place(mix(portraitCapsuleX, compactCapsuleX), mix(0, compactCapsuleY))
                fab.place(mix(portraitFabX, 0), mix(portraitFabY, compactFabY))
            }
        }
    }
}

enum class MapTab {
    LOCATION,
    ROUTE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    mapViewModel: MapViewModel,
    simulationViewModel: SimulationViewModel,
    initialTab: MapTab = MapTab.LOCATION,
    isLiquidGlass: Boolean = true,
    isTablet: Boolean = false,
    // 保留参数以兼容 AppNavigation 的调用点（navBar + 94 的旧硬编码值）。
    // 页内所有底部安全区已改由 ScreenInsets 实时推导，此值不再参与布局。
    @Suppress("UNUSED_PARAMETER")
    bottomBarPadding: Dp = 76.dp,
    onNavigateToLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    var activeMapTab by remember { mutableStateOf(initialTab) }
    LaunchedEffect(initialTab) {
        activeMapTab = initialTab
    }

    var routeStage by remember { mutableStateOf(RouteStage.SELECTING) }
    var selectedSpeed by remember { mutableFloatStateOf(8f) }
    var isCadenceEnabled by remember { mutableStateOf(false) }
    var showRouteConfigDialog by remember { mutableStateOf(false) }

    val drawnWaypoints by mapViewModel.drawnWaypoints.collectAsState()
    val simState by simulationViewModel.state.collectAsState()
    val joystickLocation by simulationViewModel.joystickLocation.collectAsState()
    // 收藏夹：定位标签读地点收藏，路线标签读航线收藏
    val bookmarkedLocations by mapViewModel.bookmarkedLocations.collectAsState()
    val savedTracks by mapViewModel.savedTracks.collectAsState()
    val selectedRoute by mapViewModel.selectedRoute.collectAsState()
    val searchQuery by mapViewModel.searchQuery.collectAsState()
    val searchResults by mapViewModel.searchResults.collectAsState()
    val isSearching by mapViewModel.isSearching.collectAsState()

    LaunchedEffect(simState.status, drawnWaypoints) {
        if (simState.status is com.mockrun.app.domain.model.SimulationStatus.Running ||
            simState.status is com.mockrun.app.domain.model.SimulationStatus.Paused) {
            routeStage = RouteStage.RUNNING
        } else if (drawnWaypoints.size >= 2 && routeStage != RouteStage.SELECTING) {
            routeStage = RouteStage.READY
        } else if (drawnWaypoints.size < 2 && routeStage == RouteStage.READY) {
            // 航点被删到少于 2 个时必须退出 READY。
            // 原实现缺这一分支：删除正在使用的路线（RouteLibraryScreen 的书签图标
            // -> 删除当前路线）会触发 MapViewModel.clearWaypoints()，此时
            // selectedRoute 变 null、drawnWaypoints 变空，但 routeStage 仍停在 READY，
            // 于是「开启模拟」按钮保持可用。点下去会用空 waypoints 构造 Route，
            // 传给 RouteSimulator.simulateRoute 的 require(size >= 2) 抛
            // IllegalArgumentException，而它发生在 serviceScope.launch 内、
            // 无 CoroutineExceptionHandler 兜底 —— 直接崩进程。
            routeStage = RouteStage.SELECTING
        }
    }

    val isPointMockActive by simulationViewModel.isPointMockActive.collectAsState()
    val pointMockLocation by simulationViewModel.pointMockLocation.collectAsState()
    val isJoystickRunning by simulationViewModel.isJoystickActive.collectAsState()
    val selectedTargetLocation by simulationViewModel.selectedTargetLocation.collectAsState()
    val realPhysicalLocation by simulationViewModel.realPhysicalLocation.collectAsState()
    val multiTargetRules by simulationViewModel.multiTargetRules.collectAsState()
    val activeTargetKey by simulationViewModel.activeTargetKey.collectAsState()
    val isRootAvailable by simulationViewModel.isRootAvailable.collectAsState()
    var showMapAppPickerSheet by remember { mutableStateOf(false) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var routeNameInput by remember { mutableStateOf("") }
    var currentMapType by remember { mutableStateOf(MapSourceType.AUTONAVI_AUTO) }
    var showMapTypeMenu by remember { mutableStateOf(false) }
    var showBookmarkSheet by remember { mutableStateOf(false) }

    val isSystemDark = LocalIosColors.current.isDark
    val shouldApplyDarkMap = when (currentMapType) {
        MapSourceType.AUTONAVI_AUTO -> isSystemDark
        MapSourceType.AUTONAVI_DARK -> true
        else -> false
    }
    val coroutineScope = rememberCoroutineScope()
    var updateCenterJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var userRealLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var centerAimingCoord by remember {
        mutableStateOf(
            pointMockLocation?.let { it.latitude to it.longitude }
                ?: selectedTargetLocation?.let { it.latitude to it.longitude }
                ?: (39.9042 to 116.4074)
        )
    }
    var currentAddressText by remember { mutableStateOf(ADDRESS_PLACEHOLDER) }
    var currentZoom by remember { mutableDoubleStateOf(16.0) }

    // 中央 Pin 的让位量由底部面板实测高度推导（面板有 COLLAPSED/DEFAULT/EXPANDED
    // 三档，以及路线页 SELECTING/READY/RUNNING 三种布局，写死的 130dp 在面板长高时
    // 会被面板盖住）。这里测容器高度与面板高度，实时校准。
    var mapBoxHeightPx by remember { mutableIntStateOf(0) }
    var bottomPanelHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // 首帧兜底用的折叠态面板高度：onGloballyPositioned 要等第一次布局结束才回填真实值，
    // 在此之前 offset 若读到 0 会让控件先贴到容器底部、下一帧再弹上去。
    // ScreenInsets 的底部预留是设备相关的（系统导航栏 + 浮空底栏 72 + 外距 12 + 呼吸 12）。
    val collapsedPanelFallback = ScreenInsets.bottomInset(extra = 12.dp) + COLLAPSED_PANEL_CARD_HEIGHT

    // 顶部安全区高度，以及右列顶边不得越过的限制线：
    // 状态栏 + 顶部图标簇（56dp）+ 呼吸（12dp）。图标簇底边在 statusBarTop + 60dp，
    // 故这条线到图标簇底边的真实余量是 8dp。参考设备 = 24 + 56 + 12 = 92dp；
    // EXPANDED 档可用 114.7dp，仍 >= 紧凑形态下限 112dp，结论不变。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val rightColumnTopLimit = statusBarTop + TOP_ICON_ROW_HEIGHT + TOP_MARGIN

    // 右列形态与胶囊目标高度 —— 由抽屉实测高度推导。
    // 用 derivedStateOf 而不是直接读 state：只有结果真的变了才通知读取者 ——
    // compact 是 Boolean，一整轮抽屉动作最多翻转一次；capsuleHeight 只在「顶边被限制线压住」
    // 的区间里逐帧变化，且是连续变化（不跳变）。
    // 竖排最小形态 = 胶囊下限 112 + 间距 12 + FAB 块（54；ROUTE 且航点 >= 2 时 120）；
    // 可用高度连它都放不下就切紧凑形态，整列高度只剩胶囊，下限仍是 112。
    val rightColumnMetrics by remember(activeMapTab, drawnWaypoints.size, density, rightColumnTopLimit) {
        derivedStateOf {
            val fabBlock = if (activeMapTab == MapTab.ROUTE && drawnWaypoints.size >= 2) {
                RIGHT_FAB_BLOCK_ROUTE
            } else {
                RIGHT_FAB_SIZE
            }
            if (mapBoxHeightPx == 0 || bottomPanelHeightPx == 0) {
                RightColumnMetrics(compact = false, capsuleHeight = RIGHT_MAX_CAPSULE, compactFloor = 0f)
            } else {
                with(density) {
                    // 列底（胶着在抽屉顶边上方 RIGHT_PANEL_GAP）到限制线之间还剩多少高度
                    val available = mapBoxHeightPx.toDp() - bottomPanelHeightPx.toDp() -
                        RIGHT_PANEL_GAP - rightColumnTopLimit
                    // 竖排口径的胶囊高度：剩余空间扣掉间距与 FAB 块，再夹到 112..180
                    val portraitCapsule = (available - RIGHT_FAB_GAP - fabBlock)
                        .coerceIn(RIGHT_MIN_CAPSULE, RIGHT_MAX_CAPSULE)
                    val portraitHeight = portraitCapsule + RIGHT_FAB_GAP + fabBlock
                    // 竖排最小形态都放不下 -> 切紧凑；紧凑下胶囊停在几何下限（不去填满剩余空间：
                    // 填满会把列顶推到限制线以上，且会在切换点与竖排的 112dp 产生跳变）。
                    val compact = available < portraitHeight
                    val capsule = if (compact) RIGHT_MIN_CAPSULE else portraitCapsule
                    val compactHeight = maxOf(capsule, fabBlock)
                    val compactFloor = if (compact && compactHeight < portraitHeight) {
                        ((available - portraitHeight) / (compactHeight - portraitHeight))
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    RightColumnMetrics(compact, capsule, compactFloor)
                }
            }
        }
    }
    val zoomCapsuleTarget = rightColumnMetrics.capsuleHeight

    // 形态切换本身也是动画：FAB 块从「胶囊下方」滑到「胶囊左侧」，与胶囊收缩共用
    // 抽屉自己的那条 spring（DRAWER_SPRING，规格来源见其 KDoc）。
    val rightColumnCompactFraction by animateFloatAsState(
        targetValue = if (rightColumnMetrics.compact) 1f else 0f,
        animationSpec = DRAWER_SPRING,
        label = "rightColumnCompact"
    )
    // 抽屉还在继续变高、而 spring 还没走完的中间帧，用实时下限把形态至少压到
    // 「列顶落在限制线上」的程度：不变量②在整个过渡期间成立，且 max 而非赋值保证曲线连续。
    val rightColumnFraction = maxOf(rightColumnCompactFraction, rightColumnMetrics.compactFloor)

    val roadOrigin by mapViewModel.roadOrigin.collectAsState()
    val roadDestination by mapViewModel.roadDestination.collectAsState()

    var showSearchDialog by remember { mutableStateOf(false) }
    var showRoadRouteDialog by remember { mutableStateOf(false) }
    // 路线规划阶段地图单击即添加路点。
    //
    // 原实现用 isContinuousDrawMode 这个恒为 false 的开关来控制，
    // 导致 MapEventsReceiver.singleTapConfirmedHelper 里的 addWaypoint 分支
    // 永不执行 —— 路点只能靠「添加第 N 点」按钮（取当前中心点）逐个加，
    // 用户点地图没有任何反应。现改为直接由「路线页 + SELECTING 阶段」推导，
    // 与该阶段下 RouteBottomPanel 显示的「添加第 N 点」语义一致。
    val isContinuousDrawMode = activeMapTab == MapTab.ROUTE &&
        routeStage == RouteStage.SELECTING
    var permissionIssueDialogType by remember { mutableStateOf<PermissionIssueType?>(null) }

    val ensurePermissionAndStart: (() -> Unit) -> Unit = { onPermitted ->
        // 统一走 ViewModel：Root 模式会先自动授予模拟权限（app-op + 全局开发者选项）再校验，
        // 避免 Root 用户仍被「需设置模拟位置应用」挡住；免 Root 模式维持原有手动勾选校验。
        coroutineScope.launch {
            val issue = simulationViewModel.resolveInjectionPermission(context)
            if (issue != PermissionIssueType.NONE) {
                permissionIssueDialogType = issue
            } else {
                onPermitted()
            }
        }
    }
    val continuousDrawRef = rememberUpdatedState(isContinuousDrawMode)

    // Actively query hardware GPS on screen entrance
    LaunchedEffect(Unit) {
        CoordinateConverter.requestFreshLocation(context) { lat, lon ->
            userRealLocation = lat to lon
            simulationViewModel.updateRealPhysicalLocation(lat, lon)
            if (pointMockLocation == null && selectedTargetLocation == null) {
                centerAimingCoord = lat to lon
            }
        }
    }

    // Active Aiming Coordinate: directly anchored to center crosshair
    val activeCoord = centerAimingCoord

    LaunchedEffect(activeCoord) {
        currentAddressText = AddressResolver.resolveAddress(context, activeCoord.first, activeCoord.second)
    }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                mapViewModel.importGpx(stream, "导入GPX路线")
            }
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 地图页专属 Haze 模糊源：原生 MapView 无法被 Haze 捕获，
    // 因此叠加一层 Compose 绘制的中性雾面（单色铺底 + 上下厚中间薄的竖直渐变）作为源层，
    // 让定位页/路线页所有卡片获得与功能页/关于页一致的真实毛玻璃。
    val pageHaze = remember { dev.chrisbanes.haze.HazeState() }
    val isDark = LocalIosColors.current.isDark

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { mapBoxHeightPx = it.size.height }
    ) {
        // ① 地图层（原生 View，不参与 Haze 捕获）
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(AutoNaviVectorTileSource)
                        setMultiTouchControls(true)
                        isClickable = true
                        isFocusable = true
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(currentZoom)

                        // Center on initial aiming coordinate
                        val initCoord = centerAimingCoord
                        val (initLat, initLon) = if (currentMapType != MapSourceType.OPEN_STREET_MAP) {
                            CoordinateConverter.wgs84ToGcj02(initCoord.first, initCoord.second)
                        } else {
                            initCoord
                        }
                        controller.setCenter(GeoPoint(initLat, initLon))

                        addMapListener(object : MapListener {
                            override fun onZoom(event: ZoomEvent?): Boolean {
                                event?.zoomLevel?.let { currentZoom = it }
                                return true
                            }
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                updateCenterJob?.cancel()
                                updateCenterJob = coroutineScope.launch {
                                    delay(120)
                                    mapViewRef?.mapCenter?.let { centerGeo ->
                                        val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                                        val (wgsLat, wgsLon) = if (isGcjMap) {
                                            CoordinateConverter.gcj02ToWgs84(centerGeo.latitude, centerGeo.longitude)
                                        } else {
                                            centerGeo.latitude to centerGeo.longitude
                                        }
                                        centerAimingCoord = wgsLat to wgsLon
                                        val activeKey = activeTargetKey
                                        if (activeKey == null) {
                                            simulationViewModel.updateSelectedTarget(wgsLat, wgsLon)
                                        }
                                    }
                                }
                                return false
                            }
                        })

                        val receiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                p?.let {
                                    val (wgsLat, wgsLon) = if (currentMapType == MapSourceType.OPEN_STREET_MAP) {
                                        it.latitude to it.longitude
                                    } else {
                                        CoordinateConverter.gcj02ToWgs84(it.latitude, it.longitude)
                                    }
                                    if (continuousDrawRef.value) {
                                        mapViewModel.addWaypoint(wgsLat, wgsLon)
                                    } else {
                                        mapViewRef?.controller?.animateTo(it)
                                        centerAimingCoord = wgsLat to wgsLon
                                        val activeKey = activeTargetKey
                                        if (activeKey == null) {
                                            simulationViewModel.updateSelectedTarget(wgsLat, wgsLon)
                                        }
                                    }
                                }
                                return true
                            }
                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        }
                        overlays.add(MapEventsOverlay(receiver))
                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP

                    // 0. Dynamic Apple Maps Dark Mode / Night Map Filter
                    if (shouldApplyDarkMap && currentMapType != MapSourceType.AUTONAVI_SATELLITE) {
                        mapView.overlayManager.tilesOverlay.setColorFilter(DarkMapColorFilter)
                    } else {
                        mapView.overlayManager.tilesOverlay.setColorFilter(null)
                    }

                    // 1. Draw route polyline
                    val routeOverlays = mapView.overlays.filterIsInstance<Polyline>()
                    routeOverlays.forEach { mapView.overlays.remove(it) }

                    if (drawnWaypoints.size >= 2) {
                        val polyline = Polyline().apply {
                            val displayPoints = drawnWaypoints.map { wp ->
                                if (isGcjMap) {
                                    val (gcjLat, gcjLon) = CoordinateConverter.wgs84ToGcj02(wp.latitude, wp.longitude)
                                    GeoPoint(gcjLat, gcjLon)
                                } else {
                                    GeoPoint(wp.latitude, wp.longitude)
                                }
                            }
                            setPoints(displayPoints)
                            outlinePaint.color = android.graphics.Color.parseColor("#34C759")
                            outlinePaint.strokeWidth = 12f
                        }
                        mapView.overlays.add(polyline)
                    }

                    // 2. Clear markers and redraw
                    val markers = mapView.overlays.filterIsInstance<Marker>()
                    markers.forEach { mapView.overlays.remove(it) }

                    // Route Simulation Runner Marker
                    simState.currentWayPoint?.let { wp ->
                        val (displayLat, displayLon) = if (isGcjMap) {
                            CoordinateConverter.wgs84ToGcj02(wp.latitude, wp.longitude)
                        } else {
                            wp.latitude to wp.longitude
                        }
                        val runner = Marker(mapView).apply {
                            position = GeoPoint(displayLat, displayLon)
                            icon = MapPinHelper.getRunnerPin(context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = "🏃 路线模拟位置"
                            rotation = simState.bearing
                        }
                        mapView.overlays.add(runner)
                    }

                    // Independent Joystick Marker
                    joystickLocation?.let { joyWp ->
                        val (joyLat, joyLon) = if (isGcjMap) {
                            CoordinateConverter.wgs84ToGcj02(joyWp.latitude, joyWp.longitude)
                        } else {
                            joyWp.latitude to joyWp.longitude
                        }
                        val joyMarker = Marker(mapView).apply {
                            position = GeoPoint(joyLat, joyLon)
                            icon = MapPinHelper.getJoystickPin(context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = "🕹️ 万向摇杆位置"
                        }
                        mapView.overlays.add(joyMarker)
                    }

                    // Single-Point Virtual Location Marker (Apple Emerald Green Teardrop Pin)
                    pointMockLocation?.let { pLoc ->
                        val (pLat, pLon) = if (isGcjMap) {
                            CoordinateConverter.wgs84ToGcj02(pLoc.latitude, pLoc.longitude)
                        } else {
                            pLoc.latitude to pLoc.longitude
                        }
                        val pMarker = Marker(mapView).apply {
                            position = GeoPoint(pLat, pLon)
                            icon = MapPinHelper.getActiveMockPin(context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "📍 虚拟定位驻留点"
                        }
                        mapView.overlays.add(pMarker)
                    }

                    // Multi-Tenant Per-App Target Markers (Vibrant Distinct Colors)
                    multiTargetRules.filter { it.isEnabled }.forEach { rule ->
                        val (appLat, appLon) = if (isGcjMap) {
                            CoordinateConverter.wgs84ToGcj02(rule.latitude, rule.longitude)
                        } else {
                            rule.latitude to rule.longitude
                        }
                        val colorInt = runCatching { android.graphics.Color.parseColor(rule.colorHex) }
                            .logFailure("MapScreen", "parse rule color (${rule.appName})", Diag.Level.DEBUG)
                            .getOrDefault(android.graphics.Color.parseColor("#007AFF"))
                        val appMarker = Marker(mapView).apply {
                            position = GeoPoint(appLat, appLon)
                            icon = MapPinHelper.getAppPin(context, colorInt)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "📍 ${rule.appName} · 分流定位"
                            snippet = "坐标: ${"%.5f".format(rule.latitude)}, ${"%.5f".format(rule.longitude)}"
                            setOnMarkerClickListener { _, _ ->
                                simulationViewModel.setActiveTargetKey(rule.key)
                                centerAimingCoord = rule.latitude to rule.longitude
                                mapView.controller.animateTo(GeoPoint(appLat, appLon))
                                true
                            }
                        }
                        mapView.overlays.add(appMarker)
                    }


                    // Real Physical Location Puck (Apple Maps Signature Glowing Blue Puck)
                    val realLoc = userRealLocation
                        ?: realPhysicalLocation?.let { it.latitude to it.longitude }
                        ?: userRealLocation
                    realLoc?.let { (rLat, rLon) ->
                        val (dispLat, dispLon) = if (isGcjMap) {
                            CoordinateConverter.wgs84ToGcj02(rLat, rLon)
                        } else {
                            rLat to rLon
                        }
                        val realPuckMarker = Marker(mapView).apply {
                            position = GeoPoint(dispLat, dispLon)
                            icon = MapPinHelper.getRealLocationPuck(context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = "🔵 当前真实物理位置"
                            snippet = "硬件 GPS 坐标: ${"%.5f".format(rLat)}, ${"%.5f".format(rLon)}"
                            setOnMarkerClickListener { _, _ ->
                                centerAimingCoord = rLat to rLon
                                simulationViewModel.updateSelectedTarget(rLat, rLon)
                                true
                            }
                        }
                        mapView.overlays.add(realPuckMarker)
                    }

                    // Road Origin Marker (Green Start Pin)
                    roadOrigin?.let { orig ->
                        val (dispLat, dispLon) = if (isGcjMap) {
                            CoordinateConverter.wgs84ToGcj02(orig.latitude, orig.longitude)
                        } else {
                            orig.latitude to orig.longitude
                        }
                        val origMarker = Marker(mapView).apply {
                            position = GeoPoint(dispLat, dispLon)
                            icon = MapPinHelper.getOriginPin(context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "🟢 路线起点"
                        }
                        mapView.overlays.add(origMarker)
                    }

                    // Road Destination Marker (Orange Destination Pin)
                    roadDestination?.let { dest ->
                        val (dispLat, dispLon) = if (isGcjMap) {
                            CoordinateConverter.wgs84ToGcj02(dest.latitude, dest.longitude)
                        } else {
                            dest.latitude to dest.longitude
                        }
                        val destMarker = Marker(mapView).apply {
                            position = GeoPoint(dispLat, dispLon)
                            icon = MapPinHelper.getDestinationPin(context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "🔴 路线终点"
                        }
                        mapView.overlays.add(destMarker)
                    }

                    mapView.invalidate()
                },
                onRelease = { mapView ->
                    updateCenterJob?.cancel()
                    mapView.overlays.clear()
                    mapView.onDetach()
                    if (mapViewRef === mapView) {
                        mapViewRef = null
                    }
                }
            )
        }

        // ② 雾面源层：作为 Haze 模糊源。卡片模糊到的内容是这层 Compose 绘制的雾面
        //    （原生 MapView 无法被 Haze 捕获，这是 Compose 架构下最接近 iOS 地图毛玻璃
        //    的做法）。这一层不能删也不能变空 —— AppNavigation.kt:75-77、:128 记着地图页的
        //    AndroidView 被 Haze 录出来是空图层，底栏与地图页卡片的模糊源就在这里。
        //
        //    为什么不铺满均匀底色：均匀半透明会把整张地图一起压暗。卡片集中在顶部
        //    （地址胶囊、FAB 列）与底部（控制面板），因此这里用「上下厚、中间薄」的
        //    垂直渐变 —— 卡片正后方始终有足够内容供 36dp 模糊采样，中央地图区保持干净。
        //    两个端色 alpha 是唯一权衡旋钮：太薄卡片会直接透出原始地图（旧版「穿模」），
        //    太厚则顶部/底部地图细节被吃掉。
        //
        //    2026-10-14：删掉原先 6 个极光光斑，改为单色中性铺底（见下方 drawBehind）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(pageHaze)
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(
                                Color(0xA60A0F1C),
                                Color(0x140A0F1C),
                                Color(0x140A0F1C),
                                Color(0x990A0F1C)
                            )
                        } else {
                            listOf(
                                Color(0xA6EDF2FA),
                                Color(0x14EDF2FA),
                                Color(0x14EDF2FA),
                                Color(0x99EDF2FA)
                            )
                        }
                    )
                )
                .drawBehind {
                    // 单色中性铺底，替代原先 6 个极光光斑：那些球与全局背景是同一套光斑、
                    // 半径还略大，叠在地图上能看出圆形边界（用户反馈「球跑到地图上」）。
                    // 这一层不能删也不能空 —— 原生 MapView 无法被 Haze 捕获，底栏与地图页
                    // 卡片的毛玻璃全靠这层提供可采样内容（AppNavigation.kt:75-77、:128）。
                    // alpha 0.05：white / black 是无彩色，只改明度（约 ±13/255）不产生色偏；
                    // 同时把中段内容密度从竖直渐变的 8% 抬到约 12.6%，36dp 模糊仍有内容可采。
                    drawRect(
                        color = if (isDark) {
                            Color.Black.copy(alpha = 0.05f)
                        } else {
                            Color.White.copy(alpha = 0.05f)
                        }
                    )
                }
        )

        // 1. Top Floating Controls Bar & Multi-Target App Capsules (iOS Frosted Floating Header)
        //    所有浮层卡片通过 LocalHazeState 引用上面的雾面源层，走真实 hazeChild 模糊。
        CompositionLocalProvider(LocalHazeState provides pageHaze) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Address Pill (Clickable to center)
                    val activeRule = multiTargetRules.find { it.key == activeTargetKey }
                    val activeRuleColor = activeRule?.let {
                        runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }
                            .logFailure("MapScreen", "parse active rule color", Diag.Level.DEBUG)
                            .getOrNull()
                    } ?: IosBlue

                    Surface(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .liquidGlass(
                                isLiquidGlass = isLiquidGlass,
                                shape = AppShapes.panel,
                                elevation = 14.dp
                            )
                            .bouncyClickable {
                                val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                                val (tLat, tLon) = if (isGcjMap) CoordinateConverter.wgs84ToGcj02(activeCoord.first, activeCoord.second) else activeCoord
                                mapViewRef?.controller?.apply {
                                    setZoom(16.5)
                                    animateTo(GeoPoint(tLat, tLon))
                                }
                            },
                        color = Color.Transparent,
                        shape = AppShapes.panel
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            // 设计稿 .topbar__pin —— 34dp / r12 着色图标块
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(activeRuleColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = activeRuleColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // 主次文：主 = 当前目标（BarTitle 14.5/700），
                            // 次 = 当前状态 + 地址（BarSub 11.5/400）。
                            // 两行都会被 ellipsis，完整值通过语义描述可达
                            // （读屏与长按无障碍均可取到全文）。
                            val topTitle = if (activeMapTab == MapTab.ROUTE) "路线巡航"
                                else if (activeRule != null) "${activeRule.appName} 独立分流"
                                else "全局模拟"
                            val topStateText = when {
                                activeMapTab == MapTab.ROUTE && routeStage == RouteStage.RUNNING -> "巡航中"
                                activeMapTab == MapTab.ROUTE && routeStage == RouteStage.READY -> "路线就绪"
                                activeMapTab == MapTab.ROUTE -> "选点中"
                                isPointMockActive -> "虚拟定位中"
                                else -> "未开启定位"
                            }
                            val topSub = if (currentAddressText.isBlank()) {
                                topStateText
                            } else {
                                "$topStateText · $currentAddressText"
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "当前目标：$topTitle。当前状态：$topStateText。当前地址：$currentAddressText"
                                    }
                            ) {
                                Text(
                                    text = topTitle,
                                    style = IosTypography.BarTitle,
                                    color = IosColors.Label,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = topSub,
                                    style = IosTypography.BarSub,
                                    color = IosColors.SecondaryLabel,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Right: Floating Map Tools Pill
                    // 触区：全部图标按钮 36dp -> 48dp（低于 48dp 时拇指难以命中）。
                    // 路线页原本有 4 个入口（沿路规划 / 底图 / 收藏夹 / 导入GPX），
                    // 4×48dp 会把顶部地址条挤没，因此把两个「路线专属」入口
                    // 合并进「更多」菜单 —— 功能一个不少，仍全部可达。
                    var showMoreToolsMenu by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Transparent,
                        modifier = Modifier.liquidGlass(
                            isLiquidGlass = isLiquidGlass,
                            shape = RoundedCornerShape(22.dp),
                            elevation = 14.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Map Layer Selector (唯一的地图API底图切换入口)
                            Box {
                                IconButton(
                                    onClick = { showMapTypeMenu = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Layers, contentDescription = "切换底图API", tint = IosBlue, modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(
                                    expanded = showMapTypeMenu,
                                    onDismissRequest = { showMapTypeMenu = false }
                                ) {
                                    MapSourceType.values().forEach { type ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    type.label,
                                                    fontWeight = if (type == currentMapType) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (type == currentMapType) IosBlue else IosColors.Label
                                                )
                                            },
                                            onClick = {
                                                currentMapType = type
                                                showMapTypeMenu = false
                                                mapViewRef?.let { map ->
                                                    when (type) {
                                                        MapSourceType.AUTONAVI_AUTO,
                                                        MapSourceType.AUTONAVI_VECTOR,
                                                        MapSourceType.AUTONAVI_DARK -> map.setTileSource(AutoNaviVectorTileSource)
                                                        MapSourceType.AUTONAVI_SATELLITE -> map.setTileSource(AutoNaviSatelliteTileSource)
                                                        MapSourceType.OPEN_STREET_MAP -> map.setTileSource(TileSourceFactory.MAPNIK)
                                                    }
                                                    map.invalidate()
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // 收藏夹入口（紧邻底图切换按钮）：定位标签看地点收藏，路线标签看航线收藏
                            IconButton(
                                onClick = { showBookmarkSheet = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = if (activeMapTab == MapTab.LOCATION) "地点收藏" else "航线收藏",
                                    tint = IosBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 「更多」菜单：路线页的沿路规划与 GPX 导入合并到这里
                            if (activeMapTab == MapTab.ROUTE) {
                                Box {
                                    IconButton(
                                        onClick = { showMoreToolsMenu = true },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "更多路线工具",
                                            tint = IosBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMoreToolsMenu,
                                        onDismissRequest = { showMoreToolsMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("沿路规划 🛣️", color = IosColors.Label) },
                                            onClick = {
                                                showMoreToolsMenu = false
                                                showRoadRouteDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("导入 GPX 文件", color = IosColors.Label) },
                                            onClick = {
                                                showMoreToolsMenu = false
                                                gpxPickerLauncher.launch(arrayOf("*/*"))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeMapTab == MapTab.LOCATION) {
                    // Location Mode: Per-App Diversion & Global Capsules
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Item 0: Global Default
                        item {
                            val isSelected = activeTargetKey == null
                            Surface(
                                shape = AppShapes.pill,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .height(48.dp)
                                    .liquidGlass(
                                        isLiquidGlass = isLiquidGlass,
                                        shape = AppShapes.pill,
                                        elevation = if (isSelected) 14.dp else 8.dp,
                                        containerColor = if (isSelected) IosColors.SystemBlue else null
                                    )
                                    .bouncyClickable {
                                        simulationViewModel.setActiveTargetKey(null)
                                        val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                                        val targetCoord = pointMockLocation?.let { p -> p.latitude to p.longitude }
                                            ?: selectedTargetLocation?.let { s -> s.latitude to s.longitude }
                                            ?: centerAimingCoord
                                        centerAimingCoord = targetCoord
                                        val (tLat, tLon) = if (isGcjMap) CoordinateConverter.wgs84ToGcj02(targetCoord.first, targetCoord.second) else targetCoord
                                        mapViewRef?.controller?.animateTo(GeoPoint(tLat, tLon))
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "全局通用",
                                        style = IosTypography.Subheadline,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else IosColors.Label
                                    )
                                }
                            }
                        }

                        // Item 1..N: Per-App Rules
                        items(
                            items = multiTargetRules,
                            key = { r: MultiTargetRule -> r.key }
                        ) { rule ->
                            val isSelected = activeTargetKey == rule.key
                            val ruleColor = remember(rule.colorHex) {
                                runCatching { Color(android.graphics.Color.parseColor(rule.colorHex)) }
                                    .logFailure("MapScreen", "parse rule color chip", Diag.Level.DEBUG)
                                    .getOrDefault(IosColors.SystemBlue)
                            }
                            Surface(
                                shape = AppShapes.pill,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .height(48.dp)
                                    .liquidGlass(
                                        isLiquidGlass = isLiquidGlass,
                                        shape = AppShapes.pill,
                                        elevation = if (isSelected) 14.dp else 8.dp,
                                        containerColor = if (isSelected) ruleColor else null
                                    )
                                    .bouncyClickable {
                                        simulationViewModel.setActiveTargetKey(rule.key)
                                    centerAimingCoord = rule.latitude to rule.longitude
                                    val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                                    val (tLat, tLon) = if (isGcjMap) CoordinateConverter.wgs84ToGcj02(rule.latitude, rule.longitude) else (rule.latitude to rule.longitude)
                                    mapViewRef?.controller?.animateTo(GeoPoint(tLat, tLon))
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(8.dp),
                                        shape = CircleShape,
                                        color = if (isSelected) Color.White else (if (rule.isEnabled) ruleColor else IosColors.SystemGray)
                                    ) {}
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = buildString {
                                            if (rule.userId != 0) append("${rule.appName} (${rule.userId})") else append(rule.appName)
                                            if (!rule.isEnabled) append(" (暂停)")
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else (if (rule.isEnabled) IosColors.Label else IosColors.SecondaryLabel)
                                    )
                                }
                            }
                        }

                        // Item N+1: Add App Capsule
                        item {
                            Surface(
                                shape = AppShapes.pill,
                                color = Color.Transparent,
                                modifier = Modifier
                                    .height(48.dp)
                                    .liquidGlass(
                                        isLiquidGlass = isLiquidGlass,
                                        shape = AppShapes.pill,
                                        elevation = 8.dp
                                    )
                                    .bouncyClickable { showMapAppPickerSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "添加分流",
                                        tint = IosBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "添加分流",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = IosBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2 + 5. 右侧悬浮控件列（设计稿 .map-fabs —— 缩放与定位同列，去除 bottom 魔法数字）
            //
            // 锚点 TopEnd -> BottomEnd。旧实现固定顶锚（top = 164.dp 字面量）且完全不引用
            // bottomPanelHeightPx，抽屉一长高就被盖住；改为底部锚定后
            // 控件列底边 = 容器底 − (抽屉实测高度 + 12dp 呼吸)，恒定贴在抽屉顶边上方 12dp。
            //
            // 垂直偏移在布局阶段求值：offset {} 的 lambda 在放置时调用，对 bottomPanelHeightPx
            // 的读取只登记在放置作用域 —— 抽屉逐帧改高度只触发重新放置，不重组、不闪，
            // 也不会「先按旧高度摆一帧再纠正」。组合期读同一个 state（如 pinBottomPadding 的
            // remember(key) 写法）会让整页每帧重组，这里刻意不照抄。
            //
            // 形态：两个子节点（0 = 缩放胶囊、1 = FAB 块）在 RightFloatingControlsLayout 里按
            // rightColumnCompactFraction 于「竖排 / 紧凑」之间插值，整列高度因此始终 <= 可用高度，
            // 列顶不会越过 rightColumnTopLimit；连紧凑形态都放不下时保①（底边依旧胶着在抽屉顶边
            // 上方 12dp），列顶会越线，越线量见交付说明里的三档数字表。
            RightFloatingControlsLayout(
                compactFraction = rightColumnFraction,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .statusBarsPadding()
                    .padding(end = 16.dp)
                    .offset {
                        val panelPx = if (bottomPanelHeightPx > 0) {
                            bottomPanelHeightPx
                        } else {
                            collapsedPanelFallback.roundToPx()
                        }
                        IntOffset(x = 0, y = -(panelPx + RIGHT_PANEL_GAP.roundToPx()))
                    }
            ) {
                IosVerticalZoomControl(
                    capsuleHeight = zoomCapsuleTarget,
                    capsuleAnimationSpec = DRAWER_SPRING_DP,
                    currentZoom = currentZoom,
                    minZoom = 3.0,
                    maxZoom = 19.0,
                    onZoomChange = { newZoom ->
                        currentZoom = newZoom
                        mapViewRef?.controller?.setZoom(newZoom)
                    },
                    onZoomIn = {
                        val newZoom = (currentZoom + 1.0).coerceAtMost(19.0)
                        currentZoom = newZoom
                        mapViewRef?.controller?.setZoom(newZoom)
                    },
                    onZoomOut = {
                        val newZoom = (currentZoom - 1.0).coerceAtLeast(3.0)
                        currentZoom = newZoom
                        mapViewRef?.controller?.setZoom(newZoom)
                    }
                )

                MapFloatingActions(
                    context = context,
                    activeMapTab = activeMapTab,
                    drawnWaypoints = drawnWaypoints,
                    currentMapType = currentMapType,
                    mapViewRef = mapViewRef,
                    isLiquidGlass = isLiquidGlass,
                    isPointMockActive = isPointMockActive,
                    isJoystickRunning = isJoystickRunning,
                    simState = simState,
                    simulationViewModel = simulationViewModel,
                    onResetToRealLocation = { real ->
                        userRealLocation = real
                        simulationViewModel.updateRealPhysicalLocation(real.first, real.second)
                        simulationViewModel.updateSelectedTarget(real.first, real.second)
                        centerAimingCoord = real
                    }
                )
            }

            // 3. Dynamic Status Capsule (iOS Dynamic Island style)
            if (isPointMockActive && pointMockLocation != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 76.dp, start = 12.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(30.dp)),
                    color = Color(0xEB000000),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(IosGreen)
                        )
                        Text(
                            "虚拟定位中: ${"%.4f".format(pointMockLocation!!.latitude)}, ${"%.4f".format(pointMockLocation!!.longitude)}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = IosRed,
                            modifier = Modifier
                                .clip(RoundedCornerShape(13.dp))
                                .minTouchTarget(minWidth = 48.dp, minHeight = 48.dp)
                                .bouncyClickable {
                                    simulationViewModel.stopPointMock(context)
                                    Toast.makeText(context, "已停止虚拟定位", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "停止定位",
                                    color = Color.White,
                                    style = IosTypography.Caption2,
                                    fontWeight = FontWeight.Bold
                                )
                            }
}
                    }
                }
            }

            // =================================================================
            // 4. Center Crosshair Aiming Pin (LocationSpoofer Style)
            // =================================================================
            AnimatedVisibility(
                visible = activeMapTab == MapTab.LOCATION || (activeMapTab == MapTab.ROUTE && routeStage == RouteStage.SELECTING),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val currentActiveRule = if (activeMapTab == MapTab.LOCATION) multiTargetRules.find { it.key == activeTargetKey } else null
                val currentAimingColor = currentActiveRule?.let {
                    runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }
                        .logFailure("MapScreen", "parse aiming-color", Diag.Level.DEBUG)
                        .getOrNull()
                } ?: IosBlue

                // 设计稿 .pin-wrap / .pin-tip —— 胶囊提示条 + 46×56 水滴 Pin（偏上 34%）
                // 让位量 = 2 × (面板高 − 容器高/2 + Pin 块高/2) + 呼吸 24dp，
                // 下限保留原来的 130dp（面板不高时行为与旧版一致）。
                // 推导：面板底部锚定，其顶边在 容器高 − 面板高；Pin 块（居中 + bottom padding X）
                // 的底边在 容器高/2 − X/2 + Pin块高/2，令其不高于面板顶边即得该式。
                val pinBottomPadding = remember(bottomPanelHeightPx, mapBoxHeightPx) {
                    if (bottomPanelHeightPx == 0 || mapBoxHeightPx == 0) {
                        130.dp
                    } else {
                        with(density) {
                            val boxH = mapBoxHeightPx.toDp()
                            val panelH = bottomPanelHeightPx.toDp()
                            val clearance = panelH - boxH / 2 + CENTER_PIN_BLOCK_HEIGHT / 2
                            maxOf(130.dp, clearance * 2 + 24.dp)
                        }
                    }
                }
                IosCenterPin(
                    label = if (currentActiveRule != null) "【${currentActiveRule.appName}】分流选点"
                            else if (activeMapTab == MapTab.ROUTE) "路线航点选点"
                            else "目标定位点",
                    pinColor = currentAimingColor,
                    icon = if (currentActiveRule != null) Icons.AutoMirrored.Filled.AltRoute else Icons.Default.Place,
                    modifier = Modifier.padding(bottom = pinBottomPadding)
                )
            }

            // =================================================================
            // 5. 右侧悬浮按钮已并入上方 .map-fabs 控件列（见 2 + 5）
            // =================================================================

            // =================================================================
            // 6. Bottom Panels (LocationControlPanel or RouteBottomPanel)
            // =================================================================
            if (activeMapTab == MapTab.LOCATION) {
                LocationControlPanel(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { bottomPanelHeightPx = it.size.height },
                    isMockActive = isPointMockActive || isJoystickRunning,
                    latitude = activeCoord.first,
                    longitude = activeCoord.second,
                    address = currentAddressText,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    // 两类收藏各自取 MapViewModel 的派生流，不做静默合并：
                    // 地点收藏 = waypoints.size <= 1（bookmarkedLocations），
                    // 路线收藏 = waypoints.size >= 2（savedTracks）。
                    savedLocations = bookmarkedLocations,
                    savedTracks = savedTracks,
                    isLiquidGlass = isLiquidGlass,
                    activeRule = multiTargetRules.find { it.key == activeTargetKey },
                    onSetTargetLocation = { rule ->
                        if (!rule.isEnabled) {
                            simulationViewModel.toggleMultiTargetRule(rule.key, true)
                        }
                        simulationViewModel.updateMultiTargetLocation(rule.key, activeCoord.first, activeCoord.second)
                        Toast.makeText(context, "✅ 已设为【${rule.appName}】分流定位点：$currentAddressText", Toast.LENGTH_SHORT).show()
                    },
                    onToggleTargetRule = { rule, isChecked ->
                        simulationViewModel.toggleMultiTargetRule(rule.key, isChecked)
                        val status = if (isChecked) "已开启" else "已暂停"
                        Toast.makeText(context, "【${rule.appName}】独立分流${status}", Toast.LENGTH_SHORT).show()
                    },
                    onClearActiveTarget = {
                        simulationViewModel.setActiveTargetKey(null)
                    },
                    onSearchQueryChange = { mapViewModel.performSearch(it) },
                    onSearchResultSelect = { item ->
                        val isGcj = currentMapType != MapSourceType.OPEN_STREET_MAP
                        val (dispLat, dispLon) = if (isGcj) CoordinateConverter.wgs84ToGcj02(item.latitude, item.longitude) else (item.latitude to item.longitude)
                        mapViewRef?.controller?.apply {
                            setZoom(16.5)
                            animateTo(GeoPoint(dispLat, dispLon))
                        }
                        simulationViewModel.updateSelectedTarget(item.latitude, item.longitude)
                        centerAimingCoord = item.latitude to item.longitude
                        mapViewModel.clearSearch()
                    },
                    onClearSearch = { mapViewModel.clearSearch() },
                    onStartMock = {
                        ensurePermissionAndStart {
                            simulationViewModel.startPointMock(context, activeCoord.first, activeCoord.second)
                            Toast.makeText(context, "虚拟定位已开启！", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStopMock = {
                        simulationViewModel.stopPointMock(context)
                        if (isJoystickRunning) {
                            context.stopService(Intent(context, com.mockrun.app.core.location.FloatingJoystickService::class.java))
                        }
                        Toast.makeText(context, "已停止虚拟定位", Toast.LENGTH_SHORT).show()
                    },
                    onResetRealLocation = {
                        if (isPointMockActive) simulationViewModel.stopPointMock(context)
                        if (simState.status is com.mockrun.app.domain.model.SimulationStatus.Running) simulationViewModel.stopSimulation(context)
                        CoordinateConverter.clearSavedRealLocation(context)
                        com.mockrun.app.core.location.MockLocationEngine.forceCleanAllTestProviders(context)
                        CoordinateConverter.requestFreshLocation(context) { freshLat, freshLon ->
                            userRealLocation = freshLat to freshLon
                            simulationViewModel.updateRealPhysicalLocation(freshLat, freshLon)
                        }
                        val real = CoordinateConverter.getRealDeviceLocation(context) ?: userRealLocation
                        if (real != null) {
                            userRealLocation = real
                            simulationViewModel.updateRealPhysicalLocation(real.first, real.second)
                            simulationViewModel.updateSelectedTarget(real.first, real.second)
                            centerAimingCoord = real
                            val isGcj = currentMapType != MapSourceType.OPEN_STREET_MAP
                            val (tLat, tLon) = if (isGcj) CoordinateConverter.wgs84ToGcj02(real.first, real.second) else real
                            mapViewRef?.controller?.apply {
                                setZoom(16.5)
                                animateTo(GeoPoint(tLat, tLon))
                            }
                            Toast.makeText(context, "已复位至真机物理位置", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSaveLocation = {
                        // 收藏作用于十字准星坐标，与 drawnWaypoints 无关：
                        // 定位标签下从未绘制航线，必须走单点收藏入库，否则静默失败。
                        val (bookmarkLat, bookmarkLon) = activeCoord
                        val addressLabel = currentAddressText
                            .takeIf { it.isNotBlank() && it != ADDRESS_PLACEHOLDER && it != "无效坐标" }
                            ?: "%.6f, %.6f".format(bookmarkLat, bookmarkLon)
                        mapViewModel.saveLocationPoint(
                            latitude = bookmarkLat,
                            longitude = bookmarkLon,
                            name = "收藏地点: ${addressLabel.take(50)}"
                        ) { saved ->
                            Toast.makeText(
                                context,
                                if (saved) "已收藏当前位置" else "收藏失败，请重试",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onSelectSavedRoute = { route ->
                        mapViewModel.selectRoute(route)
                        val first = route.waypoints.firstOrNull()
                        if (first != null) {
                            centerAimingCoord = first.latitude to first.longitude
                            val isGcj = currentMapType != MapSourceType.OPEN_STREET_MAP
                            val (dispLat, dispLon) = if (isGcj) CoordinateConverter.wgs84ToGcj02(first.latitude, first.longitude) else (first.latitude to first.longitude)
                            mapViewRef?.controller?.animateTo(GeoPoint(dispLat, dispLon))
                        }
                    }
                )
            } else if (activeMapTab == MapTab.ROUTE) {
                RouteBottomPanel(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { bottomPanelHeightPx = it.size.height },
                    stage = routeStage,
                    waypoints = drawnWaypoints,
                    simState = simState,
                    selectedSpeed = selectedSpeed,
                    isLiquidGlass = isLiquidGlass,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    onSearchQueryChange = { mapViewModel.performSearch(it) },
                    onSearchResultSelect = { item ->
                        val isGcj = currentMapType != MapSourceType.OPEN_STREET_MAP
                        val (dispLat, dispLon) = if (isGcj) CoordinateConverter.wgs84ToGcj02(item.latitude, item.longitude) else (item.latitude to item.longitude)
                        mapViewRef?.controller?.apply {
                            setZoom(16.5)
                            animateTo(GeoPoint(dispLat, dispLon))
                        }
                        centerAimingCoord = item.latitude to item.longitude
                        mapViewModel.clearSearch()
                    },
                    onClearSearch = { mapViewModel.clearSearch() },
                    onAddWaypoint = {
                        mapViewModel.addWaypoint(activeCoord.first, activeCoord.second)
                    },
                    onFinishSelecting = {
                        if (drawnWaypoints.size >= 2) {
                            routeStage = RouteStage.READY
                        }
                    },
                    onUndoWaypoint = {
                        mapViewModel.removeLastWaypoint()
                    },
                    onClearWaypoints = {
                        mapViewModel.clearWaypoints()
                        routeStage = RouteStage.SELECTING
                    },
                    onReselect = {
                        routeStage = RouteStage.SELECTING
                    },
                    onOpenConfig = {
                        showRouteConfigDialog = true
                    },
                    onSaveRoute = {
                        showSaveDialog = true
                    },
                    onStartSimulation = {
                        ensurePermissionAndStart {
                            val route = mapViewModel.selectedRoute.value ?: com.mockrun.app.domain.model.Route(
                                name = "规划路线 (${drawnWaypoints.size}点)",
                                waypoints = drawnWaypoints
                            )
                            simulationViewModel.startSimulation(context, route, selectedSpeed)
                            routeStage = RouteStage.RUNNING
                            Toast.makeText(context, "路线模拟已开启！", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPauseSimulation = {
                        simulationViewModel.pauseSimulation(context)
                    },
                    onResumeSimulation = {
                        simulationViewModel.resumeSimulation(context)
                    },
                    onStopSimulation = {
                        simulationViewModel.stopSimulation(context)
                        routeStage = RouteStage.READY
                        Toast.makeText(context, "已停止模拟", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    if (showRouteConfigDialog) {
        RouteConfigDialog(
            currentSpeed = selectedSpeed,
            isCadenceEnabled = isCadenceEnabled,
            isRootAvailable = isRootAvailable,
            onSpeedChange = { speed ->
                selectedSpeed = speed
                if (simState.status is com.mockrun.app.domain.model.SimulationStatus.Running) {
                    simulationViewModel.setSpeed(context, speed)
                }
            },
            onCadenceToggle = { enabled ->
                isCadenceEnabled = enabled
                simulationViewModel.setCadenceEnabled(enabled, selectedSpeed)
            },
            onDismiss = { showRouteConfigDialog = false },
            isLiquidGlass = isLiquidGlass
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存路线", fontWeight = FontWeight.Bold, color = IosColors.Label) },
            containerColor = IosColors.SecondaryGroupedBackground,
            text = {
                OutlinedTextField(
                    value = routeNameInput,
                    onValueChange = { routeNameInput = it },
                    label = { Text("路线名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    mapViewModel.saveCurrentRoute(routeNameInput)
                    showSaveDialog = false
                    routeNameInput = ""
                }) {
                    Text("保存", color = IosBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消", color = IosGray)
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    // 收藏夹：右上角入口打开，内容随当前标签切换（定位=地点收藏，路线=航线收藏）
    if (showBookmarkSheet) {
        val isLocationTab = activeMapTab == MapTab.LOCATION
        BookmarkBottomSheet(
            kind = if (isLocationTab) BookmarkKind.LOCATION else BookmarkKind.TRACK,
            items = if (isLocationTab) bookmarkedLocations else savedTracks,
            selectedRouteId = selectedRoute?.id,
            onDismissRequest = { showBookmarkSheet = false },
            onSelect = { route ->
                showBookmarkSheet = false
                val target = route.waypoints.firstOrNull()
                if (target != null) {
                    val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                    val (tLat, tLon) = if (isGcjMap) {
                        CoordinateConverter.wgs84ToGcj02(target.latitude, target.longitude)
                    } else {
                        target.latitude to target.longitude
                    }
                    mapViewRef?.controller?.apply {
                        setZoom(16.5)
                        animateTo(GeoPoint(tLat, tLon))
                    }
                    centerAimingCoord = target.latitude to target.longitude
                    if (isLocationTab) {
                        // 地点收藏：只把准星与目标点移到该坐标。收藏点是单点，
                        // 不写入 _selectedRoute / _drawnWaypoints，避免污染航线状态。
                        simulationViewModel.updateSelectedTarget(target.latitude, target.longitude)
                        Toast.makeText(context, "已跳转到收藏地点", Toast.LENGTH_SHORT).show()
                    } else {
                        // 航线收藏：载入为当前路线，行为与路线库一致
                        mapViewModel.selectRoute(route)
                        Toast.makeText(context, "已载入航线「${route.name}」", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = { route -> mapViewModel.deleteRoute(route.id) }
        )
    }

    if (showSearchDialog) {
        SearchLocationDialog(
            mapViewModel = mapViewModel,
            onDismissRequest = { showSearchDialog = false },
            onSelectLocation = { lat, lon, name ->
                val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                val (tLat, tLon) = if (isGcjMap) CoordinateConverter.wgs84ToGcj02(lat, lon) else lat to lon
                mapViewRef?.controller?.apply {
                    setZoom(16.5)
                    animateTo(GeoPoint(tLat, tLon))
                }
                centerAimingCoord = lat to lon
                showSearchDialog = false
                Toast.makeText(context, "已定位至：$name", Toast.LENGTH_SHORT).show()
            },
            onSetAsOrigin = { lat, lon ->
                mapViewModel.setRoadOrigin(WayPoint(lat, lon))
                showSearchDialog = false
            },
            onSetAsDestination = { lat, lon ->
                mapViewModel.setRoadDestination(WayPoint(lat, lon))
                showSearchDialog = false
            },
            onDirectMock = { lat, lon ->
                ensurePermissionAndStart {
                    simulationViewModel.startPointMock(context, lat, lon)
                    val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                    val (tLat, tLon) = if (isGcjMap) CoordinateConverter.wgs84ToGcj02(lat, lon) else lat to lon
                    mapViewRef?.controller?.apply {
                        setZoom(16.5)
                        animateTo(GeoPoint(tLat, tLon))
                    }
                    centerAimingCoord = lat to lon
                    showSearchDialog = false
                    Toast.makeText(context, "已开启即时虚拟定位！", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showRoadRouteDialog) {
        RoadRouteDialog(
            mapViewModel = mapViewModel,
            simulationViewModel = simulationViewModel,
            realLocation = userRealLocation,
            selectedTapPoint = centerAimingCoord,
            pointMockLocation = pointMockLocation,
            onDismissRequest = { showRoadRouteDialog = false },
            onNavigateToOrigin = { lat, lon ->
                val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                val (tLat, tLon) = if (isGcjMap) CoordinateConverter.wgs84ToGcj02(lat, lon) else lat to lon
                mapViewRef?.controller?.apply {
                    setZoom(16.5)
                    animateTo(GeoPoint(tLat, tLon))
                }
            }
        )
    }

    if (showMapAppPickerSheet) {
        AppPickerBottomSheet(
            onDismissRequest = { showMapAppPickerSheet = false },
            existingRules = multiTargetRules,
            initialLatitude = centerAimingCoord.first,
            initialLongitude = centerAimingCoord.second,
            onAppSelected = { newRule ->
                simulationViewModel.addOrUpdateMultiTargetRule(newRule)
                simulationViewModel.setActiveTargetKey(newRule.key)
                centerAimingCoord = newRule.latitude to newRule.longitude
                val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                val (tLat, tLon) = if (isGcjMap) CoordinateConverter.wgs84ToGcj02(newRule.latitude, newRule.longitude) else (newRule.latitude to newRule.longitude)
                mapViewRef?.controller?.animateTo(GeoPoint(tLat, tLon))
                Toast.makeText(context, "已为 ${newRule.appName} 添加独立定位分流", Toast.LENGTH_SHORT).show()
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
