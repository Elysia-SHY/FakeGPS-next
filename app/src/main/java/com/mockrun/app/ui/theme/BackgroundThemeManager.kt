package com.mockrun.app.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import java.io.File
import java.io.FileOutputStream

enum class BackgroundPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconEmoji: String,
    val accentColor: Color
) {
    AURORA("aurora", "极光幻彩", "VisionOS 微偏光流光光晕", "🌌", Color(0xFF2563EB)),
    CYBERPUNK("cyberpunk", "赛博黑曜", "黑曜底衬与电光洋红青蓝", "🪐", Color(0xFFFF007F)),
    SUNSET("sunset", "暮光落霞", "温暖琥珀与落日余晖紫红", "🌅", Color(0xFFF97316)),
    GLACIER("glacier", "薄荷冰川", "清爽极地冰蓝与薄荷微翠", "❄️", Color(0xFF06B6D4)),
    DEEP_SPACE("deep_space", "深空沉浸", "极简纯粹 OLED 沉浸黑", "🖤", Color(0xFF6B7280)),
    CUSTOM("custom", "自定义壁纸", "相册自选照片壁纸", "🖼️", Color(0xFF10B981));

    companion object {
        fun fromId(id: String?): BackgroundPreset {
            return entries.firstOrNull { it.id == id } ?: AURORA
        }
    }
}

object BackgroundThemeManager {
    private const val PREFS_NAME = "fake_gps_ui_prefs"
    private const val KEY_PRESET = "bg_theme_preset"
    private const val WALLPAPER_FILE_NAME = "custom_wallpaper.jpg"

    val currentPreset = mutableStateOf(BackgroundPreset.AURORA)
    val customWallpaperTimestamp = mutableLongStateOf(0L)

    fun initialize(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = sp.getString(KEY_PRESET, BackgroundPreset.AURORA.id)
        val preset = BackgroundPreset.fromId(savedId)
        val wallpaperFile = File(context.filesDir, WALLPAPER_FILE_NAME)
        if (preset == BackgroundPreset.CUSTOM && (!wallpaperFile.exists() || wallpaperFile.length() == 0L)) {
            currentPreset.value = BackgroundPreset.AURORA
            sp.edit().putString(KEY_PRESET, BackgroundPreset.AURORA.id).apply()
        } else {
            currentPreset.value = preset
        }
        if (wallpaperFile.exists()) {
            customWallpaperTimestamp.longValue = wallpaperFile.lastModified()
        }
    }

    fun setPreset(context: Context, preset: BackgroundPreset) {
        currentPreset.value = preset
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_PRESET, preset.id).apply()
    }

    fun setCustomWallpaper(context: Context, uri: Uri): Boolean {
        return try {
            val wallpaperFile = File(context.filesDir, WALLPAPER_FILE_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(wallpaperFile).use { output ->
                    input.copyTo(output)
                }
            }
            customWallpaperTimestamp.longValue = System.currentTimeMillis()
            setPreset(context, BackgroundPreset.CUSTOM)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearCustomWallpaper(context: Context) {
        val wallpaperFile = File(context.filesDir, WALLPAPER_FILE_NAME)
        if (wallpaperFile.exists()) {
            wallpaperFile.delete()
        }
        customWallpaperTimestamp.longValue = 0L
        setPreset(context, BackgroundPreset.AURORA)
    }

    fun getWallpaperFile(context: Context): File {
        return File(context.filesDir, WALLPAPER_FILE_NAME)
    }
}

/**
 * Universal ambient backdrop component that renders the user-configured background style
 * and registers it into Haze's GPU offscreen render buffer via Modifier.haze(hazeState).
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val preset by BackgroundThemeManager.currentPreset
    val wallpaperTimestamp by BackgroundThemeManager.customWallpaperTimestamp

    val customBitmap = remember(preset, wallpaperTimestamp) {
        if (preset == BackgroundPreset.CUSTOM) {
            val file = BackgroundThemeManager.getWallpaperFile(context)
            if (file.exists() && file.length() > 0L) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            } else null
        } else null
    }

    val baseModifier = modifier.fillMaxSize()

    val backgroundModifier = if (customBitmap != null) {
        baseModifier.drawBehind {
            drawRect(if (isDark) Color(0xFF090C12) else Color(0xFFF2F4F7))
        }
    } else {
        baseModifier.drawBehind {
            val canvasWidth = size.width
            val canvasHeight = size.height

            when (preset) {
                BackgroundPreset.AURORA -> {
                    if (isDark) {
                        drawRect(Color(0xFF090C12))
                        // Top-Right Sapphire
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF2563EB).copy(alpha = 0.42f), Color(0xFF1D4ED8).copy(alpha = 0.22f), Color.Transparent),
                                center = Offset(canvasWidth * 0.88f, canvasHeight * 0.10f),
                                radius = canvasWidth * 0.90f
                            )
                        )
                        // Mid-Left Violet
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF7C3AED).copy(alpha = 0.38f), Color(0xFF4C1D95).copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(canvasWidth * 0.10f, canvasHeight * 0.42f),
                                radius = canvasWidth * 0.88f
                            )
                        )
                        // Bottom-Right Emerald
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF0D9488).copy(alpha = 0.32f), Color(0xFF065F46).copy(alpha = 0.14f), Color.Transparent),
                                center = Offset(canvasWidth * 0.90f, canvasHeight * 0.82f),
                                radius = canvasWidth * 0.80f
                            )
                        )
                        // Center Indigo
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.20f), Color.Transparent),
                                center = Offset(canvasWidth * 0.50f, canvasHeight * 0.62f),
                                radius = canvasWidth * 0.70f
                            )
                        )
                    } else {
                        drawRect(Color(0xFFF2F4F7))
                        // Top-Right Sky
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.38f), Color(0xFF93C5FD).copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(canvasWidth * 0.88f, canvasHeight * 0.10f),
                                radius = canvasWidth * 0.90f
                            )
                        )
                        // Mid-Left Lavender
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFC084FC).copy(alpha = 0.34f), Color(0xFFE9D5FF).copy(alpha = 0.16f), Color.Transparent),
                                center = Offset(canvasWidth * 0.10f, canvasHeight * 0.42f),
                                radius = canvasWidth * 0.88f
                            )
                        )
                        // Bottom-Right Mint
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF34D399).copy(alpha = 0.30f), Color(0xFFA7F3D0).copy(alpha = 0.12f), Color.Transparent),
                                center = Offset(canvasWidth * 0.90f, canvasHeight * 0.82f),
                                radius = canvasWidth * 0.80f
                            )
                        )
                    }
                }

                BackgroundPreset.CYBERPUNK -> {
                    drawRect(if (isDark) Color(0xFF06060B) else Color(0xFFF0F0F8))
                    // Top-Right Neon Magenta
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF007F).copy(alpha = if (isDark) 0.40f else 0.30f), Color.Transparent),
                            center = Offset(canvasWidth * 0.88f, canvasHeight * 0.12f),
                            radius = canvasWidth * 0.85f
                        )
                    )
                    // Mid-Left Electric Cyan
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF00F0FF).copy(alpha = if (isDark) 0.35f else 0.28f), Color.Transparent),
                            center = Offset(canvasWidth * 0.08f, canvasHeight * 0.45f),
                            radius = canvasWidth * 0.85f
                        )
                    )
                    // Bottom-Right Deep Purple
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF8B5CF6).copy(alpha = if (isDark) 0.32f else 0.22f), Color.Transparent),
                            center = Offset(canvasWidth * 0.92f, canvasHeight * 0.85f),
                            radius = canvasWidth * 0.80f
                        )
                    )
                }

                BackgroundPreset.SUNSET -> {
                    drawRect(if (isDark) Color(0xFF0F0A1A) else Color(0xFFFFF7ED))
                    // Top-Right Sunset Amber
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFF97316).copy(alpha = if (isDark) 0.45f else 0.35f), Color.Transparent),
                            center = Offset(canvasWidth * 0.85f, canvasHeight * 0.12f),
                            radius = canvasWidth * 0.90f
                        )
                    )
                    // Mid-Left Crimson Rose
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFE11D48).copy(alpha = if (isDark) 0.38f else 0.28f), Color.Transparent),
                            center = Offset(canvasWidth * 0.12f, canvasHeight * 0.42f),
                            radius = canvasWidth * 0.85f
                        )
                    )
                    // Bottom-Right Twilight Plum
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF7C2D12).copy(alpha = if (isDark) 0.35f else 0.20f), Color.Transparent),
                            center = Offset(canvasWidth * 0.88f, canvasHeight * 0.82f),
                            radius = canvasWidth * 0.80f
                        )
                    )
                }

                BackgroundPreset.GLACIER -> {
                    drawRect(if (isDark) Color(0xFF06141D) else Color(0xFFF0FDF4))
                    // Top-Right Arctic Glacier Cyan
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF06B6D4).copy(alpha = if (isDark) 0.42f else 0.34f), Color.Transparent),
                            center = Offset(canvasWidth * 0.88f, canvasHeight * 0.10f),
                            radius = canvasWidth * 0.88f
                        )
                    )
                    // Mid-Left Mint Emerald
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF10B981).copy(alpha = if (isDark) 0.35f else 0.28f), Color.Transparent),
                            center = Offset(canvasWidth * 0.10f, canvasHeight * 0.46f),
                            radius = canvasWidth * 0.85f
                        )
                    )
                    // Bottom-Right Frost Blue
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF0284C7).copy(alpha = if (isDark) 0.30f else 0.20f), Color.Transparent),
                            center = Offset(canvasWidth * 0.90f, canvasHeight * 0.85f),
                            radius = canvasWidth * 0.80f
                        )
                    )
                }

                BackgroundPreset.DEEP_SPACE -> {
                    drawRect(Color(0xFF000000))
                    // Subtle dark starlight / nebula sheen
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1E1B4B).copy(alpha = 0.30f), Color.Transparent),
                            center = Offset(canvasWidth * 0.80f, canvasHeight * 0.20f),
                            radius = canvasWidth * 0.85f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF0F172A).copy(alpha = 0.40f), Color.Transparent),
                            center = Offset(canvasWidth * 0.20f, canvasHeight * 0.70f),
                            radius = canvasWidth * 0.85f
                        )
                    )
                }

                BackgroundPreset.CUSTOM -> {
                    // Handled above when customBitmap != null
                    drawRect(if (isDark) Color(0xFF090C12) else Color(0xFFF2F4F7))
                }
            }
        }
    }

    val finalModifier = if (hazeState != null) backgroundModifier.haze(hazeState) else backgroundModifier

    Box(modifier = finalModifier) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Subtle scrim over wallpaper to ensure text legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark) Color.Black.copy(alpha = 0.30f)
                        else Color.White.copy(alpha = 0.25f)
                    )
            )
        }
    }
}
