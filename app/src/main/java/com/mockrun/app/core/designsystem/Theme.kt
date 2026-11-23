package com.mockrun.app.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val BrandSeed = Color(0xFF2F7BFF)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF005AC1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    inversePrimary = Color(0xFFADC6FF),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF3F6374),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC2E8FC),
    onTertiaryContainer = Color(0xFF001F2A),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceTint = Color(0xFF005AC1),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceDim = Color(0xFFD8DAE0),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainerLowest = Color(0xFFFFFFFF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    inversePrimary = Color(0xFF005AC1),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFA7CDE0),
    onTertiary = Color(0xFF093544),
    tertiaryContainer = Color(0xFF264B5A),
    onTertiaryContainer = Color(0xFFC2E8FC),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFADC6FF),
    inverseSurface = Color(0xFFE1E2E8),
    inverseOnSurface = Color(0xFF2E3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainerLowest = Color(0xFF0C0E13)
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
    success = Color(0xFF146C2E),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFA6F4B8),
    onSuccessContainer = Color(0xFF002109),
    warning = Color(0xFF8A5100),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDDB8),
    onWarningContainer = Color(0xFF2C1700),
    info = Color(0xFF005AC1),
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFD8E2FF),
    onInfoContainer = Color(0xFF001A41)
)

private val DarkSemanticColors = AppSemanticPalette(
    success = Color(0xFF7DDA95),
    onSuccess = Color(0xFF003914),
    successContainer = Color(0xFF005322),
    onSuccessContainer = Color(0xFFA6F4B8),
    warning = Color(0xFFFFB95C),
    onWarning = Color(0xFF492900),
    warningContainer = Color(0xFF6B3A00),
    onWarningContainer = Color(0xFFFFDDB8),
    info = Color(0xFFADC6FF),
    onInfo = Color(0xFF002E69),
    infoContainer = Color(0xFF004494),
    onInfoContainer = Color(0xFFD8E2FF)
)

val LocalAppSemanticColors = staticCompositionLocalOf { LightSemanticColors }
val LocalDynamicColorEnabled = compositionLocalOf { false }
val LocalSetDynamicColor = compositionLocalOf<(Boolean) -> Unit> { {} }

object AppSemanticColors {
    val current: AppSemanticPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSemanticColors.current
}

val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object AppShapes {
    val phone = RoundedCornerShape(28.dp)
    val sheet = RoundedCornerShape(28.dp)
    val card = RoundedCornerShape(16.dp)
    val panel = RoundedCornerShape(12.dp)
    val bar = RoundedCornerShape(28.dp)
    val button = RoundedCornerShape(8.dp)
    val icon = RoundedCornerShape(12.dp)
    val iconLg = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(8.dp)
    val pill = RoundedCornerShape(999.dp)
    val pill24 = RoundedCornerShape(24.dp)
}

object AppSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp

    val pageH = 16.dp
    val mapBottomInset = 102.dp
    val pageBottomInset = 122.dp

    val mapBottomInsetComputed: Dp
        @Composable
        get() = ScreenInsets.bottomInset(extra = 8.dp)

    val pageBottomInsetComputed: Dp
        @Composable
        get() = ScreenInsets.bottomInset(extra = 24.dp)
}

val AppTypography = Typography(
    displayLarge = IosTypography.DisplayLarge,
    displayMedium = IosTypography.DisplayMedium,
    displaySmall = IosTypography.DisplaySmall,
    headlineLarge = IosTypography.HeadlineLarge,
    headlineMedium = IosTypography.HeadlineMedium,
    headlineSmall = IosTypography.HeadlineSmall,
    titleLarge = IosTypography.TitleLarge,
    titleMedium = IosTypography.TitleMedium,
    titleSmall = IosTypography.TitleSmall,
    bodyLarge = IosTypography.BodyLarge,
    bodyMedium = IosTypography.BodyMedium,
    bodySmall = IosTypography.BodySmall,
    labelLarge = IosTypography.LabelLarge,
    labelMedium = IosTypography.LabelMedium,
    labelSmall = IosTypography.LabelSmall
)

@Composable
fun MockRunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        supportsDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val iosPalette = colorScheme.toIosColorPalette(darkTheme)

    CompositionLocalProvider(
        LocalIosColors provides iosPalette,
        LocalAppSemanticColors provides semanticColors,
        LocalDynamicColorEnabled provides supportsDynamic
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = IosShapes,
            typography = AppTypography,
            content = content
        )
    }
}
