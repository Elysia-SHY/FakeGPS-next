package com.mockrun.app.feature.mock

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.AppSemanticColors
import com.mockrun.app.core.designsystem.minTouchTarget
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.domain.model.TargetMockMode
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure

@Composable
fun MultiTargetRuleItemRow(
    rule: MultiTargetRule,
    onToggle: (Boolean) -> Unit,
    onSetLocationOnMap: () -> Unit,
    onModeChange: (TargetMockMode) -> Unit,
    onDelete: () -> Unit
) {
    val defaultRuleColor = MaterialTheme.colorScheme.primary
    val ruleColor = remember(rule.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(rule.colorHex)) }
            .logFailure("LocationMockScreen", "parse rule color", Diag.Level.DEBUG)
            .getOrDefault(defaultRuleColor)
    }

    var showModeMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = ruleColor.copy(alpha = 0.16f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rule.appName.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (rule.isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (rule.userId != 0) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = AppSemanticColors.current.warningContainer,
                        contentColor = AppSemanticColors.current.onWarningContainer
                    ) {
                        Text(
                            text = "分身 · UID ${rule.userId}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rule.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.minTouchTarget()
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = ruleColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${"%.5f".format(rule.latitude)}, ${"%.5f".format(rule.longitude)}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                FilledTonalButton(
                    onClick = { showModeMenu = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = when (rule.mode) {
                            TargetMockMode.REAL_PASSTHROUGH -> "物理透传 ▾"
                            else -> "独立定点 ▾"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                DropdownMenu(
                    expanded = showModeMenu,
                    onDismissRequest = { showModeMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("📍 独立定点 (自定义分流坐标)") },
                        onClick = {
                            onModeChange(TargetMockMode.STATIONARY)
                            showModeMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🛰️ 物理透传 (放行真实物理定位)") },
                        onClick = {
                            onModeChange(TargetMockMode.REAL_PASSTHROUGH)
                            showModeMenu = false
                        }
                    )
                }
            }

            OutlinedButton(
                onClick = onSetLocationOnMap,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "地图选点",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除规则",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
