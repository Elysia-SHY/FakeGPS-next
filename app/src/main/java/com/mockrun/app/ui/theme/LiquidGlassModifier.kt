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

val LocalHazeState = compositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }
val LocalBottomBarHazeState = compositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

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
 * Liquid Glass (液态玻璃) styling modifier.
 * When [isLiquidGlass] is true:
 *   - If a HazeState is provided (screens whose background is drawn by Compose):
 *     cards apply real backdrop blur via Haze (the iOS-style frosted glass).
 *   - Otherwise (MapScreen, where OSMDroid renders into its own View hierarchy and
 *     the screen is under a horizontal-swipe translation that broke Haze's capture
 *     alignment in v1.3.9): cards fall back to a translucent crystal gradient
 *     so the map remains readable underneath.
 *   - Solid surface when [isLiquidGlass] is false.
 *
 * The signature is frozen — 31 call sites across the app depend on it.
 */
fun Modifier.liquidGlass(
    isLiquidGlass: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 12.dp,
    containerColor: Color? = null,
    borderWidth: Dp = 1.0.dp,
    hazeState: Any? = null
): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val resolvedHaze = LocalHazeState.current

    if (isLiquidGlass) {
        // Crystal Base Gradient - 底衬（让地图道路与图钉清晰穿透）
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
            // Obsidian Smoked Crystal
            Brush.linearGradient(
                0.0f to Color(0x94262B38),
                0.40f to Color(0x5212141A),
                0.75f to Color(0x66181B22),
                1.0f to Color(0x801F232D),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        } else {
            // Ultra-Clear Prismatic Crystal
            Brush.linearGradient(
                0.0f to Color(0xB8FFFFFF),
                0.38f to Color(0x52E8F2FC),
                0.78f to Color(0x66FFFFFF),
                1.0f to Color(0x78D8E8F8),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        }

        // VisionOS single hairline rim
        val borderBrush = Brush.linearGradient(
            colors = if (containerColor != null) {
                listOf(
                    containerColor.copy(alpha = 0.60f),
                    containerColor.copy(alpha = 0.25f)
                )
            } else if (isDark) {
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.06f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.65f),
                    Color.White.copy(alpha = 0.20f)
                )
            },
            start = Offset.Zero,
            end = Offset.Infinite
        )

        // Base modifier with soft clipping
        val baseModifier = if (elevation > 0.dp) {
            this.shadow(
                elevation = elevation,
                shape = shape,
                spotColor = if (isDark) Color(0x8C000000) else Color(0x24001A33),
                ambientColor = if (isDark) Color(0x4D000000) else Color(0x12001020)
            ).clip(shape)
        } else {
            this.clip(shape)
        }

        // Path A: backdrop blur via Haze (real iOS-style frosted glass)
        if (resolvedHaze != null) {
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
            ).border(
                borderWidth.coerceAtMost(0.8.dp).coerceAtLeast(0.5.dp),
                borderBrush, shape
            )
        } else {
            // Path B: gradient only (where the screen cannot provide a backdrop — MapScreen).
            // This is what gave the "flat cellophane" reading. It is the best we can do
            // when the underlying content is an Android View, not a Compose layer.
            baseModifier
                .background(crystalBaseBrush, shape)
                .border(
                    borderWidth.coerceAtMost(0.8.dp).coerceAtLeast(0.5.dp),
                    borderBrush, shape
                )
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
 * Makes everything inside [content] available as a **backdrop** for Liquid Glass cards.
 *
 * Wrap the root of a screen once; every `Modifier.liquidGlass(...)` inside will then blur
 * whatever sits behind it. This is opt-in per screen on purpose:
 *
 * - **Works on** screens whose background is drawn by Compose (About, LocationMock,
 *   RouteLibrary, dialogs) — Haze records the Compose layer and blurs it.
 * - **Must not wrap MapScreen.** OSMDroid draws into its own View hierarchy, invisible to
 *   Compose's GraphicsLayer capture; wrapping it produced the misaligned "ghost blur" that
 *   v1.3.9 had to remove. MapScreen therefore provides `LocalHazeState = null` and keeps
 *   edge-optics-only glass.
 *
 * [modifier] normally passes `Modifier.fillMaxSize()` so the recorded backdrop covers the
 * whole screen; [content] is the screen itself.
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // haze 0.7.3 ships HazeState but no rememberHazeState() factory, so it is created directly.
    val hazeState = remember { HazeState() }
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(
            modifier = modifier.haze(
                state = hazeState,
                style = HazeDefaults.style(backgroundColor = Color.Transparent)
            )
        ) {
            content()
        }
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
    hazeState: Any? = null,
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

    Box(modifier = baseModifier)
}
