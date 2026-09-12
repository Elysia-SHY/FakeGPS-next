package com.mockrun.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.ui.theme.IosColors
import com.mockrun.app.ui.theme.bouncyClickable
import com.mockrun.app.ui.theme.liquidGlass
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class FloatingTabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

/**
 * Floating Capsule Bottom Navigation Bar with real-time gesture & screen translation synchronization.
 * Driven by [currentPosition] (Float from 0.0 to tabCount - 1).
 */
@Composable
fun AndroidFloatingBottomBar(
    modifier: Modifier = Modifier,
    tabs: List<FloatingTabItem>,
    currentPosition: Float,
    isLiquidGlass: Boolean,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTabSelected: (Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val tabCount = tabs.size.coerceAtLeast(1)

    // Single crystal liquid frosted glass capsule (58dp height, 29dp pill corners)
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .height(58.dp)
            .liquidGlass(
                isLiquidGlass = isLiquidGlass,
                shape = RoundedCornerShape(29.dp),
                elevation = 10.dp
            ),
        shape = RoundedCornerShape(29.dp),
        color = Color.Transparent
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 3.dp)
                // Real-time horizontal drag gesture tracking
                .pointerInput(tabCount) {
                    detectHorizontalDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val tabWidthPx = size.width.toFloat() / tabCount
                            val deltaFraction = dragAmount / tabWidthPx
                            onDragDelta(deltaFraction)
                        }
                    )
                }
        ) {
            val tabWidth = maxWidth / tabCount
            val clampedPosition = currentPosition.coerceIn(0f, (tabCount - 1).toFloat())
            val indicatorOffset = tabWidth * clampedPosition

            // 1. Soft seamless micro-glow indicator pill
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (isLiquidGlass) {
                            if (isDark) Color.White.copy(alpha = 0.15f) else IosColors.SystemBlue.copy(alpha = 0.15f)
                        } else {
                            if (isDark) Color.White.copy(alpha = 0.10f) else IosColors.SystemBlue.copy(alpha = 0.10f)
                        }
                    )
            )

            // 2. Tab Items with real-time linear interpolation
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val distance = abs(clampedPosition - index)
                    val selectProgress = (1.0f - distance).coerceIn(0f, 1f)

                    val unselectedColor = if (isDark) Color.White.copy(alpha = 0.52f) else Color.Black.copy(alpha = 0.48f)
                    val activeColor = IosColors.SystemBlue
                    val currentColor = lerp(unselectedColor, activeColor, selectProgress)
                    val currentScale = 1.0f + 0.12f * selectProgress

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .bouncyClickable {
                                onTabSelected(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = currentColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        scaleX = currentScale
                                        scaleY = currentScale
                                    }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                fontSize = 10.5.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (selectProgress > 0.5f) FontWeight.Bold else FontWeight.Medium,
                                color = currentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility overload for route string-based calls.
 */
@Composable
fun AndroidFloatingBottomBar(
    modifier: Modifier = Modifier,
    tabs: List<FloatingTabItem>,
    currentRoute: String?,
    isLiquidGlass: Boolean,
    onTabSelected: (String) -> Unit
) {
    val selectedIndex = remember(currentRoute, tabs) {
        val idx = tabs.indexOfFirst { it.route == currentRoute }
        if (idx >= 0) idx else 0
    }
    val scope = rememberCoroutineScope()
    val animatedPosition = remember { Animatable(selectedIndex.toFloat()) }

    LaunchedEffect(selectedIndex) {
        if (animatedPosition.targetValue.roundToInt() != selectedIndex) {
            animatedPosition.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    AndroidFloatingBottomBar(
        modifier = modifier,
        tabs = tabs,
        currentPosition = animatedPosition.value,
        isLiquidGlass = isLiquidGlass,
        onDragDelta = { delta ->
            scope.launch {
                val next = (animatedPosition.value + delta).coerceIn(0f, (tabs.size - 1).toFloat())
                animatedPosition.snapTo(next)
            }
        },
        onDragEnd = {
            val target = animatedPosition.value.roundToInt().coerceIn(0, tabs.size - 1)
            scope.launch {
                animatedPosition.animateTo(
                    targetValue = target.toFloat(),
                    animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
                )
                if (target != selectedIndex) {
                    onTabSelected(tabs[target].route)
                }
            }
        },
        onTabSelected = { targetIndex ->
            scope.launch {
                animatedPosition.animateTo(
                    targetValue = targetIndex.toFloat(),
                    animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
                )
                if (targetIndex != selectedIndex) {
                    onTabSelected(tabs[targetIndex].route)
                }
            }
        }
    )
}
