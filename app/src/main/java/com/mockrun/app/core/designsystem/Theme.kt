package com.mockrun.app.core.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
}

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
            content = content
        )
    }
}
