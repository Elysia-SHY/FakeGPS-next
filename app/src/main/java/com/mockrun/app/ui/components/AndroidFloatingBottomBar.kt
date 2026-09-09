package com.mockrun.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.ui.theme.IosColors
import com.mockrun.app.ui.theme.liquidGlass

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
    val selectedIndex = remember(currentRoute, tabs) {
        val idx = tabs.indexOfFirst { it.route == currentRoute }
        if (idx >= 0) idx else 0
    }

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(62.dp)
            .liquidGlass(
                isLiquidGlass = isLiquidGlass,
                shape = RoundedCornerShape(31.dp),
                elevation = 16.dp
            ),
        shape = RoundedCornerShape(31.dp),
        color = Color.Transparent
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val tabCount = tabs.size.coerceAtLeast(1)
            val tabWidth = maxWidth / tabCount

            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "bottom_bar_indicator"
            )

            // =================================================================
            // 1. Sliding Indicator Capsule (LocationSpoofer Style)
            // =================================================================
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        if (isLiquidGlass) {
                            if (isDark) Color.White.copy(alpha = 0.16f) else IosColors.SystemBlue.copy(alpha = 0.14f)
                        } else {
                            if (isDark) Color.White.copy(alpha = 0.10f) else IosColors.SystemBlue.copy(alpha = 0.10f)
                        }
                    )
                    .border(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(Color.White.copy(0.32f), Color.Transparent)
                            } else {
                                listOf(Color.White.copy(0.70f), Color.Transparent)
                            }
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
            )

            // =================================================================
            // 2. Navigation Tab Items
            // =================================================================
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex

                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "tab_icon_scale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!isSelected) onTabSelected(tab.route)
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
                                tint = if (isSelected) {
                                    IosColors.SystemBlue
                                } else {
                                    if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.50f)
                                },
                                modifier = Modifier
                                    .size(21.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                fontSize = 10.5.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    IosColors.SystemBlue
                                } else {
                                    if (isDark) Color.White.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.55f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
