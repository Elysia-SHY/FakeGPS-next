package com.mockrun.app.core.designsystem

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages custom wallpaper persistence and the default solid-color background.
 * Eliminates unnecessary preset themes, keeping only custom album wallpaper.
 */
object BackgroundThemeManager {
    private const val TAG = "BackgroundTheme"
    private const val PREFS_NAME = "fake_gps_ui_prefs"
    private const val KEY_HAS_CUSTOM_WALLPAPER = "bg_has_custom_wallpaper"
    private const val WALLPAPER_FILE_NAME = "custom_wallpaper.jpg"

    /**
     * 计算 2 的幂次下采样率，使解码后的位图不超过目标尺寸。
     *
     * 壁纸只作为背景显示，无需原始分辨率；不做下采样时一张 4000×3000 的
     * 照片解码后约占 48 MB 堆内存，在中低端机上会触发 OOM。
     *
     * 可见性为 internal 而非 private：调用方 AppBackground 是同文件内的
     * 顶层 composable，不在本 object 内。
     */
    internal fun calculateInSampleSize(
        width: Int, height: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        while ((height / sampleSize) > reqHeight || (width / sampleSize) > reqWidth) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

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

    /**
     * 保存自定义壁纸。
     *
     * 必须从后台线程调用 —— 复制一张 3-25 MB 的照片到 filesDir 是纯 I/O，
     * 放在主线程会直接卡住 UI（调用方 AboutScreen 的
     * `rememberLauncherForActivityResult` 回调就跑在主线程上）。
     * 这里内部切到 IO 调度器，调用方无需自行包裹。
     */
    suspend fun setCustomWallpaper(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
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
                Diag.w(TAG, "保存自定义壁纸失败", e)
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
 * Otherwise, paint a flat solid color: pure black in dark mode, pure white in light mode.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    val context = LocalContext.current
    val isDark = LocalIosColors.current.isDark
    val isCustom by BackgroundThemeManager.hasCustomWallpaper
    val timestamp by BackgroundThemeManager.customWallpaperTimestamp

    val customBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = isCustom,
        key2 = timestamp
    ) {
        // produceState 的 block 跑在组合的协程上下文（Main）上。
        // 直接 decodeFile 一张全尺寸壁纸会让主线程停顿数百毫秒到数秒（ANR 风险），
        // 因此切到 IO，并按目标显示尺寸做下采样 —— 背景图不需要原始分辨率。
        value = if (isCustom) {
            withContext(Dispatchers.IO) {
                val file = BackgroundThemeManager.getWallpaperFile(context)
                if (!file.exists() || file.length() <= 0L) return@withContext null
                runCatching {
                    // 先只读边界，算出 2 的幂次下采样率，避免把整张原图读进内存
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = BackgroundThemeManager.calculateInSampleSize(
                            bounds.outWidth, bounds.outHeight, 1080, 2400
                        )
                    }
                    BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                }.logFailure("BackgroundTheme", "解码自定义壁纸").getOrNull()
            }
        } else {
            null
        }
    }

    val baseModifier = modifier
        .fillMaxSize()
        .hazeSource(hazeState)
        .drawBehind {
            if (customBitmap == null) {
                drawRect(if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF))
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
            // 降低壁纸遮罩浓度，保留壁纸高频纹理细节供毛玻璃卡片高斯模糊折射
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark) Color.Black.copy(alpha = 0.12f)
                        else Color.White.copy(alpha = 0.08f)
                    )
            )
        }
    }
}
