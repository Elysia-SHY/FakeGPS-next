package com.mockrun.app.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Apple Human Interface Guidelines (HIG) SF Pro Typographic Scale
 * + v1.7.0 设计稿语义字号层级
 * Official specs: https://developer.apple.com/design/human-interface-guidelines/typography
 */
object IosTypography {
    val LargeTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.37.sp
    )

    val Title1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp
    )

    val Title2 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.35.sp
    )

    val Title3 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.38.sp
    )

    val Headline = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp
    )

    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp
    )

    val Callout = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.32).sp
    )

    val Subheadline = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp
    )

    val Footnote = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp
    )

    val Caption1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )

    val Caption2 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.07.sp
    )

    // =========================================================================
    // v1.7.0 设计稿语义层级
    // =========================================================================

    /** 页面主标题　30 / 800　「虚拟定位」 */
    val Display = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.8).sp
    )

    /** Hero 标题　27 / 800　「FakeGPS-next」 */
    val HeroTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 27.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp
    )

    /** 卡片标题　16 / 700 */
    val CardTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp
    )

    /** 分组标题　16 / 700 */
    val GroupTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp
    )

    /** 列表行主文字　14.5 / 600 */
    val RowTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.1).sp
    )

    /** 列表行副文字　12 / 400 */
    val RowSub = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )

    /** 顶部浮条标题　14.5 / 700 */
    val BarTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.5.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp
    )

    /** 顶部浮条副文字　11.5 / 400 */
    val BarSub = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp
    )

    /** 位置卡主标题　17 / 700 */
    val LocTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.2).sp
    )

    /** 按钮文字　16 / 600 */
    val ButtonText = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )

    /** 小按钮 / 次要按钮　13 / 500 */
    val ButtonSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )

    /** Tab 标签　10.5 / 600 */
    val TabLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp
    )

    /** 标签 / chip　11.5 / 600 */
    val Chip = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    )

    /** 键值对标签　13.5 / 400 */
    val KvLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )

    /** 键值对取值　14 / 600 */
    val KvValue = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )

    /** 说明正文　12.5 / 400 */
    val Note = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    )

    // ---- 等宽（坐标 / 版本号 / 标识符）----

    /** 坐标等宽　12 */
    val Mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.2).sp
    )

    /** 坐标等宽（大）　13 */
    val Mono13 = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = (-0.2).sp
    )

    /** 版本号等宽（强调）　20 / 800 */
    val MonoValue = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.4).sp
    )
}
