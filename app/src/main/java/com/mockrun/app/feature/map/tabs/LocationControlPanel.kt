package com.mockrun.app.feature.map.tabs
import androidx.compose.material3.MaterialTheme
import com.mockrun.app.core.designsystem.AppSemanticColors
import androidx.compose.ui.text.font.FontFamily

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.domain.model.Route
import com.mockrun.app.core.location.SearchResultItem
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.ScreenInsets
import com.mockrun.app.core.designsystem.minTouchTarget
import com.mockrun.app.feature.map.MapSearchField
import com.mockrun.app.feature.map.MapSearchState
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure

enum class LocationPanelState {
    COLLAPSED,
    DEFAULT,
    EXPANDED
}

@Composable
fun LocationControlPanel(
    modifier: Modifier = Modifier,
    isMockActive: Boolean,
    latitude: Double,
    longitude: Double,
    address: String,
    searchQuery: String,
    searchResults: List<SearchResultItem>,
    isSearching: Boolean,
    savedLocations: List<Route> = emptyList(),
    savedTracks: List<Route> = emptyList(),
    activeRule: MultiTargetRule? = null,
    onSetTargetLocation: ((MultiTargetRule) -> Unit)? = null,
    onToggleTargetRule: ((MultiTargetRule, Boolean) -> Unit)? = null,
    onClearActiveTarget: (() -> Unit)? = null,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultSelect: (SearchResultItem) -> Unit,
    onClearSearch: () -> Unit,
    onStartMock: () -> Unit,
    onStopMock: () -> Unit,
    onResetRealLocation: () -> Unit,
    onSaveLocation: () -> Unit,
    onSelectSavedRoute: (Route) -> Unit
) {
    val context = LocalContext.current
    // 初始状态恒为「完全收起」—— 不读任何持久化存储、也不沿用上一次的选择。
    // 于是「首次安装 / 冷启动 / 从其它页面切回定位页」三条路径都从收起开始，先给用户一张完整地图。
    // 旧实现把用户选择写进 SharedPreferences 并在每次进入页面时读回来当初始值：
    // 用户只要曾经把抽屉拖开过一次，之后每次打开应用进定位页抽屉都是展开的、直接压住地图。
    // 状态是页面级的（remember 随页面离开组合而销毁）：只有用户本次停留在定位页时的
    // 拖拽 / 点击手柄才会改变它，切走再回来即回到默认收起。
    var panelState by remember { mutableStateOf(LocationPanelState.COLLAPSED) }
    val setPanelState: (LocationPanelState) -> Unit = { newState ->
        panelState = newState
    }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // 底部安全区统一由 ScreenInsets 推导（系统导航栏 + 底栏 72 + 上下外距 12 + 呼吸 12）。
    // 旧实现由调用方传入 navBar + 94 的硬编码值，设备安全区变化时不会跟随。
    val bottomInset = ScreenInsets.bottomInset(extra = 12.dp)

    val dragGestureModifier = Modifier.pointerInput(panelState) {
        detectVerticalDragGestures(
            onDragStart = { dragAccumulator = 0f },
            onVerticalDrag = { _, dragAmount ->
                dragAccumulator += dragAmount
            },
            onDragEnd = {
                if (dragAccumulator > 30f) {
                    when (panelState) {
                        LocationPanelState.EXPANDED -> setPanelState(LocationPanelState.DEFAULT)
                        LocationPanelState.DEFAULT -> setPanelState(LocationPanelState.COLLAPSED)
                        LocationPanelState.COLLAPSED -> {}
                    }
                } else if (dragAccumulator < -30f) {
                    when (panelState) {
                        LocationPanelState.COLLAPSED -> setPanelState(LocationPanelState.DEFAULT)
                        LocationPanelState.DEFAULT -> setPanelState(LocationPanelState.EXPANDED)
                        LocationPanelState.EXPANDED -> {}
                    }
                }
                dragAccumulator = 0f
            },
            onDragCancel = { dragAccumulator = 0f }
        )
    }

    // 长地址可达性：折叠时 2 行 + 省略号，点击整行切换展开/收起；
    // 同时把完整地址写进语义描述，读屏用户无需展开即可听到全文。
    val fullAddress = address.ifBlank { "已选定目标坐标" }
    var addressExpanded by remember(fullAddress) { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = bottomInset.coerceAtLeast(0.dp))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = AppShapes.sheet,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
        // 1. Drag Handle Pill (支持点击快速在 COLLAPSED / DEFAULT / EXPANDED 间循环切换)
        //    触区用 minTouchTarget 抬到 48dp —— 原实现只有 5dp 高的胶囊本身可点。
        //    位置固定在抽屉顶部、不随内容滚动：抽屉被高度上限压住后内容会滚动，
        //    若手柄也参与滚动就会被卷出视野，用户找不到收起/展开入口。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp)
                .minTouchTarget(minHeight = 48.dp)
                .then(dragGestureModifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    setPanelState(
                        when (panelState) {
                            LocationPanelState.COLLAPSED -> LocationPanelState.DEFAULT
                            LocationPanelState.DEFAULT -> LocationPanelState.EXPANDED
                            LocationPanelState.EXPANDED -> LocationPanelState.DEFAULT
                        }
                    )
                }
                .semantics {
                    contentDescription = when (panelState) {
                        LocationPanelState.COLLAPSED -> "展开定位面板"
                        LocationPanelState.DEFAULT -> "展开更多设置"
                        LocationPanelState.EXPANDED -> "收起定位面板"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            )
        }

        // 2..5 其余内容：抽屉被调用方的 heightIn(max = …) 压在地图净空以内时，多出来的部分在这里
        //    滚动查看 —— 既不把抽屉顶到顶部控件（应用分流胶囊行）上，也不把内容裁掉。
        //    weight(1f, fill = false)：内容不足时按内容高（抽屉不撑高），超出时吃满剩余高度再滚动。
        //    搜索行 / 状态卡等自带竖直拖拽手势的子节点在 Main 阶段先消费事件，
        //    所以「拖手柄改抽屉档位」与「拖内容滚动」互不干扰。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 2. Master Action & Search Rows
            if (activeRule != null) {
                val defaultRuleColor = MaterialTheme.colorScheme.primary
                val activeRuleColor = remember(activeRule.colorHex, defaultRuleColor) {
                    runCatching { Color(android.graphics.Color.parseColor(activeRule.colorHex)) }
                        .logFailure("LocationControlPanel", "parse rule color", Diag.Level.DEBUG)
                        .getOrDefault(defaultRuleColor)
                }

                // A. Search Box (Full Width)
                Box(modifier = Modifier.fillMaxWidth().then(dragGestureModifier)) {
                    MapSearchField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = "搜索【${activeRule.appName}】分流地点",
                        onClear = onClearSearch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // B. App Diversion Status Card with Inline Switch
                Surface(
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(
                        1.dp,
                        if (activeRule.isEnabled) {
                            activeRuleColor.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().then(dragGestureModifier)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (activeRule.isEnabled) {
                                    activeRuleColor
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = activeRule.appName.take(1),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = activeRule.appName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = if (activeRule.isEnabled) {
                                            activeRuleColor.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = if (activeRule.isEnabled) "分流运行中" else "分流已暂停",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (activeRule.isEnabled) activeRuleColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (activeRule.isEnabled) "独立虚拟定位生效中" else "暂停后使用全局或物理定位",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = activeRule.isEnabled,
                            onCheckedChange = { isChecked ->
                                onToggleTargetRule?.invoke(activeRule, isChecked)
                            }
                        )
                    }
                }

                // C. Main Action Row: [ 设为该应用分流定位点 ] & [ 切回全局 ]
                Row(
                    modifier = Modifier.fillMaxWidth().then(dragGestureModifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onSetTargetLocation?.invoke(activeRule) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (activeRule.isEnabled) "更新【${activeRule.appName}】定位点" else "开启并设为【${activeRule.appName}】定位点",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onClearActiveTarget?.invoke() },
                        modifier = Modifier
                            .height(52.dp),
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text(
                            text = "切回全局",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            } else {
                // Global Mode: Search Bar + Master Action Capsule Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dragGestureModifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 搜索框统一走 M3 OutlinedTextField 封装
                    Box(modifier = Modifier.weight(1f)) {
                        MapSearchField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = "搜索地点",
                            onClear = onClearSearch,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (isMockActive) {
                        // Dual Capsule when active: [ 移动到此 ] & [ 停止定位 ]
                        Row(
                            modifier = Modifier.height(52.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onStartMock,
                                modifier = Modifier
                                    .height(52.dp)
                                    .widthIn(min = 88.dp),
                                shape = MaterialTheme.shapes.large,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = "移动到此",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onStopMock,
                                modifier = Modifier
                                    .height(52.dp),
                                shape = MaterialTheme.shapes.large,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = "停止定位",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = onStartMock,
                            modifier = Modifier
                                .height(52.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    // 术语统一：与功能页「开启虚拟定位」同一动词与状态名
                                    text = "开启虚拟定位",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 3. Search Results Overlay —— 有结果 / 搜索中 / 无匹配 三态
            if (searchResults.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(searchResults) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minTouchTarget(minHeight = 48.dp)
                                    .clickable { onSearchResultSelect(item) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 旧实现只在数组非空时渲染 —— 搜索中与「无匹配结果」两种状态没有任何反馈
                MapSearchState(
                    isSearching = isSearching,
                    query = searchQuery,
                    resultCount = searchResults.size,
                    loadingText = "正在检索地点…",
                    emptyText = "未找到匹配的地点，换个关键词或直接在地图上点选"
                )
            }

            // 4. Coordinates & Address Card (visible in DEFAULT and EXPANDED)
            AnimatedVisibility(
                visible = panelState != LocationPanelState.COLLAPSED,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Address & Coordinates header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = MaterialTheme.shapes.large,
                                color = if (isMockActive) {
                                    AppSemanticColors.current.successContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (isMockActive) {
                                            AppSemanticColors.current.onSuccessContainer
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                // 长地址：点击整行展开/收起，且完整值进语义描述
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minTouchTarget(minHeight = 48.dp)
                                        .clickable { addressExpanded = !addressExpanded }
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = if (addressExpanded) {
                                                "完整地址：$fullAddress。点击收起"
                                            } else {
                                                "地址：$fullAddress。点击展开完整地址"
                                            }
                                        },
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = fullAddress,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = if (addressExpanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (addressExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .size(18.dp)
                                    )
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = "WGS-84 · ${"%.6f".format(latitude)}, ${"%.6f".format(longitude)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Action buttons row: 复制坐标 / 真机复位 / 收藏地点
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Copy button
                            OutlinedButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cb?.setPrimaryClip(ClipData.newPlainText("Coordinates", "$latitude, $longitude"))
                                    Toast.makeText(context, "已复制经纬度坐标", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = AppShapes.button,
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("复制坐标", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }

                            // Reset to real device location button
                            OutlinedButton(
                                onClick = onResetRealLocation,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp),
                                shape = AppShapes.button,
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp), tint = AppSemanticColors.current.warning)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "真机复位",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppSemanticColors.current.warning,
                                    maxLines = 1
                                )
                            }

                            // Bookmark button —— 明确是「地点」收藏，与路线收藏区分
                            Button(
                                onClick = onSaveLocation,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = AppShapes.button,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.BookmarkBorder, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("收藏地点", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 5. Saved Drawer (visible only in EXPANDED)
            //    收藏地点（单点）与收藏路线（≥2 航点）分两组展示：
            //    数据源是 MapViewModel 自己的两条派生流，不做静默合并。
            AnimatedVisibility(
                visible = panelState == LocationPanelState.EXPANDED,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (savedLocations.isEmpty() && savedTracks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "暂无收藏：在地图上选点后点「收藏地点」，或规划航线后点「收藏路线」",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = "⭐ 收藏地点 (${savedLocations.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (savedLocations.isEmpty()) {
                                Text(
                                    text = "暂无地点收藏",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 108.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(savedLocations) { route ->
                                        SavedEntryRow(
                                            name = route.name,
                                            trailing = "%.2f, %.2f".format(
                                                route.waypoints.firstOrNull()?.latitude ?: 0.0,
                                                route.waypoints.firstOrNull()?.longitude ?: 0.0
                                            ),
                                            icon = Icons.Default.Place,
                                            onClick = { onSelectSavedRoute(route) }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "🧭 收藏路线 (${savedTracks.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (savedTracks.isEmpty()) {
                                Text(
                                    text = "暂无路线收藏",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 108.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(savedTracks) { route ->
                                        SavedEntryRow(
                                            name = route.name,
                                            trailing = "%.2f km".format(route.totalDistanceKm),
                                            icon = Icons.Default.Route,
                                            onClick = { onSelectSavedRoute(route) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/** 收藏条目行 —— 48dp 触区，名称省略、右侧数值等宽。 */
@Composable
private fun SavedEntryRow(
    name: String,
    trailing: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minTouchTarget(minHeight = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = trailing,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
