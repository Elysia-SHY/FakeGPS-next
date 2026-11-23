package com.mockrun.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.bouncyClickable
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class FloatingTabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

/**
 * Material 3 navigation bar with real-time gesture and screen translation synchronization.
 * Driven by [currentPosition] (Float from 0.0 to tabCount - 1).
 */
@Composable
fun AndroidFloatingBottomBar(
    modifier: Modifier = Modifier,
    tabs: List<FloatingTabItem>,
    currentPosition: Float,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTabSelected: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tabCount = tabs.size.coerceAtLeast(1)

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .height(72.dp),
        shape = AppShapes.bar,
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
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

            // 1. 选中态指示底（设计稿 .tab.active —— rgba(47,123,255,.14)）
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clip(AppShapes.pill)
                    .background(scheme.primaryContainer)
            )

            // 2. NavigationBar items retain the continuous selection interpolation.
            NavigationBar(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentColor = scheme.onSurface,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val distance = abs(clampedPosition - index)
                    val selectProgress = (1.0f - distance).coerceIn(0f, 1f)
                    val currentColor = lerp(
                        scheme.onSurfaceVariant,
                        scheme.onPrimaryContainer,
                        selectProgress
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(AppShapes.pill)
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
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = currentColor,
                                maxLines = 1
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
