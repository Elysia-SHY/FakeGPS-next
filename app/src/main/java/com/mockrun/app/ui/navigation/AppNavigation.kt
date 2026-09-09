package com.mockrun.app.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.mockrun.app.ui.screen.LocationMockScreen
import com.mockrun.app.ui.screen.MapScreen
import com.mockrun.app.ui.screen.RouteLibraryScreen
import com.mockrun.app.ui.screen.RouteSimulationScreen
import com.mockrun.app.ui.theme.*
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.ui.viewmodel.SimulationViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Map : Screen("map", "地图选点", Icons.Default.LocationOn)
    object LocationMock : Screen("location_mock", "虚拟定位", Icons.Default.Place)
    object RouteSimulation : Screen("route_simulation", "路线模拟", Icons.Default.PlayArrow)
    object Library : Screen("library", "路线库", Icons.Default.Menu)
}

@Composable
fun AppNavigation(
    mapViewModel: MapViewModel = hiltViewModel(),
    simulationViewModel: SimulationViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val items = listOf(Screen.Map, Screen.LocationMock, Screen.RouteSimulation, Screen.Library)

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Map.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.LocationMock.route) {
                LocationMockScreen(
                    simulationViewModel = simulationViewModel,
                    onNavigateToMap = {
                        navController.navigate(Screen.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.RouteSimulation.route) {
                RouteSimulationScreen(
                    simulationViewModel = simulationViewModel,
                    mapViewModel = mapViewModel,
                    onNavigateToMap = {
                        navController.navigate(Screen.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
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
            composable(Screen.Map.route) {
                MapScreen(mapViewModel = mapViewModel, simulationViewModel = simulationViewModel)
            }
            composable(Screen.Library.route) {
                RouteLibraryScreen(
                    mapViewModel = mapViewModel,
                    onRouteSelected = {
                        navController.navigate(Screen.RouteSimulation.route) {
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

        // =====================================================================
        // Authentic Apple Floating Frosted Glass Capsule TabBar
        // =====================================================================
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(62.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color.Black.copy(alpha = 0.15f),
                    ambientColor = Color.Black.copy(alpha = 0.08f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = IosColors.ThinMaterial,
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.65f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.1f else 1.0f,
                        animationSpec = MotionTokens.BouncySpringSpec,
                        label = "navTabScale"
                    )

                    val pillBackground = if (isSelected) {
                        IosColors.SystemBlue.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(pillBackground)
                            .bouncyClickable {
                                if (!isSelected) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = if (isSelected) IosColors.SystemBlue else IosColors.SecondaryLabel,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = screen.title,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) IosColors.SystemBlue else IosColors.SecondaryLabel
                            )
                        }
                    }
                }
            }
        }
    }
}