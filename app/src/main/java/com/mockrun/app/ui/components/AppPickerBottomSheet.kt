package com.mockrun.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.mockrun.app.core.designsystem.AppSemanticColors
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.AtlasSurface
import com.mockrun.app.core.designsystem.GlassDialogWindowEffect
import com.mockrun.app.core.designsystem.LocalLiquidGlassEnabled
import com.mockrun.app.core.designsystem.minTouchTarget
import com.mockrun.app.core.data.repository.InstalledAppItem
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.domain.model.TargetMockMode
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MultiTargetPalette = listOf(
    "#007AFF", // iOS Blue
    "#34C759", // iOS Green
    "#FF9500", // iOS Orange
    "#AF52DE", // iOS Purple
    "#FF2D55", // iOS Pink
    "#30B0C7", // iOS Teal
    "#5856D6", // iOS Indigo
    "#FFCC00"  // iOS Yellow
)

enum class AppCategoryFilter(val label: String) {
    USER("用户应用"),
    ALL("全部应用"),
    SYSTEM("系统应用")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerBottomSheet(
    onDismissRequest: () -> Unit,
    existingRules: List<MultiTargetRule>,
    initialLatitude: Double,
    initialLongitude: Double,
    onAppSelected: (MultiTargetRule) -> Unit,
    loadInstalledApps: suspend () -> List<InstalledAppItem>
) {
    val isLiquidGlass = LocalLiquidGlassEnabled.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedUserId by remember { mutableIntStateOf(0) } // 0 = Main, 999 = Dual
    var isCustomUserId by remember { mutableStateOf(false) }
    var customUserIdText by remember { mutableStateOf("999") }
    var isLoading by remember { mutableStateOf(true) }
    var allApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf(AppCategoryFilter.USER) }
    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualPkgInput by remember { mutableStateOf("") }
    var manualNameInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        allApps = loadInstalledApps()
        isLoading = false
    }

    val finalUserId = if (isCustomUserId) {
        customUserIdText.toIntOrNull() ?: 0
    } else {
        selectedUserId
    }

    val userCount = remember(allApps) { allApps.count { !it.isSystem } }
    val systemCount = remember(allApps) { allApps.count { it.isSystem } }
    val allCount = remember(allApps) { allApps.size }

    val filteredApps = remember(searchQuery, allApps, selectedCategory) {
        val baseList = when (selectedCategory) {
            AppCategoryFilter.USER -> allApps.filter { !it.isSystem }
            AppCategoryFilter.SYSTEM -> allApps.filter { it.isSystem }
            AppCategoryFilter.ALL -> allApps
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            val q = searchQuery.trim().lowercase()
            // 只在当前分类内搜索，不做全量兜底。
            //
            // 原实现在本分类无匹配时会回落到全量搜索：用户在「系统应用」下搜到
            // 一个用户应用，列表显示它、分段控件仍高亮「系统应用」，
            // 而「已筛选 N 个应用」与各分类计数（上方统计）都与可见列表矛盾。
            // 空态已提供「以包名添加」入口，不会让用户无路可走。
            baseList.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color.Transparent,
        shape = AppShapes.sheet,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f)
    ) {
        AtlasSurface(
            modifier = Modifier.fillMaxWidth(),
            isLiquidGlass = isLiquidGlass,
            shape = AppShapes.sheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            elevation = if (isLiquidGlass) 22.dp else 8.dp
        ) {
            GlassDialogWindowEffect(isLiquidGlass)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "添加独立分流应用",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "指定应用将拥有独立的虚拟坐标，不影响其他应用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = onDismissRequest) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                SegmentedButton(
                    selected = !isCustomUserId && selectedUserId == 0,
                    onClick = {
                        isCustomUserId = false
                        selectedUserId = 0
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = {
                        Text(
                            text = "主空间 (User 0)",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                SegmentedButton(
                    selected = !isCustomUserId && selectedUserId == 999,
                    onClick = {
                        isCustomUserId = false
                        selectedUserId = 999
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = {
                        Text(
                            text = "应用分身 (User 999)",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                AppCategoryFilter.values().forEachIndexed { index, cat ->
                    val count = when (cat) {
                        AppCategoryFilter.USER -> userCount
                        AppCategoryFilter.ALL -> allCount
                        AppCategoryFilter.SYSTEM -> systemCount
                    }
                    SegmentedButton(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AppCategoryFilter.values().size
                        ),
                        label = {
                            Text(
                                text = "${cat.label} ($count)",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = {
                    Text(
                        text = "搜索应用名称或包名...",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    null
                }
            )

            // Filter status & Manual Input Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已筛选 ${filteredApps.size} 个应用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        manualPkgInput = searchQuery.trim()
                        showManualInputDialog = true
                    },
                    modifier = Modifier.minTouchTarget()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "手动输入包名",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Content List
            // 列表高度按屏幕高度取 45%，窄屏（如 640dp 高）下也不会把
            // 空态 CTA 顶出可视区；上限 420dp 保持大屏观感不变。
            val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.45f)
                .coerceIn(200f, 420f).dp

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "未找到匹配应用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchQuery.isNotBlank()) {
                            FilledTonalButton(
                                onClick = {
                                    manualPkgInput = searchQuery.trim()
                                    showManualInputDialog = true
                                },
                                modifier = Modifier.minTouchTarget()
                            ) {
                                Text("直接以包名添加 \"${searchQuery.take(20)}\"")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showManualInputDialog = true },
                                modifier = Modifier.minTouchTarget()
                            ) {
                                Text("手动输入包名添加")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { "${it.packageName}_${finalUserId}" }) { item ->
                        val targetKey = "${item.packageName}_${finalUserId}"
                        val isAlreadyAdded = existingRules.any { it.key == targetKey }

                        AppItemRow(
                            item = item,
                            userId = finalUserId,
                            isAlreadyAdded = isAlreadyAdded,
                            onSelect = {
                                if (!isAlreadyAdded) {
                                    val assignedColor = MultiTargetPalette[existingRules.size % MultiTargetPalette.size]
                                    val newRule = MultiTargetRule(
                                        packageName = item.packageName,
                                        userId = finalUserId,
                                        appName = item.appName,
                                        isEnabled = true,
                                        mode = TargetMockMode.STATIONARY,
                                        latitude = initialLatitude,
                                        longitude = initialLongitude,
                                        colorHex = assignedColor
                                    )
                                    onAppSelected(newRule)
                                    onDismissRequest()
                                }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showManualInputDialog) {
        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            title = {
                Text(
                    text = "手动指定应用包名",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "适用于隐藏应用、分身容器或未在列表展示的应用。输入包名后将直接按目标接管其位置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manualPkgInput,
                        onValueChange = { manualPkgInput = it },
                        label = { Text("应用包名 (Package Name)") },
                        placeholder = { Text("例如 com.tencent.mm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualNameInput,
                        onValueChange = { manualNameInput = it },
                        label = { Text("应用显示名称 (可选)") },
                        placeholder = { Text("例如 微信分身") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "生效目标: ${if (finalUserId == 0) "主空间 (User 0)" else "应用分身 (User $finalUserId)"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppSemanticColors.current.warning
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pkg = manualPkgInput.trim()
                        if (pkg.isNotBlank()) {
                            val name = manualNameInput.trim().ifBlank { pkg }
                            val assignedColor = MultiTargetPalette[existingRules.size % MultiTargetPalette.size]
                            val newRule = MultiTargetRule(
                                packageName = pkg,
                                userId = finalUserId,
                                appName = name,
                                isEnabled = true,
                                mode = TargetMockMode.STATIONARY,
                                latitude = initialLatitude,
                                longitude = initialLongitude,
                                colorHex = assignedColor
                            )
                            onAppSelected(newRule)
                            showManualInputDialog = false
                            onDismissRequest()
                        }
                    },
                    enabled = manualPkgInput.isNotBlank(),
                    modifier = Modifier.minTouchTarget()
                ) {
                    Text("确认添加", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualInputDialog = false },
                    modifier = Modifier.minTouchTarget()
                ) {
                    Text("取消", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Composable
private fun AppItemRow(
    item: InstalledAppItem,
    userId: Int,
    isAlreadyAdded: Boolean,
    onSelect: () -> Unit
) {
val bitmap = remember(item.icon) {
           item.icon?.let { d ->
               runCatching {
                   d.toBitmap(width = 80, height = 80)
               }.logFailure("AppPicker", "icon toBitmap (${item.appName})", Diag.Level.DEBUG)
                   .getOrNull()
           }
       }

    Surface(
        onClick = onSelect,
        enabled = !isAlreadyAdded,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isAlreadyAdded) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = item.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = item.appName.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // App Name & Package Name
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isAlreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isSystem) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = "系统",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (userId != 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = AppSemanticColors.current.warningContainer,
                            contentColor = AppSemanticColors.current.onWarningContainer
                        ) {
                            Text(
                                text = "分身 $userId",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Status Badge
            if (isAlreadyAdded) {
                Surface(
                    shape = CircleShape,
                    color = AppSemanticColors.current.successContainer,
                    contentColor = AppSemanticColors.current.onSuccessContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = AppSemanticColors.current.onSuccessContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "已分流",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppSemanticColors.current.onSuccessContainer
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = "+ 添加",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
