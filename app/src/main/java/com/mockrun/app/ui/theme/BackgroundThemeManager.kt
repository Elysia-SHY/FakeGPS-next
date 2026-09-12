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
import java.io.File
import java.io.FileOutputStream

/**
 * Manages custom wallpaper persistence and default Aurora ambient gradient.
 * Eliminates unnecessary preset themes, keeping only custom album wallpaper.
 */
object BackgroundThemeManager {
    private const val PREFS_NAME = "fake_gps_ui_prefs"
    private const val KEY_HAS_CUSTOM_WALLPAPER = "bg_has_custom_wallpaper"
    private const val WALLPAPER_FILE_NAME = "custom_wallpaper.jpg"

    val hasCustomWallpaper = mutableStateOf(false)
    val customWallpaperTimestamp = mutableLongStateOf(0L)

    fun initialize(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wallpaperFile = File(context.filesDir, WALLPAPER_FILE_NAME)
        val isCustom = sp.getBoolean(KEY_HAS_CUSTOM_WALLPAPER, false) && wallpaperFile.exists() && wallpaperFile.length() > 0L
        hasCustomWallpaper.value = isCustom
        if (wallpaperFile.exists()) {
            customWallpaperTimestamp.longValue = wallpaperFile.lastModified()
        }
    }

    fun setCustomWallpaper(context: Context, uri: Uri): Boolean {
        return try {
            val wallpaperFile = File(context.filesDir, WALLPAPER_FILE_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(wallpaperFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (wallpaperFile.exists() && wallpaperFile.length() > 0L) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_HAS_CUSTOM_WALLPAPER, true)
                    .apply()
                customWallpaperTimestamp.longValue = System.currentTimeMillis()
                hasCustomWallpaper.value = true
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearCustomWallpaper(context: Context) {
        try {
            val wallpaperFile = File(context.filesDir, WALLPAPER_FILE_NAME)
            if (wallpaperFile.exists()) {
                wallpaperFile.delete()
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_HAS_CUSTOM_WALLPAPER, false)
                .apply()
            customWallpaperTimestamp.longValue = System.currentTimeMillis()
            hasCustomWallpaper.value = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getWallpaperFile(context: Context): File {
        return File(context.filesDir, WALLPAPER_FILE_NAME)
    }
}

/**
 * Renders the global app background.
 * If user set a custom photo, display the photo with a subtle scrim.
 * Otherwise, render the default Apple VisionOS Aurora gradient.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    hazeState: Any? = null
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isCustom by BackgroundThemeManager.hasCustomWallpaper
    val timestamp by BackgroundThemeManager.customWallpaperTimestamp

    val customBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = isCustom,
        key2 = timestamp
    ) {
        if (isCustom) {
            val file = BackgroundThemeManager.getWallpaperFile(context)
            if (file.exists() && file.length() > 0L) {
                value = try {
                    val bm = BitmapFactory.decodeFile(file.absolutePath)
                    bm?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            } else {
                value = null
            }
        } else {
            value = null
        }
    }

    val baseModifier = modifier
        .fillMaxSize()
        .drawBehind {
            if (customBitmap == null) {
                val canvasWidth = size.width
                val canvasHeight = size.height

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
        }

    Box(modifier = baseModifier) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Subtle scrim over wallpaper to ensure text legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark) Color.Black.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.18f)
                    )
            )
        }
    }
}
