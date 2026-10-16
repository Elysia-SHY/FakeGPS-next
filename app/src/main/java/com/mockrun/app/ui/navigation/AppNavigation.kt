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
import com.mockrun.app.core.designsystem.BackgroundThemeManager
import com.mockrun.app.core.designsystem.LiquidGlassDefaults
import com.mockrun.app.core.designsystem.LocalHazeState
import com.mockrun.app.core.designsystem.LocalLiquidGlassEnabled
import com.mockrun.app.core.designsystem.hazeSource
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

    var isLiquidGlassEnabled by remember {
        mutableStateOf(LiquidGlassDefaults.isEnabled(context))
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 设计稿：Tab 栏 72dp + 底距 22dp
    val bottomBarPadding = navBarBottom + 94.dp

    // 全局模糊源：整个内容区（含地图页 Aurora 雾面层）会被捕获，供底部玻璃 Tab 栏采样。
    // 地图页的原生 MapView 无法被 Haze 捕获，但地图页内已铺一层 Compose 绘制的
    // Aurora 雾面，因此这里录到的内容非空，底栏能拿到真实模糊而不是空图层。
    val chromeHaze = remember { dev.chrisbanes.haze.HazeState() }

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

    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides isLiquidGlassEnabled
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Sliding Multi-Screen View (Driven by tabPosition in real-time)
            //
            // 关键修复（解决功能页 / 关于页滑动卡片时毛玻璃模糊突然消失的根因）：
            //  1. 页面横向位移改用 Layout Placement 阶段的 Modifier.offset { IntOffset(...) }
            //     而非 graphicsLayer { translationX = ... }。graphicsLayer 只修改 RenderNode
            //     变换矩阵而不触发 Layout 阶段，导致切换动画结束后 AppBackground 的
            //     HazeNode.onGloballyPositioned 不再触发，pageHaze.positionOnScreen 卡在
            //     动画中途的屏幕外旧坐标；一旦用户上下滚动卡片触发 HazeChildNode 重算
            //     positionInContent = cardPos - pageHaze.positionOnScreen，就会把背景图层
            //     平移出卡片裁剪框之外，使模糊瞬间消失只剩半透明遮罩。
            //     改为 Modifier.offset 后，Placement 阶段自动同步更新源层与卡片坐标，误差为 0。
            //  2. 该容器同时作为底部悬浮 Tab 栏的模糊源（chromeHaze）。过去这里不挂源，
            //     理由是地图页的 AndroidView 录出来是空图层；现在地图页内已铺 Aurora 雾面
            //     （Compose 绘制），录制内容非空，底栏因此能获得真实背景模糊。
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(chromeHaze)
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
                            isLiquidGlass = isLiquidGlassEnabled,
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
                            isLiquidGlass = isLiquidGlassEnabled,
                            isTablet = isTablet,
                            onToggleLiquidGlass = { isLiquidGlassEnabled = it },
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
            // 底栏接页面容器录制的 chromeHaze：内容区在它下方，模糊采样即页面真实内容
            // （地图页为 Aurora 雾面 + 地图边缘、其余页为极光底衬），因此得到与 iOS
            // 系统 Tab 栏同类的通透悬浮玻璃，而不是一块纯色不透明板。
            if (!showLibrarySubScreen) {
                CompositionLocalProvider(LocalHazeState provides chromeHaze) {
                    AndroidFloatingBottomBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        tabs = navigationTabs,
                        currentPosition = tabPosition.value,
                        isLiquidGlass = isLiquidGlassEnabled,
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
                    isLiquidGlass = isLiquidGlassEnabled,
                    onDismiss = { VersionSyncManager.dismissUpdatePrompt() }
                )
            }
        }
    }
}