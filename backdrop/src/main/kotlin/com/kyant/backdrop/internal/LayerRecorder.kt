package com.kyant.backdrop.internal

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize

internal fun DrawScope.recordLayer(
    layer: GraphicsLayer,
    size: IntSize = this.size.toIntSize(),
    density: Density = this,
    block: DrawScope.() -> Unit
) {
    layer.record(size) {
        val prevDensity = drawContext.density
        drawContext.density = density
        try {
            this.block()
        } finally {
            drawContext.density = prevDensity
        }
    }
}
