package com.mockrun.app.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * CompositionLocal providing global Liquid Glass effect toggle state.
 */
val LocalLiquidGlassEnabled = compositionLocalOf { true }

/**
 * CompositionLocal providing Chris Banes HazeState for authentic background frosted blur.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

object LiquidGlassDefaults {
    const val PREFS_NAME = "fake_gps_ui_prefs"
    const val KEY_LIQUID_GLASS_ENABLED = "is_liquid_glass_enabled"

    fun isEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_LIQUID_GLASS_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_LIQUID_GLASS_ENABLED, enabled).apply()
    }
}

/**
 * High-performance Liquid Glass (液态毛玻璃) styling modifier.
 * When [isLiquidGlass] is true:
 *   - Ultra-translucent crystal prismatic gradient background (通透晶莹微棱镜折射底衬)
 *   - 135° Fresnel total reflection rim-light border (菲涅尔微棱镜双光边框)
 *   - Physical 3D inner bevel specular highlight (微倒角立体高光内沿)
 *   - Top-down glancing specular surface sheen & bottom ambient reflection (表面掠射弧光)
 *   - Soft diffuse floating ambient shadow (空间悬浮光晕)
 *   - Fully safe: rendered via drawBehind to protect foreground text/icon sharpness
 * When [isLiquidGlass] is false:
 *   - Crisp solid surface (pure material, zero blur/shader overhead, battery-saving)
 */
fun Modifier.liquidGlass(
    isLiquidGlass: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 12.dp,
    containerColor: Color? = null,
    borderWidth: Dp = 1.0.dp,
    hazeState: HazeState? = null
): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val resolvedHaze = hazeState

    if (isLiquidGlass) {
        // 1. Crystal Base Gradient (通透晶莹微棱镜底衬 - 高透光率，让底层地图道路地标清晰穿透)
        val crystalBaseBrush = if (containerColor != null) {
            Brush.linearGradient(
                colors = listOf(
                    containerColor.copy(alpha = 0.88f),
                    containerColor.copy(alpha = 0.68f),
                    containerColor.copy(alpha = 0.82f)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        } else if (isDark) {
            // Obsidian Smoked Crystal (黑曜水晶通透深邃微光)
            Brush.linearGradient(
                0.0f to Color(0x94262B38), // 58% top-left specular highlight
                0.40f to Color(0x5212141A), // 32% high-transparency cosmic dark
                0.75f to Color(0x66181B22), // 40% obsidian crystal body
                1.0f to Color(0x801F232D),  // 50% deep obsidian depth
                start = Offset.Zero,
                end = Offset.Infinite
            )
        } else {
            // Ultra-Clear Prismatic Crystal (超白玻微偏光极度通透 - 拒绝乳白扁平塑料感)
            Brush.linearGradient(
                0.0f to Color(0xB8FFFFFF), // 72% 入射光掠影
                0.38f to Color(0x52E8F2FC), // 32% 冰晶微偏光通透带 - 地图道路与图钉清晰穿透！
                0.78f to Color(0x66FFFFFF), // 40% 晶体本体高透光
                1.0f to Color(0x78D8E8F8),  // 47% 边缘微棱镜折射散色
                start = Offset.Zero,
                end = Offset.Infinite
            )
        }

        // 2. High-Contrast Fresnel Rim Light (135° 菲涅尔全反射微棱镜双光边框)
        val borderBrush = Brush.linearGradient(
            colors = if (containerColor != null) {
                listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.60f)
                )
            } else if (isDark) {
                listOf(
                    Color.White.copy(alpha = 0.80f), // 顶角锐利折射高光
                    Color.White.copy(alpha = 0.35f),
                    Color.White.copy(alpha = 0.08f),
                    Color(0x507090B0)                // 底角微偏光反射
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.98f), // 98% 纯净高光棱镜光芒
                    Color.White.copy(alpha = 0.65f),
                    Color.White.copy(alpha = 0.25f),
                    Color(0x99B2CEE8)                // 晶体折射边缘光晕
                )
            },
            start = Offset.Zero,
            end = Offset.Infinite
        )

        // 3. Ambient Diffuse Shadow (空间悬浮柔光投影 - 偏深蓝调模拟透光阴影)
        val spotColor = if (isDark) Color(0x8C000000) else Color(0x24001A33)
        val ambientColor = if (isDark) Color(0x4D000000) else Color(0x12001020)

        val baseModifier = this
            .shadow(
                elevation = elevation,
                shape = shape,
                spotColor = spotColor,
                ambientColor = ambientColor
            )
            .clip(shape)

        val blurredModifier = if (resolvedHaze != null) {
            baseModifier.hazeChild(
                state = resolvedHaze,
                shape = shape,
                style = HazeDefaults.style(
                    tint = if (containerColor != null) {
                        containerColor.copy(alpha = 0.28f)
                    } else if (isDark) {
                        Color(0x38161B26)
                    } else {
                        Color(0x44FFFFFF)
                    },
                    blurRadius = 24.dp,
                    noiseFactor = 0.10f
                )
            )
        } else {
            baseModifier.background(crystalBaseBrush, shape)
        }

        blurredModifier
            .border(borderWidth.coerceAtLeast(1.0.dp), borderBrush, shape)
            .drawBehind {
                // Top-Left Inner Specular Bevel (物理玻璃厚度立体内倒角高光)
                val innerBevelBrush = Brush.linearGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    },
                    start = Offset.Zero,
                    end = Offset(size.width * 0.65f, size.height * 0.45f)
                )
                drawRoundRect(
                    brush = innerBevelBrush,
                    size = size,
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // Top Specular Surface Sheen & Bottom Ambient Reflection (表面掠射弧光与环境反射)
                val topSheenBrush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = if (isDark) 0.16f else 0.42f),
                    0.22f to Color.White.copy(alpha = if (isDark) 0.05f else 0.14f),
                    0.55f to Color.Transparent,
                    0.90f to Color.Transparent,
                    1.0f to Color.White.copy(alpha = if (isDark) 0.04f else 0.10f)
                )
                drawRect(topSheenBrush)
            }
    } else {
        val solidFill = containerColor ?: if (isDark) {
            Color(0xFF1E1E20)
        } else {
            Color(0xFFFFFFFF)
        }
        val solidBorder = if (isDark) Color(0x33FFFFFF) else Color(0x18000000)

        this
            .shadow(
                elevation = (elevation * 0.75f).coerceAtLeast(4.dp),
                shape = shape,
                spotColor = Color.Black.copy(alpha = if (isDark) 0.4f else 0.12f)
            )
            .clip(shape)
            .background(solidFill, shape)
            .border(borderWidth, solidBorder, shape)
    }
}

/**
 * Ambient chromatic luminous backdrop for non-map screens (e.g. LocationMockScreen, AboutScreen).
 * Renders Apple HIG / VisionOS style ethereal radiant lighting orbs onto an obsidian or pearlescent canvas,
 * and captures them via dev.chrisbanes.haze.haze(hazeState).
 * When cards float and scroll above this backdrop with .liquidGlass(hazeState), Haze's GPU Gaussian blur
 * disperses these luminous gradients, creating genuine, refraction-rich liquid frosted glass!
 */
@Composable
fun FrostedAmbientBackground(
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    val baseModifier = modifier
        .fillMaxSize()
            .background(if (isDark) Color(0xFF090C12) else Color(0xFFF2F4F7))
            .drawBehind {
                val canvasWidth = size.width
                val canvasHeight = size.height

                if (isDark) {
                    // 1. Top-Right Radiant Sapphire Orb (深邃皇家蓝宝石光晕)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2563EB).copy(alpha = 0.40f),
                                Color(0xFF1D4ED8).copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            center = Offset(canvasWidth * 0.88f, canvasHeight * 0.10f),
                            radius = canvasWidth * 0.85f
                        )
                    )

                    // 2. Mid-Left Luminous Violet / Amethyst Orb (紫罗兰晶体偏光)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF7C3AED).copy(alpha = 0.35f),
                                Color(0xFF4C1D95).copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            center = Offset(canvasWidth * 0.12f, canvasHeight * 0.42f),
                            radius = canvasWidth * 0.85f
                        )
                    )

                    // 3. Bottom-Right Cyan / Emerald Atmospheric Glow (青翠薄荷微光)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0D9488).copy(alpha = 0.30f),
                                Color(0xFF065F46).copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(canvasWidth * 0.90f, canvasHeight * 0.80f),
                            radius = canvasWidth * 0.75f
                        )
                    )

                    // 4. Subtle Center Indigo Fill
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3B82F6).copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(canvasWidth * 0.50f, canvasHeight * 0.62f),
                            radius = canvasWidth * 0.65f
                        )
                    )
                } else {
                    // Light Mode - Luminous Apple VisionOS Pastel Gradient Fields
                    // 1. Top-Right Cerulean Sky Glow (天蓝晶莹漫射光)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.36f),
                                Color(0xFF93C5FD).copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(canvasWidth * 0.88f, canvasHeight * 0.10f),
                            radius = canvasWidth * 0.85f
                        )
                    )

                    // 2. Mid-Left Lavender / Quartz Aura (淡紫幻彩光晕)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFC084FC).copy(alpha = 0.32f),
                                Color(0xFFE9D5FF).copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            center = Offset(canvasWidth * 0.12f, canvasHeight * 0.42f),
                            radius = canvasWidth * 0.85f
                        )
                    )

                    // 3. Bottom-Right Emerald / Mint Glow (薄荷青绿柔光)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF34D399).copy(alpha = 0.28f),
                                Color(0xFFA7F3D0).copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(canvasWidth * 0.90f, canvasHeight * 0.80f),
                            radius = canvasWidth * 0.75f
                        )
                    )

                    // 4. Center Radiant Reflection
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF60A5FA).copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(canvasWidth * 0.45f, canvasHeight * 0.28f),
                            radius = canvasWidth * 0.60f
                        )
                    )
                }
            }

    val finalModifier = if (hazeState != null) baseModifier.haze(hazeState) else baseModifier
    Box(modifier = finalModifier)
}
