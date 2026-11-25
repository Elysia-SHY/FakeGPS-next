package com.mockrun.app.feature.map
import androidx.compose.material3.MaterialTheme
import com.mockrun.app.core.designsystem.AppSemanticColors

import com.mockrun.app.feature.map.SearchLocationDialog
import com.mockrun.app.feature.map.RoadRouteDialog
import com.mockrun.app.feature.map.MapFloatingActions
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mockrun.app.feature.map.tabs.LocationControlPanel
import com.mockrun.app.feature.map.tabs.RouteBottomPanel
import com.mockrun.app.feature.map.tabs.RouteConfigDialog
import com.mockrun.app.feature.map.tabs.RouteStage
import com.mockrun.app.feature.track.TrackRecordScreen
import com.mockrun.app.ui.viewmodel.TrackViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.mockrun.app.ui.components.PermissionGuideDialog
import com.mockrun.app.hook.HookChannelStatus
import com.mockrun.app.hook.XposedStatusHelper
import com.mockrun.app.util.Diag
import com.mockrun.app.util.HookChannelPrefs
import com.mockrun.app.util.PermissionIssueType
import com.mockrun.app.util.logFailure
import com.mockrun.app.core.designsystem.LiquidGlassDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** 反向地理编码尚未返回时的占位文案；收藏命名据此判断地址是否可用。 */
private const val ADDRESS_PLACEHOLDER = "正在获取当前地址..."

/** 中央准星 Pin 块的固有高度（dp），只用于让水滴尖端对准屏幕中心。 */
private val CENTER_PIN_BLOCK_HEIGHT = 83.dp

/** Pin 尖端相对 Pin 块中心的固定偏移，与抽屉高度完全无关。 */
private val PIN_TIP_BELOW_BLOCK_CENTER = CENTER_PIN_BLOCK_HEIGHT / 2 - 8.dp

private val CROSSHAIR_FADE_BAND = 48.dp

/**
 * Crosshair opacity is derived only from whether the drawer has reached the screen centre. The
 * crosshair position never reads drawer height, so dragging the sheet cannot push it toward the
 * status bar.
 */
internal fun crosshairAlphaForDrawer(
    containerHeightPx: Int,
    panelHeightPx: Int,
    fadeBandPx: Int
): Float {
    if (containerHeightPx <= 0 || panelHeightPx <= 0) return 1f
    val drawerTop = containerHeightPx - panelHeightPx
    val center = containerHeightPx / 2f
    val halfBand = fadeBandPx.coerceAtLeast(1) / 2f
    return ((drawerTop - center + halfBand) / (halfBand * 2f)).coerceIn(0f, 1f)
}

/**
 * 右列悬浮控件（缩放胶囊 + 物理定位 FAB）的几何常量，与实现逐条对齐：
 * - 54dp / 12dp 来自 MapFloatingActions（MapFloatingActions.kt:54「全览路线」FAB、
 *   :80「真机物理定位」FAB，以及它自己 Column 的 spacedBy(12.dp)）。
 *   ROUTE 且航点 >= 2 时两个圆同时在列里，所以那一档 FAB 块高 = 54 + 12 + 54 = 120dp。
 * - 缩放控件固定为 176dp，绝不随抽屉高度压缩。
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

private data class RightControlsVisibility(
    val zoom: Boolean,
    val actions: Boolean
)

/**
 * 应用分流胶囊行（「全局通用」+ 各应用分流 +「添加分流」）的固有高度 [估算]：
 * 胶囊 48dp + LazyRow 上下各 4dp 内边距 = 56dp（顶部 Column 的第二行，仅定位页有）。
 * 与 TOP_ICON_ROW_HEIGHT 一样，只用于 onGloballyPositioned 首次回填之前的那一帧。
 */
private val TOP_CAPSULE_ROW_HEIGHT = 56.dp

enum class MapTab {
    LOCATION,
    ROUTE
}

internal enum class MapOverlayMode {
    SINGLE_LOCATION,
    ROUTE
}

internal fun overlayModeFor(tab: MapTab): MapOverlayMode = when (tab) {
    MapTab.LOCATION -> MapOverlayMode.SINGLE_LOCATION
    MapTab.ROUTE -> MapOverlayMode.ROUTE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    mapViewModel: MapViewModel,
    simulationViewModel: SimulationViewModel,
    initialTab: MapTab = MapTab.LOCATION,
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
    // 未保存的手绘路线也需要独立保存运行方式，不能只依赖已保存路线的 mode。
    var draftRouteMode by remember { mutableStateOf(com.mockrun.app.domain.model.RouteMode.ONE_WAY) }
    var isCadenceEnabled by remember { mutableStateOf(false) }
    var showRouteConfigDialog by remember { mutableStateOf(false) }

    val drawnWaypoints by mapViewModel.drawnWaypoints.collectAsState()
    val simState by simulationViewModel.state.collectAsState()
    val joystickLocation by simulationViewModel.joystickLocation.collectAsState()
    // 收藏夹：定位标签读地点收藏，路线标签读航线收藏
    val bookmarkedLocations by mapViewModel.bookmarkedLocations.collectAsState()
    val savedTracks by mapViewModel.savedTracks.collectAsState()
    val selectedRoute by mapViewModel.selectedRoute.collectAsState()

    // 选中已保存路线时同步草稿模式；否则加载后直接保存会把原模式改回单程。
    LaunchedEffect(selectedRoute?.id, selectedRoute?.mode) {
        selectedRoute?.let { draftRouteMode = it.mode }
    }

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
    val sensorState by simulationViewModel.sensorState.collectAsState()
    // v2.4.1：轨迹录制从「关于 → 路线库」提到路线面板，直接可见
    val trackViewModel: TrackViewModel = hiltViewModel()
    // rememberSaveable：横竖屏切换会重建 Activity，用 remember 会把录制页直接关掉
    var showTrackScreen by rememberSaveable { mutableStateOf(false) }

    // ---- 生效通道指示 ----
    // 地图页的「虚拟定位中」只说明应用层在模拟；hook 可能没生效（LSPosed 未激活、作用域没勾）。
    // 这里把真实通道算出来显示，避免"以为在伪造、实际是真值"。
    var lsposedAlive by remember { mutableStateOf(XposedStatusHelper.isLsposedHookReallyActive(context)) }
    val hookChannelPref = remember(isPointMockActive) { HookChannelPrefs.get(context) }
    LaunchedEffect(isPointMockActive) {
        // 只有正在模拟时才轮询：逻辑定位停止后就没人看了，不必持续 IPC。
        while (isPointMockActive) {
            lsposedAlive = XposedStatusHelper.isLsposedHookReallyActive(context)
            delay(3000)
        }
        lsposedAlive = XposedStatusHelper.isLsposedHookReallyActive(context)
    }
    val hookChannelStatus = remember(hookChannelPref, lsposedAlive, isPointMockActive) {
        HookChannelStatus.resolve(
            preference = hookChannelPref,
            isLsposedAlive = lsposedAlive,
            // 应用侧发布的"是否 active"镜像：框架活着但没拿到配置时，这里仍是 false
            hookConfigActive = com.mockrun.app.hook.HookStateBridge.isHookActive,
            isMockActive = isPointMockActive || isJoystickRunning
        )
    }
    var showMapAppPickerSheet by remember { mutableStateOf(false) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var routeNameInput by remember { mutableStateOf("") }
    var currentMapType by remember { mutableStateOf(MapSourceType.AUTONAVI_AUTO) }
    var showMapTypeMenu by remember { mutableStateOf(false) }
    var showBookmarkSheet by remember { mutableStateOf(false) }

    val isSystemDark = isSystemInDarkTheme()
    val shouldApplyDarkMap = when (currentMapType) {
        MapSourceType.AUTONAVI_AUTO -> isSystemDark
        MapSourceType.AUTONAVI_DARK -> true
        else -> false
    }
    val coroutineScope = rememberCoroutineScope()
    var updateCenterJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var userRealLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locationAimingCoord by remember {
        mutableStateOf(
            pointMockLocation?.let { it.latitude to it.longitude }
                ?: selectedTargetLocation?.let { it.latitude to it.longitude }
                ?: (39.9042 to 116.4074)
        )
    }
    var routeAimingCoord by remember {
        mutableStateOf(
            selectedRoute?.waypoints?.firstOrNull()?.let { it.latitude to it.longitude }
                ?: (39.9042 to 116.4074)
        )
    }
    var currentAddressText by remember { mutableStateOf(ADDRESS_PLACEHOLDER) }
    var currentZoom by remember { mutableDoubleStateOf(16.0) }

    // 中央 Pin 固定在屏幕中心；抽屉实测高度只用于计算覆盖后的淡出透明度。
    var mapBoxHeightPx by remember { mutableIntStateOf(0) }
    var bottomPanelHeightPx by remember { mutableIntStateOf(0) }
    // 顶部控件簇（状态栏 + 地址条 + 定位页的应用分流胶囊行）的实测高度。
    // 与 bottomPanelHeightPx 一起构成本页「地图可用区」的上下边界：
    // 右列悬浮控件的顶边限制线与底部抽屉的高度上限都由它推导。
    var topChromeHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val selectionColors = AppSelectionColors.current
    val pageBackdrop = if (isLiquidGlass) rememberLayerBackdrop() else null

    // 顶部安全区高度（只用于 onGloballyPositioned 首次回填之前的兜底估算）。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 顶部控件簇的实测高度 = 状态栏 + 地址条（56dp）+ 定位页的应用分流胶囊行（56dp）。
    // 首帧还没有实测值时用常量兜底，之后完全跟随实测（旋转、平板、字体缩放都会自动跟上）。
    // 这是「地图可用区」的上边界：右列悬浮控件与底部抽屉都不许越过它。
    val topChromeFallback = statusBarTop + TOP_ICON_ROW_HEIGHT +
        (if (activeMapTab == MapTab.LOCATION) TOP_CAPSULE_ROW_HEIGHT else 0.dp)
    val topChromeHeight = if (topChromeHeightPx > 0) {
        with(density) { topChromeHeightPx.toDp() }
    } else {
        topChromeFallback
    }
    // The location drawer may expand under the secondary app-diversion capsule row, but never
    // under the primary address/tool card. Route mode has no secondary row.
    val panelTopChromeHeight = if (activeMapTab == MapTab.LOCATION) {
        (topChromeHeight - TOP_CAPSULE_ROW_HEIGHT)
            .coerceAtLeast(statusBarTop + TOP_ICON_ROW_HEIGHT)
    } else {
        topChromeHeight
    }

    // 右列顶边不得越过的限制线 = 顶部控件簇实测底边 + 呼吸。
    // 旧实现写死「状态栏 + 56dp 图标行 + 12dp」，漏掉了定位页第二行的应用分流胶囊行
    // （它的底边比图标行再低 56dp）：抽屉长高、右列被压缩上移时，缩放控件会被顶进
    // 胶囊行里盖住「添加分流」等入口。改为跟随实测高度后这条重叠在物理上不可能发生。
    val rightColumnTopLimit = topChromeHeight + TOP_MARGIN

    val collapsedPanelFallback = ScreenInsets.bottomBarHeight +
        if (activeMapTab == MapTab.LOCATION) 96.dp else 84.dp

    // The zoom capsule has a fixed 176dp geometry. If the drawer leaves less than that (plus the
    // FAB block and breathing room), it fades out instead of being squeezed. derivedStateOf keeps
    // the map page out of per-frame recomposition while the drawer itself is being dragged.
    val rightControlsVisibility by remember(
        activeMapTab,
        drawnWaypoints.size,
        density,
        rightColumnTopLimit
    ) {
        derivedStateOf {
            val fabBlock = if (activeMapTab == MapTab.ROUTE && drawnWaypoints.size >= 2) {
                RIGHT_FAB_BLOCK_ROUTE
            } else {
                RIGHT_FAB_SIZE
            }
            if (mapBoxHeightPx == 0 || bottomPanelHeightPx == 0) {
                RightControlsVisibility(zoom = true, actions = true)
            } else {
                with(density) {
                    val available = mapBoxHeightPx.toDp() - bottomPanelHeightPx.toDp() -
                        RIGHT_PANEL_GAP - rightColumnTopLimit
                    RightControlsVisibility(
                        zoom = available >= MapVerticalZoomControlHeight + RIGHT_FAB_GAP + fabBlock,
                        actions = available >= fabBlock
                    )
                }
            }
        }
    }

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
                locationAimingCoord = lat to lon
            }
        }
    }

    // Page-local aiming coordinates. Route waypoints must never overwrite the single-location
    // target merely because the two tabs share one MapView instance.
    val activeCoord = if (activeMapTab == MapTab.LOCATION) {
        locationAimingCoord
    } else {
        routeAimingCoord
    }
    val mapOverlayMode = overlayModeFor(activeMapTab)

    fun setActiveAimingCoord(latitude: Double, longitude: Double) {
        if (activeMapTab == MapTab.LOCATION) {
            locationAimingCoord = latitude to longitude
            if (activeTargetKey == null) {
                simulationViewModel.updateSelectedTarget(latitude, longitude)
            }
        } else {
            routeAimingCoord = latitude to longitude
        }
    }

    LaunchedEffect(activeCoord) {
        currentAddressText = AddressResolver.resolveAddress(context, activeCoord.first, activeCoord.second)
    }

    LaunchedEffect(activeMapTab, mapViewRef) {
        bottomPanelHeightPx = 0
        val mapView = mapViewRef ?: return@LaunchedEffect
        val target = if (activeMapTab == MapTab.LOCATION) {
            locationAimingCoord
        } else {
            routeAimingCoord
        }
        val (displayLat, displayLon) = if (currentMapType == MapSourceType.OPEN_STREET_MAP) {
            target
        } else {
            CoordinateConverter.wgs84ToGcj02(target.first, target.second)
        }
        mapView.controller.setCenter(GeoPoint(displayLat, displayLon))
        mapView.invalidate()
    }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            mapViewModel.importGpx(context, it, "导入GPX路线") { count ->
                if (count < 2) {
                    Toast.makeText(context, "GPX 解析失败或点数不足", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** The crosshair is fixed at the map centre, so the aiming coordinate is the centre point. */
    fun recomputeAimingFromMapCenter() {
        val mapView = mapViewRef ?: return
        if (mapView.width <= 0 || mapView.height <= 0) return
        val centerGeo = runCatching {
            mapView.projection.fromPixels(mapView.width / 2, mapView.height / 2)
        }
            .logFailure("MapScreen", "project map centre to GeoPoint", Diag.Level.DEBUG)
            .getOrNull() ?: return
        val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
        val (wgsLat, wgsLon) = if (isGcjMap) {
            CoordinateConverter.gcj02ToWgs84(centerGeo.latitude, centerGeo.longitude)
        } else {
            centerGeo.latitude to centerGeo.longitude
        }
        setActiveAimingCoord(wgsLat, wgsLon)
    }

    // AndroidView.factory stores its first lambda. These holders keep map gestures bound to the
    // currently selected page instead of the tab that happened to create the MapView.
    val latestSetActiveAimingCoord = rememberUpdatedState<(Double, Double) -> Unit>(
        newValue = { latitude, longitude ->
            setActiveAimingCoord(latitude, longitude)
        }
    )
    val latestRecomputeAiming = rememberUpdatedState<() -> Unit>(
        newValue = { recomputeAimingFromMapCenter() }
    )
    val latestMapType = rememberUpdatedState(currentMapType)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    mapViewRef?.onResume()
                    coroutineScope.launch { simulationViewModel.refreshRootAvailability() }
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    CompositionLocalProvider(LocalGlassBackdrop provides pageBackdrop) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { mapBoxHeightPx = it.size.height }
    ) {
        // The page content is captured once by AppNavigation's shared Backdrop.
        // Map chrome must stay a sibling of that source, never sample itself.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (pageBackdrop != null) {
                        Modifier.layerBackdrop(pageBackdrop)
                    } else {
                        Modifier
                    }
                )
        ) {
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
                        val initCoord = activeCoord
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
                                    latestRecomputeAiming.value()
                                }
                                return false
                            }
                        })

                        val receiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                p?.let {
                                    val (wgsLat, wgsLon) = if (latestMapType.value == MapSourceType.OPEN_STREET_MAP) {
                                        it.latitude to it.longitude
                                    } else {
                                        CoordinateConverter.gcj02ToWgs84(it.latitude, it.longitude)
                                    }
                                    if (continuousDrawRef.value) {
                                        mapViewModel.addWaypoint(wgsLat, wgsLon)
                                    } else {
                                        mapViewRef?.controller?.animateTo(it)
                                        latestSetActiveAimingCoord.value(wgsLat, wgsLon)
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

                    // Overlay ownership is exclusive: every update clears both marker and polyline
                    // layers, then redraws only the active page's mode.
                    mapView.overlays.filterIsInstance<Polyline>().forEach(mapView.overlays::remove)
                    mapView.overlays.filterIsInstance<Marker>().forEach(mapView.overlays::remove)

                    fun displayPoint(latitude: Double, longitude: Double): GeoPoint {
                        return if (isGcjMap) {
                            val (gcjLat, gcjLon) = CoordinateConverter.wgs84ToGcj02(latitude, longitude)
                            GeoPoint(gcjLat, gcjLon)
                        } else {
                            GeoPoint(latitude, longitude)
                        }
                    }

                    when (mapOverlayMode) {
                        MapOverlayMode.ROUTE -> {
                            if (drawnWaypoints.size >= 2) {
                                val polyline = Polyline().apply {
                                    setPoints(drawnWaypoints.map {
                                        displayPoint(it.latitude, it.longitude)
                                    })
                                    outlinePaint.color = android.graphics.Color.parseColor("#34C759")
                                    outlinePaint.strokeWidth = 12f
                                }
                                mapView.overlays.add(polyline)
                            }

                            drawnWaypoints.forEachIndexed { index, waypoint ->
                                val isTerminal = index == 0 || index == drawnWaypoints.lastIndex
                                val waypointMarker = Marker(mapView).apply {
                                    position = displayPoint(waypoint.latitude, waypoint.longitude)
                                    icon = MapPinHelper.getWaypointPin(context, index + 1, isTerminal)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    title = "航点 ${index + 1}"
                                    snippet = "${"%.5f".format(waypoint.latitude)}, ${"%.5f".format(waypoint.longitude)}"
                                }
                                mapView.overlays.add(waypointMarker)
                            }

                            simState.currentWayPoint?.let { progress ->
                                val runner = Marker(mapView).apply {
                                    position = displayPoint(progress.latitude, progress.longitude)
                                    icon = MapPinHelper.getRunnerPin(context)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    title = "路线巡航实时位置"
                                    rotation = simState.bearing
                                }
                                mapView.overlays.add(runner)
                            }

                            roadOrigin?.let { origin ->
                                val marker = Marker(mapView).apply {
                                    position = displayPoint(origin.latitude, origin.longitude)
                                    icon = MapPinHelper.getOriginPin(context)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "沿路规划起点"
                                }
                                mapView.overlays.add(marker)
                            }
                            roadDestination?.let { destination ->
                                val marker = Marker(mapView).apply {
                                    position = displayPoint(destination.latitude, destination.longitude)
                                    icon = MapPinHelper.getDestinationPin(context)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "沿路规划终点"
                                }
                                mapView.overlays.add(marker)
                            }
                        }

                        MapOverlayMode.SINGLE_LOCATION -> {
                            val activePoint = pointMockLocation
                            if (activePoint != null) {
                                val marker = Marker(mapView).apply {
                                    position = displayPoint(activePoint.latitude, activePoint.longitude)
                                    icon = MapPinHelper.getActiveMockPin(context)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "虚拟定位驻留点"
                                }
                                mapView.overlays.add(marker)
                            } else {
                                selectedTargetLocation?.let { target ->
                                    val marker = Marker(mapView).apply {
                                        position = displayPoint(target.latitude, target.longitude)
                                        icon = MapPinHelper.getSelectedPin(context)
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        title = "当前目标虚拟定位点"
                                    }
                                    mapView.overlays.add(marker)
                                }
                            }

                            if (isJoystickRunning) {
                                joystickLocation?.let { joystick ->
                                    val marker = Marker(mapView).apply {
                                        position = displayPoint(joystick.latitude, joystick.longitude)
                                        icon = MapPinHelper.getJoystickPin(context)
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                        title = "万向摇杆位置"
                                    }
                                    mapView.overlays.add(marker)
                                }
                            }

                            multiTargetRules.filter { it.isEnabled }.forEach { rule ->
                                val colorInt = runCatching { android.graphics.Color.parseColor(rule.colorHex) }
                                    .logFailure(
                                        "MapScreen",
                                        "parse rule color (${rule.appName})",
                                        Diag.Level.DEBUG
                                    )
                                    .getOrDefault(android.graphics.Color.parseColor("#007AFF"))
                                val marker = Marker(mapView).apply {
                                    position = displayPoint(rule.latitude, rule.longitude)
                                    icon = MapPinHelper.getAppPin(context, colorInt)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "${rule.appName} · 分流定位"
                                    snippet = "坐标: ${"%.5f".format(rule.latitude)}, ${"%.5f".format(rule.longitude)}"
                                    setOnMarkerClickListener { _, _ ->
                                        simulationViewModel.setActiveTargetKey(rule.key)
                                        locationAimingCoord = rule.latitude to rule.longitude
                                        mapView.controller.animateTo(
                                            displayPoint(rule.latitude, rule.longitude)
                                        )
                                        true
                                    }
                                }
                                mapView.overlays.add(marker)
                            }

                            val realLoc = userRealLocation
                                ?: realPhysicalLocation?.let { it.latitude to it.longitude }
                            realLoc?.let { (latitude, longitude) ->
                                val marker = Marker(mapView).apply {
                                    position = displayPoint(latitude, longitude)
                                    icon = MapPinHelper.getRealLocationPuck(context)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    title = "当前真实物理位置"
                                    snippet = "硬件 GPS 坐标: ${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}"
                                    setOnMarkerClickListener { _, _ ->
                                        locationAimingCoord = latitude to longitude
                                        simulationViewModel.updateSelectedTarget(latitude, longitude)
                                        true
                                    }
                                }
                                mapView.overlays.add(marker)
                            }
                        }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (isSystemDark) 0.035f else 0.025f
                                ),
                                Color.Transparent,
                                MaterialTheme.colorScheme.scrim.copy(
                                    alpha = if (isSystemDark) 0.08f else 0.025f
                                )
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(
                    MaterialTheme.colorScheme.background.copy(
                        alpha = if (isSystemDark) 0.58f else 0.68f
                    )
                )
        )

        // 1. Top Floating Controls Bar & Multi-Target App Capsules
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    // 实测顶部控件簇高度（含 statusBarsPadding 的安全区），
                    // 作为「地图可用区」上边界、右列顶边限制线与抽屉高度上限的输入。
                    // 必须挂在 statusBarsPadding() 之前：挂在之后量到的是不含状态栏的内容高。
                    .onGloballyPositioned { topChromeHeightPx = it.size.height }
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
                    } ?: MaterialTheme.colorScheme.primary

                    AtlasSurface(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable {
                                val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                                val (tLat, tLon) = if (isGcjMap) CoordinateConverter.wgs84ToGcj02(activeCoord.first, activeCoord.second) else activeCoord
                                mapViewRef?.controller?.apply {
                                    setZoom(16.5)
                                    animateTo(GeoPoint(tLat, tLon))
                                }
                            },
                        isLiquidGlass = isLiquidGlass,
                        shape = AppShapes.card,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        elevation = if (isLiquidGlass) 12.dp else 4.dp
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
                                    .clip(AppShapes.iconLg)
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
                            // 主次文：主 = 当前目标，次 = 当前状态 + 地址。
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
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = topSub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    AtlasSurface(
                        isLiquidGlass = isLiquidGlass,
                        shape = AppShapes.card,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        elevation = if (isLiquidGlass) 12.dp else 4.dp
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
                                    Icon(Icons.Default.Layers, contentDescription = "切换底图API", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
                                                    color = if (type == currentMapType) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 轨迹录制入口（v2.4.1）：录真实 GPS 成路线，**不依赖先规划航点**
                            if (activeMapTab == MapTab.ROUTE) {
                                IconButton(
                                    onClick = { showTrackScreen = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FiberManualRecord,
                                        contentDescription = "轨迹录制",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
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
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMoreToolsMenu,
                                        onDismissRequest = { showMoreToolsMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("沿路规划", color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                showMoreToolsMenu = false
                                                showRoadRouteDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("导入 GPX 文件", color = MaterialTheme.colorScheme.onSurface) },
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
                            AtlasSurface(
                                isLiquidGlass = isLiquidGlass,
                                shape = AppShapes.pill,
                                containerColor = if (isSelected) {
                                    selectionColors.primaryContainer
                                } else {
                                    selectionColors.unselectedContainer
                                },
                                contentColor = if (isSelected) {
                                    selectionColors.onPrimaryContainer
                                } else {
                                    selectionColors.unselectedContent
                                },
                                elevation = if (isSelected) 8.dp else 3.dp,
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable {
                                        simulationViewModel.setActiveTargetKey(null)
                                        val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                                        val targetCoord = pointMockLocation?.let { p -> p.latitude to p.longitude }
                                            ?: selectedTargetLocation?.let { s -> s.latitude to s.longitude }
                                            ?: locationAimingCoord
                                        locationAimingCoord = targetCoord
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) {
                                            selectionColors.onPrimaryContainer
                                        } else {
                                            selectionColors.unselectedContent
                                        }
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
                            val defaultRuleColor = MaterialTheme.colorScheme.primary
                            val ruleColor = remember(rule.colorHex) {
                                runCatching { Color(android.graphics.Color.parseColor(rule.colorHex)) }
                                    .logFailure("MapScreen", "parse rule color chip", Diag.Level.DEBUG)
                                    .getOrDefault(defaultRuleColor)
                            }
                            AtlasSurface(
                                isLiquidGlass = isLiquidGlass,
                                shape = AppShapes.pill,
                                containerColor = if (isSelected) {
                                    selectionColors.secondaryContainer
                                } else {
                                    selectionColors.unselectedContainer
                                },
                                contentColor = if (isSelected) {
                                    selectionColors.onSecondaryContainer
                                } else {
                                    selectionColors.unselectedContent
                                },
                                elevation = if (isSelected) 8.dp else 3.dp,
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable {
                                        simulationViewModel.setActiveTargetKey(rule.key)
                                        locationAimingCoord = rule.latitude to rule.longitude
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
                                        color = if (rule.isEnabled) ruleColor else MaterialTheme.colorScheme.outline
                                    ) {}
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = buildString {
                                            if (rule.userId != 0) append("${rule.appName} (${rule.userId})") else append(rule.appName)
                                            if (!rule.isEnabled) append(" (暂停)")
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            selectionColors.onSecondaryContainer
                                        } else if (rule.isEnabled) {
                                            selectionColors.unselectedContent
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }

                        // Item N+1: Add App Capsule
                        item {
                            AtlasSurface(
                                isLiquidGlass = isLiquidGlass,
                                shape = AppShapes.pill,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.primary,
                                elevation = 3.dp,
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable { showMapAppPickerSheet = true }
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
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "添加分流",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2 + 5. 右侧悬浮控件列。缩放条固定高度，空间不足时淡出，绝不压缩轨道。
            Column(
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
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RIGHT_FAB_GAP)
            ) {
                AnimatedVisibility(
                    visible = rightControlsVisibility.zoom,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(120))
                ) {
                    MapVerticalZoomControl(
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
                }

                AnimatedVisibility(
                    visible = rightControlsVisibility.actions,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(120))
                ) {
                    MapFloatingActions(
                        context = context,
                        activeMapTab = activeMapTab,
                        drawnWaypoints = drawnWaypoints,
                        currentMapType = currentMapType,
                        mapViewRef = mapViewRef,
                        isPointMockActive = isPointMockActive,
                        isJoystickRunning = isJoystickRunning,
                        simState = simState,
                        simulationViewModel = simulationViewModel,
                        onResetToRealLocation = { real ->
                            userRealLocation = real
                            simulationViewModel.updateRealPhysicalLocation(real.first, real.second)
                            simulationViewModel.updateSelectedTarget(real.first, real.second)
                            locationAimingCoord = real
                        }
                    )
                }
            }

            // 3. Dynamic Status Capsule (iOS Dynamic Island style)
            if (
                activeMapTab == MapTab.LOCATION &&
                isPointMockActive &&
                pointMockLocation != null
            ) {
                AtlasSurface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        // 旧值是写死的 76dp（= 状态栏 + 地址条），正好压在应用分流胶囊行上：
                        // 一旦开启虚拟定位，这条胶囊就把「全局通用 / 添加分流」盖住、点不到。
                        // 改为跟随实测的顶部控件簇底边，永远落在分流行下方。
                        .padding(top = topChromeHeight + 8.dp, start = 12.dp),
                    isLiquidGlass = isLiquidGlass,
                    shape = AppShapes.pill,
                    containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.90f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    elevation = if (isLiquidGlass) 16.dp else 6.dp
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
                                .background(AppSemanticColors.current.success)
                        )
                        Text(
                            "虚拟定位中: ${"%.4f".format(pointMockLocation!!.latitude)}, ${"%.4f".format(pointMockLocation!!.longitude)}",
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        // 生效通道徽标：明确告诉用户现在靠哪条通道伪造。
                        // LSPosed = 系统服务/SDK 一起被改写；"仅 Provider" = 只有应用层 mock，
                        // 地图与社交类应用很可能仍显示真实位置；"未生效" = 选了 LSPosed 但模块没起来。
                        Surface(
                            shape = AppShapes.chip,
                            color = when (hookChannelStatus.active) {
                                com.mockrun.app.hook.ActiveChannel.LSPOSED -> MaterialTheme.colorScheme.primaryContainer
                                com.mockrun.app.hook.ActiveChannel.PROVIDER_ONLY -> AppSemanticColors.current.warningContainer
                                com.mockrun.app.hook.ActiveChannel.NONE -> MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = when (hookChannelStatus.active) {
                                com.mockrun.app.hook.ActiveChannel.LSPOSED -> MaterialTheme.colorScheme.onPrimaryContainer
                                com.mockrun.app.hook.ActiveChannel.PROVIDER_ONLY -> AppSemanticColors.current.onWarningContainer
                                com.mockrun.app.hook.ActiveChannel.NONE -> MaterialTheme.colorScheme.onErrorContainer
                            }
                        ) {
                            Text(
                                text = hookChannelStatus.shortLabel,
                                color = if (hookChannelStatus.active == com.mockrun.app.hook.ActiveChannel.LSPOSED) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else if (hookChannelStatus.active == com.mockrun.app.hook.ActiveChannel.PROVIDER_ONLY) {
                                    AppSemanticColors.current.onWarningContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            onClick = {
                                simulationViewModel.stopPointMock(context)
                                Toast.makeText(context, "已停止虚拟定位", Toast.LENGTH_SHORT).show()
                            },
                            shape = AppShapes.pill,
                            color = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            modifier = Modifier
                                .minTouchTarget(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "停止定位",
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelSmall,
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
            val crosshairAlpha by animateFloatAsState(
                targetValue = crosshairAlphaForDrawer(
                    containerHeightPx = mapBoxHeightPx,
                    panelHeightPx = bottomPanelHeightPx,
                    fadeBandPx = with(density) { CROSSHAIR_FADE_BAND.roundToPx() }
                ),
                animationSpec = tween(durationMillis = 120),
                label = "crosshairDrawerCoverage"
            )
            AnimatedVisibility(
                visible = activeMapTab == MapTab.LOCATION || (activeMapTab == MapTab.ROUTE && routeStage == RouteStage.SELECTING),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val currentActiveRule = if (activeMapTab == MapTab.LOCATION) multiTargetRules.find { it.key == activeTargetKey } else null
                val currentAimingColor = currentActiveRule?.let {
                    runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }
                        .logFailure("MapScreen", "parse aiming-color", Diag.Level.DEBUG)
                        .getOrNull()
                } ?: MaterialTheme.colorScheme.primary

                // The tip is always pinned to the geometric screen centre. Only opacity responds to
                // drawer coverage; the map projection and injected coordinate stay centre-based.
                IosCenterPin(
                    label = if (currentActiveRule != null) "【${currentActiveRule.appName}】分流选点"
                            else if (activeMapTab == MapTab.ROUTE) "路线航点选点"
                            else "目标定位点",
                    pinColor = currentAimingColor,
                    icon = if (currentActiveRule != null) Icons.AutoMirrored.Filled.AltRoute else Icons.Default.Place,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = 0,
                                y = -PIN_TIP_BELOW_BLOCK_CENTER.roundToPx()
                            )
                        }
                        .graphicsLayer { alpha = crosshairAlpha }
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
                    modifier = Modifier.align(Alignment.BottomCenter),
                    topChromeHeight = panelTopChromeHeight,
                    onPanelHeightChanged = { height ->
                        bottomPanelHeightPx = height
                    },
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
                    activeRule = multiTargetRules.find { it.key == activeTargetKey },
                    onSetTargetLocation = { rule ->
                        if (!rule.isEnabled) {
                            simulationViewModel.toggleMultiTargetRule(rule.key, true)
                        }
                        simulationViewModel.updateMultiTargetLocation(rule.key, activeCoord.first, activeCoord.second)
                        Toast.makeText(context, "已设为【${rule.appName}】分流定位点：$currentAddressText", Toast.LENGTH_SHORT).show()
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
                        locationAimingCoord = item.latitude to item.longitude
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
                            locationAimingCoord = real
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
                        val first = route.waypoints.firstOrNull()
                        if (first != null) {
                            locationAimingCoord = first.latitude to first.longitude
                            simulationViewModel.updateSelectedTarget(first.latitude, first.longitude)
                            val isGcj = currentMapType != MapSourceType.OPEN_STREET_MAP
                            val (dispLat, dispLon) = if (isGcj) CoordinateConverter.wgs84ToGcj02(first.latitude, first.longitude) else (first.latitude to first.longitude)
                            mapViewRef?.controller?.animateTo(GeoPoint(dispLat, dispLon))
                        }
                    }
                )
            } else if (activeMapTab == MapTab.ROUTE) {
                RouteBottomPanel(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    topChromeHeight = topChromeHeight,
                    onPanelHeightChanged = { height ->
                        bottomPanelHeightPx = height
                    },
                    stage = routeStage,
                    waypoints = drawnWaypoints,
                    simState = simState,
                    selectedSpeed = selectedSpeed,
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
                        routeAimingCoord = item.latitude to item.longitude
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
                            val base = mapViewModel.selectedRoute.value ?: com.mockrun.app.domain.model.Route(
                                name = "规划路线 (${drawnWaypoints.size}点)",
                                waypoints = drawnWaypoints
                            )
                            val route = base.copy(mode = draftRouteMode)
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
            mode = draftRouteMode,
            closingGapMeters = com.mockrun.app.domain.model.Route(
                name = "规划路线", waypoints = drawnWaypoints
            ).closingGapMeters,
            onModeChange = { newMode ->
                draftRouteMode = newMode
                selectedRoute?.let { mapViewModel.updateRouteMode(it, newMode) }
                simulationViewModel.setRouteMode(context, newMode)
            },
            cadenceMode = sensorState.cadenceMode,
            customSpm = sensorState.customCadence,
            onCadenceConfigChange = { cm, spm ->
                simulationViewModel.setCadenceMode(cm, spm, selectedSpeed)
            }
        )
    }

    if (showTrackScreen) {
        TrackRecordScreen(
            trackViewModel = trackViewModel,
            onClose = { showTrackScreen = false },
            onRouteCreated = { route ->
                mapViewModel.selectRoute(route)
                routeStage = RouteStage.READY
                showTrackScreen = false
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Column {
                    GlassDialogWindowEffect(isLiquidGlass)
                    Text("保存路线", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = if (isLiquidGlass) {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            tonalElevation = 0.dp,
            text = {
                OutlinedTextField(
                    value = routeNameInput,
                    onValueChange = { routeNameInput = it },
                    label = { Text("路线名称") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    mapViewModel.saveCurrentRoute(routeNameInput, draftRouteMode)
                    showSaveDialog = false
                    routeNameInput = ""
                }) {
                    Text("保存", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = AppShapes.sheet
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
                    if (isLocationTab) {
                        // 地点收藏：只把准星与目标点移到该坐标。收藏点是单点，
                        // 不写入 _selectedRoute / _drawnWaypoints，避免污染航线状态。
                        locationAimingCoord = target.latitude to target.longitude
                        simulationViewModel.updateSelectedTarget(target.latitude, target.longitude)
                        Toast.makeText(context, "已跳转到收藏地点", Toast.LENGTH_SHORT).show()
                    } else {
                        // 航线收藏：载入为当前路线，行为与路线库一致
                        routeAimingCoord = target.latitude to target.longitude
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
                setActiveAimingCoord(lat, lon)
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
                    locationAimingCoord = lat to lon
                    simulationViewModel.updateSelectedTarget(lat, lon)
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
            selectedTapPoint = routeAimingCoord,
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
            initialLatitude = locationAimingCoord.first,
            initialLongitude = locationAimingCoord.second,
            onAppSelected = { newRule ->
                simulationViewModel.addOrUpdateMultiTargetRule(newRule)
                simulationViewModel.setActiveTargetKey(newRule.key)
                locationAimingCoord = newRule.latitude to newRule.longitude
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
