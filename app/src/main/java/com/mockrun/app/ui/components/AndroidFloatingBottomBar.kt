package com.mockrun.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.mockrun.app.ui.theme.LocalHazeState
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
 * Floating Capsule Bottom Navigation Bar.
 * Inspired by LocationSpoofer & Orb Liquid Glass:
 * - Ultra-smooth sliding indicator capsule with spring physics
 * - Translucent obsidian/crystal frosted glass surface with specular reflection rim border
 * - Top-down refractive sheen and deep ambient double-layer shadow
 */
@Composable
fun AndroidFloatingBottomBar(
    modifier: Modifier = Modifier,
    tabs: List<FloatingTabItem>,
    currentRoute: String?,
    isLiquidGlass: Boolean,
    onTabSelected: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val hazeState = LocalHazeState.current

    val selectedIndex = remember(currentRoute, tabs) {
        val idx = tabs.indexOfFirst { it.route == currentRoute }
        if (idx >= 0) idx else 0
    }

    val tabCount = tabs.size.coerceAtLeast(1)
    val animatedIndex = remember { Animatable(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // 当外部路由变化（或点击切换）时，平滑驱动滑块滑动至对应标签
    LaunchedEffect(selectedIndex) {
        if (!isDragging && animatedIndex.targetValue.roundToInt() != selectedIndex) {
            animatedIndex.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // 外层单一晶莹液态毛玻璃容器（58dp 高度，29dp 大圆角胶囊，接入真 Haze 模糊）
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
                elevation = 12.dp,
                hazeState = hazeState
            ),
        shape = RoundedCornerShape(29.dp),
        color = Color.Transparent
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 3.dp)
                // 横向拖动手势监听：手势拖动时实时跟手，松手平滑弹簧回弹并切换页面
                .pointerInput(tabCount) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            scope.launch { animatedIndex.stop() }
                        },
                        onDragEnd = {
                            isDragging = false
                            val targetIndex = animatedIndex.value.roundToInt().coerceIn(0, tabCount - 1)
                            scope.launch {
                                animatedIndex.animateTo(
                                    targetValue = targetIndex.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = 0.76f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                if (targetIndex != selectedIndex) {
                                    onTabSelected(tabs[targetIndex].route)
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                animatedIndex.animateTo(
                                    targetValue = selectedIndex.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = 0.76f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val tabWidthPx = size.width.toFloat() / tabCount
                            val deltaFraction = dragAmount / tabWidthPx
                            val newFraction = (animatedIndex.value + deltaFraction).coerceIn(0f, (tabCount - 1).toFloat())
                            scope.launch {
                                animatedIndex.snapTo(newFraction)
                            }
                        }
                    )
                }
        ) {
            val tabWidth = maxWidth / tabCount
            val indicatorOffset = tabWidth * animatedIndex.value

            // =================================================================
            // 1. 一体化柔和微光滑块（无生硬重叠边框，彻底消除“两层盒中盒”感）
            // =================================================================
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

            // =================================================================
            // 2. 标签项（支持实时线性插值缩放与颜色渐变）
            // =================================================================
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    // 根据滑块与标签中心距离，计算当前标签的选中权重（0.0 ~ 1.0）
                    val distance = abs(animatedIndex.value - index)
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
                                scope.launch {
                                    animatedIndex.animateTo(
                                        targetValue = index.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = 0.76f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                                if (index != selectedIndex) {
                                    onTabSelected(tab.route)
                                }
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
