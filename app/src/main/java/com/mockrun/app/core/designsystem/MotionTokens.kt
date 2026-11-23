package com.mockrun.app.core.designsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Emil Kowalski Design Engineering & Apple Motion Tokens
 * Reference: https://github.com/emilkowalski/skills
 */
object MotionTokens {
    // 1. Emil Kowalski Sanctioned Custom Cubic Bezier Easings
    val EaseOutStrong = CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)
    val EaseInOutStrong = CubicBezierEasing(0.77f, 0.0f, 0.175f, 1.0f)
    val EaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)

    // 2. Apple Physics Spring Configurations
    val BouncySpringSpec = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = 450f
    )

    val SnappySpringSpec = spring<Float>(
        dampingRatio = 1.0f, // Critically damped (no overshoot, settles immediately)
        stiffness = 500f
    )

    val FluidSpringSpec = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 380f
    )
}

/** Compatibility click modifier backed by the Material 3 ripple and state layer. */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current

    clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * Scale down modifier tied to an external pressed/dragged state (e.g. for custom sliders or gesture handles)
 */
fun Modifier.bouncyScale(
    isPressed: Boolean,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1.0f,
        animationSpec = MotionTokens.BouncySpringSpec,
        label = "bouncyScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
