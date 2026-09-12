package com.mockrun.app.ui.screen.tabs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.domain.model.Route
import com.mockrun.app.location.SearchResultItem
import com.mockrun.app.ui.theme.IosColors
import com.mockrun.app.ui.theme.liquidGlass

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
    savedRoutes: List<Route>,
    isLiquidGlass: Boolean,
    bottomBarPadding: Dp,
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
    var panelState by remember { mutableStateOf(LocationPanelState.DEFAULT) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val dragGestureModifier = Modifier.pointerInput(panelState) {
        detectVerticalDragGestures(
            onDragStart = { dragAccumulator = 0f },
            onVerticalDrag = { _, dragAmount ->
                dragAccumulator += dragAmount
            },
            onDragEnd = {
                if (dragAccumulator > 30f) {
                    when (panelState) {
                        LocationPanelState.EXPANDED -> panelState = LocationPanelState.DEFAULT
                        LocationPanelState.DEFAULT -> panelState = LocationPanelState.COLLAPSED
                        LocationPanelState.COLLAPSED -> {}
                    }
                } else if (dragAccumulator < -30f) {
                    when (panelState) {
                        LocationPanelState.COLLAPSED -> panelState = LocationPanelState.DEFAULT
                        LocationPanelState.DEFAULT -> panelState = LocationPanelState.EXPANDED
                        LocationPanelState.EXPANDED -> {}
                    }
                }
                dragAccumulator = 0f
            },
            onDragCancel = { dragAccumulator = 0f }
        )
    }

    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = (bottomBarPadding + 8.dp).coerceAtLeast(0.dp))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Drag Handle Pill (支持点击快速在 COLLAPSED / DEFAULT / EXPANDED 间循环切换)
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .then(dragGestureModifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    panelState = when (panelState) {
                        LocationPanelState.COLLAPSED -> LocationPanelState.DEFAULT
                        LocationPanelState.DEFAULT -> LocationPanelState.EXPANDED
                        LocationPanelState.EXPANDED -> LocationPanelState.DEFAULT
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

        // 2. Master Action & Search Rows
        if (activeRule != null) {
            val activeRuleColor = remember(activeRule.colorHex) {
                runCatching { Color(android.graphics.Color.parseColor(activeRule.colorHex)) }
                    .getOrDefault(IosColors.SystemBlue)
            }

            // A. Search Box (Full Width)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .then(dragGestureModifier)
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(24.dp),
                        elevation = 6.dp
                    ),
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = if (isDark) Color.White.copy(0.7f) else IosColors.SecondaryLabel,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                "搜索【${activeRule.appName}】分流地点",
                                fontSize = 13.5.sp,
                                color = if (isDark) Color.White.copy(0.5f) else IosColors.SecondaryLabel
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = IosColors.SystemBlue
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = onClearSearch,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除",
                                tint = if (isDark) Color.White.copy(0.7f) else IosColors.SecondaryLabel,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // B. App Diversion Status Card with Inline Switch
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                border = androidx.compose.foundation.BorderStroke(
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
                            shape = RoundedCornerShape(8.dp),
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

            // C. Main Action Row: [ 确定设为该应用分流定位点 ] & [ 切回全局 ]
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
                            fontSize = 13.5.sp
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
                            color = IosColors.SystemBlue
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
                // Search Box (styled with Liquid Glass or Solid)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .liquidGlass(
                            isLiquidGlass = isLiquidGlass,
                            shape = RoundedCornerShape(26.dp),
                            elevation = 6.dp
                        ),
                    shape = RoundedCornerShape(26.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = if (isDark) Color.White.copy(0.7f) else IosColors.SecondaryLabel,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    "搜索地点 / POI / 道路",
                                    fontSize = 14.sp,
                                    color = if (isDark) Color.White.copy(0.5f) else IosColors.SecondaryLabel
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = IosColors.SystemBlue
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSearch,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "清除",
                                    tint = if (isDark) Color.White.copy(0.7f) else IosColors.SecondaryLabel,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (isMockActive) {
                    // Dual Capsule when active: [ 移动到此 ] & [ 停止模拟 ]
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
                                    shape = RoundedCornerShape(26.dp),
                                    elevation = 8.dp,
                                    containerColor = IosColors.SystemBlue.copy(0.92f)
                                )
                                .clickable { onStartMock() },
                            shape = RoundedCornerShape(26.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "移动",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .height(52.dp)
                                .liquidGlass(
                                    isLiquidGlass = isLiquidGlass,
                                    shape = RoundedCornerShape(26.dp),
                                    elevation = 8.dp,
                                    containerColor = IosColors.SystemRed.copy(0.92f)
                                )
                                .clickable { onStopMock() },
                            shape = RoundedCornerShape(26.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "停止",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
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
                                shape = RoundedCornerShape(26.dp),
                                elevation = 8.dp,
                                containerColor = IosColors.SystemBlue.copy(0.95f)
                            )
                            .clickable { onStartMock() },
                        shape = RoundedCornerShape(26.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 18.dp),
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
                                text = "开启定位",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Search Results Overlay (If user is searching)
        if (searchResults.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = RoundedCornerShape(20.dp),
                        elevation = 10.dp
                    ),
                shape = RoundedCornerShape(20.dp),
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
        }

        // 4. Coordinates & Address Card (visible in DEFAULT and EXPANDED)
        AnimatedVisibility(
            visible = panelState != LocationPanelState.COLLAPSED,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
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
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Address & Coordinates header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isMockActive) IosColors.SystemGreen.copy(0.2f) else IosColors.SystemBlue.copy(0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = if (isMockActive) IosColors.SystemGreen else IosColors.SystemBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = address.ifBlank { "已选定目标坐标" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = if (isDark) Color.White else IosColors.Label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "WGS-84: ${"%.6f".format(latitude)}, ${"%.6f".format(longitude)}",
                                fontSize = 12.sp,
                                color = if (isDark) Color.White.copy(0.7f) else IosColors.SecondaryLabel
                            )
                        }
                    }

                    // Action buttons row: 复制坐标 / 真机复位 / 收藏此点
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
                                .height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制坐标", fontSize = 12.sp, maxLines = 1)
                        }

                        // Reset to real device location button
                        OutlinedButton(
                            onClick = onResetRealLocation,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp), tint = IosColors.SystemOrange)
                            Spacer(Modifier.width(4.dp))
                            Text("真机复位", fontSize = 12.sp, color = IosColors.SystemOrange, maxLines = 1)
                        }

                        // Bookmark button
                        Button(
                            onClick = onSaveLocation,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemBlue),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.BookmarkBorder, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("收藏此点", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 5. Saved Locations Drawer (visible only in EXPANDED)
        AnimatedVisibility(
            visible = panelState == LocationPanelState.EXPANDED,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
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
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⭐ 常用与收藏轨迹 (${savedRoutes.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = if (isDark) Color.White else IosColors.Label
                    )
                    Spacer(Modifier.height(6.dp))

                    if (savedRoutes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "暂无收藏路线，可在地图选点后收藏",
                                fontSize = 12.sp,
                                color = if (isDark) Color.White.copy(0.6f) else IosColors.SecondaryLabel
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(savedRoutes) { route ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelectSavedRoute(route) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Route,
                                        contentDescription = null,
                                        tint = IosColors.SystemBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = route.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDark) Color.White else IosColors.Label,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${"%.2f".format(route.totalDistanceKm)} km",
                                        fontSize = 12.sp,
                                        color = IosColors.SecondaryLabel
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
