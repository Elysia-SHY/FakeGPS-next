package com.mockrun.app.core.designsystem

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A continuous rounded rectangle: straight edges flow into cubic corner
 * segments instead of meeting a circular arc abruptly. It still derives from
 * CornerBasedShape so Backdrop's AGSL lens can resolve the four corner radii.
 */
data class SmoothRoundedCornerShape(
    private val corner: Dp
) : CornerBasedShape(
    topStart = CornerSize(corner),
    topEnd = CornerSize(corner),
    bottomEnd = CornerSize(corner),
    bottomStart = CornerSize(corner)
) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        val maxRadius = size.minDimension / 2f
        val radius = maxOf(topStart, topEnd, bottomEnd, bottomStart)
            .coerceIn(0f, maxRadius)
        val width = size.width
        val height = size.height
        val handle = radius * 0.45f
        val path = Path().apply {
            moveTo(radius, 0f)
            lineTo(width - radius, 0f)
            cubicTo(width - handle, 0f, width, handle, width, radius)
            lineTo(width, height - radius)
            cubicTo(width, height - handle, width - handle, height, width - radius, height)
            lineTo(radius, height)
            cubicTo(handle, height, 0f, height - handle, 0f, height - radius)
            lineTo(0f, radius)
            cubicTo(0f, handle, handle, 0f, radius, 0f)
            close()
        }
        return Outline.Generic(path)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ): CornerBasedShape = SmoothRoundedCornerShape(corner)
}

fun smoothShape(radius: Dp) = SmoothRoundedCornerShape(radius)

val SmoothPillShape = SmoothRoundedCornerShape(999.dp)
