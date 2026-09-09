package com.mockrun.app.ui.components

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.ui.theme.IosColors
import com.mockrun.app.ui.theme.LiquidGlassDefaults
import com.mockrun.app.ui.theme.liquidGlass

data class FloatingTabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

@Composable
fun AndroidFloatingBottomBar(
    modifier: Modifier = Modifier,
    tabs: List<FloatingTabItem>,
    currentRoute: String?,
    isLiquidGlass: Boolean,
    onTabSelected: (String) -> Unit,
    onToggleLiquidGlass: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(64.dp)
            .liquidGlass(
                isLiquidGlass = isLiquidGlass,
                shape = RoundedCornerShape(32.dp),
                elevation = 16.dp
            ),
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navigation Tabs (4 items)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = currentRoute == tab.route
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "tab_scale"
                    )

                    val pillBgColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            if (isLiquidGlass) IosColors.SystemBlue.copy(alpha = 0.16f) else IosColors.SystemBlue.copy(alpha = 0.12f)
                        } else Color.Transparent,
                        label = "tab_pill_bg"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(pillBgColor)
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
                                tint = if (isSelected) IosColors.SystemBlue else (if (isDark) Color.White.copy(0.6f) else IosColors.SecondaryLabel),
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) IosColors.SystemBlue else (if (isDark) Color.White.copy(0.6f) else IosColors.SecondaryLabel)
                            )
                        }
                    }
                }
            }

            // Divider between Tabs and Liquid Glass switch
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(26.dp)
                    .background(if (isDark) Color.White.copy(0.18f) else Color.Black.copy(0.12f))
            )

            // =================================================================
            // ⭐ Liquid Glass Toggle Switch (底栏液态玻璃开关)
            // =================================================================
            val glassIconScale by animateFloatAsState(
                targetValue = if (isLiquidGlass) 1.1f else 0.95f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "glass_icon_scale"
            )

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        val next = !isLiquidGlass
                        LiquidGlassDefaults.setEnabled(context, next)
                        onToggleLiquidGlass(next)
                        Toast.makeText(
                            context,
                            if (next) "💧 已开启液态毛玻璃模式" else "⬛ 已切换为经典纯色模式",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (isLiquidGlass) IosColors.SystemBlue.copy(alpha = 0.15f) else (if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isLiquidGlass) Icons.Default.WaterDrop else Icons.Default.BlurOff,
                        contentDescription = "液态玻璃开关",
                        tint = if (isLiquidGlass) IosColors.SystemBlue else (if (isDark) Color.White.copy(0.6f) else IosColors.SecondaryLabel),
                        modifier = Modifier
                            .size(17.dp)
                            .graphicsLayer {
                                scaleX = glassIconScale
                                scaleY = glassIconScale
                            }
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "玻璃",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLiquidGlass) IosColors.SystemBlue else (if (isDark) Color.White.copy(0.6f) else IosColors.SecondaryLabel)
                        )
                        Text(
                            text = if (isLiquidGlass) "开启" else "关闭",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLiquidGlass) IosColors.SystemGreen else IosColors.SecondaryLabel
                        )
                    }
                }
            }
        }
    }
}
