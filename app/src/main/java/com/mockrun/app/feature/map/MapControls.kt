package com.mockrun.app.feature.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.*

/**
 * iOS Control Center Style Vertical Zoom Slider
 * Emil Kowalski & Apple Design principles:
 * - 1:1 direct vertical drag tracking with fluid spring settle
 * - Frosted glass capsule with subtle border and shadow
 * - 48dp 命中区（+/- 按钮 48dp，胶囊体宽 56dp），无需瞄准即可点中
 * - Responsive pointer-down bouncy tactile feedback
 */
@Composable
fun IosVerticalZoomControl(
    currentZoom: Double,
    minZoom: Double = 3.0,
    maxZoom: Double = 19.0,
    onZoomChange: (Double) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    // 胶囊体目标高度：底部抽屉完全展开时右列会被压进顶部安全区，调用方按「顶边限制线」
    // 算好这一档的硬预算（几何下限 112dp = 两个 48dp 按钮 + 轨道与内边距 16dp）。
    // 默认值保持原来写死的 180dp —— 其余调用点与预览不受影响。
    capsuleHeight: Dp = 180.dp,
    // 高度过渡曲线：调用方传入与底部抽屉同源的那条 spring（见 MapScreen 的 DRAWER_SPRING_DP）。
    capsuleAnimationSpec: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
) {
    var isDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }

    // capsuleHeight 是硬预算（由顶边限制线推出），所以只对「变高」方向走 spring：
    // 收缩方向直接取 min —— 否则动画的滞后会让胶囊（连同整列）顶破限制线，
    // 而「右列不与抽屉重叠」这条不变量优先级最高，不能被动画破坏。
    // 曲线与抽屉展开同源：展开时胶囊的收缩节奏 = 抽屉的展开节奏，收起时按同一条 spring 弹回。
    val animatedCapsuleHeight by animateDpAsState(
        targetValue = capsuleHeight,
        animationSpec = capsuleAnimationSpec,
        label = "zoomCapsuleHeight"
    )
    val renderedCapsuleHeight = minOf(animatedCapsuleHeight, capsuleHeight)

    val updatedMinZoom by rememberUpdatedState(minZoom)
    val updatedMaxZoom by rememberUpdatedState(maxZoom)
    val updatedOnZoomChange by rememberUpdatedState(onZoomChange)

    val normalizedZoom = ((currentZoom - minZoom) / (maxZoom - minZoom)).coerceIn(0.0, 1.0).toFloat()
    val animatedFill by animateFloatAsState(
        targetValue = normalizedZoom,
        animationSpec = if (isDragging) spring(stiffness = Spring.StiffnessHigh) else MotionTokens.FluidSpringSpec,
        label = "zoomFill"
    )

    Surface(
        modifier = modifier
            .width(56.dp)
            .height(renderedCapsuleHeight)
            .clip(RoundedCornerShape(22.dp))
            .border(0.5.dp, IosHairlineBorder, RoundedCornerShape(22.dp))
            .bouncyScale(isDragging, pressedScale = 0.97f),
        shape = RoundedCornerShape(22.dp),
        color = IosFrostedCapsule,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Zoom In (+) Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bouncyClickable { onZoomIn() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "放大地图",
                    tint = IosBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Vertical Slider Touch Container (spans full 44.dp width for 1:1 direct tracking)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .onGloballyPositioned { coordinates ->
                        trackHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            down.consume()

                            // Immediate 1:1 response on pointer-down tap
                            val initialRatio = (1f - (down.position.y / trackHeightPx)).coerceIn(0f, 1f)
                            val newInitialZoom = updatedMinZoom + initialRatio * (updatedMaxZoom - updatedMinZoom)
                            updatedOnZoomChange(newInitialZoom)

                            // Follow finger drag continuously
                            while (true) {
                                val event = awaitPointerEvent()
                                val dragPointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!dragPointer.pressed) break

                                dragPointer.consume()
                                val ratio = (1f - (dragPointer.position.y / trackHeightPx)).coerceIn(0f, 1f)
                                val newZoom = updatedMinZoom + ratio * (updatedMaxZoom - updatedMinZoom)
                                updatedOnZoomChange(newZoom)
                            }
                            isDragging = false
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                // Background Track Capsule
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(IosSeparator.copy(alpha = 0.5f))
                )

                // Dynamic Active Progress Capsule
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight(animatedFill)
                        .clip(RoundedCornerShape(3.dp))
                        .background(IosBlue)
                )

                // Tactile Pill Thumb Indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedFill),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(IosBlue)
                            .border(0.5.dp, IosHairlineBorder, RoundedCornerShape(2.5.dp))
                    )
                }
            }

            // Zoom Out (-) Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bouncyClickable { onZoomOut() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "缩小地图",
                    tint = IosBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 搜索结果三态承载 —— 定位面板与路线面板共用。
 *
 * 旧实现只在 `searchResults.isNotEmpty()` 时渲染列表：搜索中与「无匹配结果」两种
 * 状态完全没有承载，用户看到的是面板毫无变化。这里补齐「搜索中 / 无匹配 / 有结果」。
 *
 * 关于错误态：MapViewModel.performSearch 只暴露 `isSearching` 与 `searchResults`，
 * 没有对外错误通道（LocationSearchService.search 也不抛出可见异常），因此这里
 * **不臆造**「网络错误」文案，空态只做「未找到匹配的地点」。
 */
@Composable
fun MapSearchState(
    isSearching: Boolean,
    query: String,
    resultCount: Int,
    isLiquidGlass: Boolean,
    loadingText: String = "正在检索地点…",
    emptyText: String = "未找到匹配的地点，换个关键词或直接输入坐标"
) {
    val showEmpty = !isSearching && query.isNotBlank() && resultCount == 0
    if (!isSearching && !showEmpty) return
    val isDark = isSystemInDarkTheme()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                isLiquidGlass = isLiquidGlass,
                shape = RoundedCornerShape(22.dp),
                elevation = 10.dp
            ),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = IosColors.SystemBlue
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = IosColors.TertiaryLabel,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isSearching) loadingText else emptyText,
                style = IosTypography.Caption1,
                color = if (isDark) Color.White.copy(alpha = 0.7f) else IosColors.SecondaryLabel
            )
        }
    }
}
