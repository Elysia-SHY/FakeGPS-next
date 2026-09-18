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
import androidx.compose.ui.unit.Dp
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
    simState: com.mockrun.app.domain.model.SimulationState,
    simulationViewModel: SimulationViewModel,
    bottomBarPadding: Dp,
    modifier: Modifier = Modifier,
    onResetToRealLocation: (Pair<Double, Double>) -> Unit
) {
    Column(
        modifier = modifier
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
                    com.mockrun.app.core.location.MockLocationEngine.forceCleanAllTestProviders(context)
                    CoordinateConverter.requestFreshLocation(context) { freshLat, freshLon ->
                        onResetToRealLocation(freshLat to freshLon)
                    }
                    val real = CoordinateConverter.getRealDeviceLocation(context)
                    if (real != null) {
                        onResetToRealLocation(real)
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
}
