package com.mockrun.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Gives a page a real, sibling background source for its glass cards. The
 * source contains only the atmosphere/wallpaper; cards and scrolling content
 * consume it through LocalGlassBackdrop.
 */
@Composable
fun AtlasGlassPage(
    modifier: Modifier = Modifier,
    sharedBackdrop: LayerBackdrop? = null,
    background: @Composable BoxScope.() -> Unit = { AppBackground() },
    content: @Composable BoxScope.() -> Unit
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val backdrop = sharedBackdrop ?: if (isLiquidGlass) rememberLayerBackdrop() else null

    Box(modifier = modifier.fillMaxSize()) {
        if (sharedBackdrop == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (backdrop != null) {
                            Modifier.layerBackdrop(backdrop)
                        } else {
                            Modifier
                        }
                    )
            ) {
                background()
            }
        }

        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = content
            )
        }
    }
}
