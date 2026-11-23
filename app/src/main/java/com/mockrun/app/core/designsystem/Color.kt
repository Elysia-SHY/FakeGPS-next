package com.mockrun.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Legacy design-token aliases.
 *
 * Pages still reference the historical Ios* names while the UI is migrated. The
 * semantic palette below resolves those names through Material Design 3 so the
 * old API and the new theme cannot drift into two independent color systems.
 */
object DesignTokens {
    val Bg0 = Color(0xFF111318)
    val Bg1 = Color(0xFF191C20)
    val Surface = Color(0xFF1D2024)
    val Surface2 = Color(0xFF282A2F)
    val Surface3 = Color(0xFF33353A)
    val Line = Color(0xFF44474E)
    val LineSoft = Color(0xFF37393E)

    val Brand = BrandSeed
    val BrandPress = Color(0xFF005AC1)
    val BrandSoft = Color(0xFFADC6FF)
    val Ok = Color(0xFF7DDA95)
    val Warn = Color(0xFFFFB95C)
    val Danger = Color(0xFFFFB4AB)
    val Violet = Color(0xFFC8BFFF)

    val T1 = Color(0xFFE1E2E8)
    val T2 = Color(0xFFC4C6D0)
    val T3 = Color(0xFF8E9099)

    // Kept only for source compatibility; these roles are no longer rendered as glass.
    val GlassBg = Color(0xFF1D2024)
    val GlassBgBar = Color(0xFF282A2F)
    val GlassLine = Color(0xFF44474E)
    val GlassHighlight = Color.Transparent
    val GlassBgOpaque = Color(0xFF1D2024)
    val GlassBgBarOpaque = Color(0xFF282A2F)

    val LBg0 = Color(0xFFF8F9FF)
    val LBg1 = Color(0xFFF2F3F9)
    val LSurface = Color(0xFFFFFFFF)
    val LSurface2 = Color(0xFFECEEF4)
    val LSurface3 = Color(0xFFE1E2E8)
    val LLine = Color(0xFFC4C6D0)
    val LLineSoft = Color(0xFFE0E2EC)
    val LGlassBg = Color(0xFFFFFFFF)
    val LGlassBgBar = Color(0xFFF2F3F9)
    val LGlassLine = Color(0xFFC4C6D0)
    val LGlassHighlight = Color.Transparent
    val LGlassBgOpaque = Color(0xFFFFFFFF)
    val LGlassBgBarOpaque = Color(0xFFF2F3F9)

    val LPinTipBg = Color(0xFFFFFFFF)
    val DPinTipBg = Color(0xFF282A2F)
}

data class IosColorPalette(
    val isDark: Boolean,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,
    val systemGroupedBackground: Color,
    val secondaryGroupedBackground: Color,
    val tertiaryGroupedBackground: Color,
    val systemFill: Color,
    val secondarySystemFill: Color,
    val tertiarySystemFill: Color,
    val quaternarySystemFill: Color,
    val separator: Color,
    val opaqueSeparator: Color,
    val ultraThinMaterial: Color,
    val thinMaterial: Color,
    val frostedCapsule: Color,
    val hairlineBorder: Color,
    val glassTrack: Color,
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
    val glassBgBar: Color,
    val pinTipBg: Color
)

fun ColorScheme.toIosColorPalette(isDark: Boolean) = IosColorPalette(
    isDark = isDark,
    label = onSurface,
    secondaryLabel = onSurfaceVariant,
    tertiaryLabel = outline,
    quaternaryLabel = outlineVariant,
    systemGroupedBackground = background,
    secondaryGroupedBackground = surfaceContainer,
    tertiaryGroupedBackground = surfaceContainerHigh,
    systemFill = surfaceContainerHighest,
    secondarySystemFill = surfaceContainerHigh,
    tertiarySystemFill = surfaceContainer,
    quaternarySystemFill = surfaceContainerLow,
    separator = outlineVariant,
    opaqueSeparator = outline,
    ultraThinMaterial = surfaceContainerLow,
    thinMaterial = surfaceContainer,
    frostedCapsule = surfaceContainerHigh,
    hairlineBorder = outlineVariant,
    glassTrack = surfaceContainerHighest,
    bg1 = surfaceContainerLow,
    surface2 = surfaceContainer,
    surface3 = surfaceContainerHigh,
    line = outlineVariant,
    lineSoft = outlineVariant,
    brandPress = onPrimaryContainer,
    brandSoft = primary,
    glassBg = surfaceContainer,
    glassLine = outlineVariant,
    glassHighlight = Color.Transparent,
    glassBgBar = surfaceContainerHigh,
    pinTipBg = inverseSurface
)

val LightIosColorPalette = IosColorPalette(
    isDark = false,
    label = Color(0xFF191C20),
    secondaryLabel = Color(0xFF44474E),
    tertiaryLabel = Color(0xFF74777F),
    quaternaryLabel = Color(0xFFC4C6D0),
    systemGroupedBackground = Color(0xFFF8F9FF),
    secondaryGroupedBackground = Color(0xFFECEEF4),
    tertiaryGroupedBackground = Color(0xFFE6E8EE),
    systemFill = Color(0xFFE1E2E8),
    secondarySystemFill = Color(0xFFE6E8EE),
    tertiarySystemFill = Color(0xFFECEEF4),
    quaternarySystemFill = Color(0xFFF2F3F9),
    separator = Color(0xFFC4C6D0),
    opaqueSeparator = Color(0xFF74777F),
    ultraThinMaterial = Color(0xFFF2F3F9),
    thinMaterial = Color(0xFFECEEF4),
    frostedCapsule = Color(0xFFE6E8EE),
    hairlineBorder = Color(0xFFC4C6D0),
    glassTrack = Color(0xFFE1E2E8),
    bg1 = Color(0xFFF2F3F9),
    surface2 = Color(0xFFECEEF4),
    surface3 = Color(0xFFE6E8EE),
    line = Color(0xFFC4C6D0),
    lineSoft = Color(0xFFE0E2EC),
    brandPress = Color(0xFF001A41),
    brandSoft = Color(0xFF005AC1),
    glassBg = Color(0xFFECEEF4),
    glassLine = Color(0xFFC4C6D0),
    glassHighlight = Color.Transparent,
    glassBgBar = Color(0xFFE6E8EE),
    pinTipBg = Color(0xFF2E3036)
)

val DarkIosColorPalette = IosColorPalette(
    isDark = true,
    label = Color(0xFFE1E2E8),
    secondaryLabel = Color(0xFFC4C6D0),
    tertiaryLabel = Color(0xFF8E9099),
    quaternaryLabel = Color(0xFF44474E),
    systemGroupedBackground = Color(0xFF111318),
    secondaryGroupedBackground = Color(0xFF1D2024),
    tertiaryGroupedBackground = Color(0xFF282A2F),
    systemFill = Color(0xFF33353A),
    secondarySystemFill = Color(0xFF282A2F),
    tertiarySystemFill = Color(0xFF1D2024),
    quaternarySystemFill = Color(0xFF191C20),
    separator = Color(0xFF44474E),
    opaqueSeparator = Color(0xFF8E9099),
    ultraThinMaterial = Color(0xFF191C20),
    thinMaterial = Color(0xFF1D2024),
    frostedCapsule = Color(0xFF282A2F),
    hairlineBorder = Color(0xFF44474E),
    glassTrack = Color(0xFF33353A),
    bg1 = Color(0xFF191C20),
    surface2 = Color(0xFF1D2024),
    surface3 = Color(0xFF282A2F),
    line = Color(0xFF44474E),
    lineSoft = Color(0xFF37393E),
    brandPress = Color(0xFFD8E2FF),
    brandSoft = Color(0xFFADC6FF),
    glassBg = Color(0xFF1D2024),
    glassLine = Color(0xFF44474E),
    glassHighlight = Color.Transparent,
    glassBgBar = Color(0xFF282A2F),
    pinTipBg = Color(0xFFE1E2E8)
)

val LocalIosColors = staticCompositionLocalOf { LightIosColorPalette }

object IosColors {
    val current: IosColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalIosColors.current

    val SystemBlue: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val SystemGreen: Color
        @Composable
        @ReadOnlyComposable
        get() = AppSemanticColors.current.success

    val SystemRed: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.error

    val SystemOrange: Color
        @Composable
        @ReadOnlyComposable
        get() = AppSemanticColors.current.warning

    val SystemYellow: Color
        @Composable
        @ReadOnlyComposable
        get() = AppSemanticColors.current.warning

    val SystemPurple: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.tertiary

    val SystemTeal: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondary

    val SystemIndigo: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val SystemPink: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.tertiary

    val SystemMint: Color
        @Composable
        @ReadOnlyComposable
        get() = AppSemanticColors.current.success

    val SystemCyan: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondary

    val BrandPress: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer

    val BrandSoft: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val Violet: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.tertiary

    val SystemGray: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline

    val SystemGray2: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outlineVariant

    val SystemGray3: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceVariant

    val SystemGray4: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHigh

    val SystemGray5: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainer

    val SystemGray6: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerLow

    val Label: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.label

    val SecondaryLabel: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.secondaryLabel

    val TertiaryLabel: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.tertiaryLabel

    val QuaternaryLabel: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.quaternaryLabel

    val SystemGroupedBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.systemGroupedBackground

    val SecondaryGroupedBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.secondaryGroupedBackground

    val TertiaryGroupedBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.tertiaryGroupedBackground

    val SystemFill: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.systemFill

    val SecondarySystemFill: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.secondarySystemFill

    val TertiarySystemFill: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.tertiarySystemFill

    val QuaternarySystemFill: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.quaternarySystemFill

    val Separator: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.separator

    val OpaqueSeparator: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.opaqueSeparator

    val UltraThinMaterial: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.ultraThinMaterial

    val ThinMaterial: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.thinMaterial

    val Bg1: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.bg1

    val Surface2: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.surface2

    val Surface3: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.surface3

    val Line: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.line

    val LineSoft: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.lineSoft

    val GlassBg: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.glassBg

    val GlassLine: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.glassLine

    val GlassHighlight: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.glassHighlight

    val GlassBgBar: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.glassBgBar

    val PinTipBg: Color
        @Composable
        @ReadOnlyComposable
        get() = IosColors.current.pinTipBg

    val BrandOnGlass: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary
}

val IosBlue = BrandSeed
val IosGreen = Color(0xFF30D158)
val IosRed = Color(0xFFFF453A)
val IosOrange = Color(0xFFFF9F0A)
val IosYellow = Color(0xFFFFD60A)
val IosPurple = Color(0xFF7C4DFF)
val IosTeal = Color(0xFF64D2FF)
val IosIndigo = Color(0xFF5E5CE6)
val IosGray = Color(0xFF8E8E93)
val IosGray2 = Color(0xFFAEAEB2)
val IosGray3 = Color(0xFFC7C7CC)
val IosGray4 = Color(0xFFD1D1D6)
val IosGray5 = Color(0xFFE5E5EA)
val IosGray6 = Color(0xFFF2F2F7)

val IosSystemBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = IosColors.SystemGroupedBackground

val IosCardBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = IosColors.SecondaryGroupedBackground

val IosCardBackgroundDark = DesignTokens.Surface
val IosFrostedLight = Color(0xFFFFFFFF)
val IosFrostedDark = Color(0xFF1D2024)

val IosFrostedCapsule: Color
    @Composable
    @ReadOnlyComposable
    get() = IosColors.current.frostedCapsule

val IosFrostedDarkCapsule = DesignTokens.GlassBg
val IosFrostedPill = Color(0xFFF2F3F9)

val IosHairlineBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = IosColors.current.hairlineBorder

val IosHairlineBorderDark = DesignTokens.GlassLine

val IosGlassTrack: Color
    @Composable
    @ReadOnlyComposable
    get() = IosColors.current.glassTrack

val IosGlassTrackLight = Color(0xFFE1E2E8)

val IosSeparator: Color
    @Composable
    @ReadOnlyComposable
    get() = IosColors.current.separator

val IosSubtleHighlight = Color.Transparent
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

object IosSemanticColors {
    val Danger = Color(0xFFFFB4AB)
    val DangerLight = Color(0xFFBA1A1A)
    val DangerContainer = Color(0xFF93000A)
    val DangerContainerLight = Color(0xFFFFDAD6)
    val Warning = Color(0xFFFFB95C)
    val WarningLight = Color(0xFF8A5100)
    val WarningContainer = Color(0xFF6B3A00)
    val WarningContainerLight = Color(0xFFFFDDB8)
    val Success = Color(0xFF7DDA95)
    val SuccessLight = Color(0xFF146C2E)
    val SuccessContainer = Color(0xFF005322)
    val SuccessContainerLight = Color(0xFFA6F4B8)
    val Info = Color(0xFFADC6FF)
    val InfoLight = Color(0xFF005AC1)
    val InfoContainer = Color(0xFF004494)
    val InfoContainerLight = Color(0xFFD8E2FF)
}

data class IosSemanticPalette(
    val danger: Color,
    val dangerContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val success: Color,
    val successContainer: Color,
    val info: Color,
    val infoContainer: Color
)

val DarkSemanticPalette = IosSemanticPalette(
    danger = IosSemanticColors.Danger,
    dangerContainer = IosSemanticColors.DangerContainer,
    warning = IosSemanticColors.Warning,
    warningContainer = IosSemanticColors.WarningContainer,
    success = IosSemanticColors.Success,
    successContainer = IosSemanticColors.SuccessContainer,
    info = IosSemanticColors.Info,
    infoContainer = IosSemanticColors.InfoContainer
)

val LightSemanticPalette = IosSemanticPalette(
    danger = IosSemanticColors.DangerLight,
    dangerContainer = IosSemanticColors.DangerContainerLight,
    warning = IosSemanticColors.WarningLight,
    warningContainer = IosSemanticColors.WarningContainerLight,
    success = IosSemanticColors.SuccessLight,
    successContainer = IosSemanticColors.SuccessContainerLight,
    info = IosSemanticColors.InfoLight,
    infoContainer = IosSemanticColors.InfoContainerLight
)

fun IosSemanticColors.forDark(isDark: Boolean): IosSemanticPalette =
    if (isDark) DarkSemanticPalette else LightSemanticPalette

val IosDanger = IosSemanticColors.Danger
val IosWarning = IosSemanticColors.Warning
val IosSuccess = IosSemanticColors.Success
val IosInfo = IosSemanticColors.Info

val PrimaryGreen = IosGreen
val PrimaryGreenDark = Color(0xFF146C2E)
val AccentOrange = IosOrange
val SurfaceDark = IosCardBackgroundDark
val BackgroundDark = DesignTokens.Bg0
val Purple80 = IosPurple
val PurpleGrey80 = IosGray2
val Pink80 = Color(0xFFFF375F)
