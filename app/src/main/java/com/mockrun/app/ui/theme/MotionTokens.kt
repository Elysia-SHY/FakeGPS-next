package com.mockrun.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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

/**
 * Emil Kowalski Core Principle: "Buttons must feel responsive: transform scale(0.97) on :active"
 * Responds instantaneously on pointer-down with subtle spring decompression upon release.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1.0f,
        animationSpec = MotionTokens.BouncySpringSpec,
        label = "bouncyClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Disable default ripple for authentic, crisp iOS tactile response
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
