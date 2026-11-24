package com.mockrun.app.core.designsystem

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.backdrop.Backdrop

/**
 * A glass or solid Atlas surface with identical geometry in both modes.
 *
 * Content-heavy lists should stay on [AtlasGroup] even while glass is enabled;
 * reserve this component for navigation, floating tools, sheets and dialogs.
 */
@Composable
fun AtlasSurface(
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = LocalLiquidGlassEnabled.current,
    shape: Shape = AppShapes.card,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = 6.dp,
    borderWidth: Dp = 1.dp,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    content: @Composable BoxScope.() -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier.liquidGlass(
                isLiquidGlass = isLiquidGlass,
                shape = shape,
                elevation = elevation,
                containerColor = containerColor,
                borderWidth = borderWidth,
                backdrop = backdrop
            ),
            content = content
        )
    }
}

/**
 * A glass card with column semantics. It uses the page-level Backdrop as a
 * sibling source, so the card never samples its own rendered content.
 */
@Composable
fun AtlasGlassGroup(
    modifier: Modifier = Modifier,
    isLiquidGlass: Boolean = LocalLiquidGlassEnabled.current,
    shape: Shape = AppShapes.card,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    elevation: Dp = 5.dp,
    borderWidth: Dp = 0.75.dp,
    tintAlpha: Float? = null,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier.liquidGlass(
            isLiquidGlass = isLiquidGlass,
            shape = shape,
            elevation = elevation,
            containerColor = containerColor,
            borderWidth = borderWidth,
            tintAlpha = tintAlpha,
            backdrop = backdrop
        )
    ) {
        Column(content = content)
    }
}

/** A solid, readable content group. It never performs backdrop capture. */
@Composable
fun AtlasGroup(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(AppShapes.card)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                shape = AppShapes.card
            ),
        content = content
    )
}

@Composable
fun AtlasPageHeader(
    title: String,
    description: String,
    status: String? = null,
    statusTone: AtlasStatusTone = AtlasStatusTone.NEUTRAL,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (status != null) {
            AtlasStatusPill(text = status, tone = statusTone)
        }
    }
}

@Composable
fun AtlasSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (supporting != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = action
            )
        }
    }
}

enum class AtlasStatusTone {
    NEUTRAL,
    ACTIVE,
    WARNING,
    DANGER
}

@Composable
fun AtlasStatusPill(
    text: String,
    tone: AtlasStatusTone,
    modifier: Modifier = Modifier
) {
    val semantic = AppSemanticColors.current
    val (container, content, dot) = when (tone) {
        AtlasStatusTone.NEUTRAL -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline
        )
        AtlasStatusTone.ACTIVE -> Triple(
            semantic.successContainer,
            semantic.onSuccessContainer,
            semantic.success
        )
        AtlasStatusTone.WARNING -> Triple(
            semantic.warningContainer,
            semantic.onWarningContainer,
            semantic.warning
        )
        AtlasStatusTone.DANGER -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
    }

    Row(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(container)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1
        )
    }
}

@Composable
fun AtlasMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    accent: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                color = accent,
                maxLines = 1
            )
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * API 31+ window blur for dialogs that live in their own window. The platform
 * blur samples the host window beneath the dialog; the in-dialog Backdrop
 * material supplies refraction, vibrancy and highlights. It is a no-op in
 * solid mode and on older devices.
 */
@Composable
fun GlassDialogWindowEffect(enabled: Boolean = LocalLiquidGlassEnabled.current) {
    if (!GlassCapabilities.supportsBackdropBlur) return
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.setBackgroundBlurRadius(28)
                }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.setBackgroundBlurRadius(0)
                }
            }
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.setBackgroundBlurRadius(0)
                }
            }
        }
    }
}
