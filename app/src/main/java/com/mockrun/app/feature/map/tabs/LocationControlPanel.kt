package com.mockrun.app.feature.map.tabs

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.domain.model.Route
import com.mockrun.app.core.location.SearchResultItem
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.IosColors
import com.mockrun.app.core.designsystem.IosGlassDock
import com.mockrun.app.core.designsystem.IosSearchField
import com.mockrun.app.core.designsystem.IosSearchFieldStyle
import com.mockrun.app.core.designsystem.IosTypography
import com.mockrun.app.core.designsystem.ScreenInsets
import com.mockrun.app.core.designsystem.liquidGlass
import com.mockrun.app.core.designsystem.minTouchTarget
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
    isLiquidGlass: Boolean,
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
    val isDark = isSystemInDarkTheme()
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

    // 设计稿 .dock —— 浮空玻璃卡片式抽屉（r32 / blur 30 / 1px 8% 白描边 / 内高光）
    IosGlassDock(
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
        isLiquidGlass = isLiquidGlass,
        elevation = 18.dp
    ) {
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
                    .background(if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f))
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
                val activeRuleColor = remember(activeRule.colorHex) {
                    runCatching { Color(android.graphics.Color.parseColor(activeRule.colorHex)) }
                        .logFailure("LocationControlPanel", "parse rule color", Diag.Level.DEBUG)
                        .getOrDefault(IosColors.SystemBlue)
                }

                // A. Search Box (Full Width) —— 统一走设计系统 IosSearchField（STANDARD 52dp / r18）
                Box(modifier = Modifier.fillMaxWidth().then(dragGestureModifier)) {
                    IosSearchField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = "搜索【${activeRule.appName}】分流地点",
                        modifier = Modifier.fillMaxWidth(),
                        style = IosSearchFieldStyle.STANDARD
                    )
                    if (searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .minTouchTarget(minWidth = 48.dp, minHeight = 48.dp)
                                                            .clickable(onClick = onClearSearch),
                            contentAlignment = Alignment.Center
                        ) {
                            // 20dp 遮罩圆：既让「清除」在玻璃底上可见，
                            // 又遮住从按钮下方穿过的输入文字
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "清除搜索内容",
                                    tint = if (isDark) Color.White.copy(0.85f) else IosColors.SecondaryLabel,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // B. App Diversion Status Card with Inline Switch
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                    border = BorderStroke(
                        1.dp,
                        if (activeRule.isEnabled) activeRuleColor.copy(alpha = 0.45f) else (if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
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
                                shape = RoundedCornerShape(9.dp),
                                color = if (activeRule.isEnabled) activeRuleColor else IosColors.SystemGray,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = activeRule.appName.take(1),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
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
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else Color.Black
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (activeRule.isEnabled) activeRuleColor.copy(alpha = 0.15f) else IosColors.SystemGray.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (activeRule.isEnabled) "分流运行中" else "分流已暂停",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (activeRule.isEnabled) activeRuleColor else IosColors.SecondaryLabel,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (activeRule.isEnabled) "独立虚拟定位生效中" else "暂停后使用全局或物理定位",
                                    fontSize = 10.5.sp,
                                    color = IosColors.SecondaryLabel
                                )
                            }
                        }

                        Switch(
                            checked = activeRule.isEnabled,
                            onCheckedChange = { isChecked ->
                                onToggleTargetRule?.invoke(activeRule, isChecked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = activeRuleColor,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
                            )
                        )
                    }
                }

                // C. Main Action Row: [ 设为该应用分流定位点 ] & [ 切回全局 ]
                Row(
                    modifier = Modifier.fillMaxWidth().then(dragGestureModifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .liquidGlass(
                                isLiquidGlass = isLiquidGlass,
                                shape = RoundedCornerShape(24.dp),
                                elevation = 8.dp,
                                containerColor = if (activeRule.isEnabled) activeRuleColor else IosColors.SystemBlue
                            )
                            .clickable {
                                onSetTargetLocation?.invoke(activeRule)
                            },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (activeRule.isEnabled) "更新【${activeRule.appName}】定位点" else "开启并设为【${activeRule.appName}】定位点",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .height(48.dp)
                            .liquidGlass(
                                isLiquidGlass = isLiquidGlass,
                                shape = RoundedCornerShape(24.dp),
                                elevation = 4.dp
                            )
                            .clickable { onClearActiveTarget?.invoke() },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "切回全局",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IosColors.SystemBlue,
                                maxLines = 1
                            )
                        }
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
                    // 搜索框统一走 IosSearchField（STANDARD 52dp / r18 / 16sp）
                    Box(modifier = Modifier.weight(1f)) {
                        IosSearchField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = "搜索地点",
                            modifier = Modifier.fillMaxWidth(),
                            style = IosSearchFieldStyle.STANDARD
                        )
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .minTouchTarget(minWidth = 48.dp, minHeight = 48.dp)
                                                                    .clickable(onClick = onClearSearch),
                                contentAlignment = Alignment.Center
                            ) {
                                // 20dp 遮罩圆：既让「清除」在玻璃底上可见，
                                // 又遮住从按钮下方穿过的输入文字
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "清除搜索内容",
                                        tint = if (isDark) Color.White.copy(0.85f) else IosColors.SecondaryLabel,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isMockActive) {
                        // Dual Capsule when active: [ 移动到此 ] & [ 停止定位 ]
                        Row(
                            modifier = Modifier.height(52.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .height(52.dp)
                                    .liquidGlass(
                                        isLiquidGlass = isLiquidGlass,
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = 12.dp,
                                        containerColor = IosColors.SystemBlue
                                    )
                                    .clickable { onStartMock() },
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = "移动到此",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .height(52.dp)
                                    .liquidGlass(
                                        isLiquidGlass = isLiquidGlass,
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = 12.dp,
                                        containerColor = IosColors.SystemRed
                                    )
                                    .clickable { onStopMock() },
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = "停止定位",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .height(52.dp)
                                .liquidGlass(
                                    isLiquidGlass = isLiquidGlass,
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = 12.dp,
                                    containerColor = IosColors.SystemBlue
                                )
                                .clickable { onStartMock() },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    // 术语统一：与功能页「开启虚拟定位」同一动词与状态名
                                    text = "开启虚拟定位",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
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
                        .heightIn(max = 240.dp)
                        .liquidGlass(
                            isLiquidGlass = isLiquidGlass,
                            shape = RoundedCornerShape(22.dp),
                            elevation = 10.dp
                        ),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent
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
                                    tint = IosColors.SystemBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isDark) Color.White else IosColors.Label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.address,
                                        fontSize = 12.sp,
                                        color = if (isDark) Color.White.copy(0.7f) else IosColors.SecondaryLabel,
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
                    isLiquidGlass = isLiquidGlass,
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
                // 设计稿 .loccard —— r20 / 白 4.5% 底 / 6% 描边
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0x0BFFFFFF),
                    border = BorderStroke(1.dp, Color(0x0FFFFFFF))
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
                                shape = RoundedCornerShape(16.dp),
                                color = if (isMockActive) IosColors.SystemGreen.copy(0.2f) else IosColors.SystemBlue.copy(0.16f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (isMockActive) IosColors.SystemGreen else IosColors.BrandSoft,
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
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        lineHeight = 23.sp,
                                        color = if (isDark) Color.White else IosColors.Label,
                                        maxLines = if (addressExpanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (addressExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = IosColors.SecondaryLabel,
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .size(18.dp)
                                    )
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = "WGS-84 · ${"%.6f".format(latitude)}, ${"%.6f".format(longitude)}",
                                    style = IosTypography.Mono,
                                    color = if (isDark) Color.White.copy(0.7f) else IosColors.SecondaryLabel
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
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("复制坐标", fontSize = 12.5.sp, maxLines = 1)
                            }

                            // Reset to real device location button
                            OutlinedButton(
                                onClick = onResetRealLocation,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp), tint = IosColors.SystemOrange)
                                Spacer(Modifier.width(5.dp))
                                Text("真机复位", fontSize = 12.5.sp, color = IosColors.SystemOrange, maxLines = 1)
                            }

                            // Bookmark button —— 明确是「地点」收藏，与路线收藏区分
                            Button(
                                onClick = onSaveLocation,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemBlue),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.BookmarkBorder, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("收藏地点", fontSize = 12.5.sp, maxLines = 1)
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
                        .heightIn(max = 260.dp)
                        .liquidGlass(
                            isLiquidGlass = isLiquidGlass,
                            shape = RoundedCornerShape(22.dp),
                            elevation = 8.dp
                        ),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent
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
                                    fontSize = 12.sp,
                                    color = if (isDark) Color.White.copy(0.6f) else IosColors.SecondaryLabel
                                )
                            }
                        } else {
                            Text(
                                text = "⭐ 收藏地点 (${savedLocations.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = if (isDark) Color.White else IosColors.Label
                            )
                            if (savedLocations.isEmpty()) {
                                Text(
                                    text = "暂无地点收藏",
                                    style = IosTypography.Caption1,
                                    color = IosColors.TertiaryLabel,
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
                                            isDark = isDark,
                                            onClick = { onSelectSavedRoute(route) }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "🧭 收藏路线 (${savedTracks.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = if (isDark) Color.White else IosColors.Label
                            )
                            if (savedTracks.isEmpty()) {
                                Text(
                                    text = "暂无路线收藏",
                                    style = IosTypography.Caption1,
                                    color = IosColors.TertiaryLabel,
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
                                            isDark = isDark,
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
    }  // IosGlassDock
}

/** 收藏条目行 —— 48dp 触区，名称省略、右侧数值等宽。 */
@Composable
private fun SavedEntryRow(
    name: String,
    trailing: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minTouchTarget(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = IosColors.SystemBlue,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color.White else IosColors.Label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = trailing,
            style = IosTypography.Mono,
            color = IosColors.SecondaryLabel,
            maxLines = 1
        )
    }
}
