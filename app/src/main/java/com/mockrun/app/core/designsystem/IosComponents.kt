package com.mockrun.app.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// 行图标色调 —— 对应设计稿 .row__icon / .row__icon.brand / .danger / .ok
// =============================================================================
enum class IosRowTone {
    /** 中性：rgba 白 6% 底 + 次文字色 */
    NEUTRAL,

    /** 品牌：16% 品牌蓝底 + brand-soft 图标 */
    BRAND,

    /** 危险：16% 红底 + 红图标 */
    DANGER,

    /** 成功：16% 绿底 + 绿图标 */
    OK,

    /** 警告：16% 橙底 + 橙图标 */
    WARN
}

@Composable
private fun toneBackground(tone: IosRowTone): Color = when (tone) {
    IosRowTone.NEUTRAL -> IosColors.Surface2
    IosRowTone.BRAND -> IosColors.SystemBlue.copy(alpha = 0.16f)
    IosRowTone.DANGER -> IosColors.SystemRed.copy(alpha = 0.16f)
    IosRowTone.OK -> IosColors.SystemGreen.copy(alpha = 0.16f)
    IosRowTone.WARN -> IosColors.SystemOrange.copy(alpha = 0.16f)
}

@Composable
private fun toneForeground(tone: IosRowTone): Color = when (tone) {
    IosRowTone.NEUTRAL -> IosColors.SecondaryLabel
    IosRowTone.BRAND -> IosColors.BrandSoft
    IosRowTone.DANGER -> IosColors.SystemRed
    IosRowTone.OK -> IosColors.SystemGreen
    IosRowTone.WARN -> IosColors.SystemOrange
}

// =============================================================================
// 分组标题
// =============================================================================

/**
 * 分组标题（简化版）。
 * 设计稿 .group__title —— 16 / 700，不再强制大写。
 */
@Composable
fun IosSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = IosTypography.GroupTitle,
        color = IosColors.Label,
        modifier = modifier.padding(start = AppSpacing.pageH + 4.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)
    )
}

/**
 * 完整分组头：标题 + 计数 + 右侧链接。
 * 设计稿 .group__head
 */
@Composable
fun IosGroupHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    linkText: String? = null,
    onLinkClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppSpacing.pageH + 4.dp, end = AppSpacing.pageH, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = IosTypography.GroupTitle,
            color = IosColors.Label
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "($count)",
                style = IosTypography.Mono13,
                color = IosColors.TertiaryLabel
            )
        }
        if (linkText != null && onLinkClick != null) {
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(AppShapes.chip)
                    .bouncyClickable(onClick = onLinkClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = IosColors.BrandSoft,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = linkText,
                    style = IosTypography.ButtonSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = IosColors.BrandSoft
                )
            }
        }
    }
}

/** 分组副标题（设计稿 .group__sub） */
@Composable
fun IosGroupSub(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = IosTypography.RowSub,
        color = IosColors.TertiaryLabel,
        modifier = modifier.padding(start = AppSpacing.pageH + 4.dp, end = 16.dp, bottom = 10.dp)
    )
}

// =============================================================================
// 卡片
// =============================================================================

/**
 * 分组卡片 —— **毛玻璃卡**。
 *
 * 设计稿 .card 给的是实色 `--surface:#151B2B`，但那个稿子页面底下没有氛围层。
 * 本项目滚动页铺了 [AppBackground] 的极光氛围渐变，把卡片做成玻璃之后：
 *   - 氛围光晕会从卡片下柔和透出，玻璃质感才立得住；
 *   - 卡片与背景之间拉开明确层次，不再是一块块纯色板。
 *
 * ⚠️ 接线约束：卡片必须与 [AppBackground] 处于**同一个 HazeState 的兄弟节点**关系 ——
 * 页面根 Box 里先放 `AppBackground(hazeState = pageHaze)` 当模糊源，
 * 再把 [hazeState] 传给卡片。否则玻璃会自引用。若未传源，
 * [liquidGlass] 会退化为不透明磨砂板，不会崩。
 */
@Composable
fun IosInsetGroupCard(
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = LocalLiquidGlassEnabled.current,
    shape: Shape = AppShapes.card,
    elevation: Dp = 10.dp,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.pageH)
            .liquidGlass(
                isLiquidGlass = isLiquidGlass,
                shape = shape,
                elevation = elevation,
                hazeState = hazeState
            ),
        shape = shape,
        color = Color.Transparent
    ) {
        Column(content = content)
    }
}

/**
 * 玻璃浮空卡（地图页浮层 / 底部抽屉专用）。
 * 这是设计稿里真正做 `backdrop-filter: blur(30px)` 的那一类容器。
 */
@Composable
fun IosGlassDock(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.sheet,
    isLiquidGlass: Boolean = LocalLiquidGlassEnabled.current,
    elevation: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(isLiquidGlass = isLiquidGlass, shape = shape, elevation = elevation)
    ) {
        Column(content = content)
    }
}

/**
 * 卡片内分隔线（设计稿 .row + .row::before —— left 62px）
 */
@Composable
fun IosHairlineDivider(
    startIndent: Dp = 69.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(IosColors.LineSoft)
    )
}

// =============================================================================
// 行
// =============================================================================

/**
 * 设计稿 .row__icon —— 40dp / r13 圆角方形图标底
 */
@Composable
fun IosRowIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: IosRowTone = IosRowTone.NEUTRAL,
    iconSize: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(AppShapes.icon)
            .background(toneBackground(tone)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = toneForeground(tone),
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 标准列表行。
 * 设计稿 .row —— padding 14×16 / icon 40dp r13 / title 14.5-600 / sub 12-次色 / chevron
 */
@Composable
fun IosListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    iconBackground: Color = Color.Unspecified,
    tone: IosRowTone = IosRowTone.NEUTRAL,
    trailingText: String? = null,
    trailingTextColor: Color = Color.Unspecified,
    showChevron: Boolean = false,
    chevronColor: Color = Color.Unspecified,
    trailingContent: (@Composable () -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    subtitleColor: Color = Color.Unspecified,
    subtitleMono: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.bouncyClickable(onClick = onClick)
    } else Modifier

    // 图标底色 / 前景色的解析：
    // ① 显式给了不透明底色 -> 白图标（保证对比度）
    // ② 显式给了半透明底色 -> 用色调前景或显式 tint
    // ③ 什么都没给 -> 按 tone 取
    val resolvedBg: Color
    val resolvedFg: Color
    when {
        iconBackground != Color.Unspecified && iconBackground.alpha >= 0.5f -> {
            resolvedBg = iconBackground
            resolvedFg = if (iconTint != Color.Unspecified) iconTint else Color.White
        }
        iconBackground != Color.Unspecified -> {
            resolvedBg = iconBackground
            resolvedFg = if (iconTint != Color.Unspecified) iconTint else toneForeground(tone)
        }
        else -> {
            resolvedBg = toneBackground(tone)
            resolvedFg = if (iconTint != Color.Unspecified) iconTint else toneForeground(tone)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = AppSpacing.pageH, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AppShapes.icon)
                    .background(resolvedBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = resolvedFg,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = IosTypography.RowTitle,
                color = if (titleColor != Color.Unspecified) titleColor else IosColors.Label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = if (subtitleMono) IosTypography.Mono else IosTypography.RowSub,
                    color = if (subtitleColor != Color.Unspecified) subtitleColor else IosColors.SecondaryLabel,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = IosTypography.Footnote,
                fontWeight = FontWeight.Medium,
                color = if (trailingTextColor != Color.Unspecified) trailingTextColor else IosColors.SecondaryLabel
            )
        }

        if (trailingContent != null) {
            trailingContent()
        }

        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (chevronColor != Color.Unspecified) chevronColor else IosColors.TertiaryLabel,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// =============================================================================
// 开关 / 分段控件
// =============================================================================

/**
 * Apple UISwitch（51×31dp，弹簧物理）
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useBrandColor: Boolean = false
) {
    val offTrackColor = Color(0x24FFFFFF)
    val onColor = if (useBrandColor) IosColors.SystemBlue else IosColors.SystemGreen
    val trackColor by animateColorAsState(
        targetValue = if (checked) onColor else offTrackColor,
        animationSpec = spring(),
        label = "iosSwitchTrack"
    )

    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 20f else 0f,
        animationSpec = MotionTokens.BouncySpringSpec,
        label = "iosSwitchThumb"
    )

    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.dp)
                .size(27.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * Apple HIG 分段控件
 */
@Composable
fun <T> IosSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() }
) {
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val activePillColor = IosColors.Surface3

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp),
        shape = RoundedCornerShape(9.dp),
        color = IosColors.TertiarySystemFill
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) activePillColor else Color.Transparent)
                        .bouncyClickable { onItemSelected(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        style = IosTypography.Caption1,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) IosColors.Label else IosColors.SecondaryLabel
                    )
                }
            }
        }
    }
}

// =============================================================================
// 按钮体系
// =============================================================================

/**
 * 主按钮　设计稿 .btn--primary —— 52dp / r16 / 16-600 / 品牌蓝 + 蓝色光晕
 */
@Composable
fun IosPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = IosColors.SystemBlue,
    contentColor: Color = Color.White,
    icon: ImageVector? = null,
    height: Dp = 52.dp
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppShapes.button)
            .bouncyClickable(onClick = onClick),
        shape = AppShapes.button,
        color = containerColor,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = IosTypography.ButtonText,
                color = contentColor
            )
        }
    }
}

/**
 * 次级按钮　设计稿 .btn--ghost —— 46dp / r16 / 白 7% 底 + 白 7% 描边
 */
@Composable
fun IosGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    height: Dp = 46.dp,
    fillColor: Color = Color.Unspecified
) {
    val fg = if (contentColor != Color.Unspecified) contentColor else IosColors.Label
    Box(
        modifier = modifier
            .clip(AppShapes.button)
            .background(if (fillColor != Color.Unspecified) fillColor else Color(0x12FFFFFF))
            .border(1.dp, IosColors.Line, AppShapes.button)
            .bouncyClickable(onClick = onClick)
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (iconTint != Color.Unspecified) iconTint else fg,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = IosTypography.ButtonSmall,
                color = fg,
                maxLines = 1
            )
        }
    }
}

/**
 * 禁用态按钮　设计稿 .btn--disabled —— 白 5% 底 + 三级文字
 */
@Composable
fun IosDisabledButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 46.dp
) {
    Box(
        modifier = modifier
            .clip(AppShapes.button)
            .background(Color(0x0DFFFFFF))
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = IosColors.TertiaryLabel, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = IosTypography.ButtonSmall,
                color = IosColors.TertiaryLabel
            )
        }
    }
}

// =============================================================================
// 芯片 / 徽标
// =============================================================================

/** 特性标签　设计稿 .chip —— 11.5-600 / r9 / 白 6% 底 + 描边 */
@Composable
fun IosChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(AppShapes.chip)
            .background(Color(0x0FFFFFFF))
            .border(1.dp, IosColors.Line, AppShapes.chip)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = IosTypography.Chip,
            color = IosColors.SecondaryLabel
        )
    }
}

/** 状态徽标　设计稿 .badge / .badge.run */
@Composable
fun IosStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    running: Boolean = false
) {
    val bg = if (running) IosColors.SystemGreen.copy(alpha = 0.15f) else Color(0x14FFFFFF)
    val fg = if (running) IosColors.SystemGreen else IosColors.SecondaryLabel
    val dot = if (running) IosColors.SystemGreen else IosColors.TertiaryLabel

    Row(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            style = IosTypography.ButtonSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

/** 版本号芯片　设计稿 .ver-chip —— mono 13 / brand-soft / 蓝 12% 底 */
@Composable
fun IosVerChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(IosColors.SystemBlue.copy(alpha = 0.12f))
            .padding(horizontal = 13.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = IosTypography.Mono13,
            color = IosColors.BrandSoft
        )
    }
}

// =============================================================================
// 警告条 / 键值对 / 页面头 / 搜索框
// =============================================================================

/**
 * 警告条　设计稿 .alert —— 橙渐变 + 橙描边 + 36dp 图标 + 标题 14-700 + 行动链接
 */
@Composable
fun IosAlertBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.pageH)
            .clip(AppShapes.card)
            .background(
                Brush.linearGradient(
                    listOf(
                        IosColors.SystemOrange.copy(alpha = 0.18f),
                        IosColors.SystemOrange.copy(alpha = 0.07f)
                    )
                )
            )
            .border(1.dp, IosColors.SystemOrange.copy(alpha = 0.28f), AppShapes.card)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(IosColors.SystemOrange.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = IosColors.SystemOrange,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = IosTypography.RowTitle,
                color = Color(0xFFFFD08A)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = IosTypography.RowSub,
                color = Color(0xC7FFDCAA)
            )
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(AppShapes.chip)
                        .bouncyClickable(onClick = onAction),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = actionText,
                        style = IosTypography.ButtonSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD08A)
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFFFD08A),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/** 键值对行　设计稿 .kv —— 标签 13.5 次色 / 取值 14-600 */
@Composable
fun IosKvRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.pageH + 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = IosTypography.KvLabel,
            color = IosColors.SecondaryLabel,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = value,
            style = if (mono) IosTypography.Mono13 else IosTypography.KvValue,
            color = IosColors.Label
        )
    }
}

/**
 * 页面头　设计稿 .page-head —— kicker 13 次色 + 30-800 大标题 + 徽标 + 版本芯片
 */
@Composable
fun IosPageHead(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    badgeRunning: Boolean = false,
    chipText: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppSpacing.pageH + 4.dp, end = AppSpacing.pageH, top = 10.dp, bottom = 22.dp)
    ) {
        Text(
            text = kicker,
            style = IosTypography.Footnote,
            color = IosColors.SecondaryLabel
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = IosTypography.Display,
                color = IosColors.Label,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            if (badgeText != null) {
                IosStatusBadge(text = badgeText, running = badgeRunning)
                Spacer(Modifier.width(10.dp))
            }
            if (chipText != null) {
                IosVerChip(text = chipText)
            }
        }
    }
}

/**
 * 搜索框　设计稿 .search —— 52dp / r18 / 白 6% 底 + 6% 描边，聚焦时描边转品牌蓝
 */
@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) Color(0x17FFFFFF) else Color(0x0FFFFFFF)
    val borderColor = if (focused) IosColors.SystemBlue.copy(alpha = 0.4f) else Color(0x0FFFFFFF)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = IosColors.SecondaryLabel,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = IosTypography.Subheadline,
                    color = IosColors.TertiaryLabel
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = IosColors.Label,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(IosColors.SystemBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
        }
    }
}

// =============================================================================
// 浮层控件
// =============================================================================

/**
 * 玻璃图标按钮　设计稿 .icon-btn —— 48dp / r18 / 玻璃 / active 态蓝底
 */
@Composable
fun IosGlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = LocalLiquidGlassEnabled.current,
    active: Boolean = false,
    activeColor: Color = IosColors.SystemBlue,
    size: Dp = 48.dp,
    iconSize: Dp = 21.dp,
    contentDescription: String? = null
) {
    val shape = if (size <= 44.dp) AppShapes.button else AppShapes.iconLg
    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(
                isLiquidGlass = isLiquidGlass,
                shape = shape,
                elevation = 10.dp,
                containerColor = if (active) activeColor.copy(alpha = 0.22f) else null
            )
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) activeColor else IosColors.Label,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 多图标玻璃胶囊　设计稿 .iconpill
 */
@Composable
fun IosGlassIconPill(
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = LocalLiquidGlassEnabled.current,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .liquidGlass(
                isLiquidGlass = isLiquidGlass,
                shape = RoundedCornerShape(22.dp),
                elevation = 12.dp
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

/** 胶囊内单个图标按钮　设计稿 .iconpill button —— 38dp / r14 */
@Composable
fun IosPillIcon(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = IosColors.BrandSoft,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) IosColors.SystemBlue.copy(alpha = 0.16f) else Color.Transparent)
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) activeColor else IosColors.Label,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * 模式胶囊　设计稿 .pill / .pill--solid / .pill--ghost —— 48dp / 全圆角 / 15-600
 */
@Composable
fun IosModePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    solid: Boolean = true
) {
    val bg = if (solid) IosColors.SystemBlue else Color(0x12FFFFFF)
    val fg = if (solid) Color.White else IosColors.Label

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(AppShapes.pill)
            .then(
                if (solid) Modifier.shadow(
                    elevation = 12.dp,
                    shape = AppShapes.pill,
                    spotColor = IosColors.SystemBlue.copy(alpha = 0.9f),
                    ambientColor = Color.Transparent
                ) else Modifier.border(1.dp, IosColors.Line, AppShapes.pill)
            )
            .background(bg)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = IosTypography.Subheadline,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1
        )
    }
}

/**
 * 中心定位 Pin　设计稿 .pin-wrap / .pin-tip —— 胶囊提示条 + 46×56 蓝色水滴
 */
@Composable
fun IosCenterPin(
    label: String,
    modifier: Modifier = Modifier,
    pinColor: Color = IosColors.SystemBlue,
    icon: ImageVector? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 7.dp,
                    shape = AppShapes.pill,
                    spotColor = Color.Black.copy(alpha = 0.24f),
                    ambientColor = Color.Transparent
                )
                .clip(AppShapes.pill)
                .background(IosColors.PinTipBg)
                .border(1.dp, IosColors.Line, AppShapes.pill)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = IosColors.BrandOnGlass,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = label,
                style = IosTypography.ButtonSmall,
                fontWeight = FontWeight.SemiBold,
                color = IosColors.Label,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(10.dp))
        // 水滴形 Pin（46 × 56）
        androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 46.dp, height = 56.dp)) {
            val w = size.width
            val h = size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w / 2f, 0f)
                cubicTo(w * 0.12f, 0f, 0f, h * 0.22f, 0f, h * 0.38f)
                cubicTo(0f, h * 0.66f, w / 2f, h, w / 2f, h)
                cubicTo(w / 2f, h, w, h * 0.66f, w, h * 0.38f)
                cubicTo(w, h * 0.22f, w * 0.88f, 0f, w / 2f, 0f)
                close()
            }
            drawPath(path, color = pinColor)
            drawCircle(color = Color.White, radius = w * 0.17f, center = Offset(w / 2f, h * 0.38f))
        }
        Spacer(Modifier.height(3.dp))
        // 落地阴影
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 7.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        )
    }
}
