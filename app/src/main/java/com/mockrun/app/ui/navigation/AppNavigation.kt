package com.mockrun.app.ui.navigation

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.mockrun.app.ui.components.AndroidFloatingBottomBar
import com.mockrun.app.ui.components.FloatingTabItem
import com.mockrun.app.ui.screen.AboutScreen
import com.mockrun.app.ui.screen.LocationMockScreen
import com.mockrun.app.ui.screen.MapScreen
import com.mockrun.app.ui.screen.MapTab
import com.mockrun.app.ui.screen.RouteLibraryScreen
import com.mockrun.app.ui.theme.LiquidGlassDefaults
import com.mockrun.app.ui.theme.LocalLiquidGlassEnabled
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel

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

    CompositionLocalProvider(LocalLiquidGlassEnabled provides isLiquidGlassEnabled) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Location.route,
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Location Spoofing (LocationSpoofer Style Interactive Map)
                composable(Screen.Location.route) {
                    MapScreen(
                        mapViewModel = mapViewModel,
                        simulationViewModel = simulationViewModel,
                        initialTab = MapTab.LOCATION,
                        isLiquidGlass = isLiquidGlassEnabled,
                        bottomBarPadding = bottomBarPadding,
                        onNavigateToLibrary = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                // 2. Route Simulation (LocationSpoofer Style Route Planner & Simulator)
                composable(Screen.Route.route) {
                    MapScreen(
                        mapViewModel = mapViewModel,
                        simulationViewModel = simulationViewModel,
                        initialTab = MapTab.ROUTE,
                        isLiquidGlass = isLiquidGlassEnabled,
                        bottomBarPadding = bottomBarPadding,
                        onNavigateToLibrary = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                // 3. System Features, SELinux status, Root & Joystick & Stepper
                composable(Screen.Features.route) {
                    LocationMockScreen(
                        simulationViewModel = simulationViewModel,
                        onNavigateToMap = {
                            navController.navigate(Screen.Location.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                // 4. About App Screen (With GitHub link, liquid glass secondary setting, credits)
                composable(Screen.About.route) {
                    AboutScreen(
                        isLiquidGlass = isLiquidGlassEnabled,
                        onToggleLiquidGlass = { enabled ->
                            isLiquidGlassEnabled = enabled
                        },
                        onNavigateToLibrary = {
                            navController.navigate(Screen.Library.route)
                        }
                    )
                }

                // 5. Saved Route Library
                composable(Screen.Library.route) {
                    RouteLibraryScreen(
                        mapViewModel = mapViewModel,
                        onRouteSelected = {
                            navController.navigate(Screen.Route.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }

            // =================================================================
            // LocationSpoofer Style Floating Bottom Bar (Clean Tabs, Sliding Indicator)
            // =================================================================
            AndroidFloatingBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                tabs = navigationTabs,
                currentRoute = currentRoute,
                isLiquidGlass = isLiquidGlassEnabled,
                onTabSelected = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}