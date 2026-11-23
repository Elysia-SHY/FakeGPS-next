package com.mockrun.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/**
 * Compatibility state holder retained while legacy call sites are migrated.
 * Blur is no longer rendered; Material 3 surfaces are always used.
 */
val LocalLiquidGlassEnabled = compositionLocalOf { false }
val LocalHazeState = compositionLocalOf<HazeState?> { null }
val LocalBottomBarHazeState = compositionLocalOf<HazeState?> { null }

object LiquidGlassDefaults {
    const val PREFS_NAME = AppAppearancePreferences.PREFS_NAME
    const val KEY_LIQUID_GLASS_ENABLED = "is_liquid_glass_enabled"
    const val KEY_DYNAMIC_COLOR_ENABLED = AppAppearancePreferences.KEY_DYNAMIC_COLOR_ENABLED

    val BlurRadius = 0.dp
    const val NoiseFactorDark = 0f
    const val NoiseFactorLight = 0f

    fun isEnabled(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return preferences.getBoolean(
            KEY_DYNAMIC_COLOR_ENABLED,
            preferences.getBoolean(KEY_LIQUID_GLASS_ENABLED, false)
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        AppAppearancePreferences.setDynamicColorEnabled(context, enabled)
    }
}

/** No-op compatibility adapter. Haze capture is no longer part of the UI. */
fun Modifier.hazeSource(state: HazeState?): Modifier = this

/**
 * Material 3 replacement for translucent glass panels.
 *
 * Legacy parameters remain for source compatibility and are intentionally ignored.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.liquidGlass(
    isLiquidGlass: Boolean,
    shape: Shape = AppShapes.card,
    elevation: Dp = 2.dp,
    containerColor: Color? = null,
    borderWidth: Dp = 1.dp,
    hazeState: HazeState? = null,
    blurRadius: Dp = 0.dp,
    noiseFactor: Float = 0f
): Modifier = md3Surface(
    shape = shape,
    elevation = elevation,
    containerColor = containerColor,
    borderWidth = borderWidth
)

/**
 * Shared M3 surface adapter for panels which still use modifier-based styling.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.md3Surface(
    shape: Shape = AppShapes.card,
    elevation: Dp = 2.dp,
    containerColor: Color? = null,
    borderWidth: Dp = 1.dp
): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val resolvedContainer = containerColor ?: scheme.surfaceContainerHigh
    val resolvedBorder =
        if (resolvedContainer == scheme.primary) scheme.primary else scheme.outlineVariant
    this
        .shadow(
            elevation = elevation.coerceIn(0.dp, 6.dp),
            shape = shape,
            spotColor = scheme.scrim.copy(alpha = 0.20f),
            ambientColor = Color.Transparent
        )
        .clip(shape)
        .background(resolvedContainer, shape)
        .border(borderWidth, resolvedBorder, shape)
}

fun Modifier.iosCardSurface(
    shape: Shape = AppShapes.card
): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    this
        .clip(shape)
        .background(scheme.surfaceContainerLow, shape)
        .border(BorderStroke(1.dp, scheme.outlineVariant), shape)
}

/** Solid M3 page background used by scrollable screens. */
@Composable
fun FrostedAmbientBackground(
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    )
}
