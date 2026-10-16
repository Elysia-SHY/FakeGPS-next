package com.mockrun.app.feature.settings


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.BuildConfig
import com.mockrun.app.core.data.repository.RemoteVersionInfo
import com.mockrun.app.core.data.repository.VersionSyncManager
import com.mockrun.app.core.data.repository.DownloadStatus
import com.mockrun.app.core.data.repository.VersionSyncStatus
import com.mockrun.app.core.designsystem.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val BUILTIN_CHANGELOG = """【FakeGPS-next v2.1.2 更新日志】

1. 💧 修复定位页 / 路线页毛玻璃全面失效（严重）：
   - 这两页此前没有可被 Haze 捕获的 Compose 源层（底层是 OSMDroid 原生 View），
     全部玻璃卡片静默退化为不透明色块，底部悬浮栏同样无模糊；
   - 现叠加一层 Compose 绘制的 Aurora 雾面作为模糊源（上下厚、中间薄，
     中央地图区保持干净），底部悬浮 Tab 栏接入页面内容源，全部恢复真实高斯模糊。

2. 🎯 环境指纹采集真机物理定位优先：
   - 采集弹窗默认直取真机真实物理定位，不再误填地图选定模拟点；
   - 弹窗内置「获取本机真实位置」实时校准与「一键停止模拟」，无需退回首页重置。

3. ☁️ 在线更新服务无官网化多源升级：
   - 「蓝奏云（南咒云）直链自动解析 + 多 CDN 静态清单 + 镜像兜底」多级容灾；
   - 下载完成后执行 SHA-256 哈希与 APK 签名证书双重完整性校验。

4. 📡 环境指纹采集与 5G NR 回放：打通 5G NR 36-bit NCI 采集与回放。"""

@Composable
fun AboutScreen(
    isLiquidGlass: Boolean,
    isTablet: Boolean = false,
    onToggleLiquidGlass: (Boolean) -> Unit,
    onNavigateToLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val syncState by VersionSyncManager.status
    val downloadState by VersionSyncManager.downloadStatus
    var showChangelogDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    // =========================================================================
    // 状态快照 —— 必须在组合期取一次，之后全程只用这份快照。
    //
    // 为什么：syncState / downloadState 都是 `by` 委托的 State，由
    // VersionSyncManager 在 CoroutineScope(Dispatchers.IO) 上写入。而
    // `when (sync) { is X -> ... syncState as X ... }` 这种写法是
    // **两次独立读取** —— 判定与强转之间状态可能已经翻转，强转随即抛
    // ClassCastException，崩在组合阶段。
    //
    // 本页一打开就会 checkForUpdates()，状态必然经历
    // Idle -> Checking -> UpToDate/HasUpdate 的连续变化，正好撞上这个竞态。
    // 这是「打开关于页就闪退」的直接原因。
    //
    // 快照只在本次重组内有效；状态变化会触发重组，快照随之刷新，
    // 因此不会丢失更新。
    // =========================================================================
    val sync = syncState
    val dl = downloadState

    val hasCustomWallpaper by BackgroundThemeManager.hasCustomWallpaper
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // setCustomWallpaper 现在是 suspend 且内部切到 IO 调度器：
            // 该回调本身跑在主线程上，直接复制 3-25 MB 的照片会卡住 UI。
            scope.launch {
                val success = BackgroundThemeManager.setCustomWallpaper(context, uri)
                Toast.makeText(
                    context,
                    if (success) "🖼️ 自定义壁纸已设置并在全应用生效" else "⚠️ 壁纸读取失败，请重试",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Auto-sync version & background preferences on screen launch
    LaunchedEffect(Unit) {
        BackgroundThemeManager.initialize(context)
        VersionSyncManager.checkForUpdates(force = false)
    }

    // Changelog Dialog
    showChangelogDialog?.let { (dialogTitle, dialogContent) ->
        ChangelogDialog(
            dialogTitle = dialogTitle,
            dialogContent = dialogContent,
            onDismissRequest = { showChangelogDialog = null }
        )
    }

    // 详情 sheet —— 品牌介绍 / 核心架构 / 官网 / 联系反馈 / 开源致谢与许可。
    // 这些是介绍性内容，折叠进 sheet 后仍全部可达；设置项则全部提到页面顶层。
    if (showDetailsSheet) {
        AboutDetailsSheet(onDismiss = { showDetailsSheet = false })
    }

    // 本页专属模糊源：AppBackground 铺底当源层，卡片玻璃采样它 ——
    // 两者是同一个 Box 下的兄弟节点，不会自引用。
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = ScreenInsets.bottomInset(extra = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // =====================================================================
            // 0. 品牌标识（精简 Hero）—— 完整品牌介绍已移入「关于与致谢」详情 sheet
            // =====================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = AppShapes.card,
                        elevation = 8.dp,
                        hazeState = pageHaze
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        IosColors.SystemBlue,
                                        Color(0xFF5A6BFF),
                                        IosColors.Violet
                                    )
                                )
                            )
                            .border(
                                1.5.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.65f),
                                        Color.White.copy(alpha = 0.15f)
                                    )
                                ),
                                RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FakeGPS-next",
                            style = IosTypography.Title2,
                            color = IosColors.Label
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = AppShapes.pill,
                                color = IosColors.SystemBlue.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = BuildConfig.VERSION_NAME,
                                    style = IosTypography.Mono13,
                                    color = IosColors.BrandSoft,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "构建时间: ${BuildConfig.BUILD_TIME}",
                                fontSize = 11.sp,
                                color = IosColors.SecondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // 2. 视觉渲染风格
            // =====================================================================
            IosSectionHeader("视觉渲染风格")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = AppShapes.card,
                        elevation = 8.dp,
                        hazeState = pageHaze
                    )
            ) {
                Column(modifier = Modifier.padding(AppSpacing.s4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(AppShapes.button)
                                    .background(
                                        if (isLiquidGlass) IosColors.SystemBlue.copy(0.18f) else IosColors.SystemFill
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isLiquidGlass) Icons.Default.WaterDrop else Icons.Default.BlurOff,
                                    contentDescription = null,
                                    tint = if (isLiquidGlass) IosColors.SystemBlue else IosColors.SecondaryLabel,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "液态毛玻璃渲染效果",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IosColors.Label
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isLiquidGlass) "半透明磨砂 · 镜面光泽 · 柔和环境光" else "纯色材质 · 超低 GPU 负荷 · 极致省电",
                                    fontSize = 11.5.sp,
                                    color = IosColors.SecondaryLabel
                                )
                            }
                        }

                        Switch(
                            checked = isLiquidGlass,
                            onCheckedChange = { next ->
                                LiquidGlassDefaults.setEnabled(context, next)
                                onToggleLiquidGlass(next)
                                Toast.makeText(
                                    context,
                                    if (next) "💧 已开启液态毛玻璃视觉效果" else "⬛ 已切换为经典纯色节能模式",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IosColors.SystemBlue
                            )
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(
                        color = IosColors.Line,
                        thickness = 0.8.dp
                    )
                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "💡 视觉设计参考了 LocationSpoofer 与 Orb 的折射高光光泽。在开启状态下，浮动底栏与抽屉将呈现通透细腻的质感。",
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = IosColors.SecondaryLabel
                    )
                }
            }

            // =====================================================================
            // 3. 个性化背景壁纸
            // =====================================================================
            IosSectionHeader("个性化背景壁纸")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = AppShapes.card,
                        elevation = 8.dp,
                        hazeState = pageHaze
                    )
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(AppShapes.icon)
                                    .background(if (hasCustomWallpaper) IosColors.SystemGreen.copy(alpha = 0.18f) else IosColors.SystemPurple.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (hasCustomWallpaper) Icons.Default.AddPhotoAlternate else Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = if (hasCustomWallpaper) IosColors.SystemGreen else IosColors.SystemPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (hasCustomWallpaper) "已应用自选相册壁纸" else "默认纯色背景",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IosColors.Label
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (hasCustomWallpaper) "选择照片作为全应用底衬，卡片将通透毛玻璃折射" else "纯黑 / 纯白纯色底衬，卡片呈通透毛玻璃折射",
                                    fontSize = 11.5.sp,
                                    color = IosColors.SecondaryLabel
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = IosColors.LineSoft)

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hasCustomWallpaper) {
                                TextButton(
                                    onClick = {
                                        BackgroundThemeManager.clearCustomWallpaper(context)
                                        Toast.makeText(context, "已恢复为默认纯色背景", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("恢复默认", fontSize = 12.5.sp, color = IosColors.SystemRed)
                                }
                            }
                            Button(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasCustomWallpaper) IosColors.SystemGreen else IosColors.SystemBlue
                                ),
                                shape = AppShapes.button,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (hasCustomWallpaper) "更换壁纸" else "从相册选取壁纸",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 4. 数据管理与路线库
            // =====================================================================
            IosSectionHeader("数据管理与路线库")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = AppShapes.card,
                        elevation = 8.dp,
                        hazeState = pageHaze
                    )
                    .bouncyClickable { onNavigateToLibrary() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.s4, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(AppShapes.icon)
                                .background(IosColors.SystemPurple.copy(0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = IosColors.SystemPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "已收藏路线与航点库",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IosColors.Label
                            )
                            Text(
                                text = "管理历史规划、导入/导出 GPX 文件",
                                fontSize = 11.5.sp,
                                color = IosColors.SecondaryLabel
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = IosColors.SecondaryLabel,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // =====================================================================
            // 5. 版本与云端同步
            // =====================================================================
            IosSectionHeader("版本与云端同步")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = AppShapes.card,
                        elevation = 8.dp,
                        hazeState = pageHaze
                    )
            ) {
                Column(modifier = Modifier.padding(AppSpacing.s4)) {
                    val isChecking = sync is VersionSyncStatus.Checking

                    // 仅在检查更新期间运行旋转动画，避免静止状态下每帧触发重组重绘
                    val angle = if (isChecking) {
                        val infiniteTransition = rememberInfiniteTransition(label = "spin")
                        val animatedAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "spinAngle"
                        )
                        animatedAngle
                    } else {
                        0f
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(AppShapes.icon)
                                    .background(
                                        when (sync) {
                                            is VersionSyncStatus.HasUpdate -> IosColors.SystemOrange.copy(0.18f)
                                            is VersionSyncStatus.UpToDate -> IosColors.SystemGreen.copy(0.18f)
                                            is VersionSyncStatus.Error -> IosColors.SystemRed.copy(0.18f)
                                            else -> IosColors.SystemBlue.copy(0.18f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (sync) {
                                        is VersionSyncStatus.HasUpdate -> Icons.Default.SystemUpdate
                                        is VersionSyncStatus.UpToDate -> Icons.Default.CheckCircle
                                        is VersionSyncStatus.Error -> Icons.Default.CloudOff
                                        else -> Icons.Default.CloudSync
                                    },
                                    contentDescription = null,
                                    tint = when (sync) {
                                        is VersionSyncStatus.HasUpdate -> IosColors.SystemOrange
                                        is VersionSyncStatus.UpToDate -> IosColors.SystemGreen
                                        is VersionSyncStatus.Error -> IosColors.SystemRed
                                        else -> IosColors.SystemBlue
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "云端版本自动同步",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IosColors.Label
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = when (sync) {
                                        is VersionSyncStatus.Checking -> "正在同步云端最新版本..."
                                        is VersionSyncStatus.UpToDate -> "已同步 · 当前安装即为最新版"
                                        is VersionSyncStatus.HasUpdate -> "检测到新版本可用"
                                        is VersionSyncStatus.Error -> (sync as VersionSyncStatus.Error).message
                                        is VersionSyncStatus.Idle -> "自动同步已就绪"
                                    },
                                    fontSize = 11.5.sp,
                                    color = when (sync) {
                                        is VersionSyncStatus.HasUpdate -> IosColors.SystemOrange
                                        is VersionSyncStatus.UpToDate -> IosColors.SystemGreen
                                        is VersionSyncStatus.Error -> IosColors.SystemRed
                                        else -> IosColors.SecondaryLabel
                                    }
                                )
                            }
                        }

                        // Sync / Refresh Button
                        Box(
                            modifier = Modifier
                                .minTouchTarget()
                                .bouncyClickable(enabled = !isChecking) {
                                    scope.launch {
                                        val res = VersionSyncManager.checkForUpdates(force = true)
                                        when (res) {
                                            is VersionSyncStatus.UpToDate -> Toast.makeText(context, "已是最新版本 (${res.info.versionName})", Toast.LENGTH_SHORT).show()
                                            is VersionSyncStatus.HasUpdate -> Toast.makeText(context, "发现新版本: ${res.info.versionName}", Toast.LENGTH_SHORT).show()
                                            is VersionSyncStatus.Error -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            else -> {}
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(IosColors.SystemFill),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "刷新同步",
                                    tint = IosColors.Label,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .then(if (isChecking) Modifier.rotate(angle) else Modifier)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = IosColors.Line)
                    Spacer(Modifier.height(12.dp))

                    // Version comparison matrix
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "当前安装版本",
                                fontSize = 11.5.sp,
                                color = IosColors.SecondaryLabel
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = IosColors.Label
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = IosColors.SecondaryLabel.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "云端最新版本",
                                fontSize = 11.5.sp,
                                color = IosColors.SecondaryLabel
                            )
                            Spacer(Modifier.height(2.dp))
                            val remoteName = when (sync) {
                                is VersionSyncStatus.UpToDate -> (sync as VersionSyncStatus.UpToDate).info.versionName
                                is VersionSyncStatus.HasUpdate -> (sync as VersionSyncStatus.HasUpdate).info.versionName
                                is VersionSyncStatus.Checking -> "检测中..."
                                else -> BuildConfig.VERSION_NAME
                            }
                            Text(
                                text = remoteName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = when (sync) {
                                    is VersionSyncStatus.HasUpdate -> IosColors.SystemOrange
                                    is VersionSyncStatus.UpToDate -> IosColors.SystemGreen
                                    else -> IosColors.SecondaryLabel
                                }
                            )
                        }
                    }

                    // Has update banner
                    if (sync is VersionSyncStatus.HasUpdate) {
                        val info = (sync as VersionSyncStatus.HasUpdate).info
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.button,
                            color = IosColors.SystemOrange.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IosColors.SystemOrange.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Celebration, contentDescription = null, tint = IosColors.SystemOrange, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = "发现新版本 ${info.versionName}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IosColors.Label
                                        )
                                    }
                                    if (info.releaseDate.isNotBlank()) {
                                        Text(
                                            text = info.releaseDate,
                                            fontSize = 11.5.sp,
                                            color = IosColors.SecondaryLabel
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                // In-App Download Status & Action Rows
                                //
                                // 先取一次快照再 when —— downloadState 是 by 委托的 State，
                                // 由 VersionSyncManager 在 CoroutineScope(Dispatchers.IO) 上写入。
                                // 直接写 `when (downloadState)` + `downloadState as X`
                                // 是两次独立读取：若期间状态被改写，强转会抛 ClassCastException
                                // 并崩在组合阶段。
                                val dlState = dl
                                when (dlState) {
                                    is DownloadStatus.Downloading -> {
                                        val dl = dlState
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            LinearProgressIndicator(
                                                progress = { dl.progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = IosColors.SystemOrange,
                                                trackColor = IosColors.SystemOrange.copy(alpha = 0.2f)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${(dl.progress * 100).toInt()}% (${formatMb(dl.downloadedBytes)} / ${formatMb(dl.totalBytes)})",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = IosColors.Label.copy(alpha = 0.9f)
                                                )
                                                Text(
                                                    text = formatSpeed(dl.speedBytesPerSec),
                                                    fontSize = 11.5.sp,
                                                    color = IosColors.SystemOrange,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { VersionSyncManager.cancelDownload() },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = AppShapes.button
                                            ) {
                                                Text("取消下载", fontSize = 12.sp, color = IosColors.SystemRed)
                                            }
                                        }
                                    }

                                    is DownloadStatus.Success -> {
                                        val file = dlState.file
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IosColors.SystemGreen, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "新版本已下载就绪 (${formatMb(file.length())})",
                                                    fontSize = 12.sp,
                                                    color = IosColors.SystemGreen,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        VersionSyncManager.startDownload(context, info)
                                                    },
                                                    modifier = Modifier.weight(1f).height(48.dp),
                                                    shape = AppShapes.button
                                                ) {
                                                    Text("重新下载", fontSize = 12.sp)
                                                }
                                                Button(
                                                    onClick = {
                                                        VersionSyncManager.installApk(context, file)
                                                    },
                                                    modifier = Modifier.weight(1.3f).height(48.dp),
                                                    shape = AppShapes.button,
                                                    colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemGreen)
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("立即安装", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }

                                    is DownloadStatus.Error -> {
                                        val errMsg = dlState.message
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "下载失败: $errMsg",
                                                fontSize = 11.5.sp,
                                                color = IosColors.SystemRed
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { VersionSyncManager.startDownload(context, info) },
                                                    modifier = Modifier.weight(1f).height(48.dp),
                                                    shape = AppShapes.button,
                                                    colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemOrange)
                                                ) {
                                                    Text("重试在线下载", fontSize = 12.sp, color = Color.White)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f).height(48.dp),
                                                    shape = AppShapes.button
                                                ) {
                                                    Text("浏览器下载", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }

                                    DownloadStatus.Idle -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    showChangelogDialog = Pair("${info.versionName} 更新说明", info.releaseNotes)
                                                },
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                shape = AppShapes.button,
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = IosColors.Label)
                                            ) {
                                                Text("更新说明", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }

                                            Button(
                                                onClick = {
                                                    VersionSyncManager.startDownload(context, info)
                                                },
                                                modifier = Modifier.weight(1.3f).height(48.dp),
                                                shape = AppShapes.button,
                                                colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemOrange)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                Spacer(Modifier.width(5.dp))
                                                Text("在线下载更新", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .minTouchTarget()
                                                    .clip(AppShapes.chip)
                                                    .bouncyClickable {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "备用：在浏览器中直接下载",
                                                    fontSize = 11.sp,
                                                    color = IosColors.SystemBlue
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Up to date banner with view changelog button
                    if (sync is VersionSyncStatus.UpToDate || sync is VersionSyncStatus.Idle) {
                        val checkedAt = (sync as? VersionSyncStatus.UpToDate)?.checkedAt ?: System.currentTimeMillis()
                        val timeStr = remember(checkedAt) {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(checkedAt))
                        }
                        val remoteNotes = (sync as? VersionSyncStatus.UpToDate)?.info?.releaseNotes?.takeIf { it.isNotBlank() } ?: BUILTIN_CHANGELOG

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.button)
                                .background(IosColors.SystemGreen.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = IosColors.SystemGreen, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "已是最新版本 · $timeStr 已同步",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = IosColors.SystemGreen
                                )
                            }

                            TextButton(
                                onClick = {
                                    showChangelogDialog = Pair("${BuildConfig.VERSION_NAME} 最新特性日志", remoteNotes)
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("查看日志", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = IosColors.SystemBlue)
                            }
                        }
                    }

                    // Error banner
                    if (sync is VersionSyncStatus.Error) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.button)
                                .background(IosColors.SystemRed.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = IosColors.SystemRed, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "网络离线或同步受阻",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = IosColors.SystemRed
                                )
                            }

                            TextButton(
                                onClick = {
                                    showChangelogDialog = Pair("${BuildConfig.VERSION_NAME} 本地特性说明", BUILTIN_CHANGELOG)
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("本地日志", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = IosColors.SystemBlue)
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 6. 设备环境与运行诊断
            // =====================================================================
            IosSectionHeader("设备环境与运行诊断")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = AppShapes.card,
                        elevation = 8.dp,
                        hazeState = pageHaze
                    )
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                    val osInfo = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

                    DiagnosticRow(label = "设备型号", value = deviceModel)
                    HorizontalDivider(color = IosColors.LineSoft)
                    DiagnosticRow(label = "系统版本", value = osInfo)
                    HorizontalDivider(color = IosColors.LineSoft)
                    DiagnosticRow(label = "指令集架构", value = abi)
                    HorizontalDivider(color = IosColors.LineSoft)
                    DiagnosticRow(label = "构建变体", value = "${BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }} (R8 混淆优化)")

                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.button,
                        color = IosColors.SystemBlue.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = IosColors.SystemBlue, modifier = Modifier.size(16.dp))
                            Text(
                                text = "LSPosed 模块建议仅勾选「系统框架 (system)」，各分流目标应用无需勾选即可获得底层路由支持。",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = IosColors.SecondaryLabel
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // 7. 更多与关于
            // =====================================================================
            IosSectionHeader("更多与关于")

            // 详情 sheet 入口 —— 触区 >= 56dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        isLiquidGlass = isLiquidGlass,
                        shape = AppShapes.card,
                        elevation = 8.dp,
                        hazeState = pageHaze
                    )
                    .bouncyClickable { showDetailsSheet = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.s4, vertical = 14.dp)
                        .heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(AppShapes.icon)
                                .background(IosColors.SystemBlue.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = IosColors.SystemBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "关于与致谢",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IosColors.Label
                            )
                            Text(
                                text = "品牌介绍 · 架构特性 · 官网 · 联系 · 开源许可",
                                fontSize = 11.5.sp,
                                color = IosColors.SecondaryLabel
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = IosColors.SecondaryLabel,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutDetailsSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = IosColors.SecondaryGroupedBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "关于 FakeGPS-next",
                        style = IosTypography.Title2,
                        color = IosColors.Label
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "品牌介绍 · 架构特性 · 官网 · 联系 · 开源许可",
                        style = IosTypography.Footnote,
                        color = IosColors.SecondaryLabel
                    )
                }
            }

            // =====================================================================
            // S1. 品牌介绍
            // =====================================================================
            IosSectionHeader("品牌介绍")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.card,
                color = IosColors.Surface2
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "系统框架底层分流 · 物理运动学拟真 · 动态多星座 GNSS 星历合成",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = IosColors.Label
                    )
                    Text(
                        text = "构建时间: ${BuildConfig.BUILD_TIME} · 当前版本 ${BuildConfig.VERSION_NAME}",
                        fontSize = 12.sp,
                        color = IosColors.SecondaryLabel
                    )
                }
            }

            // =====================================================================
            // S2. 核心功能与架构特性
            // =====================================================================
            IosSectionHeader("核心功能与架构特性")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.card,
                color = IosColors.Surface2
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FeatureItem(
                        icon = Icons.AutoMirrored.Filled.AltRoute,
                        iconTint = IosColors.SystemBlue,
                        title = "多应用独立分流路由 (Multi-Target)",
                        tags = listOf("AOSP 8~15 全兼容", "多开分身支持", "零锁高频 IPC", "真实物理透传"),
                        desc = "突破全系统单点模拟限制。可为微信、钉钉、高德等单独指派独立虚拟位置与专属路线，支持多开分身 (User 999) 独立识别绑定，未添加应用自动走真实物理定位。"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    FeatureItem(
                        icon = Icons.Default.Lock,
                        iconTint = IosColors.SystemGreen,
                        title = "系统内核集中拦截 (system_server Hook)",
                        tags = listOf("0 注入特征", "仅勾选系统框架", "派发监听全拦截", "AOSP 丢包抑制"),
                        desc = "在 LSPosed 中仅需勾选「系统框架」，由系统底层集中路由。宿主应用进程内 0 注入代码，彻底免除第三方反作弊扫描与封号风险。"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    FeatureItem(
                        icon = Icons.Default.PlayArrow,
                        iconTint = IosColors.SystemOrange,
                        title = "离线运动学物理仿真 (Kinematics Pro)",
                        tags = listOf("向心加速度减速", "双峰步频微动", "高斯地形海拔起伏"),
                        desc = "结合外接圆过弯向心减速约束 (v <= sqrt(a*R)) 杜绝急转弯超速异常；步频双峰微动模型拟真人体步态；高斯地形模型生成逼真海拔曲线。"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    FeatureItem(
                        icon = Icons.Default.SatelliteAlt,
                        iconTint = IosColors.SystemPurple,
                        title = "动态多星座 GNSS 星历合成 (Synthetic GNSS)",
                        tags = listOf("北斗/GPS/GLONASS", "16~24 动态卫星", "天顶角仰角 C/N0"),
                        desc = "合成北斗、GPS 与 GLONASS 多星座卫星分布与 24~42 dB-Hz 动态信噪比，在遮挡时叠加多径衰减，解决模拟定位开启后搜星数为 0 触发平台秒封的问题。"
                    )
                }
            }

            // =====================================================================
            // S3. 开源仓库与版本发布
            // =====================================================================
            IosSectionHeader("开源仓库与版本发布")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.card,
                color = IosColors.Surface2
            ) {
                Column(modifier = Modifier.padding(AppSpacing.s4)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(AppShapes.icon)
                                .background(IosColors.SystemFill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = IosColors.Label,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "项目主页与发布页",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IosColors.Label
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = VersionSyncManager.HOMEPAGE_URL,
                                fontSize = 12.sp,
                                color = IosColors.SystemBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Copy URL Button
                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Website", VersionSyncManager.HOMEPAGE_URL)
                                cm.setPrimaryClip(clip)
                                Toast.makeText(context, "已复制项目主页地址至剪贴板！", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = AppShapes.button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IosColors.SystemFill,
                                contentColor = IosColors.Label
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("复制链接", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Open Browser Button
                        Button(
                            onClick = {
                                try {
                                    val browserIntent =
                                        Intent(Intent.ACTION_VIEW, Uri.parse(VersionSyncManager.HOMEPAGE_URL))
                                    context.startActivity(browserIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = AppShapes.button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IosColors.SystemBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("打开主页", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // =====================================================================
            // S4. 联系与反馈
            // =====================================================================
            IosSectionHeader("联系与反馈")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.card,
                color = IosColors.Surface2
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(AppShapes.icon)
                                .background(IosColors.SystemFill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = IosColors.Label,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "邮箱",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IosColors.Label
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = VersionSyncManager.CONTACT_EMAIL,
                                fontSize = 12.sp,
                                color = IosColors.SystemBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Email", VersionSyncManager.CONTACT_EMAIL)
                                cm.setPrimaryClip(clip)
                                Toast.makeText(context, "已复制邮箱地址至剪贴板！", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = AppShapes.button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IosColors.SystemFill,
                                contentColor = IosColors.Label
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("复制邮箱", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:${VersionSyncManager.CONTACT_EMAIL}")
                                        val verTag = if (BuildConfig.VERSION_NAME.startsWith("v", ignoreCase = true)) {
                                            BuildConfig.VERSION_NAME
                                        } else {
                                            "v${BuildConfig.VERSION_NAME}"
                                        }
                                        putExtra(
                                            Intent.EXTRA_SUBJECT,
                                            "FakeGPS-next 反馈 ($verTag)"
                                        )
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "未找到邮件应用", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = AppShapes.button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IosColors.SystemBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("发送邮件", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // =====================================================================
            // S5. 开源致谢与许可协议
            // =====================================================================
            IosSectionHeader("开源致谢与许可协议")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.card,
                color = IosColors.Surface2
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CreditItem(
                        name = "Haze",
                        author = "chrisbanes",
                        desc = "现代化 Jetpack Compose 玻璃拟态 (Glassmorphism) 与实时毛玻璃背景模糊渲染库",
                        url = "https://github.com/chrisbanes/haze"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    CreditItem(
                        name = "OSMDroid",
                        author = "osmdroid",
                        desc = "免 Key 开源 OpenStreetMap 高速瓦片底图渲染引擎与多手势地图控制器",
                        url = "https://github.com/osmdroid/osmdroid"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    CreditItem(
                        name = "LSPosed Framework",
                        author = "LSPosed Developers",
                        desc = "现代化 ART 运行时系统级 Hook 与 Xposed API 挂载注入框架，接管系统服务分发",
                        url = "https://github.com/LSPosed/LSPosed"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    CreditItem(
                        name = "Jetpack Compose",
                        author = "Google / AOSP",
                        desc = "Android 官方现代化声明式响应式 UI 工具包与 Material 3 基础套件",
                        url = "https://github.com/androidx/androidx"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    CreditItem(
                        name = "Kotlin Coroutines & Flow",
                        author = "JetBrains",
                        desc = "高性能异步并发控制与响应式状态流调度框架，驱动网络与位置模拟引擎",
                        url = "https://github.com/Kotlin/kotlinx.coroutines"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    CreditItem(
                        name = "Dagger Hilt",
                        author = "Google",
                        desc = "Android 官方标准编译期静态依赖注入组件，构建松耦合高扩展架构",
                        url = "https://github.com/google/dagger"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    CreditItem(
                        name = "Room Database",
                        author = "Google / Android Jetpack",
                        desc = "类型安全 SQLite 对象关系映射数据库，支持自定义轨迹与路线持久化存储",
                        url = "https://developer.android.com/training/data-storage/room"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    CreditItem(
                        name = "Gson",
                        author = "Google",
                        desc = "轻量级高性能 JSON 数据序列化与反序列化解析库，处理云端版本同步与配置交换",
                        url = "https://github.com/google/gson"
                    )
                    HorizontalDivider(color = IosColors.LineSoft)
                    CreditItem(
                        name = "LocationSpoofer",
                        author = "HuangZhuoRui",
                        desc = "全屏交互地图、抽屉式控制面板与拟真定位模拟交互设计参考",
                        url = "https://github.com/HuangZhuoRui"
                    )
                }
            }
        }
    }
}
