package com.mockrun.app.core.designsystem

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.min

/**
 * Backdrop exported by the page content. The value is null in solid mode so
 * no page content is captured and no graphics layer is recorded.
 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

val LocalLiquidGlassEnabled = compositionLocalOf { false }

object GlassCapabilities {
    val supportsBackdropBlur: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val supportsRefraction: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

object LiquidGlassDefaults {
    const val PREFS_NAME = AppAppearancePreferences.PREFS_NAME
    const val KEY_LIQUID_GLASS_ENABLED = AppAppearancePreferences.KEY_LIQUID_GLASS_ENABLED

    val BlurRadius = 14.dp
    val RefractionHeight = 24.dp
    val RefractionAmount = 30.dp
    const val BackdropSamplingScale = 0.5f

    fun isEnabled(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GlassCapabilities.supportsBackdropBlur &&
            preferences.getBoolean(KEY_LIQUID_GLASS_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        AppAppearancePreferences.setLiquidGlassEnabled(context, enabled)
    }
}

/**
 * Atlas material with two real backends.
 *
 * Liquid Glass uses AndroidLiquidGlass/Backdrop: a sibling content layer is
 * captured into a GraphicsLayer, then vibrancy, Gaussian blur, AGSL refraction,
 * specular highlights and a masked shadow are applied by RenderEffect. The
 * tint is deliberately thin; readability continues to come from the content
 * color, not from a heavy translucent plate.
 *
 * Solid mode keeps the same geometry and spring motion but uses an opaque
 * surface. It performs no capture, blur or shader work.
 */
fun Modifier.liquidGlass(
    isLiquidGlass: Boolean,
    shape: Shape = AppShapes.card,
    elevation: Dp = 6.dp,
    containerColor: Color? = null,
    borderWidth: Dp = 1.dp,
    backdrop: Backdrop? = null,
    blurRadius: Dp = LiquidGlassDefaults.BlurRadius,
    refractionHeight: Dp = LiquidGlassDefaults.RefractionHeight,
    refractionAmount: Dp = LiquidGlassDefaults.RefractionAmount,
    tintAlpha: Float? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null
): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalIsDarkTheme.current
    val resolvedBackdrop = backdrop ?: LocalGlassBackdrop.current
    val resolvedContainer = containerColor ?: scheme.surfaceContainerHigh
    val useGlass = isLiquidGlass &&
        GlassCapabilities.supportsBackdropBlur &&
        resolvedBackdrop != null

    if (!useGlass) {
        return@composed this
            .shadow(
                elevation = elevation.coerceIn(0.dp, 8.dp),
                shape = shape,
                spotColor = scheme.scrim.copy(alpha = 0.18f),
                ambientColor = Color.Transparent
            )
            .clip(shape)
            .background(resolvedContainer, shape)
            .border(
                borderWidth,
                scheme.outlineVariant.copy(alpha = 0.68f),
                shape
            )
            .jellyPressFeedback()
    }

    val effectiveBlur = blurRadius.coerceAtLeast(1.dp)
    val effectiveTintAlpha = tintAlpha ?: if (dark) 0.32f else 0.38f
    val effects: BackdropEffectScope.() -> Unit = remember(
        effectiveBlur,
        refractionHeight,
        refractionAmount,
        shape
    ) {
        {
            vibrancy()
            blur(effectiveBlur.toPx())
            if (shape is CornerBasedShape && size != Size.Unspecified) {
                val minDimension = size.minDimension
                lens(
                    refractionHeight = min(
                        refractionHeight.toPx(),
                        minDimension * 0.34f
                    ),
                    refractionAmount = min(
                        refractionAmount.toPx(),
                        minDimension * 0.42f
                    ),
                    depthEffect = true
                )
            }
        }
    }
    val highlight: () -> Highlight = remember(dark) {
        {
            Highlight.Default.copy(
                width = if (dark) 0.58.dp else 0.66.dp,
                blurRadius = 0.32.dp,
                alpha = if (dark) 0.68f else 0.82f
            )
        }
    }
    val shadow: () -> Shadow = remember(dark) {
        {
            Shadow(
                radius = if (dark) 14.dp else 12.dp,
                color = Color.Black.copy(alpha = if (dark) 0.30f else 0.13f)
            )
        }
    }
    val innerShadow: () -> InnerShadow = remember(dark) {
        {
            InnerShadow(
                radius = 1.dp,
                offset = androidx.compose.ui.unit.DpOffset(0.dp, 0.5.dp),
                color = Color.Black.copy(alpha = if (dark) 0.28f else 0.12f),
                alpha = 0.28f
            )
        }
    }
    val surfaceTint = resolvedContainer.copy(alpha = effectiveTintAlpha)
    val opticalAccent = scheme.primary.copy(alpha = if (dark) 0.035f else 0.025f)

    this
        .drawBackdrop(
            backdrop = resolvedBackdrop,
            shape = { shape },
            effects = effects,
            samplingScale = LiquidGlassDefaults.BackdropSamplingScale,
            highlight = highlight,
            shadow = shadow,
            innerShadow = innerShadow,
            layerBlock = layerBlock,
            onDrawSurface = {
                drawRect(surfaceTint)
                drawRect(opticalAccent)
            }
        )
        .border(
            width = borderWidth.coerceAtMost(0.66.dp),
            color = Color.White.copy(alpha = if (dark) 0.14f else 0.24f),
            shape = shape
        )
        .jellyPressFeedback()
}

fun Modifier.md3Surface(
    shape: Shape = AppShapes.card,
    elevation: Dp = 2.dp,
    containerColor: Color? = null,
    borderWidth: Dp = 1.dp
): Modifier = composed {
    liquidGlass(
        isLiquidGlass = false,
        shape = shape,
        elevation = elevation,
        containerColor = containerColor,
        borderWidth = borderWidth
    )
}

fun Modifier.iosCardSurface(
    shape: Shape = AppShapes.card
): Modifier = composed {
    liquidGlass(
        isLiquidGlass = LocalLiquidGlassEnabled.current,
        shape = shape,
        elevation = 8.dp
    )
}
