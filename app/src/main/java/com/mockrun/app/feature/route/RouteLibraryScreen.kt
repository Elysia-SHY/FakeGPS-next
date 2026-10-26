package com.mockrun.app.feature.route

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mockrun.app.BuildConfig
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.RouteMode
import com.mockrun.app.core.location.RouteSimulator
import com.mockrun.app.feature.track.TrackRecordScreen
import com.mockrun.app.ui.viewmodel.TrackViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.mockrun.app.core.designsystem.*
import com.mockrun.app.ui.viewmodel.MapViewModel
import com.mockrun.app.util.Diag
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 路线库覆盖层。
 *
 * 弹窗规范（本文件内统一）：
 *   - 短确认 / 短输入（重命名、删除确认）→ [RoundedCornerShape] 24dp 的 AlertDialog
 *   - 长表单 / 长列表 → 32dp 圆角的 sheet（见 BookmarkBottomSheet / AppPickerBottomSheet）
 *   - 品牌更新对话框 28dp 是唯一例外（见 AppUpdateDialog）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteLibraryScreen(
    mapViewModel: MapViewModel,
    onRouteSelected: () -> Unit
) {
    val context = LocalContext.current
    val savedRoutes by mapViewModel.savedRoutes.collectAsState()
    val selectedRoute by mapViewModel.selectedRoute.collectAsState()

    // 轨迹录制（v2.4.0）：作为本页的叠加层打开，不再动导航图
    val trackViewModel: TrackViewModel = hiltViewModel()
    // rememberSaveable：转屏重建 Activity 后仍停在这一层
    var showTrackScreen by rememberSaveable { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var routeToRename by remember { mutableStateOf<Route?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var routeToDelete by remember { mutableStateOf<Route?>(null) }

    val filteredRoutes = remember(savedRoutes, searchQuery) {
        if (searchQuery.isBlank()) savedRoutes
        else savedRoutes.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // 本页是覆盖层，底栏在覆盖期间被 AppNavigation 隐藏，
    // 因此底部安全区只保留系统导航栏 + 呼吸距离，不再预留 Tab 栏的 100dp。
    val bottomInset = ScreenInsets.bottomInset(includeBottomBar = false)

    // 显式返回：复用系统 Back 语义 —— AppNavigation.kt:100 的 BackHandler 负责
    // 收起覆盖层并保留底层 Tab（从「关于」进来时不会跳回「路线」）。
    val backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current

    // 空库 CTA「导入 GPX」：与 MapScreen / RouteSimulationScreen 走同一条导入链路。
    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                mapViewModel.importGpx(context, it, "导入GPX路线") { count ->
                    if (count >= 2) {
                        Toast.makeText(context, "GPX 路线导入成功（$count 个点）", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            "该 GPX 只解析出 $count 个点，至少需要 2 个点才能构成路线",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.onFailure { e ->
                Diag.w("RouteLibrary", "GPX 导入失败", e)
                Toast.makeText(context, "导入失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 导出 / 分享 GPX。
    //
    // 不能把 GPX 原文放进 Intent.EXTRA_TEXT：exportRouteToGpx 每点约 82 字节且无上限，
    // 1 万点的轨迹约 820 KB，经 Binder 打包成 UTF-16 后约 1.6 MB，超过 Binder 事务上限，
    // startActivity 会抛 TransactionTooLargeException。改为写入 cacheDir 并经 FileProvider 分享。
    fun shareRouteAsGpx(route: Route) {
        runCatching {
            val gpxXml = mapViewModel.exportRouteToGpx(route)
            val safeName = route.name
                .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
                .take(60)
                .ifBlank { "route" }
            val dir = File(context.cacheDir, "gpx").apply { mkdirs() }
            val file = File(dir, "$safeName.gpx")
            file.writeText(gpxXml)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_SUBJECT, "$safeName.gpx")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "导出 GPX 路线")
            )
        }.onFailure { e ->
            Diag.w("RouteLibrary", "GPX 导出失败", e)
            Toast.makeText(
                context,
                "导出失败：${e.message ?: "未知错误"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // 本页专属模糊源：本页是「叠加层」，若只继承 AppNavigation 的全局源，
    // 会采样到身后内容区的地图（而本页自己有铺底背景），观感是错的。
    // 所以自建一个源，由本页 AppBackground 当源层，卡片玻璃采样它。
    val pageHaze = remember { dev.chrisbanes.haze.HazeState() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AppBackground(hazeState = pageHaze)

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
        ) {
        // =====================================================================
        // 0. 覆盖层导航条 —— 显式返回按钮（此前只有系统 Back 可用）
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .minTouchTarget()
                    .clip(CircleShape)
                    .bouncyClickable {
                        backDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = IosColors.Label,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (showTrackScreen) "关闭轨迹库" else "轨迹录制",
                style = IosTypography.Footnote,
                fontWeight = FontWeight.SemiBold,
                color = IosColors.SystemBlue,
                modifier = Modifier
                    .minTouchTarget()
                    .bouncyClickable { showTrackScreen = !showTrackScreen }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // =====================================================================
        // 1. Page Head —— 与其他页面共用同一排版基准
        // =====================================================================
        IosPageHead(
            kicker = "FAKE GPS",
            title = "路线库",
            badgeText = "${savedRoutes.size} 条路线",
            chipText = BuildConfig.VERSION_NAME
        )

        // =====================================================================
        // 2. 搜索框 —— 复用设计系统 IosSearchField，清除按钮 48dp 触区
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "搜索路线名称...",
                modifier = Modifier.weight(1f),
                style = IosSearchFieldStyle.STANDARD
            )

            // 固定占位：搜索框宽度不随清除按钮出现/消失而跳变。
            Box(
                modifier = Modifier
                    .minTouchTarget()
                    .clip(CircleShape)
                    .bouncyClickable(enabled = searchQuery.isNotEmpty()) { searchQuery = "" },
                contentAlignment = Alignment.Center
            ) {
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除搜索",
                        tint = IosColors.SecondaryLabel,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =====================================================================
        // 3. Route List / Empty State
        // =====================================================================
        when {
            // 空库：给「去规划路线 / 导入 GPX」两条出路
            savedRoutes.isEmpty() -> LibraryEmptyState(
                icon = Icons.Default.Route,
                title = "暂无保存的路线",
                subtitle = "在【路线】标签手动画线后点「保存路线」，或直接导入 GPX 文件",
                bottomInset = bottomInset,
                primaryText = "去规划路线",
                primaryIcon = Icons.Default.EditLocationAlt,
                onPrimary = { onRouteSelected() },
                secondaryText = "导入 GPX",
                secondaryIcon = Icons.Default.FileOpen,
                onSecondary = { gpxPickerLauncher.launch(arrayOf("*/*")) }
            )

            // 搜索无匹配：与空库是两种状态，给「清除搜索」
            filteredRoutes.isEmpty() -> LibraryEmptyState(
                icon = Icons.Default.SearchOff,
                title = "未找到匹配的路线",
                subtitle = "路线库里没有名称包含「${searchQuery}」的路线",
                bottomInset = bottomInset,
                primaryText = "清除搜索",
                primaryIcon = Icons.Default.Close,
                onPrimary = { searchQuery = "" }
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomInset)
            ) {
                items(filteredRoutes, key = { it.id }) { route ->
                    val isCurrent = selectedRoute?.id == route.id
                    var menuExpanded by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(
                                isLiquidGlass = LocalLiquidGlassEnabled.current,
                                shape = AppShapes.card,
                                elevation = 8.dp,
                                borderWidth = if (isCurrent) 1.5.dp else 1.0.dp,
                                hazeState = pageHaze
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Title + Date + Selection Capsule
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = route.name,
                                        style = IosTypography.Headline,
                                        color = IosColors.Label,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "存档时间: ${dateFormatter.format(Date(route.createdAt))}",
                                        style = IosTypography.Footnote,
                                        color = IosColors.SecondaryLabel
                                    )
                                }

                                if (isCurrent) {
                                    Spacer(Modifier.width(8.dp))
                                    IosStatusBadge(text = "当前载入", tone = IosBadgeTone.RUNNING)
                                }
                                if (route.mode != RouteMode.ONE_WAY) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = IosColors.SystemBlue.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = route.mode.label,
                                            style = IosTypography.Caption2,
                                            fontWeight = FontWeight.SemiBold,
                                            color = IosColors.SystemBlue,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (route.sourceTrackId != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = IosColors.TertiarySystemFill
                                    ) {
                                        Text(
                                            text = "轨迹",
                                            style = IosTypography.Caption2,
                                            fontWeight = FontWeight.SemiBold,
                                            color = IosColors.SecondaryLabel,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Middle Row: Metrics Badges —— 距离 + 航点数
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = IosColors.TertiarySystemFill
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Place,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = IosColors.SystemBlue
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "${"%.2f".format(route.totalDistanceKm)} km",
                                            style = IosTypography.Caption1,
                                            color = IosColors.Label,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = IosColors.TertiarySystemFill
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = IosColors.SystemGreen
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "${route.waypoints.size} 航点",
                                            style = IosTypography.Caption1,
                                            color = IosColors.Label,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = IosColors.Separator.copy(alpha = 0.5f), thickness = 0.5.dp)
                            Spacer(Modifier.height(10.dp))

                            // Bottom Row: 「更多」菜单（重命名 / 导出 GPX / 删除）+ 载入主 CTA
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .minTouchTarget()
                                            .clip(CircleShape)
                                            .bouncyClickable { menuExpanded = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "更多操作",
                                            tint = IosColors.SecondaryLabel,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                        containerColor = IosColors.SecondaryGroupedBackground
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("重命名", style = IosTypography.Subheadline, color = IosColors.Label) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null, tint = IosColors.SystemBlue, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                routeToRename = route
                                                renameInput = route.name
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("导出 GPX / 分享", style = IosTypography.Subheadline, color = IosColors.Label) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Share, contentDescription = null, tint = IosColors.SystemBlue, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                shareRouteAsGpx(route)
                                            }
                                        )
                                        RouteMode.entries.filter { it != route.mode }.forEach { targetMode ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "运行方式改为「${targetMode.label}」",
                                                        style = IosTypography.Subheadline,
                                                        color = IosColors.Label
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = IosColors.SystemBlue, modifier = Modifier.size(18.dp))
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    if (targetMode == RouteMode.LOOP && route.closingGapMeters > RouteSimulator.LOOP_MAX_GAP_M) {
                                                        Toast.makeText(
                                                            context,
                                                            "首尾相距 ${route.closingGapMeters.toInt()} m，超过 ${RouteSimulator.LOOP_MAX_GAP_M.toInt()} m，无法成环",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    } else {
                                                        mapViewModel.updateRouteMode(route, targetMode)
                                                    }
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("删除路线", style = IosTypography.Subheadline, color = IosDanger) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = IosDanger, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                routeToDelete = route
                                            }
                                        )
                                    }
                                }

                                IosPrimaryButton(
                                    text = if (isCurrent) "正在使用" else "载入路线",
                                    onClick = {
                                        mapViewModel.selectRoute(route)
                                        onRouteSelected()
                                    },
                                    modifier = Modifier.weight(1f),
                                    containerColor = if (isCurrent) IosColors.SystemGreen else IosColors.SystemBlue,
                                    icon = if (isCurrent) Icons.Default.Check else Icons.Default.PlayArrow,
                                    height = 48.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // =========================================================================
    // 短确认 / 短输入弹窗 —— 统一 24dp 圆角
    // =========================================================================
    routeToRename?.let { route ->
        AlertDialog(
            onDismissRequest = { routeToRename = null },
            title = {
                Text(
                    text = "重命名路线",
                    style = IosTypography.Headline,
                    color = IosColors.Label,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("路线新名称", style = IosTypography.Caption1) },
                    singleLine = true,
                    shape = RoundedCornerShape(13.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IosColors.SystemBlue,
                        unfocusedBorderColor = IosColors.Separator
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mapViewModel.renameRoute(route, renameInput)
                        routeToRename = null
                    },
                    // 与 ViewModel 的校验保持一致：renameRoute 对 isBlank 直接 return，
                    // 按钮不禁用会让用户以为改名成功而实际什么都没发生。
                    enabled = renameInput.isNotBlank(),
                    modifier = Modifier.minTouchTarget()
                ) {
                    Text("确定", color = IosColors.SystemBlue, fontWeight = FontWeight.Bold, style = IosTypography.Headline)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { routeToRename = null },
                    modifier = Modifier.minTouchTarget()
                ) {
                    Text("取消", color = IosColors.SecondaryLabel, style = IosTypography.Headline)
                }
            },
            containerColor = IosColors.SecondaryGroupedBackground,
            shape = RoundedCornerShape(24.dp)
        )
    }

    routeToDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            title = {
                Text(
                    text = "确认删除路线？",
                    style = IosTypography.Headline,
                    color = IosColors.Label,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "确定要删除路线「${route.name}」吗？此操作无法撤销。",
                    style = IosTypography.Subheadline,
                    color = IosColors.SecondaryLabel
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mapViewModel.deleteRoute(route.id)
                        routeToDelete = null
                    },
                    modifier = Modifier.minTouchTarget()
                ) {
                    Text("删除", color = IosDanger, fontWeight = FontWeight.Bold, style = IosTypography.Headline)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { routeToDelete = null },
                    modifier = Modifier.minTouchTarget()
                ) {
                    Text("取消", color = IosColors.SecondaryLabel, style = IosTypography.Headline)
                }
            },
            containerColor = IosColors.SecondaryGroupedBackground,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 轨迹录制叠加层：与路线库同屏打开，关闭后回到列表
    if (showTrackScreen) {
        TrackRecordScreen(
            trackViewModel = trackViewModel,
            onClose = { showTrackScreen = false },
            onRouteCreated = { route ->
                mapViewModel.selectRoute(route)
                showTrackScreen = false
                onRouteSelected()
            }
        )
    }
}

/**
 * 路线库空态脚手架。
 *
 * 空库与「搜索无匹配」是两种不同状态，文案、图标与 CTA 各不相同，
 * 这里只共用视觉骨架，不合并语义。
 *
 * 内容可滚动：窄屏或系统大字号下，72dp 图标 + 标题 + 说明 + 两个按钮
 * 会超出可视高度，滚动保证按钮始终可达。
 */
@Composable
private fun LibraryEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    bottomInset: Dp,
    primaryText: String,
    primaryIcon: ImageVector,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    secondaryIcon: ImageVector? = null,
    onSecondary: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
                .padding(bottom = bottomInset),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = IosColors.SecondaryGroupedBackground,
                modifier = Modifier.size(72.dp),
                border = BorderStroke(0.5.dp, IosColors.Separator)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = IosColors.SecondaryLabel
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                style = IosTypography.Headline,
                color = IosColors.Label,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = subtitle,
                style = IosTypography.Footnote,
                color = IosColors.SecondaryLabel,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.widthIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IosPrimaryButton(
                    text = primaryText,
                    onClick = onPrimary,
                    icon = primaryIcon,
                    height = 48.dp
                )
                if (secondaryText != null && onSecondary != null) {
                    IosGhostButton(
                        text = secondaryText,
                        onClick = onSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        icon = secondaryIcon,
                        height = 48.dp
                    )
                }
            }
        }
    }
}
