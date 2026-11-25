package com.mockrun.app.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appSegmentedButtonColors(): SegmentedButtonColors {
    if (LocalIsDarkTheme.current) return SegmentedButtonDefaults.colors()
    val selection = AppSelectionColors.current
    return SegmentedButtonDefaults.colors(
        activeContainerColor = selection.primaryContainer,
        activeContentColor = selection.onPrimaryContainer,
        activeBorderColor = selection.selectedOutline,
        inactiveContainerColor = selection.unselectedContainer,
        inactiveContentColor = selection.unselectedContent,
        inactiveBorderColor = selection.unselectedOutline
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appFilterChipColors(): SelectableChipColors {
    if (LocalIsDarkTheme.current) return FilterChipDefaults.filterChipColors()
    val selection = AppSelectionColors.current
    return FilterChipDefaults.filterChipColors(
        containerColor = selection.unselectedContainer,
        labelColor = selection.unselectedContent,
        iconColor = selection.unselectedContent,
        selectedContainerColor = selection.primaryContainer,
        selectedLabelColor = selection.onPrimaryContainer,
        selectedLeadingIconColor = selection.onPrimaryContainer,
        selectedTrailingIconColor = selection.onPrimaryContainer
    )
}
