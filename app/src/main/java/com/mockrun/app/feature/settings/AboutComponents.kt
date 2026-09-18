package com.mockrun.app.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.core.designsystem.IosColors

@Composable
fun ChangelogDialog(
    dialogTitle: String,
    dialogContent: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.NewReleases, contentDescription = null, tint = IosColors.SystemBlue)
                Text(
                    text = dialogTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = dialogContent,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = if (isDark) Color(0xFFE0E0E0) else Color(0xFF2C2C2E)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Elysia-SHY/FakeGPS-next/releases"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开发布页面", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IosColors.SystemBlue)
            ) {
                Text("查看 GitHub Releases", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("关闭", color = IosColors.SystemBlue)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    )
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = IosColors.SecondaryLabel)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = if (isDark) Color.White else Color.Black
        )
    }
}

@Composable
fun CreditItem(
    name: String,
    author: String,
    desc: String,
    url: String
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "无法打开链接: $url", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = IosColors.SystemBlue,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = author,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = IosColors.SystemBlue
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = desc,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = IosColors.SecondaryLabel
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = url,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = IosColors.SecondaryLabel.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    tags: List<String> = emptyList(),
    desc: String
) {
    val isDark = isSystemInDarkTheme()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color.Black
            )

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = iconTint.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = iconTint,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = IosColors.SecondaryLabel
            )
        }
    }
}

fun formatMb(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "-- KB/s"
    val kb = bytesPerSec / 1024.0
    return if (kb >= 1024.0) {
        "%.1f MB/s".format(kb / 1024.0)
    } else {
        "%.0f KB/s".format(kb)
    }
}
