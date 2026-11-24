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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.mockrun.app.core.designsystem.AppBackground
import com.mockrun.app.core.designsystem.BackgroundThemeManager
import com.mockrun.app.core.designsystem.GlassCapabilities
import com.mockrun.app.core.designsystem.LocalGlassBackdrop
import com.mockrun.app.core.designsystem.LocalLiquidGlassEnabled
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel
import com.mockrun.app.core.data.repository.VersionSyncManager
import com.mockrun.app.core.data.repository.VersionSyncStatus
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

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

    val liquidGlassRequested by AppAppearancePreferences.liquidGlassEnabled
    val liquidGlassEnabled = liquidGlassRequested && GlassCapabilities.supportsBackdropBlur
    val chromeBackdrop = if (liquidGlassEnabled) {
        rememberLayerBackdrop(captureHeightFraction = 0.20f)
    } else {
        null
    }
    val atlasBackdrop = if (liquidGlassEnabled) rememberLayerBackdrop() else null

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

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val saveableStateHolder = rememberSaveableStateHolder()
    var showLibrarySubScreen by remember { mutableStateOf(false) }

    val updateStatus by VersionSyncManager.status
    val showUpdatePrompt by VersionSyncManager.showUpdatePrompt

    LaunchedEffect(Unit) {
        BackgroundThemeManager.initialize(context)
        VersionSyncManager.checkForUpdates(force = false)
    }

    // Android back handler
    BackHandler(enabled = showLibrarySubScreen || selectedTabIndex != 0) {
        if (showLibrarySubScreen) {
            showLibrarySubScreen = false
        } else {
            selectedTabIndex = 0
        }
    }

    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides liquidGlassEnabled,
        LocalGlassBackdrop provides chromeBackdrop
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (chromeBackdrop != null) {
                        Modifier.layerBackdrop(chromeBackdrop)
                    } else {
                        Modifier
                    }
                )
        ) {
            val sharedAtlasBackdrop = atlasBackdrop
            if (sharedAtlasBackdrop != null) {
                AnimatedVisibility(
                    visible = selectedTabIndex >= 2,
                    enter = fadeIn(tween(240, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(180, easing = FastOutSlowInEasing)),
                    label = "sharedAtlasBackground"
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(sharedAtlasBackdrop)
                    ) {
                        AppBackground()
                    }
                }
            }

            // The four tabs crossfade in place. The MapScreen is shared by
            // Location and Route so a tab switch never creates a second map
            // instance or a full-screen backdrop capture.
            AnimatedContent(
                targetState = selectedTabIndex,
                contentKey = { tab ->
                    if (tab <= 1) "map" else "tab$tab"
                },
                transitionSpec = {
                    (
                        fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.985f,
                                animationSpec = tween(240, easing = FastOutSlowInEasing)
                            )
                        ).togetherWith(
                        fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                            scaleOut(
                                targetScale = 1.01f,
                                animationSpec = tween(180, easing = FastOutSlowInEasing)
                            )
                    )
                },
                contentAlignment = Alignment.TopStart,
                label = "mainTabTransition",
                modifier = Modifier.fillMaxSize()
            ) { tabIndex ->
                val stateKey = if (tabIndex <= 1) "map" else "tab$tabIndex"
                saveableStateHolder.SaveableStateProvider(stateKey) {
                    when (tabIndex) {
                        0, 1 -> {
                            val mapTab = if (tabIndex == 0) MapTab.LOCATION else MapTab.ROUTE
                            Box(modifier = Modifier.fillMaxSize()) {
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

                        2 -> {
                            LocationMockScreen(
                                simulationViewModel = simulationViewModel,
                                onNavigateToMap = { selectedTabIndex = 0 },
                                sharedGlassBackdrop = atlasBackdrop
                            )
                        }

                        else -> {
                            AboutScreen(
                                isLiquidGlass = liquidGlassEnabled,
                                supportsLiquidGlass = GlassCapabilities.supportsBackdropBlur,
                                isTablet = isTablet,
                                onToggleLiquidGlass = {
                                    AppAppearancePreferences.setLiquidGlassEnabled(context, it)
                                },
                                onNavigateToLibrary = { showLibrarySubScreen = true },
                                sharedGlassBackdrop = atlasBackdrop
                            )
                        }
                    }
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
                        selectedTabIndex = 1
                    }
                )
            }

            // Floating Bottom Bar (Synchronized with tabPosition in real-time)
            //
            if (!showLibrarySubScreen) {
                AndroidFloatingBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    tabs = navigationTabs,
                    currentPosition = selectedTabIndex.toFloat(),
                    onTabSelected = { targetIndex ->
                        selectedTabIndex = targetIndex
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
}
