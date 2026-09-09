package com.mockrun.app.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal providing global Liquid Glass effect toggle state.
 */
val LocalLiquidGlassEnabled = compositionLocalOf { true }

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
 *   - Translucent frosted glass background
 *   - Specular rim-light border gradient
 *   - Inner refractive top-sheen
 *   - Soft diffuse ambient shadow
 * When [isLiquidGlass] is false:
 *   - Crisp solid surface (pure material, zero blur/shader overhead, battery-saving)
 */
fun Modifier.liquidGlass(
    isLiquidGlass: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 12.dp,
    containerColor: Color? = null,
    borderWidth: Dp = 0.8.dp
): Modifier = composed {
    val isDark = isSystemInDarkTheme()

    if (isLiquidGlass) {
        val baseFill = containerColor ?: if (isDark) {
            Color(0xB31C1C1E) // ~70% dark obsidian translucent
        } else {
            Color(0xD9FFFFFF) // ~85% pure white translucent frosted
        }

        val borderBrush = Brush.linearGradient(
            colors = if (isDark) {
                listOf(
                    Color.White.copy(alpha = 0.35f),
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.04f),
                    Color.White.copy(alpha = 0.20f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.40f),
                    Color.White.copy(alpha = 0.15f),
                    Color.White.copy(alpha = 0.65f)
                )
            }
        )

        this
            .shadow(
                elevation = elevation,
                shape = shape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.14f),
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.08f)
            )
            .clip(shape)
            .background(baseFill, shape)
            .border(borderWidth, borderBrush, shape)
            .drawWithContent {
                drawContent()
                // Top-down specular reflection sheen
                val sheenBrush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.06f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.03f)
                        )
                    }
                )
                drawRect(sheenBrush)
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
