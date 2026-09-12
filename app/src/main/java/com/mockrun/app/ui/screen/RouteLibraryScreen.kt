package com.mockrun.app.ui.screen

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.BuildConfig
import com.mockrun.app.domain.model.Route
import com.mockrun.app.ui.theme.*
import com.mockrun.app.ui.viewmodel.MapViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteLibraryScreen(
    mapViewModel: MapViewModel,
    onRouteSelected: () -> Unit
) {
    val context = LocalContext.current
    val savedRoutes by mapViewModel.savedRoutes.collectAsState()
    val selectedRoute by mapViewModel.selectedRoute.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var routeToRename by remember { mutableStateOf<Route?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var routeToDelete by remember { mutableStateOf<Route?>(null) }

    val filteredRoutes = remember(savedRoutes, searchQuery) {
        if (searchQuery.isBlank()) savedRoutes
        else savedRoutes.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
        // =====================================================================
        // 1. Apple Large Title Navigation Header
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "FAKE GPS",
                    style = IosTypography.Caption2,
                    color = IosColors.SecondaryLabel,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "路线库",
                    style = IosTypography.LargeTitle,
                    color = IosColors.Label
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = IosColors.SystemBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${savedRoutes.size} 条路线",
                        color = IosColors.SystemBlue,
                        style = IosTypography.Caption1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = IosColors.SystemBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        color = IosColors.SystemBlue,
                        style = IosTypography.Caption1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // =====================================================================
        // 2. iOS HIG Native Search Capsule Bar
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(IosColors.TertiarySystemFill)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = IosColors.SecondaryLabel,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "搜索路线名称...",
                            style = IosTypography.Body.copy(fontSize = 14.sp),
                            color = IosColors.TertiaryLabel
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = IosColors.Label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(IosColors.SystemBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(IosColors.SecondaryLabel.copy(alpha = 0.3f))
                            .bouncyClickable { searchQuery = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // =====================================================================
        // 3. Route List / Empty State
        // =====================================================================
        if (filteredRoutes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = IosColors.SecondaryGroupedBackground,
                        modifier = Modifier.size(72.dp),
                        border = BorderStroke(0.5.dp, IosColors.Separator)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = IosColors.SecondaryLabel
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "未找到匹配的路线" else "暂无保存的路线",
                        style = IosTypography.Headline,
                        color = IosColors.Label
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "可在【地图】标签上手动画线，或导入 GPX 文件后点击保存",
                        style = IosTypography.Footnote,
                        color = IosColors.SecondaryLabel
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp)
            ) {
                items(filteredRoutes, key = { it.id }) { route ->
                    val isCurrent = selectedRoute?.id == route.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(
                                isLiquidGlass = true,
                                shape = RoundedCornerShape(16.dp),
                                elevation = 6.dp
                            ),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = if (isCurrent) BorderStroke(1.5.dp, IosColors.SystemBlue) else null
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
                                    Surface(
                                        color = IosColors.SystemGreen.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            "当前载入",
                                            color = IosColors.SystemGreen,
                                            style = IosTypography.Caption1,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Middle Row: Metrics Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
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
                                    shape = RoundedCornerShape(8.dp),
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

                            // Bottom Row: Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Utility Icons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Rename Button
                                    Surface(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .bouncyClickable {
                                                routeToRename = route
                                                renameInput = route.name
                                            },
                                        shape = CircleShape,
                                        color = IosColors.TertiarySystemFill
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "重命名",
                                                tint = IosColors.SystemBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Share / Export GPX Button
                                    Surface(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .bouncyClickable {
                                                val gpxXml = mapViewModel.exportRouteToGpx(route)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "${route.name}.gpx")
                                                    putExtra(Intent.EXTRA_TEXT, gpxXml)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "导出 GPX 路线"))
                                            },
                                        shape = CircleShape,
                                        color = IosColors.TertiarySystemFill
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "分享/导出 GPX",
                                                tint = IosColors.SystemBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Delete Button
                                    Surface(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .bouncyClickable { routeToDelete = route },
                                        shape = CircleShape,
                                        color = IosColors.SystemRed.copy(alpha = 0.12f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "删除路线",
                                                tint = IosColors.SystemRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Load Route Primary Action Button
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .bouncyClickable {
                                            mapViewModel.selectRoute(route)
                                            onRouteSelected()
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCurrent) IosColors.SystemGreen else IosColors.SystemBlue
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrent) Icons.Default.Check else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isCurrent) "正在使用" else "载入路线",
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            style = IosTypography.Subheadline.copy(fontSize = 13.sp)
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

    // =========================================================================
    // Apple HIG Styled Modals
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
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IosColors.SystemBlue,
                        unfocusedBorderColor = IosColors.Separator
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    mapViewModel.renameRoute(route, renameInput)
                    routeToRename = null
                }) {
                    Text("确定", color = IosColors.SystemBlue, fontWeight = FontWeight.Bold, style = IosTypography.Headline)
                }
            },
            dismissButton = {
                TextButton(onClick = { routeToRename = null }) {
                    Text("取消", color = IosColors.SecondaryLabel, style = IosTypography.Headline)
                }
            },
            containerColor = IosColors.SecondaryGroupedBackground,
            shape = RoundedCornerShape(18.dp)
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
                    }
                ) {
                    Text("删除", color = IosColors.SystemRed, fontWeight = FontWeight.Bold, style = IosTypography.Headline)
                }
            },
            dismissButton = {
                TextButton(onClick = { routeToDelete = null }) {
                    Text("取消", color = IosColors.SecondaryLabel, style = IosTypography.Headline)
                }
            },
            containerColor = IosColors.SecondaryGroupedBackground,
            shape = RoundedCornerShape(18.dp)
        )
    }
}
}