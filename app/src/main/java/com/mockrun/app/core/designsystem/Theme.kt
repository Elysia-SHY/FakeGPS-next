package com.mockrun.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val BrandSeed = Color(0xFF0B7376)

/**
 * Atlas visual language:
 * cool mineral neutrals, one deep-teal signal color, amber for caution and
 * coral for destructive actions. No neon or purple-blue gradient systems.
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0B7376),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F2F2),
    onPrimaryContainer = Color(0xFF0B3536),
    inversePrimary = Color(0xFF6FD4D6),
    secondary = Color(0xFF626669),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F2F3),
    onSecondaryContainer = Color(0xFF2A2D2F),
    tertiary = Color(0xFF6B6E71),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1F2F3),
    onTertiaryContainer = Color(0xFF2A2D2F),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111214),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111214),
    surfaceVariant = Color(0xFFF2F3F4),
    onSurfaceVariant = Color(0xFF676B6E),
    surfaceTint = Color(0xFF0B7376),
    inverseSurface = Color(0xFF1D1F20),
    inverseOnSurface = Color(0xFFF4F4F5),
    error = Color(0xFFC43D3D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBEAEA),
    onErrorContainer = Color(0xFF4A0B0B),
    outline = Color(0xFF8B8F92),
    outlineVariant = Color(0xFFE6E7E8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFF1F1F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFF5F5F6),
    surfaceContainerHigh = Color(0xFFEFEFF1),
    surfaceContainerHighest = Color(0xFFE8E9EA)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF69D3D5),
    onPrimary = Color(0xFF0A3637),
    primaryContainer = Color(0xFF1A4B4C),
    onPrimaryContainer = Color(0xFFC4EFEF),
    inversePrimary = Color(0xFF0B7376),
    secondary = Color(0xFFB8BABC),
    onSecondary = Color(0xFF2B2D2E),
    secondaryContainer = Color(0xFF303234),
    onSecondaryContainer = Color(0xFFE8E9EA),
    tertiary = Color(0xFFB8BABC),
    onTertiary = Color(0xFF2B2D2E),
    tertiaryContainer = Color(0xFF303234),
    onTertiaryContainer = Color(0xFFE8E9EA),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF0A0A0B),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF242527),
    onSurfaceVariant = Color(0xFFB6B8BA),
    surfaceTint = Color(0xFF69D3D5),
    inverseSurface = Color(0xFFF4F4F5),
    inverseOnSurface = Color(0xFF1C1D1E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF4A0B0B),
    errorContainer = Color(0xFF5A1718),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF85888A),
    outlineVariant = Color(0xFF2B2D2F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2B2D2F),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0F1012),
    surfaceContainer = Color(0xFF151618),
    surfaceContainerHigh = Color(0xFF1B1C1E),
    surfaceContainerHighest = Color(0xFF232426)
)

data class AppSemanticPalette(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

private val LightSemanticColors = AppSemanticPalette(
    success = Color(0xFF0B7376),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFF2F3F4),
    onSuccessContainer = Color(0xFF2A2D2F),
    warning = Color(0xFF6B6E71),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFF2F3F4),
    onWarningContainer = Color(0xFF2A2D2F),
    info = Color(0xFF0B7376),
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFF2F3F4),
    onInfoContainer = Color(0xFF2A2D2F)
)

private val DarkSemanticColors = AppSemanticPalette(
    success = Color(0xFF69D3D5),
    onSuccess = Color(0xFF0A3637),
    successContainer = Color(0xFF303234),
    onSuccessContainer = Color(0xFFE8E9EA),
    warning = Color(0xFFB8BABC),
    onWarning = Color(0xFF2B2D2E),
    warningContainer = Color(0xFF303234),
    onWarningContainer = Color(0xFFE8E9EA),
    info = Color(0xFF69D3D5),
    onInfo = Color(0xFF0A3637),
    infoContainer = Color(0xFF303234),
    onInfoContainer = Color(0xFFE8E9EA)
)

private fun contentColorFor(background: Color): Color {
    val light = Color.White
    val dark = Color(0xFF111214)
    val lightContrast = ColorUtils.calculateContrast(light.toArgb(), background.toArgb())
    val darkContrast = ColorUtils.calculateContrast(dark.toArgb(), background.toArgb())
    return if (lightContrast >= darkContrast) light else dark
}

private fun ColorScheme.withAccent(argb: Int, darkTheme: Boolean): ColorScheme {
    val primary = Color(argb)
    val onPrimary = contentColorFor(primary)
    val containerBase = if (darkTheme) Color(0xFF151618) else Color.White
    val container = Color(
        ColorUtils.blendARGB(
            primary.toArgb(),
            containerBase.toArgb(),
            if (darkTheme) 0.76f else 0.84f
        )
    )
    val inverse = Color(
        ColorUtils.blendARGB(primary.toArgb(), containerBase.toArgb(), 0.24f)
    )
    return copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = contentColorFor(container),
        inversePrimary = inverse,
        surfaceTint = primary
    )
}

val LocalAppSemanticColors = staticCompositionLocalOf { LightSemanticColors }
val LocalDynamicColorEnabled = compositionLocalOf { false }
val LocalSetDynamicColor = compositionLocalOf<(Boolean) -> Unit> { {} }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

object AppSemanticColors {
    val current: AppSemanticPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSemanticColors.current
}

val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object AppShapes {
    val phone = smoothShape(36.dp)
    val sheet = smoothShape(32.dp)
    val card = smoothShape(24.dp)
    val panel = smoothShape(18.dp)
    val bar = smoothShape(36.dp)
    val button = smoothShape(14.dp)
    val icon = smoothShape(12.dp)
    val iconLg = smoothShape(18.dp)
    val chip = smoothShape(10.dp)
    val pill = SmoothPillShape
    val pill24 = smoothShape(24.dp)
}

object AppSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp

    val pageH = 18.dp
    val mapBottomInset = 102.dp
    val pageBottomInset = 122.dp

    val mapBottomInsetComputed: Dp
        @Composable
        get() = ScreenInsets.bottomInset(extra = 8.dp)

    val pageBottomInsetComputed: Dp
        @Composable
        get() = ScreenInsets.bottomInset(extra = 24.dp)
}

private fun atlasText(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp
)

val AppTypography = Typography(
    displayLarge = atlasText(48, 54, FontWeight.SemiBold),
    displayMedium = atlasText(42, 48, FontWeight.SemiBold),
    displaySmall = atlasText(36, 42, FontWeight.SemiBold),
    headlineLarge = atlasText(32, 38, FontWeight.Bold),
    headlineMedium = atlasText(28, 34, FontWeight.Bold),
    headlineSmall = atlasText(23, 29, FontWeight.Bold),
    titleLarge = atlasText(20, 26, FontWeight.SemiBold),
    titleMedium = atlasText(17, 23, FontWeight.SemiBold),
    titleSmall = atlasText(15, 20, FontWeight.SemiBold),
    bodyLarge = atlasText(16, 24),
    bodyMedium = atlasText(14, 21),
    bodySmall = atlasText(12, 18),
    labelLarge = atlasText(14, 20, FontWeight.SemiBold),
    labelMedium = atlasText(12, 17, FontWeight.Medium),
    labelSmall = atlasText(11, 15, FontWeight.Medium)
)

@Composable
fun MockRunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val accentArgb by AppAppearancePreferences.accentColorArgb
    val colorScheme = remember(darkTheme, accentArgb) {
        val base = if (darkTheme) DarkColorScheme else LightColorScheme
        accentArgb?.let { base.withAccent(it, darkTheme) } ?: base
    }
    val semanticColors = remember(darkTheme, colorScheme, accentArgb) {
        val base = if (darkTheme) DarkSemanticColors else LightSemanticColors
        if (accentArgb == null) {
            base
        } else {
            base.copy(
                success = colorScheme.primary,
                onSuccess = colorScheme.onPrimary,
                info = colorScheme.primary,
                onInfo = colorScheme.onPrimary
            )
        }
    }
    val iosPalette = colorScheme.toIosColorPalette(darkTheme)

    CompositionLocalProvider(
        LocalIosColors provides iosPalette,
        LocalAppSemanticColors provides semanticColors,
        LocalDynamicColorEnabled provides false,
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = IosShapes,
            typography = AppTypography,
            content = content
        )
    }
}
