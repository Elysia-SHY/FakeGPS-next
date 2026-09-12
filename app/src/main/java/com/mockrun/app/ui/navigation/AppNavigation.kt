package com.mockrun.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.mockrun.app.ui.components.AndroidFloatingBottomBar
import com.mockrun.app.ui.components.AppUpdateDialog
import com.mockrun.app.ui.components.FloatingTabItem
import com.mockrun.app.ui.screen.AboutScreen
import com.mockrun.app.ui.screen.LocationMockScreen
import com.mockrun.app.ui.screen.MapScreen
import com.mockrun.app.ui.screen.MapTab
import com.mockrun.app.ui.screen.RouteLibraryScreen
import com.mockrun.app.ui.theme.LiquidGlassDefaults
import com.mockrun.app.ui.theme.LocalHazeState
import com.mockrun.app.ui.theme.LocalLiquidGlassEnabled
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel
import com.mockrun.app.data.repository.VersionSyncManager
import com.mockrun.app.data.repository.VersionSyncStatus
import dev.chrisbanes.haze.HazeState

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

private fun getRouteIndex(route: String?): Int = when (route) {
    Screen.Location.route -> 0
    Screen.Route.route -> 1
    Screen.Features.route -> 2
    Screen.About.route -> 3
    else -> 0
}

@Composable
fun AppNavigation(
    mapViewModel: MapViewModel = hiltViewModel(),
    simulationViewModel: SimulationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var isLiquidGlassEnabled by remember {
        mutableStateOf(LiquidGlassDefaults.isEnabled(context))
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Dynamic padding: nav bar inset + pill (62dp) + vertical padding (8+8dp) + safety margin (8dp)
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarPadding = navBarBottom + 86.dp

    val navigationTabs = remember {
        listOf(
            FloatingTabItem(Screen.Location.route, Screen.Location.title, Screen.Location.icon),
            FloatingTabItem(Screen.Route.route, Screen.Route.title, Screen.Route.icon),
            FloatingTabItem(Screen.Features.route, Screen.Features.title, Screen.Features.icon),
            FloatingTabItem(Screen.About.route, Screen.About.title, Screen.About.icon)
        )
    }

    val onTabNavigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val hazeState = remember { HazeState() }
    val updateStatus by VersionSyncManager.status
    val showUpdatePrompt by VersionSyncManager.showUpdatePrompt

    LaunchedEffect(Unit) {
        VersionSyncManager.checkForUpdates(force = false)
    }

    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides isLiquidGlassEnabled,
        LocalHazeState provides hazeState
    ) {
        // 沉浸式全景架构：地图铺满整屏（手机 & Pad 通用），无左侧冲突
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Location.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    val fromIdx = getRouteIndex(initialState.destination.route)
                    val toIdx = getRouteIndex(targetState.destination.route)
                    if (toIdx > fromIdx) {
                        slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(280))
                    } else {
                        slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(280))
                    }
                },
                exitTransition = {
                    val fromIdx = getRouteIndex(initialState.destination.route)
                    val toIdx = getRouteIndex(targetState.destination.route)
                    if (toIdx > fromIdx) {
                        slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(280))
                    } else {
                        slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(280))
                    }
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(280))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(280))
                }
            ) {
                composable(Screen.Location.route) {
                    MapScreen(
                        mapViewModel = mapViewModel,
                        simulationViewModel = simulationViewModel,
                        initialTab = MapTab.LOCATION,
                        isLiquidGlass = isLiquidGlassEnabled,
                        isTablet = isTablet,
                        bottomBarPadding = bottomBarPadding,
                        onNavigateToLibrary = { navController.navigate(Screen.Library.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                    )
                }
                composable(Screen.Route.route) {
                    MapScreen(
                        mapViewModel = mapViewModel,
                        simulationViewModel = simulationViewModel,
                        initialTab = MapTab.ROUTE,
                        isLiquidGlass = isLiquidGlassEnabled,
                        isTablet = isTablet,
                        bottomBarPadding = bottomBarPadding,
                        onNavigateToLibrary = { navController.navigate(Screen.Library.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                    )
                }
                composable(Screen.Features.route) {
                    LocationMockScreen(
                        simulationViewModel = simulationViewModel,
                        onNavigateToMap = { navController.navigate(Screen.Location.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                    )
                }
                composable(Screen.About.route) {
                    AboutScreen(
                        isLiquidGlass = isLiquidGlassEnabled,
                        isTablet = isTablet,
                        onToggleLiquidGlass = { isLiquidGlassEnabled = it },
                        onNavigateToLibrary = { navController.navigate(Screen.Library.route) }
                    )
                }
                composable(Screen.Library.route) {
                    RouteLibraryScreen(
                        mapViewModel = mapViewModel,
                        onRouteSelected = { navController.navigate(Screen.Route.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                    )
                }
            }

            // 悬浮式毛玻璃底栏（手机自适应拉伸，Pad 上自动居中最大 480dp 悬浮胶囊，绝不阻挡左侧地图手势）
            AndroidFloatingBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                tabs = navigationTabs,
                currentRoute = currentRoute,
                isLiquidGlass = isLiquidGlassEnabled,
                onTabSelected = onTabNavigate
            )

            // 全局启动与后台更新弹窗
            if (showUpdatePrompt && updateStatus is VersionSyncStatus.HasUpdate) {
                val info = (updateStatus as VersionSyncStatus.HasUpdate).info
                AppUpdateDialog(
                    info = info,
                    isLiquidGlass = isLiquidGlassEnabled,
                    onDismiss = { VersionSyncManager.dismissUpdatePrompt() }
                )
            }
        }
    }
}