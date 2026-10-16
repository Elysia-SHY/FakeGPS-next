package com.mockrun.app.feature.map

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.core.location.CoordinateConverter
import com.mockrun.app.core.designsystem.IosBlue
import com.mockrun.app.core.designsystem.bouncyClickable
import com.mockrun.app.core.designsystem.liquidGlass
import com.mockrun.app.ui.viewmodel.SimulationViewModel
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun MapFloatingActions(
    context: Context,
    activeMapTab: MapTab,
    drawnWaypoints: List<WayPoint>,
    currentMapType: MapSourceType,
    mapViewRef: MapView?,
    isLiquidGlass: Boolean,
    isPointMockActive: Boolean,
    isJoystickRunning: Boolean,
    simState: com.mockrun.app.domain.model.SimulationState,
    simulationViewModel: SimulationViewModel,
    modifier: Modifier = Modifier,
    onResetToRealLocation: (Pair<Double, Double>) -> Unit
) {
    // 定位由父级 Column 统一控制（设计稿：与缩放控件同列，去除 bottom 225dp 魔法数字）。
    // 原 bottomBarPadding 参数在此处从未被使用（只是被 @Suppress 吞掉的死参数），
    // 已删除 —— 底部安全区改由 ScreenInsets 在各面板内部统一推导。
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Route Fit Bounds FAB (only in ROUTE mode when waypoints >= 2)
        if (activeMapTab == MapTab.ROUTE && drawnWaypoints.size >= 2) {
            Surface(
                modifier = Modifier
                    .size(54.dp)
                    .liquidGlass(isLiquidGlass = isLiquidGlass, shape = CircleShape, elevation = 14.dp)
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
                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "全览路线", tint = IosBlue, modifier = Modifier.size(24.dp))
                }
            }
        }

        // Locate to Real Physical Location FAB —— 设计稿 .locate-btn 54dp 圆
        Surface(
            modifier = Modifier
                .size(54.dp)
                .liquidGlass(isLiquidGlass = isLiquidGlass, shape = CircleShape, elevation = 14.dp)
                .bouncyClickable {
                    if (isPointMockActive) simulationViewModel.stopPointMock(context)
                    if (simState.status is com.mockrun.app.domain.model.SimulationStatus.Running) simulationViewModel.stopSimulation(context)
                    CoordinateConverter.clearSavedRealLocation(context)
                    // 必须加摇杆守卫。forceCleanAllTestProviders 会无条件移除
                    // GPS/NETWORK/PASSIVE/fused 四个 test provider，而悬浮摇杆持有
                    // 另一个 MockLocationEngine 实例、其 isRegistered 仍为 true，
                    // 而 inject() 只在 `!isRegistered || activeProviders.isEmpty()`
                    // 时才重新注册 —— 于是此后每次 setTestProviderLocation 都抛
                    // IllegalArgumentException 并被内部 runCatching 吞掉：
                    // 界面显示摇杆开着、浮窗也在动，但不再注入任何位置。
                    // 项目另外三处调用点都带同样的守卫
                    // （MockLocationService 的 start/stop 路径）。
                    if (!isJoystickRunning) {
                        com.mockrun.app.core.location.MockLocationEngine.forceCleanAllTestProviders(context)
                    }
                    // 单路复位：requestFreshLocation 会同步回调一次，并在 provider
                    // 回调里再回调一次（取消信号挂 15 秒）。原先这里又独立查了一遍
                    // getRealDeviceLocation 并第二次调用 onResetToRealLocation，
                    // 于是用户重新瞄准后，迟到的回调会把准星和地图拽回物理位置。
                    CoordinateConverter.requestFreshLocation(context) { freshLat, freshLon ->
                        onResetToRealLocation(freshLat to freshLon)
                        val isGcj = currentMapType != MapSourceType.OPEN_STREET_MAP
                        val (tLat, tLon) = if (isGcj) {
                            CoordinateConverter.wgs84ToGcj02(freshLat, freshLon)
                        } else {
                            freshLat to freshLon
                        }
                        mapViewRef?.controller?.apply {
                            setZoom(16.5)
                            animateTo(GeoPoint(tLat, tLon))
                        }
                    }
                    Toast.makeText(context, "已复位至真机物理位置", Toast.LENGTH_SHORT).show()
                },
            shape = CircleShape,
            color = Color.Transparent
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Default.MyLocation, contentDescription = "真机物理定位", tint = IosBlue, modifier = Modifier.size(24.dp))
            }
        }
    }
}
