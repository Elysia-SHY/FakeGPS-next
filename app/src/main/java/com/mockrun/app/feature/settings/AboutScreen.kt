package com.mockrun.app.feature.settings

import android.animation.ValueAnimator
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mockrun.app.BuildConfig
import com.mockrun.app.core.data.repository.VersionSyncManager
import com.mockrun.app.core.data.repository.DownloadStatus
import com.mockrun.app.core.data.repository.VersionSyncStatus
import com.mockrun.app.core.designsystem.*
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val BUILTIN_CHANGELOG = """【FakeGPS-next v2.5.0 更新日志】

1. Liquid Glass 界面与 Dock 手势重构：
   - 四个主页面、地图浮层、路线库与弹窗统一 Material 3 和双模式配色；
   - Dock 使用手指绝对坐标吸附、跨 Tab 触觉反馈与弹簧落槽；
   - 主页面改为原地微景深交叉叠化，消除右侧弹跳留白。

2. 渲染与交互性能优化：
   - 页面共享 Backdrop，背景采样降采样至 0.5，Dock 仅捕获底部区域；
   - 胶囊位置和缩放仅在绘制阶段读取，减少拖动重组；
   - 纯净模式彻底停用实时采样。

3. 自定义强调色：
   - 提供六种预设与 #RRGGBB 自定义颜色；
   - 支持输入校验、即时应用、恢复原生和冷启动持久化。

4. 稳定性修复：
   - 修复纯净模式切换崩溃、功能与关于页底栏模糊丢失；
   - 修复地图搜索旧请求覆盖新结果，加入输入防抖与请求失效保护；
   - 保留分流应用、模拟点位、路线、壁纸和外观记忆，启动不会自动开启模拟。

—— 以下为 v2.2.0 及更早历史更新 ——

【FakeGPS-next v2.2.0 更新日志】

1. LSPosed 通道迁移至 Vector 框架（修复 HyperOS / Android 17 模块零派发）：
   - 官方 LSPosed 2.2.1 在 HyperOS 上仅注入 hyos_spawner/artd（Rust 运行时，无 ART/Java 回调），
     handleLoadPackage 全设备零触发；迁移到 JingMatrix Vector v2.2 后系统框架与目标应用双侧正常注入。

2. 系统框架定位注入在 HyperOS 真机恢复生效：
   - system_server 侧 onReportLocation / acceptLocationChange / reportLocation 钩子正常触发，
     系统定位返回值与合成卫星状态可按模拟点输出（高德地图实测已跳到模拟点）。
   - 地图 SDK 兼容层挂钩逻辑整理为 hookAmapLocationGetters，按已加载 Class 直接挂钩。

3. 坐标偏差修复：瞄准点改为 Pin 尖端投影；热门城市常量修正为 GCJ-02→WGS-84。

4. 坐标系收敛为唯一实现 GeoTransform（WGS-84/GCJ-02/BD-09），全量单测覆盖。

5. 应用独立分流：移除全局 kill-switch、空规则清缓存、按方法记账可重试、IPackageManager 兜底。

6. 注入通道默认 Provider；LSPosed 改为手动高级选项，不再自动优先。Provider 通道不发布 hook 配置，消除坐标残留。

—— 以下为 v2.1.2 历史更新 ——

1. 修复定位页 / 路线页毛玻璃全面失效（严重）：
   - 这两页此前没有可被 Haze 捕获的 Compose 源层（底层是 OSMDroid 原生 View），
     全部玻璃卡片静默退化为不透明色块，底部悬浮栏同样无模糊；
   - 现叠加一层 Compose 绘制的 Aurora 雾面作为模糊源（上下厚、中间薄，
     中央地图区保持干净），底部悬浮 Tab 栏接入页面内容源，全部恢复真实高斯模糊。

2. 环境指纹采集真机物理定位优先：
   - 采集弹窗默认直取真机真实物理定位，不再误填地图选定模拟点；
   - 弹窗内置「获取本机真实位置」实时校准与「一键停止模拟」，无需退回首页重置。

3. 在线更新服务无官网化多源升级：
   - 「蓝奏云（南咒云）直链自动解析 + 多 CDN 静态清单 + 镜像兜底」多级容灾；
   - 下载完成后执行 SHA-256 哈希与 APK 签名证书双重完整性校验。

4. 环境指纹采集与 5G NR 回放：打通 5G NR 36-bit NCI 采集与回放。"""

@Composable
private fun AboutSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun AboutCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AtlasGlassGroup(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.card,
        containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerLow,
        elevation = 4.dp,
        borderWidth = 0.75.dp,
        tintAlpha = if (LocalIsDarkTheme.current) 0.20f else 0.30f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier
                            .clip(AppShapes.card)
                            .clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
            content = content
        )
    }
}

private data class AccentOption(
    val argb: Int?,
    val label: String,
    val color: Color
)

@Composable
private fun AccentColorCard() {
    val context = LocalContext.current
    val accentArgb by AppAppearancePreferences.accentColorArgb
    val options = remember {
        listOf(
            AccentOption(null, "原生", Color(0xFF69D3D5)),
            AccentOption(0xFF2F7BFF.toInt(), "海蓝", Color(0xFF2F7BFF)),
            AccentOption(0xFF2E9E6B.toInt(), "松绿", Color(0xFF2E9E6B)),
            AccentOption(0xFFD96854.toInt(), "珊瑚", Color(0xFFD96854)),
            AccentOption(0xFF8067B7.toInt(), "鸢尾", Color(0xFF8067B7)),
            AccentOption(0xFF6B7280.toInt(), "石墨", Color(0xFF6B7280))
        )
    }
    var hexInput by remember { mutableStateOf("") }
    val parsedColor = AppAppearancePreferences.parseAccentColor(hexInput)

    LaunchedEffect(accentArgb) {
        hexInput = accentArgb?.let { "#%06X".format(it and 0xFFFFFF) }.orEmpty()
    }

    fun applyCustomColor() {
        parsedColor?.let {
            AppAppearancePreferences.setAccentColor(context, it)
        }
    }

    AboutCard {
        Column(
            modifier = Modifier.padding(AppSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    val selected = option.argb == accentArgb
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppShapes.chip)
                            .clickable(onClickLabel = "使用${option.label}强调色") {
                                AppAppearancePreferences.setAccentColor(context, option.argb)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(option.color)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                    }
                }
            }

            OutlinedTextField(
                value = hexInput,
                onValueChange = { hexInput = it.take(9) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自定义 HEX") },
                placeholder = { Text("#2F7BFF") },
                supportingText = {
                    Text(
                        if (hexInput.isNotBlank() && parsedColor == null) {
                            "请输入 6 位十六进制颜色"
                        } else {
                            "例如 #2F7BFF，应用后立即生效"
                        }
                    )
                },
                isError = hexInput.isNotBlank() && parsedColor == null,
                singleLine = true,
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(parsedColor ?: accentArgb ?: 0xFF69D3D5.toInt()))
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = ::applyCustomColor,
                        enabled = parsedColor != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "应用自定义强调色"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { applyCustomColor() })
            )

            if (accentArgb != null) {
                TextButton(
                    onClick = { AppAppearancePreferences.setAccentColor(context, null) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("恢复原生强调色")
                }
            }
        }
    }
}

@Composable
private fun MaterialFeatureChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isLiquidGlass: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AppShapes.chip,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AboutScreen(
    isLiquidGlass: Boolean,
    supportsLiquidGlass: Boolean,
    isTablet: Boolean = false,
    onToggleLiquidGlass: (Boolean) -> Unit,
    onNavigateToLibrary: () -> Unit = {},
    sharedGlassBackdrop: LayerBackdrop? = null
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
                    if (success) "自定义壁纸已设置并在全应用生效" else "壁纸读取失败，请重试",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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

    AtlasGlassPage(
        modifier = Modifier.fillMaxSize(),
        sharedBackdrop = sharedGlassBackdrop
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = AppShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(29.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FakeGPS-next",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${BuildConfig.VERSION_NAME} · 构建于 ${BuildConfig.BUILD_TIME}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // =====================================================================
            // 2. 界面材质
            // =====================================================================
            AboutSectionHeader("界面材质")

            AboutCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(AppShapes.panel)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BlurOn,
                                    contentDescription = null,
                                    tint = if (isLiquidGlass) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "液态玻璃质感",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        !supportsLiquidGlass -> "当前设备自动使用高对比纯净实色模式"
                                        isLiquidGlass -> "实时背景采样 · 柔焦 · 折射边缘 · 高光"
                                        else -> "纯净实色表面，零模糊开销"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isLiquidGlass,
                            enabled = supportsLiquidGlass,
                            onCheckedChange = { next ->
                                onToggleLiquidGlass(next)
                                Toast.makeText(
                                    context,
                                    if (next) "液态玻璃已开启" else "已切换为纯净实色模式",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    val materialFeatures = if (isLiquidGlass) {
                        listOf(
                            Icons.Default.BlurOn to "实时采样",
                            Icons.Default.CenterFocusStrong to "柔焦",
                            Icons.Default.LightMode to "折射高光"
                        )
                    } else {
                        listOf(
                            Icons.Default.CropSquare to "实色表面",
                            Icons.Default.Contrast to "高对比",
                            Icons.Default.BatteryFull to "低功耗"
                        )
                    }
                    val configuration = LocalConfiguration.current
                    val stackFeatures = configuration.fontScale >= 1.3f ||
                        configuration.screenWidthDp < 380
                    if (stackFeatures) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            materialFeatures.forEach { (icon, label) ->
                                MaterialFeatureChip(
                                    icon = icon,
                                    label = label,
                                    isLiquidGlass = isLiquidGlass,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            materialFeatures.forEach { (icon, label) ->
                                MaterialFeatureChip(
                                    icon = icon,
                                    label = label,
                                    isLiquidGlass = isLiquidGlass,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Text(
                        text = when {
                            !supportsLiquidGlass -> "实时背景采样从 Android 12 开始支持，当前设备会平滑降级。"
                            isLiquidGlass -> "玻璃只用于导航、浮层与弹窗，主体内容始终保持清晰可读。"
                            else -> "关闭后移除采样与模糊计算，更适合长时间导航和低端设备。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // =====================================================================
            // 3. 强调色
            // =====================================================================
            AboutSectionHeader("强调色")
            AccentColorCard()

            // =====================================================================
            // 3. 个性化背景壁纸
            // =====================================================================
            AboutSectionHeader("个性化背景壁纸")

            AboutCard {
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
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        if (hasCustomWallpaper) {
                                            AppSemanticColors.current.successContainer
                                        } else {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (hasCustomWallpaper) Icons.Default.AddPhotoAlternate else Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = if (hasCustomWallpaper) {
                                        AppSemanticColors.current.onSuccessContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasCustomWallpaper) "已应用自选相册壁纸" else "默认纯色背景",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        hasCustomWallpaper && isLiquidGlass ->
                                            "选择照片作为全应用底衬，玻璃浮层会实时采样背景"
                                        hasCustomWallpaper ->
                                            "选择照片作为全应用底衬，保持不变"
                                        isLiquidGlass ->
                                            "纯黑 / 纯白底衬，玻璃浮层会实时采样背景"
                                        else ->
                                            "纯黑 / 纯白底衬，主体内容保持清晰"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (hasCustomWallpaper) {
                            TextButton(
                                onClick = {
                                    BackgroundThemeManager.clearCustomWallpaper(context)
                                    Toast.makeText(context, "已恢复为默认纯色背景", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "恢复默认",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Button(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .weight(if (hasCustomWallpaper) 1.25f else 1f)
                                .heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (hasCustomWallpaper) "更换壁纸" else "从相册选取壁纸",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // 4. 数据管理与路线库
            // =====================================================================
            AboutSectionHeader("数据管理与路线库")

            AboutCard(onClick = onNavigateToLibrary) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            text = "已收藏路线与航点库",
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "管理历史规划、导入/导出 GPX 文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }

            // =====================================================================
            // 5. 版本与云端同步
            // =====================================================================
            AboutSectionHeader("版本与云端同步")

            AboutCard {
                Column(modifier = Modifier.padding(AppSpacing.s4)) {
                    val isChecking = sync is VersionSyncStatus.Checking

                    // 仅在检查更新期间运行旋转动画，避免静止状态下每帧触发重组重绘
                    val angle = if (isChecking && ValueAnimator.areAnimatorsEnabled()) {
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
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        when (sync) {
                                            is VersionSyncStatus.HasUpdate -> AppSemanticColors.current.warningContainer
                                            is VersionSyncStatus.UpToDate -> AppSemanticColors.current.successContainer
                                            is VersionSyncStatus.Error -> MaterialTheme.colorScheme.errorContainer
                                            else -> MaterialTheme.colorScheme.primaryContainer
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
                                        is VersionSyncStatus.HasUpdate -> AppSemanticColors.current.onWarningContainer
                                        is VersionSyncStatus.UpToDate -> AppSemanticColors.current.onSuccessContainer
                                        is VersionSyncStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "云端版本自动同步",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (sync) {
                                        is VersionSyncStatus.HasUpdate -> AppSemanticColors.current.warning
                                        is VersionSyncStatus.UpToDate -> AppSemanticColors.current.success
                                        is VersionSyncStatus.Error -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        // Sync / Refresh Button
                        FilledTonalIconButton(
                            onClick = {
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
                            enabled = !isChecking
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新同步",
                                modifier = Modifier
                                    .size(18.dp)
                                    .then(if (isChecking) Modifier.rotate(angle) else Modifier)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))

                    // Version comparison matrix
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "当前安装版本",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "云端最新版本",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = when (sync) {
                                    is VersionSyncStatus.HasUpdate -> AppSemanticColors.current.warning
                                    is VersionSyncStatus.UpToDate -> AppSemanticColors.current.success
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                            shape = MaterialTheme.shapes.medium,
                            color = AppSemanticColors.current.warningContainer,
                            contentColor = AppSemanticColors.current.onWarningContainer,
                            border = BorderStroke(
                                1.dp,
                                AppSemanticColors.current.warning.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Celebration,
                                            contentDescription = null,
                                            tint = AppSemanticColors.current.onWarningContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "发现新版本 ${info.versionName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = AppSemanticColors.current.onWarningContainer
                                        )
                                    }
                                    if (info.releaseDate.isNotBlank()) {
                                        Text(
                                            text = info.releaseDate,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = AppSemanticColors.current.onWarningContainer.copy(alpha = 0.8f),
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(0.6f)
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
                                                    .clip(MaterialTheme.shapes.extraSmall),
                                                color = AppSemanticColors.current.warning,
                                                trackColor = AppSemanticColors.current.warning.copy(alpha = 0.2f)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${(dl.progress * 100).toInt()}% (${formatMb(dl.downloadedBytes)} / ${formatMb(dl.totalBytes)})",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = AppSemanticColors.current.onWarningContainer,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = formatSpeed(dl.speedBytesPerSec),
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = AppSemanticColors.current.onWarningContainer
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { VersionSyncManager.cancelDownload() },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text(
                                                    text = "取消下载",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
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
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppSemanticColors.current.success, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "新版本已下载就绪 (${formatMb(file.length())})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = AppSemanticColors.current.onWarningContainer
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
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp)
                                                ) {
                                                    Text(
                                                        text = "重新下载",
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
                                                }
                                                Button(
                                                    onClick = {
                                                        VersionSyncManager.installApk(context, file)
                                                    },
                                                    modifier = Modifier
                                                        .weight(1.3f)
                                                        .heightIn(min = 48.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = AppSemanticColors.current.successContainer,
                                                        contentColor = AppSemanticColors.current.onSuccessContainer
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Download,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = "立即安装",
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
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
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppSemanticColors.current.onWarningContainer
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { VersionSyncManager.startDownload(context, info) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = AppSemanticColors.current.warning,
                                                        contentColor = AppSemanticColors.current.onWarning
                                                    )
                                                ) {
                                                    Text(
                                                        text = "重试在线下载",
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
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
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onSurface
                                                    )
                                                ) {
                                                    Text(
                                                        text = "浏览器下载",
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
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
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = AppSemanticColors.current.onWarningContainer
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    AppSemanticColors.current.onWarningContainer.copy(alpha = 0.5f)
                                                )
                                            ) {
                                                Text(
                                                    text = "更新说明",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    VersionSyncManager.startDownload(context, info)
                                                },
                                                modifier = Modifier
                                                    .weight(1.3f)
                                                    .heightIn(min = 48.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = AppSemanticColors.current.warning,
                                                    contentColor = AppSemanticColors.current.onWarning
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CloudDownload,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(5.dp))
                                                Text(
                                                    text = "在线下载更新",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
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
                                                    .clip(MaterialTheme.shapes.small)
                                                    .clickable {
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
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = AppSemanticColors.current.onWarningContainer
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
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = AppSemanticColors.current.successContainer,
                            contentColor = AppSemanticColors.current.onSuccessContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "已是最新版本 · $timeStr 已同步",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        showChangelogDialog = Pair("${BuildConfig.VERSION_NAME} 最新特性日志", remoteNotes)
                                    },
                                    modifier = Modifier
                                        .weight(0.45f)
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = AppSemanticColors.current.onSuccessContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "查看日志",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }

                    // Error banner
                    if (sync is VersionSyncStatus.Error) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "网络离线或同步受阻",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        showChangelogDialog = Pair("${BuildConfig.VERSION_NAME} 本地特性说明", BUILTIN_CHANGELOG)
                                    },
                                    modifier = Modifier
                                        .weight(0.45f)
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "本地日志",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 6. 设备环境与运行诊断
            // =====================================================================
            AboutSectionHeader("设备环境与运行诊断")

            AboutCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                    val osInfo = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

                    DiagnosticRow(label = "设备型号", value = deviceModel)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticRow(label = "系统版本", value = osInfo)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticRow(label = "指令集架构", value = abi)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticRow(label = "构建变体", value = "${BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }} (R8 混淆优化)")

                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "LSPosed 作用域先勾选「系统框架 (system)」和系统定位服务（Android/小米/OPPO fused provider）；需要分流、Mock 隐藏或步频/计步的目标应用，还必须单独勾选。仅系统框架不保证 OEM fused 或目标应用生效。",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // 7. 更多与关于
            // =====================================================================
            AboutSectionHeader("更多与关于")

            AboutCard(onClick = { showDetailsSheet = true }) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            text = "关于与致谢",
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "品牌介绍 · 架构特性 · 官网 · 联系 · 开源许可",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "品牌介绍 · 架构特性 · 官网 · 联系 · 开源许可",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // =====================================================================
            // S1. 品牌介绍
            // =====================================================================
            AboutSectionHeader("品牌介绍")

            AboutCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(
                    modifier = Modifier.padding(AppSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "系统框架底层分流 · 物理运动学拟真 · 动态多星座 GNSS 星历合成",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "构建时间: ${BuildConfig.BUILD_TIME} · 当前版本 ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // =====================================================================
            // S2. 核心功能与架构特性
            // =====================================================================
            AboutSectionHeader("核心功能与架构特性")

            AboutCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(
                    modifier = Modifier.padding(AppSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FeatureItem(
                        icon = Icons.AutoMirrored.Filled.AltRoute,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "多应用独立分流路由 (Multi-Target)",
                        tags = listOf("AOSP 8~15 全兼容", "多开分身支持", "零锁高频 IPC", "真实物理透传"),
                        desc = "突破全系统单点模拟限制。可为微信、钉钉、高德等单独指派独立虚拟位置与专属路线，支持多开分身 (User 999) 独立识别绑定，未添加应用自动走真实物理定位。"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FeatureItem(
                        icon = Icons.Default.Lock,
                        iconTint = AppSemanticColors.current.success,
                        title = "系统内核集中拦截 (system_server Hook)",
                        tags = listOf("系统框架 + 目标应用", "需要显式 scope", "派发监听全拦截", "AOSP 丢包抑制"),
                        desc = "系统框架负责系统层路由；目标应用必须在 LSPosed scope 中单独勾选，才能稳定收到分流/隐藏/步频注入。只勾系统框架时，目标应用侧可能仍读真实值。不要把它理解成“全设备零注入”。"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FeatureItem(
                        icon = Icons.Default.PlayArrow,
                        iconTint = AppSemanticColors.current.warning,
                        title = "离线运动学物理仿真 (Kinematics Pro)",
                        tags = listOf("向心加速度减速", "双峰步频微动", "高斯地形海拔起伏"),
                        desc = "结合外接圆过弯向心减速约束 (v <= sqrt(a*R)) 杜绝急转弯超速异常；步频双峰微动模型拟真人体步态；高斯地形模型生成逼真海拔曲线。"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FeatureItem(
                        icon = Icons.Default.SatelliteAlt,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = "动态多星座 GNSS 星历合成 (Synthetic GNSS)",
                        tags = listOf("北斗/GPS/GLONASS", "16~24 动态卫星", "天顶角仰角 C/N0"),
                        desc = "合成北斗、GPS 与 GLONASS 多星座卫星分布与 24~42 dB-Hz 动态信噪比，在遮挡时叠加多径衰减，解决模拟定位开启后搜星数为 0 触发平台秒封的问题。"
                    )
                }
            }

            // =====================================================================
            // S3. 开源仓库与版本发布
            // =====================================================================
            AboutSectionHeader("开源仓库与版本发布")

            AboutCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(AppSpacing.s4)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "项目主页与发布页",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = VersionSyncManager.HOMEPAGE_URL,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
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
                        FilledTonalButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Website", VersionSyncManager.HOMEPAGE_URL)
                                cm.setPrimaryClip(clip)
                                Toast.makeText(context, "已复制项目主页地址至剪贴板！", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "复制链接",
                                style = MaterialTheme.typography.labelLarge
                            )
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
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "打开主页",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // S4. 联系与反馈
            // =====================================================================
            AboutSectionHeader("联系与反馈")

            AboutCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "邮箱",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = VersionSyncManager.CONTACT_EMAIL,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
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
                        FilledTonalButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Email", VersionSyncManager.CONTACT_EMAIL)
                                cm.setPrimaryClip(clip)
                                Toast.makeText(context, "已复制邮箱地址至剪贴板！", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "复制邮箱",
                                style = MaterialTheme.typography.labelLarge
                            )
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
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "发送邮件",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // =====================================================================
            // S5. 开源致谢与许可协议
            // =====================================================================
            AboutSectionHeader("开源致谢与许可协议")

            AboutCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(
                    modifier = Modifier.padding(AppSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CreditItem(
                        name = "AndroidLiquidGlass",
                        author = "Kyant0",
                        desc = "背景采样、实时高斯模糊、AGSL 折射、Vibrancy 与边缘高光材质实现 (Apache-2.0)",
                        url = "https://github.com/Kyant0/AndroidLiquidGlass"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CreditItem(
                        name = "OSMDroid",
                        author = "osmdroid",
                        desc = "免 Key 开源 OpenStreetMap 高速瓦片底图渲染引擎与多手势地图控制器",
                        url = "https://github.com/osmdroid/osmdroid"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CreditItem(
                        name = "LSPosed Framework",
                        author = "LSPosed Developers",
                        desc = "现代化 ART 运行时系统级 Hook 与 Xposed API 挂载注入框架，接管系统服务分发",
                        url = "https://github.com/LSPosed/LSPosed"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CreditItem(
                        name = "Jetpack Compose",
                        author = "Google / AOSP",
                        desc = "Android 官方现代化声明式响应式 UI 工具包与 Material 3 基础套件",
                        url = "https://github.com/androidx/androidx"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CreditItem(
                        name = "Kotlin Coroutines & Flow",
                        author = "JetBrains",
                        desc = "高性能异步并发控制与响应式状态流调度框架，驱动网络与位置模拟引擎",
                        url = "https://github.com/Kotlin/kotlinx.coroutines"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CreditItem(
                        name = "Dagger Hilt",
                        author = "Google",
                        desc = "Android 官方标准编译期静态依赖注入组件，构建松耦合高扩展架构",
                        url = "https://github.com/google/dagger"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CreditItem(
                        name = "Room Database",
                        author = "Google / Android Jetpack",
                        desc = "类型安全 SQLite 对象关系映射数据库，支持自定义轨迹与路线持久化存储",
                        url = "https://developer.android.com/training/data-storage/room"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CreditItem(
                        name = "Gson",
                        author = "Google",
                        desc = "轻量级高性能 JSON 数据序列化与反序列化解析库，处理云端版本同步与配置交换",
                        url = "https://github.com/google/gson"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
