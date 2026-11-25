package com.mockrun.app.feature.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.AtlasSurface
import com.mockrun.app.core.designsystem.LocalGlassBackdrop
import com.mockrun.app.core.designsystem.LocalIsDarkTheme
import com.mockrun.app.core.designsystem.LocalLiquidGlassEnabled
import com.mockrun.app.core.designsystem.liquidGlass

internal val MapVerticalZoomControlHeight = 176.dp
private val MapVerticalZoomTrackHeight = 72.dp

/**
 * Material 3 vertical zoom control.
 * - Direct vertical drag tracking with a standard spring settle
 * - Tonal surface with outline and restrained elevation
 * - 48dp 命中区（+/- 按钮 48dp，胶囊体宽 56dp），无需瞄准即可点中
 */
@Composable
fun MapVerticalZoomControl(
    currentZoom: Double,
    minZoom: Double = 3.0,
    maxZoom: Double = 19.0,
    onZoomChange: (Double) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    var isDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }

    val updatedMinZoom by rememberUpdatedState(minZoom)
    val updatedMaxZoom by rememberUpdatedState(maxZoom)
    val updatedOnZoomChange by rememberUpdatedState(onZoomChange)

    val normalizedZoom = ((currentZoom - minZoom) / (maxZoom - minZoom)).coerceIn(0.0, 1.0).toFloat()
    val animatedFill by animateFloatAsState(
        targetValue = normalizedZoom,
        animationSpec = if (isDragging) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "zoomFill"
    )

    val shape = AppShapes.iconLg
    AtlasSurface(
        modifier = modifier
            .width(56.dp)
            .height(MapVerticalZoomControlHeight),
        isLiquidGlass = isLiquidGlass,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        elevation = if (isLiquidGlass) 16.dp else 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Zoom In (+) Button
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "放大地图",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Vertical Slider Touch Container (spans full 44.dp width for 1:1 direct tracking)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MapVerticalZoomTrackHeight)
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
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                // Dynamic Active Progress Capsule
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight(animatedFill)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
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
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            // Zoom Out (-) Button
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "缩小地图",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Material 3 search field shared by the location and route drawers.
 *
 * Keeping the clear action inside the field preserves the one-tap search reset
 * while letting Material 3 own focus, state-layer, shape, and text styling.
 */
@Composable
fun MapSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    val backdrop = LocalGlassBackdrop.current
    val useGlass = isLiquidGlass && backdrop != null
    val isDark = LocalIsDarkTheme.current
    val fieldColors = if (useGlass) {
        TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    } else {
        TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .liquidGlass(
                isLiquidGlass = useGlass,
                shape = AppShapes.panel,
                elevation = if (useGlass) 12.dp else 2.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                backdrop = backdrop,
                borderWidth = if (useGlass) 0.75.dp else 1.dp,
                tintAlpha = if (isDark) 0.28f else 0.34f
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            singleLine = true,
            shape = AppShapes.panel,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = fieldColors,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = if (value.isNotEmpty()) {
                {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除搜索内容",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                null
            }
        )
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
    loadingText: String = "正在检索地点…",
    emptyText: String = "未找到匹配的地点，换个关键词或直接输入坐标"
) {
    val showEmpty = !isSearching && query.isNotBlank() && resultCount == 0
    if (!isSearching && !showEmpty) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isSearching) loadingText else emptyText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
