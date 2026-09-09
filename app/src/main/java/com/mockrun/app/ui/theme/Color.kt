package com.mockrun.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =============================================================================
// Apple Human Interface Guidelines (HIG) Semantic Color Palette
// =============================================================================
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
)

val LightIosColorPalette = IosColorPalette(
    isDark = false,
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    quaternaryLabel = Color(0x2E3C3C43),
    systemGroupedBackground = Color(0xFFF2F2F7),
    secondaryGroupedBackground = Color(0xFFFFFFFF),
    tertiaryGroupedBackground = Color(0xFFFFFFFF),
    systemFill = Color(0x33787880),
    secondarySystemFill = Color(0x29787880),
    tertiarySystemFill = Color(0x1F767680),
    quaternarySystemFill = Color(0x14747480),
    separator = Color(0x473C3C43),
    opaqueSeparator = Color(0xFFC6C6C8),
    ultraThinMaterial = Color(0xE6F8F8F9),
    thinMaterial = Color(0xF2FFFFFF),
    frostedCapsule = Color(0xF2FFFFFF),
    hairlineBorder = Color(0x14000000),
    glassTrack = Color(0x1A000000)
)

val DarkIosColorPalette = IosColorPalette(
    isDark = true,
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    quaternaryLabel = Color(0x29EBEBF5),
    systemGroupedBackground = Color(0xFF000000), // OLED Pure Black
    secondaryGroupedBackground = Color(0xFF1C1C1E), // Elevated Dark Card
    tertiaryGroupedBackground = Color(0xFF2C2C2E),
    systemFill = Color(0x5C787880),
    secondarySystemFill = Color(0x52787880),
    tertiarySystemFill = Color(0x3D767680),
    quaternarySystemFill = Color(0x2E747480),
    separator = Color(0x99545458),
    opaqueSeparator = Color(0xFF38383A),
    ultraThinMaterial = Color(0xD91C1C1E),
    thinMaterial = Color(0xF21C1C1E),
    frostedCapsule = Color(0xF01C1C1E), // Dark Frosted Glass
    hairlineBorder = Color(0x2EFFFFFF),
    glassTrack = Color(0x33FFFFFF)
)

val LocalIosColors = staticCompositionLocalOf { LightIosColorPalette }

object IosColors {
    // 1. System Accent Colors
    val SystemBlue = Color(0xFF007AFF)
    val SystemGreen = Color(0xFF34C759)
    val SystemRed = Color(0xFFFF3B30)
    val SystemOrange = Color(0xFFFF9500)
    val SystemYellow = Color(0xFFFFCC00)
    val SystemPurple = Color(0xFFAF52DE)
    val SystemTeal = Color(0xFF5AC8FA)
    val SystemIndigo = Color(0xFF5856D6)
    val SystemPink = Color(0xFFFF2D55)
    val SystemMint = Color(0xFF00C7BE)
    val SystemCyan = Color(0xFF32ADE6)

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

val IosCardBackgroundDark = Color(0xFF1C1C1E)

// Frosted Glass Translucent Materials
val IosFrostedLight = Color(0xEBFFFFFF)
val IosFrostedDark = Color(0xEB1C1C1E)

val IosFrostedCapsule: Color
    @Composable
    get() = LocalIosColors.current.frostedCapsule

val IosFrostedDarkCapsule = Color(0xF21C1C1E)
val IosFrostedPill = Color(0xE0F2F2F7)

val IosHairlineBorder: Color
    @Composable
    get() = LocalIosColors.current.hairlineBorder

val IosHairlineBorderDark = Color(0x2EFFFFFF)

val IosGlassTrack: Color
    @Composable
    get() = LocalIosColors.current.glassTrack

val IosGlassTrackLight = Color(0x33FFFFFF)

val IosSeparator: Color
    @Composable
    get() = LocalIosColors.current.separator

val IosSubtleHighlight = Color(0x40FFFFFF)

// Legacy Fallbacks
val PrimaryGreen = IosGreen
val PrimaryGreenDark = Color(0xFF28A745)
val AccentOrange = IosOrange
val SurfaceDark = IosCardBackgroundDark
val BackgroundDark = Color(0xFF000000)
val Purple80 = IosPurple
val PurpleGrey80 = IosGray2
val Pink80 = IosColors.SystemPink
