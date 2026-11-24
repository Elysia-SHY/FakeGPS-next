package com.mockrun.app.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// Row tones
// =============================================================================

enum class IosRowTone {
    NEUTRAL,
    BRAND,
    DANGER,
    OK,
    WARN
}

@Composable
private fun toneBackground(tone: IosRowTone): Color {
    val scheme = MaterialTheme.colorScheme
    val semantic = AppSemanticColors.current
    return when (tone) {
        IosRowTone.NEUTRAL -> scheme.surfaceContainerHighest
        IosRowTone.BRAND -> scheme.primaryContainer
        IosRowTone.DANGER -> scheme.errorContainer
        IosRowTone.OK -> semantic.successContainer
        IosRowTone.WARN -> semantic.warningContainer
    }
}

@Composable
private fun toneForeground(tone: IosRowTone): Color {
    val scheme = MaterialTheme.colorScheme
    val semantic = AppSemanticColors.current
    return when (tone) {
        IosRowTone.NEUTRAL -> scheme.onSurfaceVariant
        IosRowTone.BRAND -> scheme.onPrimaryContainer
        IosRowTone.DANGER -> scheme.onErrorContainer
        IosRowTone.OK -> semantic.onSuccessContainer
        IosRowTone.WARN -> semantic.onWarningContainer
    }
}

// =============================================================================
// Group headers
// =============================================================================

@Composable
fun IosSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = AppSpacing.pageH + 4.dp,
            end = AppSpacing.pageH,
            top = 8.dp,
            bottom = 8.dp
        )
    )
}

@Composable
fun IosGroupHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    linkText: String? = null,
    onLinkClick: (() -> Unit)? = null,
    linkIcon: ImageVector? = Icons.Default.Add
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AppSpacing.pageH + 4.dp,
                end = AppSpacing.pageH,
                top = 4.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (linkText != null && onLinkClick != null) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLinkClick) {
                if (linkIcon != null) {
                    Icon(
                        imageVector = linkIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = linkText,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun IosGroupSub(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = AppSpacing.pageH + 4.dp,
            end = AppSpacing.pageH,
            bottom = 8.dp
        )
    )
}

// =============================================================================
// Cards and surfaces
// =============================================================================

@Composable
fun IosInsetGroupCard(
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = LocalLiquidGlassEnabled.current,
    shape: Shape = AppShapes.card,
    elevation: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.pageH),
        shape = shape,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun IosGlassDock(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.sheet,
    isLiquidGlass: Boolean = LocalLiquidGlassEnabled.current,
    elevation: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    @Suppress("UNUSED_PARAMETER")
    val compatibilityMaterial = Pair(isLiquidGlass, elevation)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(content = content)
    }
}

@Composable
fun IosHairlineDivider(
    startIndent: Dp = 69.dp,
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

// =============================================================================
// Rows
// =============================================================================

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
    val scheme = MaterialTheme.colorScheme
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

    val rowModifier = modifier
        .fillMaxWidth()
        .minTouchTarget()
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    ListItem(
        modifier = rowModifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = if (icon != null) {
            {
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
        } else {
            null
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (titleColor != Color.Unspecified) titleColor else scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = if (subtitle != null) {
            {
                Text(
                    text = subtitle,
                    style = if (subtitleMono) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = if (subtitleColor != Color.Unspecified) {
                        subtitleColor
                    } else {
                        scheme.onSurfaceVariant
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            null
        },
        trailingContent = if (
            trailingText != null || trailingContent != null || showChevron
        ) {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (trailingText != null) {
                        Text(
                            text = trailingText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (trailingTextColor != Color.Unspecified) {
                                trailingTextColor
                            } else {
                                scheme.onSurfaceVariant
                            }
                        )
                    }
                    trailingContent?.invoke()
                    if (showChevron) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = if (chevronColor != Color.Unspecified) {
                                chevronColor
                            } else {
                                scheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        } else {
            null
        }
    )
}

// =============================================================================
// Selection controls
// =============================================================================

@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useBrandColor: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = AppSemanticColors.current
    val checkedTrack = if (useBrandColor) scheme.primary else semantic.success
    val checkedThumb = if (useBrandColor) scheme.onPrimary else semantic.onSuccess
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.minTouchTarget(),
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = checkedThumb,
            checkedTrackColor = checkedTrack,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = scheme.outline,
            uncheckedTrackColor = scheme.surfaceContainerHighest,
            uncheckedBorderColor = scheme.outline
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> IosSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() }
) {
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth()
    ) {
        items.forEachIndexed { index, item ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onItemSelected(item) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = items.size
                ),
                label = {
                    Text(
                        text = labelProvider(item),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

// =============================================================================
// Buttons
// =============================================================================

@Composable
fun IosPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null
) {
    val clickable = enabled && !loading
    val label = if (loading) (loadingText ?: text) else text

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        enabled = clickable,
        shape = AppShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
    val scheme = MaterialTheme.colorScheme
    val resolvedContent = if (contentColor != Color.Unspecified) contentColor else scheme.primary
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = AppShapes.button,
        border = BorderStroke(1.dp, scheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (fillColor != Color.Unspecified) fillColor else Color.Transparent,
            contentColor = resolvedContent
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (iconTint != Color.Unspecified) iconTint else resolvedContent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun IosDisabledButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 46.dp
) {
    OutlinedButton(
        onClick = {},
        modifier = modifier.height(height),
        enabled = false,
        shape = AppShapes.button,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =============================================================================
// Chips and badges
// =============================================================================

@Composable
fun IosChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AppShapes.chip,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

enum class IosBadgeTone { NEUTRAL, RUNNING, WARNING, ERROR, INFO }

@Composable
fun IosStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    running: Boolean = false,
    tone: IosBadgeTone? = null
) {
    val resolved = tone ?: if (running) IosBadgeTone.RUNNING else IosBadgeTone.NEUTRAL
    val scheme = MaterialTheme.colorScheme
    val semantic = AppSemanticColors.current
    val container: Color
    val content: Color
    when (resolved) {
        IosBadgeTone.NEUTRAL -> {
            container = scheme.surfaceContainerHighest
            content = scheme.onSurfaceVariant
        }
        IosBadgeTone.RUNNING -> {
            container = semantic.successContainer
            content = semantic.onSuccessContainer
        }
        IosBadgeTone.WARNING -> {
            container = semantic.warningContainer
            content = semantic.onWarningContainer
        }
        IosBadgeTone.ERROR -> {
            container = scheme.errorContainer
            content = scheme.onErrorContainer
        }
        IosBadgeTone.INFO -> {
            container = scheme.primaryContainer
            content = scheme.onPrimaryContainer
        }
    }

    Surface(
        modifier = modifier,
        shape = AppShapes.pill,
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(content)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun IosVerChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AppShapes.pill,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// =============================================================================
// Banner, key-value rows, page header, search
// =============================================================================

@Composable
fun IosAlertBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null
) {
    val semantic = AppSemanticColors.current
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.pageH),
        shape = AppShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = semantic.warningContainer,
            contentColor = semantic.onWarningContainer
        ),
        border = BorderStroke(1.dp, semantic.warning.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AppShapes.icon)
                    .background(semantic.warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = semantic.onWarningContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = semantic.onWarningContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.onWarningContainer.copy(alpha = 0.82f)
                )
                if (actionText != null && onAction != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = actionText,
                            style = MaterialTheme.typography.labelLarge,
                            color = semantic.onWarningContainer
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = semantic.onWarningContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

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
            .padding(horizontal = AppSpacing.pageH + 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

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
            .padding(
                start = AppSpacing.pageH + 4.dp,
                end = AppSpacing.pageH,
                top = 12.dp,
                bottom = 20.dp
            )
    ) {
        Text(
            text = kicker,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (badgeText != null || chipText != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (badgeText != null) {
                    IosStatusBadge(text = badgeText, running = badgeRunning)
                }
                if (chipText != null) {
                    IosVerChip(text = chipText)
                }
            }
        }
    }
}

enum class IosSearchFieldStyle(
    val height: Dp,
    val cornerRadius: Dp,
    val iconSize: Dp,
    val horizontalPadding: Dp,
    val fontSize: TextUnit
) {
    STANDARD(
        height = 56.dp,
        cornerRadius = 12.dp,
        iconSize = 20.dp,
        horizontalPadding = 16.dp,
        fontSize = 16.sp
    ),
    COMPACT(
        height = 48.dp,
        cornerRadius = 8.dp,
        iconSize = 18.dp,
        horizontalPadding = 12.dp,
        fontSize = 14.sp
    )
}

@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    cornerRadius: Dp? = null,
    style: IosSearchFieldStyle = IosSearchFieldStyle.STANDARD
) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val resolvedHeight = height ?: style.height
    val resolvedCorner = cornerRadius ?: style.cornerRadius
    val shape = RoundedCornerShape(resolvedCorner)
    val borderColor by animateColorAsState(
        targetValue = if (focused) scheme.primary else scheme.outline,
        label = "searchBorder"
    )
    val textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = style.fontSize,
        color = scheme.onSurface
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(resolvedHeight),
        shape = shape,
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        border = BorderStroke(if (focused) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = style.horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(style.iconSize)
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = scheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                )
            }
        }
    }
}

// =============================================================================
// Floating controls
// =============================================================================

@Composable
fun IosGlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = LocalLiquidGlassEnabled.current,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null
) {
    @Suppress("UNUSED_PARAMETER")
    val compatibilityMaterial = isLiquidGlass
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .clip(AppShapes.icon)
            .background(
                if (active) scheme.primaryContainer else scheme.surfaceContainerHigh
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) activeColor else scheme.onSurface,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun IosGlassIconPill(
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = LocalLiquidGlassEnabled.current,
    content: @Composable RowScope.() -> Unit
) {
    @Suppress("UNUSED_PARAMETER")
    val compatibilityMaterial = isLiquidGlass
    Surface(
        modifier = modifier,
        shape = AppShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
fun IosPillIcon(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(AppShapes.icon)
            .background(
                if (active) scheme.primaryContainer else Color.Transparent
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) activeColor else scheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun IosModePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    solid: Boolean = true
) {
    if (solid) {
        Button(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = AppShapes.pill,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = AppShapes.pill,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

/**
 * Map affordance. The 32x40 pin geometry is intentionally retained because map
 * aiming calculations depend on this exact silhouette and size.
 */
@Composable
fun IosCenterPin(
    label: String,
    modifier: Modifier = Modifier,
    pinColor: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 6.dp,
                    shape = AppShapes.pill,
                    spotColor = scheme.scrim.copy(alpha = 0.30f),
                    ambientColor = Color.Transparent
                )
                .clip(AppShapes.pill)
                .background(scheme.surfaceContainerLowest)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(7.dp))
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(width = 32.dp, height = 40.dp)
        ) {
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
            drawCircle(
                color = Color.White,
                radius = w * 0.17f,
                center = Offset(w / 2f, h * 0.38f)
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(width = 15.dp, height = 5.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        )
    }
}
