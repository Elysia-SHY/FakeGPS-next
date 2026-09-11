package com.mockrun.app.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.mockrun.app.data.repository.InstalledAppItem
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.domain.model.TargetMockMode
import com.mockrun.app.ui.theme.*
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
            val matchedInBase = baseList.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
            if (matchedInBase.isNotEmpty()) {
                matchedInBase
            } else {
                allApps.filter {
                    it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = IosColors.SecondaryGroupedBackground,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 5.dp),
                shape = CircleShape,
                color = IosColors.SystemGray.copy(alpha = 0.4f)
            ) {}
        }
    ) {
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
                Column {
                    Text(
                        text = "添加独立分流应用",
                        style = IosTypography.Headline,
                        color = IosColors.Label
                    )
                    Text(
                        text = "指定应用将拥有独立的虚拟坐标，不影响其他应用",
                        style = IosTypography.Caption1,
                        color = IosColors.SecondaryLabel
                    )
                }
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(IosColors.SystemGray.copy(alpha = 0.15f))
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = IosColors.SecondaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // User Profile / Dual App Selector
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = IosColors.TertiaryGroupedBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Option 1: Main User (User 0)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .bouncyClickable {
                                isCustomUserId = false
                                selectedUserId = 0
                            },
                        color = if (!isCustomUserId && selectedUserId == 0) IosColors.SecondaryGroupedBackground else Color.Transparent,
                        shadowElevation = if (!isCustomUserId && selectedUserId == 0) 2.dp else 0.dp
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "主空间 (User 0)",
                                style = IosTypography.Subheadline,
                                fontWeight = if (!isCustomUserId && selectedUserId == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isCustomUserId && selectedUserId == 0) IosColors.SystemBlue else IosColors.SecondaryLabel
                            )
                        }
                    }

                    // Option 2: Cloned / Dual (User 999)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .bouncyClickable {
                                isCustomUserId = false
                                selectedUserId = 999
                            },
                        color = if (!isCustomUserId && selectedUserId == 999) IosColors.SecondaryGroupedBackground else Color.Transparent,
                        shadowElevation = if (!isCustomUserId && selectedUserId == 999) 2.dp else 0.dp
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "应用分身 (User 999)",
                                style = IosTypography.Subheadline,
                                fontWeight = if (!isCustomUserId && selectedUserId == 999) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isCustomUserId && selectedUserId == 999) IosColors.SystemBlue else IosColors.SecondaryLabel
                            )
                        }
                    }
                }
            }

            // Category Filter: 用户应用 / 全部应用 / 系统应用
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = IosColors.TertiaryGroupedBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppCategoryFilter.values().forEach { cat ->
                        val count = when (cat) {
                            AppCategoryFilter.USER -> userCount
                            AppCategoryFilter.ALL -> allCount
                            AppCategoryFilter.SYSTEM -> systemCount
                        }
                        val isSelected = selectedCategory == cat
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .bouncyClickable { selectedCategory = cat },
                            color = if (isSelected) IosColors.SecondaryGroupedBackground else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${cat.label} ($count)",
                                    style = IosTypography.Caption1,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) IosColors.SystemBlue else IosColors.SecondaryLabel
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = IosColors.TertiaryGroupedBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = IosColors.SecondaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "搜索应用名称或包名...",
                                style = IosTypography.Body,
                                color = IosColors.SecondaryLabel
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = IosTypography.Body.copy(color = IosColors.Label),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "清除",
                                tint = IosColors.SecondaryLabel,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

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
                    style = IosTypography.Caption1,
                    color = IosColors.SecondaryLabel
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .bouncyClickable {
                            manualPkgInput = searchQuery.trim()
                            showManualInputDialog = true
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = IosColors.SystemBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "手动输入包名",
                        style = IosTypography.Caption1,
                        color = IosColors.SystemBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Content List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = IosColors.SystemBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "未找到匹配应用",
                            style = IosTypography.Subheadline,
                            color = IosColors.SecondaryLabel
                        )
                        if (searchQuery.isNotBlank()) {
                            Button(
                                onClick = {
                                    manualPkgInput = searchQuery.trim()
                                    showManualInputDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IosColors.SystemBlue.copy(alpha = 0.15f),
                                    contentColor = IosColors.SystemBlue
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("直接以包名添加 \"${searchQuery.take(20)}\"")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showManualInputDialog = true },
                                shape = RoundedCornerShape(8.dp)
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
                        .heightIn(max = 420.dp),
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

    if (showManualInputDialog) {
        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            containerColor = IosColors.SecondaryGroupedBackground,
            title = {
                Text(
                    text = "手动指定应用包名",
                    style = IosTypography.Headline,
                    color = IosColors.Label
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "适用于隐藏应用、分身容器或未在列表展示的应用。输入包名后将直接按目标接管其位置。",
                        style = IosTypography.Caption1,
                        color = IosColors.SecondaryLabel
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
                        style = IosTypography.Caption2,
                        color = IosColors.SystemOrange
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
                    enabled = manualPkgInput.isNotBlank()
                ) {
                    Text("确认添加", color = IosColors.SystemBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text("取消", color = IosColors.SecondaryLabel)
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
            }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isAlreadyAdded, onClick = onSelect),
        color = if (isAlreadyAdded) IosColors.SystemGray.copy(alpha = 0.08f) else Color.Transparent
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
                        .clip(RoundedCornerShape(9.dp))
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = IosColors.SystemBlue.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = item.appName.take(1),
                            style = IosTypography.Headline,
                            color = IosColors.SystemBlue
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
                        style = IosTypography.Headline,
                        color = if (isAlreadyAdded) IosColors.SecondaryLabel else IosColors.Label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isSystem) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IosColors.SystemGray.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "系统",
                                style = IosTypography.Caption2,
                                color = IosColors.SecondaryLabel,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (userId != 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IosColors.SystemOrange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "分身 $userId",
                                style = IosTypography.Caption2,
                                fontWeight = FontWeight.Bold,
                                color = IosColors.SystemOrange,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.packageName,
                    style = IosTypography.Footnote,
                    color = IosColors.SecondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Status Badge
            if (isAlreadyAdded) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = IosColors.SystemGreen.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = IosColors.SystemGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "已分流",
                            style = IosTypography.Caption1,
                            fontWeight = FontWeight.SemiBold,
                            color = IosColors.SystemGreen
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = IosColors.SystemBlue.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "+ 添加",
                        style = IosTypography.Caption1,
                        fontWeight = FontWeight.SemiBold,
                        color = IosColors.SystemBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
