package com.mockrun.app.feature.mock
import androidx.compose.material3.MaterialTheme
import com.mockrun.app.core.designsystem.AppSemanticColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.domain.model.TargetMockMode
import com.mockrun.app.core.designsystem.*
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(13.dp),
                color = ruleColor.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, ruleColor.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rule.appName.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = ruleColor
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rule.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.userId != 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AppSemanticColors.current.warning.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "分身 · UID ${rule.userId}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppSemanticColors.current.warning,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rule.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IosSwitch(
                checked = rule.isEnabled,
                onCheckedChange = onToggle
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = ruleColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${"%.5f".format(rule.latitude)}, ${"%.5f".format(rule.longitude)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = when (rule.mode) {
                            TargetMockMode.REAL_PASSTHROUGH -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        },
                        modifier = Modifier
                            .minTouchTarget()
                            .clickable { showModeMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (rule.mode) {
                                    TargetMockMode.REAL_PASSTHROUGH -> "物理透传 ▾"
                                    else -> "独立定点 ▾"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when (rule.mode) {
                                    TargetMockMode.REAL_PASSTHROUGH -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
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

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    // 原触区只有 8dp 内距 + 13dp 图标 ≈ 26dp 高，拇指难以命中
                    modifier = Modifier
                        .minTouchTarget()
                        .clickable { onSetLocationOnMap() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "地图选点",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    // 原 26dp，低于 48dp 最小触区
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除规则",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
