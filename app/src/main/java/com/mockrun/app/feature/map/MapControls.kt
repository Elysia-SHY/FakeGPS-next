package com.mockrun.app.feature.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.*

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
                    .padding(horizontal = 6.dp)
                    .onGloballyPositioned { coordinates ->
                        trackHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            down.consume()

                            // Immediate 1:1 response on pointer-down tap
                            val initialRatio = (1f - (down.position.y / trackHeightPx)).coerceIn(0f, 1f)
                            val newInitialZoom = updatedMinZoom + initialRatio * (updatedMaxZoom - updatedMinZoom)
                            updatedOnZoomChange(newInitialZoom)

                            // Follow finger drag continuously
                            while (true) {
                                val event = awaitPointerEvent()
                                val dragPointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!dragPointer.pressed) break

                                dragPointer.consume()
                                val ratio = (1f - (dragPointer.position.y / trackHeightPx)).coerceIn(0f, 1f)
                                val newZoom = updatedMinZoom + ratio * (updatedMaxZoom - updatedMinZoom)
                                updatedOnZoomChange(newZoom)
                            }
                            isDragging = false
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                // Background Track Capsule
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(IosSeparator.copy(alpha = 0.5f))
                )

                // Dynamic Active Progress Capsule
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight(animatedFill)
                        .clip(RoundedCornerShape(3.dp))
                        .background(IosBlue)
                )

                // Tactile Pill Thumb Indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedFill),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(IosBlue)
                            .border(0.5.dp, IosHairlineBorder, RoundedCornerShape(2.5.dp))
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
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "缩小地图",
                    tint = IosBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
