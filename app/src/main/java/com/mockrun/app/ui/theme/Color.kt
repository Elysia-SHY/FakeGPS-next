package com.mockrun.app.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Apple Human Interface Guidelines (HIG) Semantic Color Palette
// =============================================================================
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
    val Label = Color(0xFF000000)                   // 100% Primary Label
    val SecondaryLabel = Color(0x993C3C43)          // ~60% Secondary Label
    val TertiaryLabel = Color(0x4D3C3C43)           // ~30% Tertiary Label
    val QuaternaryLabel = Color(0x2E3C3C43)         // ~18% Quaternary Label

    // 4. Grouped System Backgrounds
    val SystemGroupedBackground = Color(0xFFF2F2F7)           // Page base background
    val SecondaryGroupedBackground = Color(0xFFFFFFFF)        // Card surface inside grouped lists
    val TertiaryGroupedBackground = Color(0xFFFFFFFF)         // Nested surface

    // 5. System Fills
    val SystemFill = Color(0x33787880)              // 20% system fill
    val SecondarySystemFill = Color(0x29787880)     // 16% fill
    val TertiarySystemFill = Color(0x1F767680)      // 12% fill (e.g. search bars)
    val QuaternarySystemFill = Color(0x14747480)     // 8% fill

    // 6. Separators
    val Separator = Color(0x473C3C43)               // ~28% hairline divider
    val OpaqueSeparator = Color(0xFFC6C6C8)

    // 7. Materials & Blur Tints
    val UltraThinMaterial = Color(0xE6F8F8F9)       // 90% blur backdrop
    val ThinMaterial = Color(0xF2FFFFFF)            // 95% frosted capsule
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
val IosSystemBackground = IosColors.SystemGroupedBackground
val IosCardBackground = IosColors.SecondaryGroupedBackground
val IosCardBackgroundDark = Color(0xFF1C1C1E)

// Frosted Glass Translucent Materials
val IosFrostedLight = Color(0xEBFFFFFF)
val IosFrostedDark = Color(0xEB1C1C1E)
val IosFrostedCapsule = IosColors.ThinMaterial
val IosFrostedDarkCapsule = Color(0xF21C1C1E)
val IosFrostedPill = Color(0xE0F2F2F7)
val IosHairlineBorder = Color(0x14000000)
val IosHairlineBorderDark = Color(0x2EFFFFFF)
val IosGlassTrack = Color(0x1A000000)
val IosGlassTrackLight = Color(0x33FFFFFF)
val IosSeparator = IosColors.Separator
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
