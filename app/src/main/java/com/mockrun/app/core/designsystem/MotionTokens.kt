package com.mockrun.app.core.designsystem

import android.animation.ValueAnimator
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

object MotionTokens {
    val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOutStrong = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    val EaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    val PressSpringSpec = spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 560f
    )
    val SnappySpringSpec = spring<Float>(
        dampingRatio = 1f,
        stiffness = 520f
    )
    val FluidSpringSpec = spring<Float>(
        dampingRatio = 0.84f,
        stiffness = 360f
    )
    val GlassPressSpringSpec = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = 440f
    )
    val DockJellySpringSpec = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = 350f
    )
    val DockTouchDownSpringSpec = spring<Float>(
        dampingRatio = 0.90f,
        stiffness = 1200f,
        visibilityThreshold = 0.001f
    )
    val DockSnapSpringSpec = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 400f,
        visibilityThreshold = 0.001f
    )
}

/**
 * Non-consuming press feedback for glass surfaces. It provides the small
 * compress-and-overshoot response without taking taps away from the child's
 * own click/selectable handler.
 */
fun Modifier.jellyPressFeedback(
    enabled: Boolean = true,
    pressedScaleX: Float = 0.986f,
    pressedScaleY: Float = 0.976f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val reduceMotion = !ValueAnimator.areAnimatorsEnabled()
    val scaleX by animateFloatAsState(
        targetValue = if (enabled && isPressed && !reduceMotion) pressedScaleX else 1f,
        animationSpec = MotionTokens.GlassPressSpringSpec,
        label = "glassPressScaleX"
    )
    val scaleY by animateFloatAsState(
        targetValue = if (enabled && isPressed && !reduceMotion) pressedScaleY else 1f,
        animationSpec = MotionTokens.GlassPressSpringSpec,
        label = "glassPressScaleY"
    )

    graphicsLayer {
        this.scaleX = scaleX
        this.scaleY = scaleY
    }.pointerInput(enabled, pressedScaleX, pressedScaleY) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            isPressed = true
            try {
                var pointerPressed = true
                while (pointerPressed) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    pointerPressed = event.changes.any { change -> change.pressed }
                }
            } finally {
                isPressed = false
            }
        }
    }
}

/** Ripple plus a short spring-backed press response. */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.985f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && ValueAnimator.areAnimatorsEnabled()) {
            pressedScale
        } else {
            1f
        },
        animationSpec = MotionTokens.PressSpringSpec,
        label = "appPressScale"
    )

    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClick = onClick
    )
}

fun Modifier.bouncyScale(
    isPressed: Boolean,
    pressedScale: Float = 0.985f
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (isPressed && ValueAnimator.areAnimatorsEnabled()) pressedScale else 1f,
        animationSpec = MotionTokens.PressSpringSpec,
        label = "bouncyScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
