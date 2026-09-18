package com.mockrun.app.feature.mock

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
    val ruleColor = remember(rule.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(rule.colorHex)) }
            .logFailure("LocationMockScreen", "parse rule color", Diag.Level.DEBUG)
            .getOrDefault(IosColors.SystemBlue)
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
                shape = RoundedCornerShape(10.dp),
                color = ruleColor.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, ruleColor.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rule.appName.take(1),
                        style = IosTypography.Headline,
                        color = ruleColor
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.appName,
                        style = IosTypography.Headline,
                        color = if (rule.isEnabled) IosColors.Label else IosColors.SecondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.userId != 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IosColors.SystemOrange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "分身 · UID ${rule.userId}",
                                style = IosTypography.Caption2,
                                fontWeight = FontWeight.Bold,
                                color = IosColors.SystemOrange,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rule.packageName,
                    style = IosTypography.Footnote,
                    color = IosColors.SecondaryLabel,
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
                    style = IosTypography.Caption1,
                    color = IosColors.SecondaryLabel
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (rule.mode) {
                            TargetMockMode.REAL_PASSTHROUGH -> IosColors.SystemGray.copy(alpha = 0.15f)
                            else -> IosColors.SystemBlue.copy(alpha = 0.12f)
                        },
                        modifier = Modifier.bouncyClickable { showModeMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (rule.mode) {
                                    TargetMockMode.REAL_PASSTHROUGH -> "物理透传 ▾"
                                    else -> "独立定点 ▾"
                                },
                                style = IosTypography.Caption2,
                                fontWeight = FontWeight.SemiBold,
                                color = when (rule.mode) {
                                    TargetMockMode.REAL_PASSTHROUGH -> IosColors.SecondaryLabel
                                    else -> IosColors.SystemBlue
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
                    shape = RoundedCornerShape(8.dp),
                    color = IosColors.SystemBlue.copy(alpha = 0.12f),
                    modifier = Modifier.bouncyClickable { onSetLocationOnMap() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = IosColors.SystemBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "地图选点",
                            style = IosTypography.Caption2,
                            fontWeight = FontWeight.SemiBold,
                            color = IosColors.SystemBlue
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除规则",
                        tint = IosColors.SecondaryLabel,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
