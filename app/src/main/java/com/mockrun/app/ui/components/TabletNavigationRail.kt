package com.mockrun.app.ui.components

import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.ui.theme.IosColors
import com.mockrun.app.ui.theme.liquidGlass

/**
 * Tablet Left Navigation Rail — 80dp frosted glass vertical tab bar.
 * Mirrors AndroidFloatingBottomBar aesthetics but oriented vertically.
 */
@Composable
fun TabletNavigationRail(
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
            .fillMaxHeight()
            .width(80.dp)
            .systemBarsPadding()
            .padding(start = 10.dp, top = 16.dp, bottom = 16.dp)
            .liquidGlass(isLiquidGlass = isLiquidGlass, shape = RoundedCornerShape(24.dp), elevation = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.18f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "rail_icon_scale_$index"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .then(if (isSelected) Modifier
                            .background(if (isLiquidGlass) { if (isDark) IosColors.SystemBlue.copy(0.20f) else IosColors.SystemBlue.copy(0.13f) } else { if (isDark) Color.White.copy(0.10f) else IosColors.SystemBlue.copy(0.10f) })
                            .border(0.8.dp, Brush.verticalGradient(if (isDark) listOf(Color.White.copy(0.28f), Color.Transparent) else listOf(Color.White.copy(0.65f), Color.Transparent)), RoundedCornerShape(18.dp))
                        else Modifier)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!isSelected) onTabSelected(tab.route) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(
                            imageVector = tab.icon, contentDescription = tab.title,
                            tint = if (isSelected) IosColors.SystemBlue else if (isDark) Color.White.copy(0.55f) else Color.Black.copy(0.50f),
                            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(tab.title, fontSize = 10.sp, lineHeight = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) IosColors.SystemBlue else if (isDark) Color.White.copy(0.60f) else Color.Black.copy(0.55f))
                    }
                }
            }
        }
    }
}