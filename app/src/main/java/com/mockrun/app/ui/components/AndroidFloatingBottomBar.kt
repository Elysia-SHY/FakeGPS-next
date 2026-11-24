package com.mockrun.app.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.LocalGlassBackdrop
import com.mockrun.app.core.designsystem.LocalIsDarkTheme
import com.mockrun.app.core.designsystem.LocalLiquidGlassEnabled
import com.mockrun.app.core.designsystem.LiquidGlassDefaults
import com.mockrun.app.core.designsystem.MotionTokens
import com.mockrun.app.core.designsystem.liquidGlass
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

data class FloatingTabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

@Composable
fun AndroidFloatingBottomBar(
    modifier: Modifier = Modifier,
    tabs: List<FloatingTabItem>,
    currentPosition: Float,
    onTabSelected: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalIsDarkTheme.current
    val tabCount = tabs.size.coerceAtLeast(1)
    val liquidGlass = LocalLiquidGlassEnabled.current
    val backdrop = LocalGlassBackdrop.current
    val latestOnTabSelected by rememberUpdatedState(onTabSelected)
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var gestureOwnsIndicator by remember { mutableStateOf(false) }
    var touchPosition by remember { mutableStateOf(Offset.Zero) }
    var lastDragDirection by remember { mutableFloatStateOf(0f) }
    val indicatorPosition = remember(tabCount) {
        Animatable(currentPosition.coerceIn(0f, (tabCount - 1).toFloat()))
    }
    val animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
    val pressProgress = animateFloatAsState(
        targetValue = if ((isPressed || isDragging) && animatorsEnabled) 1f else 0f,
        animationSpec = MotionTokens.DockJellySpringSpec,
        label = "dockJellyProgress"
    )
    val highlightProgress = animateFloatAsState(
        targetValue = if ((isPressed || isDragging) && animatorsEnabled) 1f else 0f,
        animationSpec = MotionTokens.DockJellySpringSpec,
        label = "dockHighlightProgress"
    )
    val dragProgress = animateFloatAsState(
        targetValue = if (isDragging && animatorsEnabled) 1f else 0f,
        animationSpec = MotionTokens.DockJellySpringSpec,
        label = "dockDragStretch"
    )

    LaunchedEffect(currentPosition, gestureOwnsIndicator) {
        if (!gestureOwnsIndicator) {
            val target = currentPosition.coerceIn(0f, (tabCount - 1).toFloat())
            if (abs(indicatorPosition.value - target) > 0.001f) {
                if (animatorsEnabled) {
                    indicatorPosition.animateTo(target, MotionTokens.DockSnapSpringSpec)
                } else {
                    indicatorPosition.snapTo(target)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .height(72.dp)
    ) {
        // Keep the chrome and the floating lens as siblings. Applying
        // liquidGlass to the outer Box clips the scaled lens to the dock.
        Box(
            modifier = Modifier
                .fillMaxSize()
            .liquidGlass(
                isLiquidGlass = liquidGlass,
                shape = AppShapes.bar,
                elevation = if (liquidGlass) 20.dp else 4.dp,
                containerColor = if (liquidGlass) {
                    scheme.surface
                } else {
                    scheme.surfaceContainerLowest
                },
                backdrop = backdrop,
                borderWidth = 0.75.dp,
                tintAlpha = if (isDark) 0.30f else 0.34f,
                layerBlock = {
                    scaleX = 1f + 0.010f * pressProgress.value
                    scaleY = 1f - 0.022f * pressProgress.value
                }
            )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 3.dp)
                .drawWithContent {
                    val progress = highlightProgress.value
                    if (progress > 0.01f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f * progress),
                                    Color.White.copy(alpha = 0.035f * progress),
                                    Color.Transparent
                                ),
                                center = touchPosition,
                                radius = size.minDimension * 1.35f
                            ),
                            radius = size.minDimension * 1.35f,
                            center = touchPosition,
                            blendMode = BlendMode.Plus
                        )
                    }
                    drawContent()
                }
                .pointerInput(tabCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        val tabWidthPx = (size.width.toFloat() / tabCount).coerceAtLeast(1f)
                        val maxIndex = (tabCount - 1).toFloat()
                        val pointerPosition = indicatorPositionForTouch(
                            xPx = down.position.x,
                            tabWidthPx = tabWidthPx,
                            tabCount = tabCount
                        )
                        touchPosition = down.position
                        isPressed = true
                        gestureOwnsIndicator = true
                        lastDragDirection = 0f
                        down.consume()
                        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            if (animatorsEnabled) {
                                indicatorPosition.animateTo(
                                    pointerPosition,
                                    MotionTokens.DockTouchDownSpringSpec
                                )
                            } else {
                                indicatorPosition.snapTo(pointerPosition)
                            }
                        }

                        var previousX = down.position.x
                        var dragging = false
                        var pointerPressed = true
                        var dragPosition = pointerPosition
                        var hapticTargetIndex =
                            targetIndexForIndicatorPosition(pointerPosition, tabCount)

                        fun settleIndicator(targetIndex: Int) {
                            animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                try {
                                    val target = targetIndex.toFloat().coerceIn(0f, maxIndex)
                                    if (animatorsEnabled) {
                                        indicatorPosition.animateTo(
                                            target,
                                            MotionTokens.DockSnapSpringSpec
                                        )
                                    } else {
                                        indicatorPosition.snapTo(target)
                                    }
                                } finally {
                                    if (!isPressed && !isDragging) {
                                        gestureOwnsIndicator = false
                                    }
                                }
                            }
                        }

                        try {
                            while (pointerPressed) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val deltaX = change.position.x - previousX
                                previousX = change.position.x
                                pointerPressed = change.pressed
                                touchPosition = change.position
                                val nextDragPosition = indicatorPositionForTouch(
                                    xPx = change.position.x,
                                    tabWidthPx = tabWidthPx,
                                    tabCount = tabCount
                                )
                                lastDragDirection = (nextDragPosition - dragPosition).sign
                                dragPosition = nextDragPosition

                                if (!dragging && abs(deltaX) > viewConfiguration.touchSlop) {
                                    dragging = true
                                    isDragging = true
                                }

                                animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    indicatorPosition.snapTo(dragPosition)
                                }
                                event.changes.forEach { it.consume() }

                                val nextHapticIndex =
                                    targetIndexForIndicatorPosition(dragPosition, tabCount)
                                if (nextHapticIndex != hapticTargetIndex) {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove
                                    )
                                    hapticTargetIndex = nextHapticIndex
                                }
                            }

                            val target = targetIndexForIndicatorPosition(dragPosition, tabCount)
                            latestOnTabSelected(target)
                            settleIndicator(target)
                        } finally {
                            isPressed = false
                            isDragging = false
                        }
                    }
                }
        ) {
            val tabWidth = maxWidth / tabCount
            val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }
            val selectedIndex = currentPosition
                .roundToInt()
                .coerceIn(0, tabCount - 1)
            val indicatorMotion: GraphicsLayerScope.() -> Unit = {
                translationX = tabWidthPx * indicatorPosition.value.coerceIn(
                    0f,
                    (tabCount - 1).toFloat()
                )
                scaleX = 1f +
                    0.20f * pressProgress.value +
                    0.04f * dragProgress.value * abs(lastDragDirection)
                scaleY = 1f +
                    0.38f * pressProgress.value -
                    0.055f * dragProgress.value
            }
            val indicatorModifier = Modifier
                .width(tabWidth)
                .fillMaxHeight()
                .padding(horizontal = 5.dp, vertical = 3.dp)
                .then(
                    if (liquidGlass && backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { AppShapes.pill },
                            samplingScale = LiquidGlassDefaults.BackdropSamplingScale,
                            effects = {
                                val press = pressProgress.value
                                vibrancy()
                                blur(3.dp.toPx())
                                lens(
                                    refractionHeight = (12.dp + 10.dp * press).toPx(),
                                    refractionAmount = (18.dp + 12.dp * press).toPx(),
                                    depthEffect = true,
                                    chromaticAberration = isDragging
                                )
                            },
                            highlight = {
                                val press = pressProgress.value
                                Highlight.Default.copy(
                                    width = 0.7.dp,
                                    blurRadius = 0.35.dp,
                                    alpha = 0.58f + 0.40f * press
                                )
                            },
                            shadow = {
                                val press = pressProgress.value
                                Shadow(
                                    radius = 7.dp + 4.dp * press,
                                    color = Color.Black.copy(alpha = if (isDark) 0.25f else 0.10f)
                                )
                            },
                            innerShadow = {
                                val press = pressProgress.value
                                InnerShadow(
                                    radius = 2.dp + 1.2.dp * press,
                                    color = Color.Black.copy(alpha = if (isDark) 0.24f else 0.10f),
                                    alpha = 0.16f + 0.30f * press
                                )
                            },
                            layerBlock = indicatorMotion,
                            onDrawSurface = {
                                val press = pressProgress.value
                                drawRect(
                                    scheme.primary.copy(
                                        alpha = if (isDark) {
                                            0.15f + 0.07f * press
                                        } else {
                                            0.095f + 0.055f * press
                                        }
                                    )
                                )
                                drawRect(
                                    Color.White.copy(
                                        alpha = 0.035f + 0.045f * press
                                    )
                                )
                            }
                        )
                    } else {
                        Modifier
                            .graphicsLayer(indicatorMotion)
                            .clip(AppShapes.pill)
                            .background(scheme.primaryContainer)
                    }
                )

            Box(modifier = indicatorModifier)

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val contentColor = lerp(
                        scheme.onSurfaceVariant,
                        if (liquidGlass) scheme.primary else scheme.onPrimaryContainer,
                        if (selected) 1f else 0f
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                role = Role.Tab
                                this.selected = selected
                                onClick(label = "切换到${tab.title}") {
                                    onTabSelected(index)
                                    true
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier
                                .size(23.dp)
                                .graphicsLayer {
                                    val iconScale = 1f + 0.085f * pressProgress.value
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

internal fun indicatorPositionForTouch(
    xPx: Float,
    tabWidthPx: Float,
    tabCount: Int
): Float {
    if (tabCount <= 1 || tabWidthPx <= 0f) return 0f
    val safeTabWidth = tabWidthPx.coerceAtLeast(1f)
    val maxPosition = (tabCount - 1).toFloat()
    return (xPx / safeTabWidth - 0.5f).coerceIn(0f, maxPosition)
}

internal fun targetIndexForIndicatorPosition(
    indicatorPosition: Float,
    tabCount: Int
): Int {
    if (tabCount <= 1) return 0
    return indicatorPosition
        .roundToInt()
        .coerceIn(0, tabCount - 1)
}

@Composable
fun AndroidFloatingBottomBar(
    modifier: Modifier = Modifier,
    tabs: List<FloatingTabItem>,
    currentRoute: String?,
    onTabSelected: (String) -> Unit
) {
    val selectedIndex = remember(currentRoute, tabs) {
        tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    }

    AndroidFloatingBottomBar(
        modifier = modifier,
        tabs = tabs,
        currentPosition = selectedIndex.toFloat(),
        onTabSelected = { targetIndex ->
            onTabSelected(tabs[targetIndex].route)
        }
    )
}
