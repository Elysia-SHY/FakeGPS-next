package com.mockrun.app.ui.theme

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple Human Interface Guidelines (HIG) Inset Grouped Section Header
 */
@Composable
fun IosSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = IosTypography.Footnote,
        color = IosColors.SecondaryLabel,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

/**
 * Apple Inset Grouped Card (16dp rounded corner, secondary background)
 */
@Composable
fun IosInsetGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = IosColors.SecondaryGroupedBackground,
        border = BorderStroke(0.5.dp, IosColors.Separator.copy(alpha = 0.4f)),
        shadowElevation = 0.5.dp
    ) {
        Column(content = content)
    }
}

/**
 * Inset hairline divider for iOS grouped table rows
 */
@Composable
fun IosHairlineDivider(
    startIndent: Dp = 54.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(0.5.dp)
            .background(IosColors.Separator.copy(alpha = 0.35f))
    )
}

/**
 * Apple standard Table Row (icon, title, value/subtitle, chevron/action)
 */
@Composable
fun IosListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Color.White,
    iconBackground: Color = IosColors.SystemBlue,
    trailingText: String? = null,
    showChevron: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.bouncyClickable(onClick = onClick)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = RoundedCornerShape(7.dp),
                color = iconBackground
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = IosTypography.Body,
                color = IosColors.Label,
                fontWeight = FontWeight.Normal
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = IosTypography.Footnote,
                    color = IosColors.SecondaryLabel
                )
            }
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = IosTypography.Body,
                color = IosColors.SecondaryLabel
            )
        }

        if (trailingContent != null) {
            trailingContent()
        }

        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IosColors.SecondaryLabel.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Pixel-Perfect Apple UISwitch (51x31dp, spring physics)
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = LocalIosColors.current.isDark
    val offTrackColor = if (isDark) Color(0xFF39393D) else Color(0xFFE9E9EA)
    val trackColor by animateColorAsState(
        targetValue = if (checked) IosColors.SystemGreen else offTrackColor,
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
 * Apple HIG Segmented Control (sliding white/dark pill selector)
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
    val isDark = LocalIosColors.current.isDark
    val activePillColor = if (isDark) Color(0xFF636366) else Color.White

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
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isSelected) activePillColor else Color.Transparent)
                        .then(if (isSelected) Modifier.shadow(1.dp, RoundedCornerShape(7.dp)) else Modifier)
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

/**
 * Standard iOS Full-Width Action Button (14dp rounded, tactile bounce)
 */
@Composable
fun IosPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = IosColors.SystemBlue,
    contentColor: Color = Color.White,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .bouncyClickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = IosTypography.Headline,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
