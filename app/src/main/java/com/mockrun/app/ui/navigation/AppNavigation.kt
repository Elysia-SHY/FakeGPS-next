package com.mockrun.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mockrun.app.ui.components.AndroidFloatingBottomBar
import com.mockrun.app.ui.components.AppUpdateDialog
import com.mockrun.app.ui.components.FloatingTabItem
import com.mockrun.app.feature.settings.AboutScreen
import com.mockrun.app.feature.mock.LocationMockScreen
import com.mockrun.app.feature.map.MapScreen
import com.mockrun.app.feature.map.MapTab
import com.mockrun.app.feature.route.RouteLibraryScreen
import com.mockrun.app.core.designsystem.AppAppearancePreferences
import com.mockrun.app.core.designsystem.BackgroundThemeManager
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel
import com.mockrun.app.core.data.repository.VersionSyncManager
import com.mockrun.app.core.data.repository.VersionSyncStatus
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Location : Screen("location", "定位", Icons.Default.Place)
    object Route : Screen("route", "路线", Icons.Default.Navigation)
    object Features : Screen("features", "功能", Icons.Default.Build)
    object About : Screen("about", "关于", Icons.Default.Info)
    object Library : Screen("library", "路线库", Icons.Default.Menu)

    // Backward-compatibility aliases
    object Map : Screen("location", "定位", Icons.Default.Place)
    object LocationMock : Screen("features", "功能", Icons.Default.Build)
    object RouteSimulation : Screen("route", "路线", Icons.Default.Navigation)
}

@Composable
fun AppNavigation(
    mapViewModel: MapViewModel = hiltViewModel(),
    simulationViewModel: SimulationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val coroutineScope = rememberCoroutineScope()

    val dynamicColorEnabled by AppAppearancePreferences.dynamicColorEnabled

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarPadding = navBarBottom + 94.dp

    val navigationTabs = remember {
        listOf(
            FloatingTabItem(Screen.Location.route, Screen.Location.title, Screen.Location.icon),
            FloatingTabItem(Screen.Route.route, Screen.Route.title, Screen.Route.icon),
            FloatingTabItem(Screen.Features.route, Screen.Features.title, Screen.Features.icon),
            FloatingTabItem(Screen.About.route, Screen.About.title, Screen.About.icon)
        )
    }

    // Unified interactive tab position: 0.0f .. 3.0f
    val tabPosition = remember { Animatable(0f) }
    var showLibrarySubScreen by remember { mutableStateOf(false) }

    val updateStatus by VersionSyncManager.status
    val showUpdatePrompt by VersionSyncManager.showUpdatePrompt

    LaunchedEffect(Unit) {
        BackgroundThemeManager.initialize(context)
        VersionSyncManager.checkForUpdates(force = false)
    }

    // Android back handler
    BackHandler(enabled = showLibrarySubScreen || tabPosition.value.roundToInt() != 0) {
        if (showLibrarySubScreen) {
            showLibrarySubScreen = false
        } else {
            coroutineScope.launch {
                tabPosition.animateTo(0f, spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
            // Main Sliding Multi-Screen View (Driven by tabPosition in real-time)
            //
            // Keep placement-stage offsets so the map AndroidView and hit testing stay
            // synchronized with the continuously dragged tab position.
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }

                // Page 0: MapScreen (Handles Tab 0: 定位 and Tab 1: 路线)
                val mapTab = if (tabPosition.value < 0.5f) MapTab.LOCATION else MapTab.ROUTE
                val mapOffsetFraction = if (tabPosition.value <= 1.0f) 0f else (1.0f - tabPosition.value)
                if (tabPosition.value < 2.05f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                IntOffset((mapOffsetFraction * widthPx).roundToInt(), 0)
                            }
                    ) {
                        MapScreen(
                            mapViewModel = mapViewModel,
                            simulationViewModel = simulationViewModel,
                            initialTab = mapTab,
                            isTablet = isTablet,
                            bottomBarPadding = bottomBarPadding,
                            onNavigateToLibrary = { showLibrarySubScreen = true }
                        )
                    }
                }

                // Page 1: LocationMockScreen (Tab 2: 功能)
                val featuresOffsetFraction = 2.0f - tabPosition.value
                if (tabPosition.value in 0.95f..3.05f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                IntOffset((featuresOffsetFraction * widthPx).roundToInt(), 0)
                            }
                    ) {
                        LocationMockScreen(
                            simulationViewModel = simulationViewModel,
                            onNavigateToMap = {
                                coroutineScope.launch {
                                    tabPosition.animateTo(0f, spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        )
                    }
                }

                // Page 2: AboutScreen (Tab 3: 关于)
                val aboutOffsetFraction = 3.0f - tabPosition.value
                if (tabPosition.value > 1.95f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                IntOffset((aboutOffsetFraction * widthPx).roundToInt(), 0)
                            }
                    ) {
                        AboutScreen(
                            dynamicColor = dynamicColorEnabled,
                            isTablet = isTablet,
                            onToggleDynamicColor = {
                                AppAppearancePreferences.setDynamicColorEnabled(context, it)
                            },
                            onNavigateToLibrary = { showLibrarySubScreen = true }
                        )
                    }
                }
            }

            // Sub-screen overlay: Route Library
            AnimatedVisibility(
                visible = showLibrarySubScreen,
                enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(280)),
                exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(280))
            ) {
                RouteLibraryScreen(
                    mapViewModel = mapViewModel,
                    onRouteSelected = {
                        showLibrarySubScreen = false
                        coroutineScope.launch {
                            tabPosition.animateTo(1f, spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                )
            }

            // Floating Bottom Bar (Synchronized with tabPosition in real-time)
            //
            if (!showLibrarySubScreen) {
                AndroidFloatingBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    tabs = navigationTabs,
                    currentPosition = tabPosition.value,
                    onDragDelta = { delta ->
                        coroutineScope.launch {
                            val next = (tabPosition.value + delta).coerceIn(0f, (navigationTabs.size - 1).toFloat())
                            tabPosition.snapTo(next)
                        }
                    },
                    onDragEnd = {
                        val target = tabPosition.value.roundToInt().coerceIn(0, navigationTabs.size - 1)
                        coroutineScope.launch {
                            tabPosition.animateTo(
                                targetValue = target.toFloat(),
                                animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    },
                    onTabSelected = { targetIndex ->
                        coroutineScope.launch {
                            tabPosition.animateTo(
                                targetValue = targetIndex.toFloat(),
                                animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    }
                )
            }

            // In-app Update Prompt Dialog
            //
            // 必须先取一次快照再判定 —— updateStatus 是 `by` 委托的 State，
            // 由 VersionSyncManager.checkForUpdates() 在 IO 协程上写入。
            // 直接写 `if (updateStatus is HasUpdate) { ... updateStatus as HasUpdate }`
            // 是两次独立读取：判定通过后、强转之前若状态被改写（例如用户点了
            // 「检查更新」触发新一轮 checkForUpdates，状态回到 Checking），
            // 强转即抛 ClassCastException，崩在导航层的组合期 —— 整个界面挂掉。
            //
            // 这个问题一直存在，只是原先走 GitHub API，国内超时后状态停在
            // Error 分支，很难命中；改为自有服务器后响应从 10 秒降到毫秒级，
            // Idle -> Checking -> HasUpdate 快速连续翻转，才稳定触发。
            val statusSnapshot = updateStatus
            if (showUpdatePrompt && statusSnapshot is VersionSyncStatus.HasUpdate) {
                AppUpdateDialog(
                    info = statusSnapshot.info,
                    onDismiss = { VersionSyncManager.dismissUpdatePrompt() }
                )
            }
    }
}
