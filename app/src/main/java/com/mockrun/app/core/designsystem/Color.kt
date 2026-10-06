package com.mockrun.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =============================================================================
// Design Token · v1.7.0
// 参考稿：FakeGPS-next · UI 重设计规范 v1.7.0
//   「Apple 式大圆角 + 连续曲率 + 充足呼吸感」
// 三层结构：
//   ① 原始 token（DesignTokens）—— 设计稿的精确数值，不随明暗切换
//   ② 语义 palette（IosColorPalette）—— 按 isDark 提供两组值
//   ③ 访问器（IosColors）—— 向后兼容的公开 API
// =============================================================================

// -----------------------------------------------------------------------------
// ① 原始 Token · 设计稿精确数值
// -----------------------------------------------------------------------------
object DesignTokens {
    // 背景层级
    val Bg0 = Color(0xFF05070D)
    val Bg1 = Color(0xFF0B1020)
    val Surface = Color(0xFF151B2B)
    val Surface2 = Color(0xFF1C2437)
    val Surface3 = Color(0xFF252E44)
    val Line = Color(0x12FFFFFF)      // rgba(255,255,255,.07)
    val LineSoft = Color(0x0BFFFFFF)  // rgba(255,255,255,.045)

    // 语义色（iOS 系统色系）
    val Brand = Color(0xFF2F7BFF)
    val BrandPress = Color(0xFF1E63E6)
    val BrandSoft = Color(0xFF6BA5FF)
    val Ok = Color(0xFF30D158)
    val Warn = Color(0xFFFF9F0A)
    val Danger = Color(0xFFFF453A)
    val Violet = Color(0xFF7C4DFF)    // 品牌渐变第二色 / 极光紫

    // 文字
    val T1 = Color(0xFFF5F7FB)
    val T2 = Color(0xFF98A2B8)
    val T3 = Color(0xFF646E85)

    // 毛玻璃（唯一规范）
    val GlassBg = Color(0xB8141A2A)        // rgba(20,26,42,.72)  玻璃底板
    val GlassBgBar = Color(0xB81E2330)     // rgba(30,35,48,.72)  底部 Tab 栏
    val GlassLine = Color(0x14FFFFFF)      // rgba(255,255,255,.08)
    val GlassHighlight = Color(0x12FFFFFF) // inset 0 1px 0 rgba(255,255,255,.07)

    /**
     * 无可用模糊源时的**不透明磨砂底板**（= GlassBg 去掉 alpha 后合成到底色上的结果）。
     *
     * 为什么需要它：Haze 只能捕获 Compose 自己绘制的内容。当地图这类内容由
     * `AndroidView` 承载原生 View 时，源图层录出来是空的，模糊结果为空 ——
     * 此时若仍输出 72% 半透明底板，背后内容会硬生生透出来（观感即「穿模」）。
     * 用不透明底板兜底，浮层才会读起来像一整块实心磨砂玻璃。
     */
    val GlassBgOpaque = Color(0xFF141A2A)
    val GlassBgBarOpaque = Color(0xFF1E2330)

    // 明色档（同语义映射，供 isDark=false 使用）
    val LBg0 = Color(0xFFF5F7FB)
    val LBg1 = Color(0xFFFFFFFF)
    val LSurface = Color(0xFFFFFFFF)
    val LSurface2 = Color(0xFFF2F4F8)
    val LSurface3 = Color(0xFFE8ECF3)
    val LLine = Color(0x12000000)
    val LLineSoft = Color(0x0B000000)
    val LGlassBg = Color(0xB8FFFFFF)
    val LGlassBgBar = Color(0xB8FFFFFF)  // rgba(255,255,255,.72) 底部 Tab 栏
    val LGlassLine = Color(0x0F000000)
    val LGlassHighlight = Color(0x99FFFFFF)
    val LGlassBgOpaque = Color(0xFFFFFFFF)
    val LGlassBgBarOpaque = Color(0xFFF7F8FA)

    // 地图图钉上方提示条底板（压在地图瓦片上，需高不透明以保证可读）
    val LPinTipBg = Color(0xE6FFFFFF)   // rgba(255,255,255,.90)
    val DPinTipBg = Color(0xDB0A0D16)   // rgba(10,13,22,.86)
}

// -----------------------------------------------------------------------------
// ② 语义 Palette
// -----------------------------------------------------------------------------
data class IosColorPalette(
    val isDark: Boolean,

    // Dynamic Text Hierarchy (Labels)
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,

    // Grouped System Backgrounds
    val systemGroupedBackground: Color,
    val secondaryGroupedBackground: Color,
    val tertiaryGroupedBackground: Color,

    // System Fills
    val systemFill: Color,
    val secondarySystemFill: Color,
    val tertiarySystemFill: Color,
    val quaternarySystemFill: Color,

    // Separators
    val separator: Color,
    val opaqueSeparator: Color,

    // Materials & Blur Tints
    val ultraThinMaterial: Color,
    val thinMaterial: Color,
    val frostedCapsule: Color,
    val hairlineBorder: Color,
    val glassTrack: Color,

    // ---- v1.7.0 新增：层级 / 描边 / 玻璃 ----
    val bg1: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val lineSoft: Color,
    val brandPress: Color,
    val brandSoft: Color,
    val glassBg: Color,
    val glassLine: Color,
    val glassHighlight: Color,
    /** 底部 Tab 栏玻璃底板（bar 比普通浮层略亮一档） */
    val glassBgBar: Color,
    /** 地图中心图钉上方的提示条底板（压在瓦片上，需高不透明） */
    val pinTipBg: Color,
)

val LightIosColorPalette = IosColorPalette(
    isDark = false,
    label = Color(0xFF0B1020),
    secondaryLabel = Color(0xB35A6478),
    tertiaryLabel = Color(0x808894A8),
    quaternaryLabel = Color(0x4D8894A8),
    systemGroupedBackground = DesignTokens.LBg0,
    secondaryGroupedBackground = DesignTokens.LSurface,
    tertiaryGroupedBackground = DesignTokens.LSurface2,
    systemFill = Color(0x33787880),
    secondarySystemFill = Color(0x29787880),
    tertiarySystemFill = Color(0x1F767680),
    quaternarySystemFill = Color(0x14747480),
    separator = Color(0x1F000000),
    opaqueSeparator = Color(0xFFC6C6C8),
    ultraThinMaterial = Color(0xE6F8F8F9),
    thinMaterial = Color(0xF2FFFFFF),
    frostedCapsule = Color(0xF2FFFFFF),
    hairlineBorder = DesignTokens.LGlassLine,
    glassTrack = Color(0x1A000000),
    bg1 = DesignTokens.LBg1,
    surface2 = DesignTokens.LSurface2,
    surface3 = DesignTokens.LSurface3,
    line = DesignTokens.LLine,
    lineSoft = DesignTokens.LLineSoft,
    brandPress = DesignTokens.BrandPress,
    brandSoft = DesignTokens.Brand,
    glassBg = DesignTokens.LGlassBg,
    glassLine = DesignTokens.LGlassLine,
    glassHighlight = DesignTokens.LGlassHighlight,
    glassBgBar = DesignTokens.LGlassBgBar,
    pinTipBg = DesignTokens.LPinTipBg,
)

val DarkIosColorPalette = IosColorPalette(
    isDark = true,
    label = DesignTokens.T1,
    secondaryLabel = DesignTokens.T2,
    tertiaryLabel = DesignTokens.T3,
    quaternaryLabel = Color(0x4D646E85),
    systemGroupedBackground = DesignTokens.Bg0,
    secondaryGroupedBackground = DesignTokens.Surface,
    tertiaryGroupedBackground = DesignTokens.Surface2,
    systemFill = Color(0x5C787880),
    secondarySystemFill = Color(0x52787880),
    tertiarySystemFill = Color(0x3D767680),
    quaternarySystemFill = Color(0x2E747480),
    separator = DesignTokens.Line,
    opaqueSeparator = DesignTokens.Surface3,
    ultraThinMaterial = Color(0xD9141A2A),
    thinMaterial = Color(0xF2151B2B),
    frostedCapsule = DesignTokens.GlassBg,
    hairlineBorder = DesignTokens.GlassLine,
    glassTrack = Color(0x29FFFFFF),
    bg1 = DesignTokens.Bg1,
    surface2 = DesignTokens.Surface2,
    surface3 = DesignTokens.Surface3,
    line = DesignTokens.Line,
    lineSoft = DesignTokens.LineSoft,
    brandPress = DesignTokens.BrandPress,
    brandSoft = DesignTokens.BrandSoft,
    glassBg = DesignTokens.GlassBg,
    glassLine = DesignTokens.GlassLine,
    glassHighlight = DesignTokens.GlassHighlight,
    glassBgBar = DesignTokens.GlassBgBar,
    pinTipBg = DesignTokens.DPinTipBg,
)

val LocalIosColors = staticCompositionLocalOf { LightIosColorPalette }

// -----------------------------------------------------------------------------
// ③ 访问器
// -----------------------------------------------------------------------------
object IosColors {
    // 1. System Accent Colors —— v1.7.0 重标定
    val SystemBlue = DesignTokens.Brand          // #007AFF -> #2F7BFF
    val SystemGreen = DesignTokens.Ok            // #34C759 -> #30D158
    val SystemRed = DesignTokens.Danger          // #FF3B30 -> #FF453A
    val SystemOrange = DesignTokens.Warn         // #FF9500 -> #FF9F0A
    val SystemYellow = Color(0xFFFFD60A)
    val SystemPurple = DesignTokens.Violet       // #AF52DE -> #7C4DFF
    val SystemTeal = Color(0xFF64D2FF)
    val SystemIndigo = Color(0xFF5E5CE6)
    val SystemPink = Color(0xFFFF375F)
    val SystemMint = Color(0xFF63E6E2)
    val SystemCyan = Color(0xFF64D2FF)

    // 1b. v1.7.0 品牌扩展
    val BrandPress = DesignTokens.BrandPress
    val BrandSoft = DesignTokens.BrandSoft
    val Violet = DesignTokens.Violet

    // 2. Grays
    val SystemGray = Color(0xFF8E8E93)
    val SystemGray2 = Color(0xFFAEAEB2)
    val SystemGray3 = Color(0xFFC7C7CC)
    val SystemGray4 = Color(0xFFD1D1D6)
    val SystemGray5 = Color(0xFFE5E5EA)
    val SystemGray6 = Color(0xFFF2F2F7)

    // 3. Dynamic Text Hierarchy (Labels)
    val Label: Color
        @Composable
        get() = LocalIosColors.current.label

    val SecondaryLabel: Color
        @Composable
        get() = LocalIosColors.current.secondaryLabel

    val TertiaryLabel: Color
        @Composable
        get() = LocalIosColors.current.tertiaryLabel

    val QuaternaryLabel: Color
        @Composable
        get() = LocalIosColors.current.quaternaryLabel

    // 4. Grouped System Backgrounds
    val SystemGroupedBackground: Color
        @Composable
        get() = LocalIosColors.current.systemGroupedBackground

    val SecondaryGroupedBackground: Color
        @Composable
        get() = LocalIosColors.current.secondaryGroupedBackground

    val TertiaryGroupedBackground: Color
        @Composable
        get() = LocalIosColors.current.tertiaryGroupedBackground

    // 5. System Fills
    val SystemFill: Color
        @Composable
        get() = LocalIosColors.current.systemFill

    val SecondarySystemFill: Color
        @Composable
        get() = LocalIosColors.current.secondarySystemFill

    val TertiarySystemFill: Color
        @Composable
        get() = LocalIosColors.current.tertiarySystemFill

    val QuaternarySystemFill: Color
        @Composable
        get() = LocalIosColors.current.quaternarySystemFill

    // 6. Separators
    val Separator: Color
        @Composable
        get() = LocalIosColors.current.separator

    val OpaqueSeparator: Color
        @Composable
        get() = LocalIosColors.current.opaqueSeparator

    // 7. Materials & Blur Tints
    val UltraThinMaterial: Color
        @Composable
        get() = LocalIosColors.current.ultraThinMaterial

    val ThinMaterial: Color
        @Composable
        get() = LocalIosColors.current.thinMaterial

    // 8. ---- v1.7.0 层级 / 描边 / 玻璃 ----
    /** 页面次级底色 #0B1020 */
    val Bg1: Color
        @Composable
        get() = LocalIosColors.current.bg1

    /** 卡片内嵌套面 #1C2437 */
    val Surface2: Color
        @Composable
        get() = LocalIosColors.current.surface2

    /** 高一级嵌套面 #252E44 */
    val Surface3: Color
        @Composable
        get() = LocalIosColors.current.surface3

    /** rgba(255,255,255,.07) 常规描边 */
    val Line: Color
        @Composable
        get() = LocalIosColors.current.line

    /** rgba(255,255,255,.045) 行分隔线 */
    val LineSoft: Color
        @Composable
        get() = LocalIosColors.current.lineSoft

    /** rgba(20,26,42,.72) 玻璃底板 */
    val GlassBg: Color
        @Composable
        get() = LocalIosColors.current.glassBg

    /** rgba(255,255,255,.08) 玻璃 1px 描边 */
    val GlassLine: Color
        @Composable
        get() = LocalIosColors.current.glassLine

    /** inset 0 1px 0 rgba(255,255,255,.07) 内高光 */
    val GlassHighlight: Color
        @Composable
        get() = LocalIosColors.current.glassHighlight

    /** 底部 Tab 栏玻璃底板（暗 #1E2330 / 明 #FFFFFF） */
    val GlassBgBar: Color
        @Composable
        get() = LocalIosColors.current.glassBgBar

    /** 地图图钉上方提示条底板（暗 rgba(10,13,22,.86) / 明 rgba(255,255,255,.90)） */
    val PinTipBg: Color
        @Composable
        get() = LocalIosColors.current.pinTipBg

    /**
     * 玻璃面上的强调色（选中态）。
     * 暗色用 --brand-soft #6BA5FF（在深玻璃上更亮），明色用 --brand #2F7BFF
     * —— 浅蓝 #6BA5FF 在白色底栏上对比度不足，必须换成饱和蓝。
     */
    val BrandOnGlass: Color
        @Composable
        get() = LocalIosColors.current.brandSoft
}

// =============================================================================
// Top-Level Backward-Compatible Aliases
// =============================================================================
val IosBlue = IosColors.SystemBlue
val IosGreen = IosColors.SystemGreen
val IosRed = IosColors.SystemRed
val IosOrange = IosColors.SystemOrange
val IosYellow = IosColors.SystemYellow
val IosPurple = IosColors.SystemPurple
val IosTeal = IosColors.SystemTeal
val IosIndigo = IosColors.SystemIndigo
val IosGray = IosColors.SystemGray
val IosGray2 = IosColors.SystemGray2
val IosGray3 = IosColors.SystemGray3
val IosGray4 = IosColors.SystemGray4
val IosGray5 = IosColors.SystemGray5
val IosGray6 = IosColors.SystemGray6

// Surfaces & Backgrounds
val IosSystemBackground: Color
    @Composable
    get() = LocalIosColors.current.systemGroupedBackground

val IosCardBackground: Color
    @Composable
    get() = LocalIosColors.current.secondaryGroupedBackground

/** v1.7.0: #151B2B（原 #1C1C1E） */
val IosCardBackgroundDark = DesignTokens.Surface

// Frosted Glass Translucent Materials
val IosFrostedLight = Color(0xEBFFFFFF)
val IosFrostedDark = Color(0xEB1C1C1E)

val IosFrostedCapsule: Color
    @Composable
    get() = LocalIosColors.current.frostedCapsule

val IosFrostedDarkCapsule = DesignTokens.GlassBg
val IosFrostedPill = Color(0xE0F2F2F7)

val IosHairlineBorder: Color
    @Composable
    get() = LocalIosColors.current.hairlineBorder

val IosHairlineBorderDark = DesignTokens.GlassLine

val IosGlassTrack: Color
    @Composable
    get() = LocalIosColors.current.glassTrack

val IosGlassTrackLight = Color(0x33FFFFFF)

val IosSeparator: Color
    @Composable
    get() = LocalIosColors.current.separator

val IosSubtleHighlight = Color(0x40FFFFFF)

/** 非 Composable 场景直接取暗色玻璃底板（如 Canvas / Modifier.composed 外部） */
val IosGlassBgDark = DesignTokens.GlassBg
val IosGlassLineDark = DesignTokens.GlassLine
val IosGlassHighlightDark = DesignTokens.GlassHighlight
val IosLineDark = DesignTokens.Line
val IosLineSoftDark = DesignTokens.LineSoft
val IosSurfaceDark = DesignTokens.Surface
val IosSurface2Dark = DesignTokens.Surface2
val IosSurface3Dark = DesignTokens.Surface3
val IosBg0Dark = DesignTokens.Bg0
val IosBg1Dark = DesignTokens.Bg1
val IosText1Dark = DesignTokens.T1
val IosText2Dark = DesignTokens.T2
val IosText3Dark = DesignTokens.T3

// Legacy Fallbacks
val PrimaryGreen = IosGreen
val PrimaryGreenDark = Color(0xFF28A745)
val AccentOrange = IosOrange
val SurfaceDark = IosCardBackgroundDark
val BackgroundDark = DesignTokens.Bg0
val Purple80 = IosPurple
val PurpleGrey80 = IosGray2
val Pink80 = IosColors.SystemPink
