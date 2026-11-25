package com.mockrun.app.feature.map

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.LocalLiquidGlassEnabled
import com.mockrun.app.core.designsystem.ScreenInsets
import com.mockrun.app.core.designsystem.liquidGlass
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

enum class MapDrawerStage {
    COLLAPSED,
    HALF_EXPANDED,
    EXPANDED
}

/**
 * Actual, unconstrained content heights measured for each stage. The shared handle is added by
 * [calculateThreeStageDrawerAnchors], so callers never need to estimate card paddings.
 */
data class DrawerContentHeights(
    val collapsed: Dp,
    val halfExpanded: Dp,
    val expanded: Dp
)

/**
 * Full drawer heights measured from the screen bottom. Subtracting [bottomReservation] gives the
 * visible Liquid Glass surface height. This keeps collision math and Dock clearance in one unit.
 */
data class ThreeStageDrawerAnchors(
    val collapsed: Dp,
    val halfExpanded: Dp,
    val expanded: Dp,
    val bottomReservation: Dp
) {
    fun heightFor(stage: MapDrawerStage): Dp = when (stage) {
        MapDrawerStage.COLLAPSED -> collapsed
        MapDrawerStage.HALF_EXPANDED -> halfExpanded
        MapDrawerStage.EXPANDED -> expanded
    }

    fun surfaceHeightFor(stage: MapDrawerStage): Dp = heightFor(stage) - bottomReservation
}

private val DrawerHorizontalMargin = 12.dp
private val DrawerTopGap = 24.dp
private val DrawerMinimumAnchorGap = 32.dp
private val DrawerHandleHeight = 32.dp
private val DrawerMaxWidth = 560.dp
private val DrawerDragPositionalThreshold = 0.48f
private val DrawerDragVelocityThreshold = 720.dp
private val DrawerRubberBandMaxStretch = 20.dp
private val DrawerDefaultVelocityThresholdPx = 1_600f
private val DrawerSnapSpringSpec = spring<Float>(
    dampingRatio = 0.68f,
    stiffness = 320f
)

/**
 * Produces three ordered, Dock-safe anchors without forcing the expanded sheet to fill the screen.
 *
 * The expanded anchor is the natural measured content height, capped by:
 * `viewport - topChrome - 24dp`. The half anchor may additionally be capped to preserve the
 * route map-selection viewport. If content is taller than either cap, the shared drawer exposes
 * vertical scrolling inside that stage instead of adding blank space below the last control.
 */
internal fun calculateThreeStageDrawerAnchors(
    viewportHeight: Dp,
    topChromeHeight: Dp,
    bottomReservation: Dp,
    contentHeights: DrawerContentHeights,
    minMapVisibilityFraction: Float = 0f
): ThreeStageDrawerAnchors {
    val safeViewport = viewportHeight.coerceAtLeast(1.dp)
    val safeTopChrome = topChromeHeight.coerceIn(0.dp, safeViewport - 1.dp)
    val safeBottom = bottomReservation.coerceIn(1.dp, safeViewport - 1.dp)

    // Dock top minus an 8dp breathing gap is represented directly by ScreenInsets.bottomBarHeight.
    val maxAnchor = (safeViewport - safeTopChrome - DrawerTopGap)
        .coerceAtLeast(safeBottom + 1.dp)
    val maxSurface = (maxAnchor - safeBottom).coerceAtLeast(1.dp)

    val naturalCollapsed = DrawerHandleHeight + contentHeights.collapsed
    val naturalHalf = DrawerHandleHeight + contentHeights.halfExpanded
    val naturalExpanded = DrawerHandleHeight + contentHeights.expanded

    val expandedSurface = minOf(naturalExpanded, maxSurface).coerceAtLeast(1.dp)
    val availableGap = (expandedSurface / 3f).coerceAtLeast(1.dp)
    val gap = minOf(DrawerMinimumAnchorGap, availableGap)

    val collapsedSurface = naturalCollapsed
        .coerceAtMost((expandedSurface - gap * 2f).coerceAtLeast(1.dp))
        .coerceAtLeast(1.dp)

    val naturalHalfSurface = naturalHalf
        .coerceAtMost((expandedSurface - gap).coerceAtLeast(collapsedSurface + 1.dp))
    var halfSurface = naturalHalfSurface
        .coerceAtLeast((collapsedSurface + gap).coerceAtMost(expandedSurface - 1.dp))

    if (minMapVisibilityFraction > 0f) {
        // The map viewport starts below top chrome. Anchor includes the Dock reservation.
        val maxHalfAnchor = safeViewport * (1f - minMapVisibilityFraction.coerceIn(0f, 0.9f)) -
            safeTopChrome
        val maxHalfSurface = (maxHalfAnchor - safeBottom)
            .coerceAtLeast(collapsedSurface + 1.dp)
            .coerceAtMost(expandedSurface - 1.dp)
        if (maxHalfSurface > collapsedSurface) {
            halfSurface = halfSurface.coerceAtMost(maxHalfSurface)
        }
    }

    halfSurface = halfSurface.coerceIn(
        (collapsedSurface + 1.dp).coerceAtMost(expandedSurface - 1.dp),
        (expandedSurface - 1.dp).coerceAtLeast(collapsedSurface + 1.dp)
    )

    return ThreeStageDrawerAnchors(
        collapsed = safeBottom + collapsedSurface,
        halfExpanded = safeBottom + halfSurface,
        expanded = safeBottom + expandedSurface,
        bottomReservation = safeBottom
    )
}

/**
 * Chooses a release target using only the current stage and its neighbours. A moderate drag or
 * ordinary flick moves exactly one step. No single gesture can skip the middle anchor.
 */
internal fun resolveDrawerStageAfterDrag(
    currentStage: MapDrawerStage,
    startHeight: Float,
    releasedHeight: Float,
    velocityY: Float,
    anchors: ThreeStageDrawerAnchors,
    positionalThreshold: Float = DrawerDragPositionalThreshold,
    velocityThreshold: Float = DrawerDefaultVelocityThresholdPx
): MapDrawerStage {
    val delta = releasedHeight - startHeight
    if (abs(delta) < 0.5f) return currentStage

    val ordered = listOf(
        MapDrawerStage.COLLAPSED,
        MapDrawerStage.HALF_EXPANDED,
        MapDrawerStage.EXPANDED
    )
    val currentIndex = ordered.indexOf(currentStage)
    val movingUp = delta > 0f
    val adjacentIndex = (currentIndex + if (movingUp) 1 else -1).coerceIn(ordered.indices)
    val adjacentStage = ordered[adjacentIndex]
    val adjacentHeight = anchors.heightFor(adjacentStage).value
    val travelled = abs(releasedHeight - startHeight)
    val distanceToAdjacent = abs(adjacentHeight - startHeight)
    val crossedThreshold = distanceToAdjacent > 0f &&
        travelled >= distanceToAdjacent * positionalThreshold
    val matchingFlick = if (movingUp) {
        velocityY <= -velocityThreshold
    } else {
        velocityY >= velocityThreshold
    }

    return if (crossedThreshold || matchingFlick) adjacentStage else currentStage
}

/**
 * Maps raw drag distance beyond an anchor into an asymptotic elastic range. The sheet still reacts
 * immediately, but additional movement yields progressively less height before springing back.
 */
internal fun applyDrawerRubberBand(
    rawHeight: Float,
    minimum: Float,
    maximum: Float,
    maxStretch: Float
): Float {
    if (rawHeight in minimum..maximum) return rawHeight
    val safeStretch = maxStretch.coerceAtLeast(1f)
    return if (rawHeight < minimum) {
        val overshoot = minimum - rawHeight
        minimum - safeStretch * (1f - exp(-overshoot / safeStretch))
    } else {
        val overshoot = rawHeight - maximum
        maximum + safeStretch * (1f - exp(-overshoot / safeStretch))
    }
}

/**
 * Shared three-stage Liquid Glass drawer.
 *
 * A zero-height probe measures each stage at the final sheet width. Those measurements drive the
 * magnetic anchors and are refreshed whenever search results, lists, or route state change.
 */
@Composable
fun ThreeStageMapDrawer(
    stage: MapDrawerStage,
    onStageChange: (MapDrawerStage) -> Unit,
    topChromeHeight: Dp,
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = DrawerHorizontalMargin,
    minMapVisibilityFraction: Float = 0f,
    onHeightChanged: (Int) -> Unit = {},
    content: @Composable ColumnScope.(MapDrawerStage) -> Unit
) {
    val bottomReservation = ScreenInsets.bottomBarHeight
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val animationScope = rememberCoroutineScope()
    val latestOnHeightChanged by rememberUpdatedState(onHeightChanged)
    val latestOnStageChange by rememberUpdatedState(onStageChange)
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val dragVelocityThresholdPx = with(density) { DrawerDragVelocityThreshold.toPx() }
    val rubberBandStretchDp = DrawerRubberBandMaxStretch.value

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val sheetWidth = (maxWidth - horizontalMargin * 2f)
            .coerceAtMost(DrawerMaxWidth)
            .coerceAtLeast(1.dp)
        val maxAnchor = (maxHeight - topChromeHeight - DrawerTopGap)
            .coerceAtLeast(bottomReservation + DrawerHandleHeight + 1.dp)
        val maxSurface = (maxAnchor - bottomReservation)
            .coerceAtLeast(DrawerHandleHeight + 1.dp)
        val maxContentHeight = (maxSurface - DrawerHandleHeight)
            .coerceAtLeast(1.dp)

        var measuredHeights by remember { mutableStateOf<DrawerContentHeights?>(null) }
        val anchors = measuredHeights?.let {
            calculateThreeStageDrawerAnchors(
                viewportHeight = maxHeight,
                topChromeHeight = topChromeHeight,
                bottomReservation = bottomReservation,
                contentHeights = it,
                minMapVisibilityFraction = minMapVisibilityFraction
            )
        }

        DrawerStageHeightProbe(
            width = sheetWidth,
            maxContentHeight = maxContentHeight,
            content = content,
            onMeasured = { next ->
                if (next != measuredHeights) measuredHeights = next
            }
        )

        val drawerHeight = remember { Animatable(0f) }
        var hasInitialAnchor by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        var isAnimatingRelease by remember { mutableStateOf(false) }
        var dragHeight by remember { mutableStateOf<Float?>(null) }
        var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val renderedHeight = dragHeight ?: drawerHeight.value
        val renderedSurfaceHeight = (renderedHeight - bottomReservation.value)
            .coerceAtLeast(DrawerHandleHeight.value)
        val visibleStage = when {
            anchors == null -> stage
            renderedHeight >= (anchors.halfExpanded.value + anchors.expanded.value) / 2f ->
                MapDrawerStage.EXPANDED
            renderedHeight >= (anchors.collapsed.value + anchors.halfExpanded.value) / 2f ->
                MapDrawerStage.HALF_EXPANDED
            else -> MapDrawerStage.COLLAPSED
        }
        val scrollState = rememberScrollState()

        LaunchedEffect(visibleStage) {
            scrollState.scrollTo(0)
        }

        LaunchedEffect(stage, anchors, measuredHeights) {
            val targetAnchors = anchors ?: return@LaunchedEffect
            if (isDragging || isAnimatingRelease) return@LaunchedEffect
            val target = targetAnchors.heightFor(stage).value
            if (!hasInitialAnchor) {
                drawerHeight.snapTo(target)
                hasInitialAnchor = true
            } else if (animationsEnabled) {
                drawerHeight.animateTo(
                    targetValue = target,
                    animationSpec = DrawerSnapSpringSpec
                )
            } else {
                drawerHeight.snapTo(target)
            }
        }

        fun cycleStage() {
            val next = when (stage) {
                MapDrawerStage.COLLAPSED -> MapDrawerStage.HALF_EXPANDED
                MapDrawerStage.HALF_EXPANDED -> MapDrawerStage.EXPANDED
                MapDrawerStage.EXPANDED -> MapDrawerStage.HALF_EXPANDED
            }
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            latestOnStageChange(next)
        }

        fun settleDrawer(
            targetStage: MapDrawerStage,
            releasedHeight: Float,
            currentAnchors: ThreeStageDrawerAnchors
        ) {
            val targetHeight = currentAnchors.heightFor(targetStage).value
            isDragging = false
            isAnimatingRelease = true
            if (targetStage != stage) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                latestOnStageChange(targetStage)
            }
            animationScope.launch {
                drawerHeight.snapTo(releasedHeight)
                dragHeight = null
                if (animationsEnabled) {
                    drawerHeight.animateTo(
                        targetValue = targetHeight,
                        animationSpec = DrawerSnapSpringSpec
                    )
                } else {
                    drawerHeight.snapTo(targetHeight)
                }
                isAnimatingRelease = false
            }
        }

        var nestedGestureStartHeight by remember { mutableStateOf<Float?>(null) }
        var nestedSettleJob by remember { mutableStateOf<Job?>(null) }
        val nestedScrollConnection = remember(
            anchors,
            stage,
            dragVelocityThresholdPx,
            rubberBandStretchDp
        ) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    val currentAnchors = anchors ?: return Offset.Zero
                    if (
                        source != NestedScrollSource.UserInput ||
                        available.y == 0f ||
                        isAnimatingRelease
                    ) {
                        return Offset.Zero
                    }

                    if (nestedGestureStartHeight == null) {
                        nestedGestureStartHeight = drawerHeight.value
                    }
                    isDragging = true
                    val currentHeight = dragHeight ?: drawerHeight.value
                    dragHeight = applyDrawerRubberBand(
                        rawHeight = currentHeight - available.y / density.density,
                        minimum = currentAnchors.collapsed.value,
                        maximum = currentAnchors.expanded.value,
                        maxStretch = rubberBandStretchDp
                    )
                    nestedSettleJob?.cancel()
                    nestedSettleJob = animationScope.launch {
                        delay(120)
                        val startHeight = nestedGestureStartHeight ?: return@launch
                        val releasedHeight = dragHeight ?: return@launch
                        val target = resolveDrawerStageAfterDrag(
                            currentStage = stage,
                            startHeight = startHeight,
                            releasedHeight = releasedHeight,
                            velocityY = 0f,
                            anchors = currentAnchors,
                            velocityThreshold = dragVelocityThresholdPx
                        )
                        nestedGestureStartHeight = null
                        settleDrawer(target, releasedHeight, currentAnchors)
                    }
                    return available
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {
                    val currentAnchors = anchors ?: return Velocity.Zero
                    val startHeight = nestedGestureStartHeight ?: return Velocity.Zero
                    val releasedHeight = dragHeight ?: return Velocity.Zero
                    nestedSettleJob?.cancel()
                    nestedGestureStartHeight = null
                    val target = resolveDrawerStageAfterDrag(
                        currentStage = stage,
                        startHeight = startHeight,
                        releasedHeight = releasedHeight,
                        velocityY = available.y,
                        anchors = currentAnchors,
                        velocityThreshold = dragVelocityThresholdPx
                    )
                    settleDrawer(target, releasedHeight, currentAnchors)
                    return Velocity.Zero
                }
            }
        }

        val handleModifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { handleCoordinates = it }
            .pointerInput(anchors, stage) {
                val currentAnchors = anchors ?: return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    nestedSettleJob?.cancel()
                    nestedGestureStartHeight = null
                    val downRootY = handleCoordinates?.localToRoot(down.position)?.y ?: down.position.y
                    val startHeight = drawerHeight.value
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, Offset(0f, downRootY))
                    var lastRootY = downRootY
                    var moved = false
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!moved) {
                                cycleStage()
                                break
                            }

                            val releasedHeight = dragHeight ?: drawerHeight.value
                            val velocityY = runCatching {
                                velocityTracker.calculateVelocity().y
                            }.getOrDefault(0f)
                            val targetStage = resolveDrawerStageAfterDrag(
                                currentStage = stage,
                                startHeight = startHeight,
                                releasedHeight = releasedHeight,
                                velocityY = velocityY,
                                anchors = currentAnchors,
                                velocityThreshold = dragVelocityThresholdPx
                            )
                            settleDrawer(targetStage, releasedHeight, currentAnchors)
                            break
                        }

                        val rootY = handleCoordinates?.localToRoot(change.position)?.y
                            ?: change.position.y
                        velocityTracker.addPosition(change.uptimeMillis, Offset(0f, rootY))
                        val deltaY = rootY - lastRootY
                        lastRootY = rootY
                        if (abs(rootY - downRootY) >= viewConfiguration.touchSlop) {
                            moved = true
                        }
                        if (moved) {
                            isDragging = true
                            val currentHeight = dragHeight ?: drawerHeight.value
                            dragHeight = applyDrawerRubberBand(
                                rawHeight = currentHeight - deltaY / density.density,
                                minimum = currentAnchors.collapsed.value,
                                maximum = currentAnchors.expanded.value,
                                maxStretch = rubberBandStretchDp
                            )
                            change.consume()
                        }
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = when (stage) {
                    MapDrawerStage.COLLAPSED -> "展开面板"
                    MapDrawerStage.HALF_EXPANDED -> "展开更多内容"
                    MapDrawerStage.EXPANDED -> "收起面板"
                }
                onClick(label = "切换面板档位") {
                    cycleStage()
                    true
                }
            }

        Box(
            modifier = Modifier
                .widthIn(max = DrawerMaxWidth + horizontalMargin * 2f)
                .fillMaxWidth()
                .padding(horizontal = horizontalMargin)
                .height(renderedHeight.dp)
                .alpha(if (measuredHeights == null) 0f else 1f)
                .onGloballyPositioned { coordinates ->
                    latestOnHeightChanged(coordinates.size.height)
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(renderedSurfaceHeight.dp)
                    .offset(y = -bottomReservation)
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = AppShapes.sheet,
                        elevation = 20.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = handleModifier.height(DrawerHandleHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 42.dp, max = 42.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection)
                                .verticalScroll(scrollState)
                        ) {
                            content(visibleStage)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Measures stage content without taking layout space. The probe is width-constrained exactly like
 * the visible sheet, while height is capped at the largest reachable content area.
 */
@Composable
private fun DrawerStageHeightProbe(
    width: Dp,
    maxContentHeight: Dp,
    content: @Composable ColumnScope.(MapDrawerStage) -> Unit,
    onMeasured: (DrawerContentHeights) -> Unit
) {
    val density = LocalDensity.current
    val latestOnMeasured by rememberUpdatedState(onMeasured)
    val widthPx = with(density) { width.roundToPx().coerceAtLeast(1) }
    val maxHeightPx = with(density) { maxContentHeight.roundToPx().coerceAtLeast(1) }

    SubcomposeLayout(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.dp)
    ) { constraints ->
        val constraintsWidth = widthPx.coerceAtMost(constraints.maxWidth.coerceAtLeast(1))
        val childConstraints = Constraints(maxWidth = constraintsWidth, maxHeight = maxHeightPx)
        val measuredPx = MapDrawerStage.entries.associateWith { stage ->
            val measurables = subcompose(stage) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { }
                ) {
                    content(stage)
                }
            }
            measurables.maxOfOrNull { it.measure(childConstraints).height } ?: 0
        }
        val next = with(density) {
            DrawerContentHeights(
                collapsed = measuredPx.getValue(MapDrawerStage.COLLAPSED).toDp(),
                halfExpanded = measuredPx.getValue(MapDrawerStage.HALF_EXPANDED).toDp(),
                expanded = measuredPx.getValue(MapDrawerStage.EXPANDED).toDp()
            )
        }
        if (next.collapsed.value > 0f || next.halfExpanded.value > 0f || next.expanded.value > 0f) {
            latestOnMeasured(next)
        }
        layout(constraints.maxWidth, 0) {}
    }
}
