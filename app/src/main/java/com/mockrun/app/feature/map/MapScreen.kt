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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

/** 反向地理编码尚未返回时的占位文案；收藏命名据此判断地址是否可用。 */
private const val ADDRESS_PLACEHOLDER = "正在获取当前地址..."

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
    val savedRoutes by mapViewModel.savedRoutes.collectAsState()
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

    val roadOrigin by mapViewModel.roadOrigin.collectAsState()
    val roadDestination by mapViewModel.roadDestination.collectAsState()

    var showSearchDialog by remember { mutableStateOf(false) }
    var showRoadRouteDialog by remember { mutableStateOf(false) }
    var isContinuousDrawMode by remember { mutableStateOf(false) }
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

    // ⚠️ 地图页**不接** Haze 模糊源。
    //
    // OSMDroid 的 MapView 由 AndroidView 承载，是原生 View —— 它在独立的 View 层级里
    // 由系统绘制，不进入 Compose 的绘制指令流。而 Haze 只捕获 Compose 自己绘制的内容
    // （它把源内容 record 进一个 GraphicsLayer），所以给它挂 hazeSource 录出来的图层
    // 是空的 —— 真机实测结果：地图页浮层全部没有模糊，只剩 72% 半透明底板，
    // 背后的真实地图与右侧 FAB 列直接透出来，也就是看到的那处「穿模」。
    //
    // 因此这里不再提供源。去掉后，MapScreen 内所有 liquidGlass 会自动走到
    // 「不透明磨砂板」兜底分支（见 LiquidGlassModifier），穿模随之消失，
    // 也不会为一个永远捕获不到的空图层白烧 GPU。
    // 底栏不受影响：它用的是 AppNavigation 的全局源（纯 Compose 内容，可正常捕获）。
    Box(modifier = Modifier.fillMaxSize()) {
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

            // 1. Top Floating Controls Bar & Multi-Target App Capsules (iOS Frosted Floating Header)
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
                            .clip(AppShapes.panel)
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
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = if (activeMapTab == MapTab.ROUTE) "路线巡航"
                                    else if (activeRule != null) "${activeRule.appName} 独立分流"
                                    else "全局模拟",
                                    style = IosTypography.BarTitle,
                                    color = IosColors.Label,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = currentAddressText,
                                    style = IosTypography.BarSub,
                                    color = IosColors.SecondaryLabel,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Right: Floating Map Tools Pill (Map layer, GPX, Undo, Clear, Save)
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
                            // Road Route Planner Button (Only in ROUTE mode)
                            if (activeMapTab == MapTab.ROUTE) {
                                IconButton(
                                    onClick = { showRoadRouteDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text(
                                        text = "🛣️",
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            // Map Layer Selector (唯一的地图API底图切换入口)
                            Box {
                                IconButton(
                                    onClick = { showMapTypeMenu = true },
                                    modifier = Modifier.size(36.dp)
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
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = if (activeMapTab == MapTab.LOCATION) "地点收藏" else "航线收藏",
                                    tint = IosBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // GPX Import Button (Only in ROUTE mode)
                            if (activeMapTab == MapTab.ROUTE) {
                                IconButton(
                                    onClick = { gpxPickerLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "导入GPX", tint = IosBlue, modifier = Modifier.size(20.dp))
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
                                    .height(44.dp)
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
                                    .height(44.dp)
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
                                    .height(44.dp)
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
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 156.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IosVerticalZoomControl(
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
                    simState = simState,
                    simulationViewModel = simulationViewModel,
                    bottomBarPadding = bottomBarPadding,
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
                                .bouncyClickable {
                                    simulationViewModel.stopPointMock(context)
                                    Toast.makeText(context, "已停止虚拟定位", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Text(
                                "停止",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontWeight = FontWeight.Bold
                            )
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
                IosCenterPin(
                    label = if (currentActiveRule != null) "【${currentActiveRule.appName}】分流选点"
                            else if (activeMapTab == MapTab.ROUTE) "路线航点选点"
                            else "目标定位点",
                    pinColor = currentAimingColor,
                    icon = if (currentActiveRule != null) Icons.Default.AltRoute else Icons.Default.Place,
                    modifier = Modifier.padding(bottom = 130.dp)
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
                    isMockActive = isPointMockActive || isJoystickRunning,
                    latitude = activeCoord.first,
                    longitude = activeCoord.second,
                    address = currentAddressText,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    savedRoutes = savedRoutes,
                    isLiquidGlass = isLiquidGlass,
                    bottomBarPadding = bottomBarPadding,
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
                    modifier = Modifier.align(Alignment.BottomCenter),
                    stage = routeStage,
                    waypoints = drawnWaypoints,
                    simState = simState,
                    selectedSpeed = selectedSpeed,
                    isLiquidGlass = isLiquidGlass,
                    bottomBarPadding = bottomBarPadding,
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
