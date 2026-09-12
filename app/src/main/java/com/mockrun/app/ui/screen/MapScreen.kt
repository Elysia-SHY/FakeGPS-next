package com.mockrun.app.ui.screen

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
import com.mockrun.app.ui.screen.tabs.LocationControlPanel
import com.mockrun.app.ui.screen.tabs.RouteBottomPanel
import com.mockrun.app.ui.screen.tabs.RouteConfigDialog
import com.mockrun.app.ui.screen.tabs.RouteStage
import com.mockrun.app.ui.components.PermissionGuideDialog
import com.mockrun.app.util.Diag
import com.mockrun.app.util.PermissionHelper
import com.mockrun.app.util.PermissionIssueType
import com.mockrun.app.util.logFailure
import com.mockrun.app.ui.theme.LiquidGlassDefaults
import com.mockrun.app.ui.theme.liquidGlass
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
import com.mockrun.app.location.AddressResolver
import com.mockrun.app.location.CoordinateConverter
import com.mockrun.app.location.RoadMode
import com.mockrun.app.location.RoadRouteResult
import com.mockrun.app.location.SearchResultItem
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.ui.components.AppPickerBottomSheet
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.sp
import com.mockrun.app.BuildConfig
import com.mockrun.app.ui.theme.*
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

/** High-speed AutoNavi (高德地图) Vector Tile Source (Domestic CDN, No Key Required) */
val AutoNaviVectorTileSource = object : OnlineTileSourceBase(
    "AutoNavi-Vector",
    3, 19, 256, ".png",
    arrayOf(
        "https://wprd01.is.autonavi.com/appmaptile?",
        "https://wprd02.is.autonavi.com/appmaptile?",
        "https://wprd03.is.autonavi.com/appmaptile?",
        "https://wprd04.is.autonavi.com/appmaptile?"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl&lang=zh_cn&size=1&scale=1&style=7&x=$x&y=$y&z=$zoom"
    }
}

/** High-speed AutoNavi (高德地图) Satellite Tile Source */
val AutoNaviSatelliteTileSource = object : OnlineTileSourceBase(
    "AutoNavi-Satellite",
    3, 19, 256, ".png",
    arrayOf(
        "https://wprd01.is.autonavi.com/appmaptile?",
        "https://wprd02.is.autonavi.com/appmaptile?",
        "https://wprd03.is.autonavi.com/appmaptile?",
        "https://wprd04.is.autonavi.com/appmaptile?"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl&lang=zh_cn&size=1&scale=1&style=6&x=$x&y=$y&z=$zoom"
    }
}

enum class MapSourceType(val label: String) {
    AUTONAVI_AUTO("高德路网 (跟随系统)"),
    AUTONAVI_VECTOR("高德路网 (浅色标准)"),
    AUTONAVI_DARK("高德路网 (深色夜间)"),
    AUTONAVI_SATELLITE("高德卫星影像"),
    OPEN_STREET_MAP("OSM 国际地图")
}

/**
 * Authentic Apple Maps Dark / Night Mode ColorMatrix Filter for vector tiles.
 * Inverts base light backgrounds to deep night gray (#141416) while keeping roads clear and readable.
 */
val DarkMapColorMatrix = android.graphics.ColorMatrix().apply {
    val r = -0.82f
    val g = -0.82f
    val b = -0.82f
    val offset = 215f
    set(floatArrayOf(
        r,  0f, 0f, 0f, offset,
        0f, g,  0f, 0f, offset,
        0f, 0f, b,  0f, offset + 15f,
        0f, 0f, 0f, 1f, 0f
    ))
}
val DarkMapColorFilter = android.graphics.ColorMatrixColorFilter(DarkMapColorMatrix)

/**
 * Apple Maps Style Vector Pin & Radar Marker Generator.
 * Creates crisp, high-DPI teardrop pins with ground shadow, white stroke, and concentric core.
 */
object MapPinHelper {
    private var cachedSelectedPin: BitmapDrawable? = null
    private var cachedActiveMockPin: BitmapDrawable? = null
    private var cachedJoystickPin: BitmapDrawable? = null
    private var cachedRunnerPin: BitmapDrawable? = null

    fun getSelectedPin(context: Context): BitmapDrawable {
        cachedSelectedPin?.let { return it }
        val d = createTeardropPin(
            context = context,
            primaryColor = android.graphics.Color.parseColor("#FF3B30"), // Apple iOS Red
            strokeColor = android.graphics.Color.WHITE
        )
        cachedSelectedPin = d
        return d
    }

    fun getActiveMockPin(context: Context): BitmapDrawable {
        cachedActiveMockPin?.let { return it }
        val d = createTeardropPin(
            context = context,
            primaryColor = android.graphics.Color.parseColor("#34C759"), // Apple iOS Green
            strokeColor = android.graphics.Color.WHITE
        )
        cachedActiveMockPin = d
        return d
    }

    fun getJoystickPin(context: Context): BitmapDrawable {
        cachedJoystickPin?.let { return it }
        val d = createRadarPin(
            context = context,
            ringColor = android.graphics.Color.parseColor("#33007AFF"),
            coreColor = android.graphics.Color.parseColor("#007AFF") // iOS Blue
        )
        cachedJoystickPin = d
        return d
    }

    fun getRunnerPin(context: Context): BitmapDrawable {
        cachedRunnerPin?.let { return it }
        val d = createRadarPin(
            context = context,
            ringColor = android.graphics.Color.parseColor("#33FF9500"),
            coreColor = android.graphics.Color.parseColor("#FF9500") // iOS Orange
        )
        cachedRunnerPin = d
        return d
    }

    private var cachedOriginPin: BitmapDrawable? = null
    private var cachedDestinationPin: BitmapDrawable? = null

    fun getOriginPin(context: Context): BitmapDrawable {
        cachedOriginPin?.let { return it }
        val d = createTeardropPin(
            context = context,
            primaryColor = android.graphics.Color.parseColor("#34C759"), // Apple Green (Start)
            strokeColor = android.graphics.Color.WHITE
        )
        cachedOriginPin = d
        return d
    }

    fun getDestinationPin(context: Context): BitmapDrawable {
        cachedDestinationPin?.let { return it }
        val d = createTeardropPin(
            context = context,
            primaryColor = android.graphics.Color.parseColor("#FF9500"), // Apple Orange (Destination)
            strokeColor = android.graphics.Color.WHITE
        )
        cachedDestinationPin = d
        return d
    }

    private val cachedAppPins = mutableMapOf<Int, BitmapDrawable>()

    fun getAppPin(context: Context, color: Int): BitmapDrawable {
        return cachedAppPins.getOrPut(color) {
            createTeardropPin(
                context = context,
                primaryColor = color,
                strokeColor = android.graphics.Color.WHITE
            )
        }
    }

    private var cachedRealLocationPuck: BitmapDrawable? = null

    fun getRealLocationPuck(context: Context): BitmapDrawable {
        cachedRealLocationPuck?.let { return it }
        val density = context.resources.displayMetrics.density
        val size = (38 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        // 1. Soft glowing outer pulse ring (iOS Royal Blue 20% alpha)
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(55, 0, 122, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - 2f * density, haloPaint)

        // 2. Light blue stroke ring
        val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(120, 0, 122, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawCircle(center, center, center - 4.5f * density, ringStroke)

        // 3. Crisp white border
        val whiteBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 9f * density, whiteBorder)

        // 4. Solid vibrant Apple Blue center core
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#007AFF")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 6.5f * density, corePaint)

        val d = BitmapDrawable(context.resources, bitmap)
        cachedRealLocationPuck = d
        return d
    }

    private fun createTeardropPin(
        context: Context,
        primaryColor: Int,
        strokeColor: Int
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val w = (38 * density).toInt()
        val h = (48 * density).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = w / 2f
        val strokeWidth = 2.5f * density
        val shadowHeight = 6f * density
        val tipY = h - shadowHeight - 2f * density
        val topPadding = 2f * density
        val r = (w - strokeWidth * 2f - 4f * density) / 2f
        val cy = topPadding + strokeWidth + r

        // 1. Ground Shadow (soft drop shadow)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(70, 0, 0, 0)
            style = Paint.Style.FILL
        }
        val shadowRect = RectF(
            cx - r * 0.75f,
            h - shadowHeight - 1f * density,
            cx + r * 0.75f,
            h - 1f * density
        )
        canvas.drawOval(shadowRect, shadowPaint)

        // 2. Teardrop Path
        val path = Path().apply {
            moveTo(cx, tipY)
            // Left curve to bulb
            cubicTo(
                cx - r * 0.95f, cy + r * 0.95f,
                cx - r, cy + r * 0.45f,
                cx - r, cy
            )
            // Top circle arc
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f, false)
            // Right curve back to tip
            cubicTo(
                cx + r, cy + r * 0.45f,
                cx + r * 0.95f, cy + r * 0.95f,
                cx, tipY
            )
            close()
        }

        // 3. Body Fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fillPaint)

        // 4. White Outer Stroke
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, strokePaint)

        // 5. Inner White Concentric Circle
        val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val innerRadius = r * 0.44f
        canvas.drawCircle(cx, cy, innerRadius, innerCirclePaint)

        // 6. Inner Dot (Center Target)
        val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, innerRadius * 0.45f, centerDotPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun createRadarPin(
        context: Context,
        ringColor: Int,
        coreColor: Int
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val size = (34 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        // Outer translucent pulse ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ringColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - 2f * density, ringPaint)

        // White halo border
        val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val coreWhiteRadius = 9f * density
        canvas.drawCircle(center, center, coreWhiteRadius, whiteBorderPaint)

        // Inner solid core
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coreColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 6.5f * density, corePaint)

        return BitmapDrawable(context.resources, bitmap)
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
    var showMapAppPickerSheet by remember { mutableStateOf(false) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var routeNameInput by remember { mutableStateOf("") }
    var currentMapType by remember { mutableStateOf(MapSourceType.AUTONAVI_AUTO) }
    var showMapTypeMenu by remember { mutableStateOf(false) }

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
    var currentAddressText by remember { mutableStateOf("正在获取当前地址...") }
    var currentZoom by remember { mutableDoubleStateOf(16.0) }

    val roadOrigin by mapViewModel.roadOrigin.collectAsState()
    val roadDestination by mapViewModel.roadDestination.collectAsState()

    var showSearchDialog by remember { mutableStateOf(false) }
    var showRoadRouteDialog by remember { mutableStateOf(false) }
    var isContinuousDrawMode by remember { mutableStateOf(false) }
    var permissionIssueDialogType by remember { mutableStateOf<PermissionIssueType?>(null) }

    val ensurePermissionAndStart: (() -> Unit) -> Unit = { onPermitted ->
        val issue = PermissionHelper.checkPrimaryPermissions(context)
        if (issue != PermissionIssueType.NONE) {
            permissionIssueDialogType = issue
        } else {
            onPermitted()
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

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize()
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
                            .heightIn(max = 46.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(0.5.dp, IosHairlineBorder, RoundedCornerShape(20.dp))
                            .bouncyClickable {
                                val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                                val (tLat, tLon) = if (isGcjMap) CoordinateConverter.wgs84ToGcj02(activeCoord.first, activeCoord.second) else activeCoord
                                mapViewRef?.controller?.apply {
                                    setZoom(16.5)
                                    animateTo(GeoPoint(tLat, tLon))
                                }
                            },
                        color = IosFrostedCapsule,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = activeRuleColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = if (activeMapTab == MapTab.ROUTE) "全局路线巡航 · ${BuildConfig.VERSION_NAME}" else if (activeRule != null) "${activeRule.appName} 独立分流 · ${BuildConfig.VERSION_NAME}" else "全局模拟 · ${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = activeRuleColor,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentAddressText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = IosColors.Label,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Right: Floating Map Tools Pill (Map layer, GPX, Undo, Clear, Save)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = IosFrostedCapsule,
                        border = BorderStroke(0.5.dp, IosHairlineBorder),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
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
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Item 0: Global Default
                        item {
                            val isSelected = activeTargetKey == null
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) IosColors.SystemBlue else IosFrostedCapsule,
                                border = BorderStroke(0.5.dp, if (isSelected) IosColors.SystemBlue else IosHairlineBorder),
                                shadowElevation = if (isSelected) 4.dp else 2.dp,
                                modifier = Modifier.bouncyClickable {
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
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🌐 全局通用",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) ruleColor else IosFrostedCapsule,
                                border = BorderStroke(0.5.dp, if (isSelected) ruleColor else IosHairlineBorder),
                                shadowElevation = if (isSelected) 4.dp else 2.dp,
                                modifier = Modifier.bouncyClickable {
                                    simulationViewModel.setActiveTargetKey(rule.key)
                                    centerAimingCoord = rule.latitude to rule.longitude
                                    val isGcjMap = currentMapType != MapSourceType.OPEN_STREET_MAP
                                    val (tLat, tLon) = if (isGcjMap) CoordinateConverter.wgs84ToGcj02(rule.latitude, rule.longitude) else (rule.latitude to rule.longitude)
                                    mapViewRef?.controller?.animateTo(GeoPoint(tLat, tLon))
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                                shape = RoundedCornerShape(16.dp),
                                color = IosFrostedCapsule,
                                border = BorderStroke(0.5.dp, IosHairlineBorder),
                                shadowElevation = 2.dp,
                                modifier = Modifier.bouncyClickable { showMapAppPickerSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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

            // 2. iOS Style Vertical Zoom Slider (右上角竖向缩放条，位于工具栏下方)
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
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 80.dp, end = 12.dp)
            )

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
                            shape = RoundedCornerShape(10.dp),
                            color = IosRed,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
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

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    // Floating Aiming Label
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (currentActiveRule != null) currentAimingColor else Color(0xEB000000),
                        shadowElevation = 6.dp,
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (currentActiveRule != null) Icons.Default.AltRoute else Icons.Default.Place,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (currentActiveRule != null) "【${currentActiveRule.appName}】分流选点" else (if (activeMapTab == MapTab.ROUTE) "路线航点选点" else "全局目标定位点"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.AddLocationAlt,
                        contentDescription = "定位十字准心",
                        tint = currentAimingColor,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            // =================================================================
            // 5. Right Floating Action Buttons (Fit Bounds / GPS Relocate / Layers)
            // =================================================================
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = bottomBarPadding + 225.dp, end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Route Fit Bounds FAB (only in ROUTE mode when waypoints >= 2)
                if (activeMapTab == MapTab.ROUTE && drawnWaypoints.size >= 2) {
                    Surface(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .liquidGlass(isLiquidGlass = isLiquidGlass, shape = CircleShape, elevation = 6.dp)
                            .bouncyClickable {
                                val isGcj = currentMapType != MapSourceType.OPEN_STREET_MAP
                                val points = drawnWaypoints.map { wp ->
                                    if (isGcj) CoordinateConverter.wgs84ToGcj02(wp.latitude, wp.longitude) else (wp.latitude to wp.longitude)
                                }
                                val minLat = points.minOf { it.first }
                                val maxLat = points.maxOf { it.first }
                                val minLon = points.minOf { it.second }
                                val maxLon = points.maxOf { it.second }
                                val box = org.osmdroid.util.BoundingBox(maxLat + 0.002, maxLon + 0.002, minLat - 0.002, minLon - 0.002)
                                mapViewRef?.zoomToBoundingBox(box, true, 80)
                            },
                        shape = CircleShape,
                        color = Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "全览路线", tint = IosBlue, modifier = Modifier.size(22.dp))
                        }
                    }
                }

                // Locate to Real Physical Location FAB
                Surface(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .liquidGlass(isLiquidGlass = isLiquidGlass, shape = CircleShape, elevation = 6.dp)
                        .bouncyClickable {
                            if (isPointMockActive) simulationViewModel.stopPointMock(context)
                            if (simState.status is com.mockrun.app.domain.model.SimulationStatus.Running) simulationViewModel.stopSimulation(context)
                            CoordinateConverter.clearSavedRealLocation(context)
                            com.mockrun.app.location.MockLocationEngine.forceCleanAllTestProviders(context)
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
                    shape = CircleShape,
                    color = Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.MyLocation, contentDescription = "真机物理定位", tint = IosBlue, modifier = Modifier.size(22.dp))
                    }
                }
            }

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
                            context.stopService(Intent(context, com.mockrun.app.location.FloatingJoystickService::class.java))
                        }
                        Toast.makeText(context, "已停止虚拟定位", Toast.LENGTH_SHORT).show()
                    },
                    onResetRealLocation = {
                        if (isPointMockActive) simulationViewModel.stopPointMock(context)
                        if (simState.status is com.mockrun.app.domain.model.SimulationStatus.Running) simulationViewModel.stopSimulation(context)
                        CoordinateConverter.clearSavedRealLocation(context)
                        com.mockrun.app.location.MockLocationEngine.forceCleanAllTestProviders(context)
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
                        mapViewModel.saveCurrentRoute("收藏地点: $currentAddressText")
                        Toast.makeText(context, "已收藏当前位置", Toast.LENGTH_SHORT).show()
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
                    shape = RoundedCornerShape(12.dp)
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
            shape = RoundedCornerShape(20.dp)
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

/**
 * iOS Control Center Style Vertical Zoom Slider
 * Emil Kowalski & Apple Design principles:
 * - 1:1 direct vertical drag tracking with fluid spring settle
 * - Frosted glass capsule with subtle border and shadow
 * - Full 44dp hit target for effortless swipe and tap
 * - Responsive pointer-down bouncy tactile feedback
 */
@Composable
fun IosVerticalZoomControl(
    currentZoom: Double,
    minZoom: Double = 3.0,
    maxZoom: Double = 19.0,
    onZoomChange: (Double) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }

    val updatedMinZoom by rememberUpdatedState(minZoom)
    val updatedMaxZoom by rememberUpdatedState(maxZoom)
    val updatedOnZoomChange by rememberUpdatedState(onZoomChange)

    val normalizedZoom = ((currentZoom - minZoom) / (maxZoom - minZoom)).coerceIn(0.0, 1.0).toFloat()
    val animatedFill by animateFloatAsState(
        targetValue = normalizedZoom,
        animationSpec = if (isDragging) spring(stiffness = Spring.StiffnessHigh) else MotionTokens.FluidSpringSpec,
        label = "zoomFill"
    )

    Surface(
        modifier = modifier
            .width(44.dp)
            .height(168.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(0.5.dp, IosHairlineBorder, RoundedCornerShape(22.dp))
            .bouncyScale(isDragging, pressedScale = 0.97f),
        shape = RoundedCornerShape(22.dp),
        color = IosFrostedCapsule,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Zoom In (+) Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .bouncyClickable { onZoomIn() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "放大地图",
                    tint = IosBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Vertical Slider Touch Container (spans full 44.dp width for 1:1 direct tracking)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 2.dp)
                    .onGloballyPositioned { coordinates ->
                        trackHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            isDragging = true

                            fun updateZoomFromY(y: Float) {
                                val fraction = (1f - (y / trackHeightPx)).coerceIn(0f, 1f)
                                val newZoom = updatedMinZoom + fraction * (updatedMaxZoom - updatedMinZoom)
                                updatedOnZoomChange(newZoom)
                            }

                            updateZoomFromY(down.position.y)

                            while (true) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!pointer.pressed) {
                                    break
                                }
                                pointer.consume()
                                updateZoomFromY(pointer.position.y)
                            }
                            isDragging = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Liquid capsule track centered inside the wide touch target
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(IosGlassTrack),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Liquid fill from bottom upwards
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(animatedFill)
                            .clip(RoundedCornerShape(3.dp))
                            .background(IosBlue)
                    )
                }
            }

            // Zoom Out (-) Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .bouncyClickable { onZoomOut() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 12.dp, height = 2.5.dp)
                        .background(IosBlue, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchLocationDialog(
    mapViewModel: MapViewModel,
    onDismissRequest: () -> Unit,
    onSelectLocation: (Double, Double, String) -> Unit,
    onSetAsOrigin: (Double, Double) -> Unit,
    onSetAsDestination: (Double, Double) -> Unit,
    onDirectMock: (Double, Double) -> Unit
) {
    val searchQuery by mapViewModel.searchQuery.collectAsState()
    val searchResults by mapViewModel.searchResults.collectAsState()
    val isSearching by mapViewModel.isSearching.collectAsState()

    var textInput by remember { mutableStateOf(searchQuery) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = IosColors.SecondaryGroupedBackground,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍 搜索地点与经纬度", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = IosColors.Label)
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = IosGray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Input Field
                OutlinedTextField(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        mapViewModel.performSearch(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入地名/POI或经纬度(如 39.9, 116.4)", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (textInput.isNotEmpty()) {
                            IconButton(onClick = {
                                textInput = ""
                                mapViewModel.clearSearch()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "清空", tint = IosGray)
                            }
                        }
                    }
                )

                // Quick Popular Shortcut Chips
                Text("热门地点直达：", style = MaterialTheme.typography.labelSmall, color = IosGray)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val quickPois = listOf(
                        "北京天安门" to (39.9087 to 116.3975),
                        "上海外滩" to (31.2397 to 121.4904),
                        "广州塔" to (23.1064 to 113.3245),
                        "深圳湾" to (22.5025 to 113.9510),
                        "成都春熙路" to (30.6558 to 104.0818),
                        "杭州西湖" to (30.2460 to 120.1490)
                    )
                    items(quickPois) { (name, coords) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IosBlue.copy(alpha = 0.08f),
                            border = BorderStroke(0.5.dp, IosBlue.copy(alpha = 0.2f)),
                            modifier = Modifier.bouncyClickable {
                                textInput = name
                                onSelectLocation(coords.first, coords.second, name)
                            }
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = IosBlue,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = IosSeparator.copy(alpha = 0.4f))

                // Search Results or Loading
                if (isSearching) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = IosBlue, strokeWidth = 2.5.dp)
                            Spacer(Modifier.height(8.dp))
                            Text("正在联网检索地点信息...", style = MaterialTheme.typography.bodySmall, color = IosGray)
                        }
                    }
                } else if (searchResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { item ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (LocalIosColors.current.isDark) Color(0xFF2C2C2E) else Color.White,
                                border = BorderStroke(0.5.dp, IosHairlineBorder),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = IosBlue, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = IosColors.Label)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = item.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IosGray,
                                        maxLines = 2
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = IosBlue,
                                            modifier = Modifier.bouncyClickable {
                                                onSelectLocation(item.latitude, item.longitude, item.name)
                                            }
                                        ) {
                                            Text(
                                                "定位到此",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = IosGreen.copy(alpha = 0.15f),
                                            modifier = Modifier.bouncyClickable {
                                                onSetAsOrigin(item.latitude, item.longitude)
                                            }
                                        ) {
                                            Text(
                                                "设为起点",
                                                color = IosGreen,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = IosOrange.copy(alpha = 0.15f),
                                            modifier = Modifier.bouncyClickable {
                                                onSetAsDestination(item.latitude, item.longitude)
                                            }
                                        ) {
                                            Text(
                                                "设为终点",
                                                color = IosOrange,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = IosRed.copy(alpha = 0.15f),
                                            modifier = Modifier.bouncyClickable {
                                                onDirectMock(item.latitude, item.longitude)
                                            }
                                        ) {
                                            Text(
                                                "即刻伪装",
                                                color = IosRed,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (textInput.isNotBlank()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("未找到相关地点，请尝试更通用的地名或输入坐标", style = MaterialTheme.typography.bodySmall, color = IosGray)
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("输入地名或直接粘贴经纬度即可全网智能联想定位", style = MaterialTheme.typography.bodySmall, color = IosGray)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun RoadRouteDialog(
    mapViewModel: MapViewModel,
    simulationViewModel: SimulationViewModel,
    realLocation: Pair<Double, Double>?,
    selectedTapPoint: Pair<Double, Double>?,
    pointMockLocation: WayPoint?,
    onDismissRequest: () -> Unit,
    onNavigateToOrigin: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val roadOrigin by mapViewModel.roadOrigin.collectAsState()
    val roadDestination by mapViewModel.roadDestination.collectAsState()
    val roadMode by mapViewModel.roadMode.collectAsState()
    val isCalculatingRoute by mapViewModel.isCalculatingRoute.collectAsState()
    val calculatedRoadResult by mapViewModel.calculatedRoadResult.collectAsState()

    var speedSliderValue by remember(roadMode) {
        mutableFloatStateOf(
            when (roadMode) {
                RoadMode.DRIVING -> 45f
                RoadMode.CYCLING -> 18f
                RoadMode.WALKING -> 5f
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = IosColors.SecondaryGroupedBackground,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛣️ 真实道路路线规划", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = IosColors.Label)
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
                Text(
                    "基于真实道路网拓扑算法，精准沿车道与街道折线生成模拟轨迹",
                    style = MaterialTheme.typography.labelSmall,
                    color = IosGray
                )

                // 1. Origin Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (LocalIosColors.current.isDark) Color(0xFF2C2C2E) else Color.White,
                    border = BorderStroke(0.5.dp, IosHairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(IosGreen)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("起点 (Origin)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = IosColors.Label)
                            }
                            if (roadOrigin != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "定位",
                                        color = IosBlue,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.bouncyClickable {
                                            onNavigateToOrigin(roadOrigin!!.latitude, roadOrigin!!.longitude)
                                        }
                                    )
                                    Text(
                                        text = "清除",
                                        color = IosRed,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.bouncyClickable { mapViewModel.setRoadOrigin(null) }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = roadOrigin?.let { "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" } ?: "尚未设置起点",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (roadOrigin != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (roadOrigin != null) IosColors.Label else IosGray
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (selectedTapPoint != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosGreen.copy(alpha = 0.12f),
                                    modifier = Modifier.bouncyClickable {
                                        mapViewModel.setRoadOrigin(WayPoint(selectedTapPoint.first, selectedTapPoint.second))
                                    }
                                ) {
                                    Text("使用当前选点", color = IosGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (pointMockLocation != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosOrange.copy(alpha = 0.12f),
                                    modifier = Modifier.bouncyClickable {
                                        mapViewModel.setRoadOrigin(pointMockLocation)
                                    }
                                ) {
                                    Text("使用模拟点", color = IosOrange, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (realLocation != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosBlue.copy(alpha = 0.12f),
                                    modifier = Modifier.bouncyClickable {
                                        mapViewModel.setRoadOrigin(WayPoint(realLocation.first, realLocation.second))
                                    }
                                ) {
                                    Text("使用物理位置", color = IosBlue, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // 2. Destination Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (LocalIosColors.current.isDark) Color(0xFF2C2C2E) else Color.White,
                    border = BorderStroke(0.5.dp, IosHairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(IosOrange)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("终点 (Destination)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = IosColors.Label)
                            }
                            if (roadDestination != null) {
                                Text(
                                    text = "清除",
                                    color = IosRed,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.bouncyClickable { mapViewModel.setRoadDestination(null) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = roadDestination?.let { "${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}" } ?: "尚未设置终点",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (roadDestination != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (roadDestination != null) IosColors.Label else IosGray
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (selectedTapPoint != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosOrange.copy(alpha = 0.12f),
                                    modifier = Modifier.bouncyClickable {
                                        mapViewModel.setRoadDestination(WayPoint(selectedTapPoint.first, selectedTapPoint.second))
                                    }
                                ) {
                                    Text("使用当前选点", color = IosOrange, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (roadOrigin != null && roadDestination != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosGray.copy(alpha = 0.12f),
                                    modifier = Modifier.bouncyClickable {
                                        val temp = roadOrigin
                                        mapViewModel.setRoadOrigin(roadDestination)
                                        mapViewModel.setRoadDestination(temp)
                                    }
                                ) {
                                    Text("互换起终点", color = IosGray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // 3. Transport Mode Selector (Segmented Control)
                Text("出行方式 (道路拓扑权重)：", style = MaterialTheme.typography.labelSmall, color = IosGray)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IosColors.TertiarySystemFill)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RoadMode.values().forEach { mode ->
                        val isSelected = mode == roadMode
                        val isDark = LocalIosColors.current.isDark
                        val activePillColor = if (isDark) Color(0xFF636366) else Color.White
                        val activeTextColor = if (isDark) Color.White else Color.Black
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .bouncyClickable {
                                    mapViewModel.setRoadMode(mode)
                                },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) activePillColor else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "${mode.emoji} ${mode.label}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) activeTextColor else IosGray,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // 4. Speed Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("模拟移动速度", style = MaterialTheme.typography.labelSmall, color = IosGray)
                    Text("${speedSliderValue.toInt()} km/h", fontWeight = FontWeight.Bold, color = IosBlue, style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = speedSliderValue,
                    onValueChange = { speedSliderValue = it },
                    valueRange = 1f..120f,
                    steps = 119,
                    colors = SliderDefaults.colors(
                        thumbColor = IosBlue,
                        activeTrackColor = IosBlue,
                        inactiveTrackColor = IosGlassTrack
                    )
                )

                // 5. Calculate Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .bouncyClickable {
                            if (roadOrigin != null && roadDestination != null) {
                                mapViewModel.calculateRoadRoute { res ->
                                    Toast.makeText(context, "规划成功：全程 ${"%.2f".format(res.distanceKm)} km，共 ${res.waypoints.size} 个道路折点", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "请先设置起点与终点！", Toast.LENGTH_SHORT).show()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = if (roadOrigin != null && roadDestination != null && !isCalculatingRoute) IosBlue else IosGray.copy(alpha = 0.3f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                        if (isCalculatingRoute) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在计算沿路折点...", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Text("⚡ 沿真实道路智能规划路线", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // 6. Calculated Result Card & Start Simulation Action
                if (calculatedRoadResult != null) {
                    val result = calculatedRoadResult!!
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = IosGreen.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, IosGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IosGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("路线已绘制至底图", fontWeight = FontWeight.Bold, color = IosGreen, style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "距离: ${"%.2f".format(result.distanceKm)} km · 预计耗时: ${"%.0f".format(result.durationMinutes)} 分钟 · 折点数: ${result.waypoints.size}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .bouncyClickable {
                                        val route = Route(
                                            name = "沿路规划 (${result.mode.label} ${"%.1f".format(result.distanceKm)}km)",
                                            waypoints = result.waypoints
                                        )
                                        simulationViewModel.startSimulation(context, route, speedSliderValue)
                                        onDismissRequest()
                                        Toast.makeText(context, "已开启沿真实道路模拟运动！", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = IosGreen
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                    Text("🏃 立即开始沿路模拟", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}