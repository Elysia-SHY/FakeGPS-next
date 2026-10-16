package com.mockrun.app.core.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1C3860),
    onPrimaryContainer = Color(0xFF90C2FF),
    secondary = IosGreen,
    onSecondary = Color.White,
    tertiary = IosPurple,
    background = DesignTokens.Bg0,
    onBackground = DesignTokens.T1,
    surface = DesignTokens.Surface,
    onSurface = DesignTokens.T1,
    surfaceVariant = DesignTokens.Surface2,
    onSurfaceVariant = DesignTokens.T2,
    outline = DesignTokens.Surface3,
    error = IosRed
)

private val LightColorScheme = lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = IosBlue,
    secondary = IosGreen,
    onSecondary = Color.White,
    tertiary = IosPurple,
    background = DesignTokens.LBg0,
    onBackground = Color(0xFF0B1020),
    surface = DesignTokens.LSurface,
    onSurface = Color(0xFF0B1020),
    surfaceVariant = DesignTokens.LSurface2,
    onSurfaceVariant = Color(0xFF5A6478),
    outline = DesignTokens.LSurface3,
    error = IosRed
)

/**
 * v1.7.0 圆角体系 —— Apple 式连续曲率，数值整体放大
 * 原 8/12/18/22/30 -> 新 9/16/22/24/32
 */
val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),   // chip
    small = RoundedCornerShape(16.dp),       // btn / ctl
    medium = RoundedCornerShape(22.dp),      // panel
    large = RoundedCornerShape(24.dp),       // card
    extraLarge = RoundedCornerShape(32.dp)   // sheet
)

/**
 * 语义化圆角 token（对应设计稿 --r-* 变量），供非 MaterialTheme.shapes 场景直接取用。
 */
object AppShapes {
    val phone = RoundedCornerShape(66.dp)
    val sheet = RoundedCornerShape(32.dp)      // 底部抽屉
    val card = RoundedCornerShape(24.dp)       // 卡片
    val panel = RoundedCornerShape(22.dp)      // 面板 / 浮条
    val bar = RoundedCornerShape(36.dp)        // 底部 Tab 栏（72dp 高）
    val button = RoundedCornerShape(16.dp)     // 按钮 / 控件
    val icon = RoundedCornerShape(13.dp)       // 图标底板
    val iconLg = RoundedCornerShape(18.dp)     // 48dp 图标钮
    val chip = RoundedCornerShape(9.dp)        // 标签
    val pill = RoundedCornerShape(999.dp)      // 药丸 / 胶囊
    val pill24 = RoundedCornerShape(24.dp)     // 48dp 高胶囊
}

/**
 * 间距 token —— 8pt 网格
 */
object AppSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp

    /** 页面左右安全边距 */
    val pageH = 16.dp

    /** 地图类屏幕底部预留（Tab 栏 72 + 底距 22 + 余量） */
    val mapBottomInset = 102.dp

    /** 滚动页底部预留（Tab 栏 + 呼吸） */
    val pageBottomInset = 122.dp

    /**
     * 上两个常量是历史硬编码（按「底栏 72 + 底距 22」的旧注释推导，比实际多留 16dp）。
     * 新代码请改用下面的计算值：它们从 WindowInsets 与 BottomBarMetrics 实时推导，
     * 手势条高度、平板/手机切换都会自动跟随。旧常量保留不动，已有调用点不受影响。
     */
    val mapBottomInsetComputed: Dp
        @Composable
        get() = ScreenInsets.bottomInset(extra = 8.dp)

    val pageBottomInsetComputed: Dp
        @Composable
        get() = ScreenInsets.bottomInset(extra = 24.dp)
}

/**
 * MaterialTheme.typography 的 iOS 语义映射。
 *
 * 为什么需要：项目里同时存在两种写法 —— 169 处 [IosTypography.*] 与 40 处
 * MaterialTheme.typography.*。此前 [MockRunTheme] 没给 MaterialTheme 传 typography，
 * M3 走的是自身默认值（labelSmall 11sp / bodyMedium 14sp / titleMedium 16sp…），
 * 于是同一个界面里两套字号并行，标题和正文对不上。
 *
 * 这里把 MaterialTheme 的 15 个角色**一一映射到已有的 IosTypography 值**，
 * 不放大、不新增数值：映射后两种写法的视觉输出完全一致。
 *
 * 映射表（M3 角色 -> IosTypography 值）：
 *   displayLarge   -> LargeTitle   34 / Bold      / 41
 *   displayMedium  -> Title1       28 / Bold      / 34
 *   displaySmall   -> HeroTitle    27 / ExtraBold / 32
 *   headlineLarge  -> Display      30 / ExtraBold / 33
 *   headlineMedium -> Title2       22 / Bold      / 28
 *   headlineSmall  -> Title3       20 / SemiBold  / 25
 *   titleLarge     -> LocTitle     17 / Bold      / 23
 *   titleMedium    -> CardTitle    16 / Bold      / 20
 *   titleSmall     -> RowTitle     14.5 / SemiBold/ 19
 *   bodyLarge      -> Body         17 / Normal    / 22
 *   bodyMedium     -> Callout      16 / Normal    / 21
 *   bodySmall      -> Footnote     13 / Normal    / 18
 *   labelLarge     -> ButtonText   16 / SemiBold  / 20
 *   labelMedium    -> KvValue      14 / SemiBold  / 18
 *   labelSmall     -> RowSub       12 / Normal    / 18
 *
 * 覆盖到的现网调用点：labelSmall(19) / labelMedium(9) / bodySmall(4) /
 * labelLarge(3) / bodyMedium(3) / titleMedium(2)，集中在 feature/map/MapDialogs.kt
 * 与 feature/map/MapScreen.kt。这些都是显式传 style 的 Text，映射后按上表取值。
 */
val IosM3Typography = Typography(
    displayLarge = IosTypography.LargeTitle,
    displayMedium = IosTypography.Title1,
    displaySmall = IosTypography.HeroTitle,
    headlineLarge = IosTypography.Display,
    headlineMedium = IosTypography.Title2,
    headlineSmall = IosTypography.Title3,
    titleLarge = IosTypography.LocTitle,
    titleMedium = IosTypography.CardTitle,
    titleSmall = IosTypography.RowTitle,
    bodyLarge = IosTypography.Body,
    bodyMedium = IosTypography.Callout,
    bodySmall = IosTypography.Footnote,
    labelLarge = IosTypography.ButtonText,
    labelMedium = IosTypography.KvValue,
    labelSmall = IosTypography.RowSub
)

@Composable
fun MockRunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false, // Keep consistent authentic iOS theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val iosPalette = if (darkTheme) DarkIosColorPalette else LightIosColorPalette

    androidx.compose.runtime.CompositionLocalProvider(
        LocalIosColors provides iosPalette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = IosShapes,
            typography = IosM3Typography,
            content = content
        )
    }
}
