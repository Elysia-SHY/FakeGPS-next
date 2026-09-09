package com.mockrun.app.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mockrun.app.BuildConfig
import com.mockrun.app.ui.theme.*

@Composable
fun AboutScreen(
    isLiquidGlass: Boolean,
    onToggleLiquidGlass: (Boolean) -> Unit,
    onNavigateToLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val gitRepoUrl = "https://github.com/Elysia-SHY/FakeGPS-next"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else IosColors.SystemGroupedBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // =====================================================================
        // 1. App Header & Logo Card
        // =====================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    isLiquidGlass = isLiquidGlass,
                    shape = RoundedCornerShape(24.dp),
                    elevation = 10.dp
                ),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 26.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Luminous App Logo Badge
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF007AFF),
                                    Color(0xFF5856D6),
                                    Color(0xFF00C7BE)
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White.copy(alpha = 0.1f)
                                )
                            ),
                            RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "FakeGPS-next",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = IosColors.SystemBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IosColors.SystemBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "Build: ${BuildConfig.BUILD_TIME}",
                        fontSize = 11.5.sp,
                        color = IosColors.SecondaryLabel
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "系统底层级虚拟定位 · 全屏地图交互 · 轨迹巡航",
                    fontSize = 13.sp,
                    color = IosColors.SecondaryLabel,
                    textAlign = TextAlign.Center
                )
            }
        }

        // =====================================================================
        // 2. Liquid Glass Appearance Settings Card (二级菜单开关)
        // =====================================================================
        Text(
            text = "视觉渲染风格",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = IosColors.SecondaryLabel,
            modifier = Modifier.padding(start = 6.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    isLiquidGlass = isLiquidGlass,
                    shape = RoundedCornerShape(20.dp),
                    elevation = 8.dp
                ),
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isLiquidGlass) IosColors.SystemBlue.copy(0.18f) else (if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLiquidGlass) Icons.Default.WaterDrop else Icons.Default.BlurOff,
                                contentDescription = null,
                                tint = if (isLiquidGlass) IosColors.SystemBlue else IosColors.SecondaryLabel,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "液态毛玻璃渲染效果",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White else Color.Black
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isLiquidGlass) "半透明磨砂 · 镜面光泽 · 柔和环境光" else "纯色材质 · 超低 GPU 负荷 · 极致省电",
                                fontSize = 11.5.sp,
                                color = IosColors.SecondaryLabel
                            )
                        }
                    }

                    Switch(
                        checked = isLiquidGlass,
                        onCheckedChange = { next ->
                            LiquidGlassDefaults.setEnabled(context, next)
                            onToggleLiquidGlass(next)
                            Toast.makeText(
                                context,
                                if (next) "💧 已开启液态毛玻璃视觉效果" else "⬛ 已切换为经典纯色节能模式",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = IosColors.SystemBlue
                        )
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(
                    color = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f),
                    thickness = 0.8.dp
                )
                Spacer(Modifier.height(10.dp))

                Text(
                    text = "💡 视觉设计参考了 LocationSpoofer 与 Orb 的折射高光光泽。在开启状态下，浮动底栏与控制抽屉将呈现细腻的玻璃通透质感。",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = IosColors.SecondaryLabel
                )
            }
        }

        // =====================================================================
        // 3. GitHub Open Source Address & Actions
        // =====================================================================
        Text(
            text = "项目开源与代码仓库",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = IosColors.SecondaryLabel,
            modifier = Modifier.padding(start = 6.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    isLiquidGlass = isLiquidGlass,
                    shape = RoundedCornerShape(20.dp),
                    elevation = 8.dp
                ),
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GitHub 源码仓库",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else Color.Black
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = gitRepoUrl,
                            fontSize = 12.sp,
                            color = IosColors.SystemBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Copy URL Button
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("GitHub Repository", gitRepoUrl)
                            cm.setPrimaryClip(clip)
                            Toast.makeText(context, "已复制 GitHub 仓库地址至剪贴板！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f),
                            contentColor = if (isDark) Color.White else Color.Black
                        )
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("复制链接", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Open Browser Button
                    Button(
                        onClick = {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(gitRepoUrl))
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IosColors.SystemBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("前往仓库", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // =====================================================================
        // 4. Quick Access: Route Library
        // =====================================================================
        Text(
            text = "数据管理与路线库",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = IosColors.SecondaryLabel,
            modifier = Modifier.padding(start = 6.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    isLiquidGlass = isLiquidGlass,
                    shape = RoundedCornerShape(20.dp),
                    elevation = 8.dp
                )
                .clickable { onNavigateToLibrary() },
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(IosColors.SystemPurple.copy(0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = IosColors.SystemPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "已收藏路线与航点库",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else Color.Black
                        )
                        Text(
                            text = "管理历史规划、导入/导出 GPX 文件",
                            fontSize = 11.5.sp,
                            color = IosColors.SecondaryLabel
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = IosColors.SecondaryLabel,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // =====================================================================
        // 5. Open Source Credits & Acknowledgements
        // =====================================================================
        Text(
            text = "开源致谢",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = IosColors.SecondaryLabel,
            modifier = Modifier.padding(start = 6.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    isLiquidGlass = isLiquidGlass,
                    shape = RoundedCornerShape(20.dp),
                    elevation = 8.dp
                ),
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CreditItem(
                    name = "LocationSpoofer",
                    author = "HuangZhuoRui",
                    desc = "全屏交互地图、抽屉式控制面板设计灵感来源"
                )
                HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                CreditItem(
                    name = "Orb Liquid Glass",
                    author = "LerSent001",
                    desc = "液态玻璃边缘折射与镜面高光视觉参考"
                )
                HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                CreditItem(
                    name = "OSMDroid & AutoNavi CDN",
                    author = "OpenSource Community",
                    desc = "免 Key 国内高速瓦片地图渲染引擎"
                )
                HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                CreditItem(
                    name = "LSPosed Framework",
                    author = "LSPosed Developers",
                    desc = "系统级 Hook 运行环境与 Android 12~16 穿透支持"
                )
            }
        }
    }
}

@Composable
private fun CreditItem(
    name: String,
    author: String,
    desc: String
) {
    val isDark = isSystemInDarkTheme()
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black
            )
            Text(
                text = author,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = IosColors.SystemBlue
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = desc,
            fontSize = 11.5.sp,
            color = IosColors.SecondaryLabel
        )
    }
}
