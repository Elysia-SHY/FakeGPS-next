package com.mockrun.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * CompositionLocal providing global Liquid Glass effect toggle state.
 */
val LocalLiquidGlassEnabled = compositionLocalOf { true }

/**
 * 全局毛玻璃「模糊源」。
 *
 * 使用方式（源层与玻璃层必须是**兄弟节点**，否则会自引用递归）：
 * ```
 * val hazeState = remember { HazeState() }
 * Box {
 *     // ① 源层：会被捕获并模糊的内容（地图 / 页面背景）
 *     Box(Modifier.fillMaxSize().haze(hazeState)) { MapContent() }
 *
 *     // ② 玻璃层：在源层之上，引用同一 state
 *     CompositionLocalProvider(LocalHazeState provides hazeState) {
 *         GlassPanel(Modifier.liquidGlass(isLiquidGlass = true))
 *     }
 * }
 * ```
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** 底部 Tab 栏专用的独立模糊源（避免与内容区互相污染） */
val LocalBottomBarHazeState = compositionLocalOf<HazeState?> { null }

object LiquidGlassDefaults {
    const val PREFS_NAME = "fake_gps_ui_prefs"
    const val KEY_LIQUID_GLASS_ENABLED = "is_liquid_glass_enabled"

    /** 增强型高斯模糊半径：36.dp，使背后极光与壁纸呈现饱满奶油散景 */
    val BlurRadius = 36.dp

    /** 细腻光学磨砂噪点：赋予毛玻璃真实微晶质感，避免像单纯半透明塑料遮罩 */
    const val NoiseFactorDark = 0.035f
    const val NoiseFactorLight = 0.025f

    fun isEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_LIQUID_GLASS_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_LIQUID_GLASS_ENABLED, enabled).apply()
    }

    /**
     * 构造针对明暗模式分别优化的 HazeStyle。
     *
     * 关键修复：
     *  1. [backgroundColor] 必须传入页面真实底色（而非 Color.Transparent），
     *     防止 HazeChildNode 在采样边缘或局部录制时混入透明黑导致模糊退化为单薄遮罩；
     *  2. 亮色与暗色采用不同的着色与微噪点，确保亮色不过白、暗色通透有层次。
     */
    fun glassStyle(
        tint: Color,
        isDark: Boolean = true,
        blurRadius: Dp = BlurRadius,
        noiseFactor: Float = if (isDark) NoiseFactorDark else NoiseFactorLight
    ): HazeStyle {
        val baseBg = if (isDark) Color(0xFF090C12) else DesignTokens.LBg0
        val primaryTint = HazeTint(tint)
        val fallback = HazeTint(
            if (isDark) DesignTokens.GlassBgOpaque else DesignTokens.LGlassBgOpaque
        )
        return HazeStyle(
            backgroundColor = baseBg,
            tint = primaryTint,
            blurRadius = blurRadius,
            noiseFactor = noiseFactor,
            fallbackTint = fallback
        )
    }
}

/**
 * 把当前节点标记为 Haze 的**模糊源**。
 * 传入 null 时退化为无操作（例如设备不支持或未开启玻璃时）。
 */
fun Modifier.hazeSource(state: HazeState?): Modifier =
    if (state != null) this.haze(state) else this

/**
 * 实心卡片面（设计稿 .card）。
 *
 * 滚动页（功能页 / 关于页）的卡片按设计稿为**不透明面**，而非玻璃：
 * 一来 `--surface:#151B2B` 本身是实色，二来大量可滚动卡片若都做实时模糊会显著吃 GPU。
 * 真毛玻璃只用于地图浮层、底部抽屉与 Tab 栏。
 */
fun Modifier.iosCardSurface(
    shape: Shape = RoundedCornerShape(24.dp)
): Modifier = composed {
    this
        .clip(shape)
        .background(LocalIosColors.current.secondaryGroupedBackground, shape)
        .border(1.dp, LocalIosColors.current.line, shape)
}

/**
 * Liquid Glass（液态毛玻璃）· v1.7.0
 *
 * 开启时（[isLiquidGlass] = true）：
 *   - 由 Haze 对 [hazeState] 指向的源层做真实 GPU 高斯模糊（blur 30 / saturate 180%）
 *   - Android 12（API 31）以下由 Haze 内部自动降级为磨砂 tint 板，不会崩
 *   - 叠加 1px 8% 白描边 + 顶部内高光 + 空间悬浮投影
 *
 * 关闭时（[isLiquidGlass] = false）：
 *   - 输出纯色面板，零模糊开销（省电）
 *
 * [hazeState] 默认取 [LocalHazeState]。若解析结果为 null（上层未提供模糊源），
 * 或源层是 AndroidView 承载的原生 View（Haze 捕获不到），自动退化为
 * **不透明磨砂板**（描边 + 内高光仍在），保证浮层读起来是一整块实心玻璃，
 * 而不是让背后内容透出来。
 */
fun Modifier.liquidGlass(
    isLiquidGlass: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 12.dp,
    containerColor: Color? = null,
    borderWidth: Dp = 1.0.dp,
    hazeState: HazeState? = null,
    blurRadius: Dp = LiquidGlassDefaults.BlurRadius,
    noiseFactor: Float = 0f
): Modifier = composed {
    val palette = LocalIosColors.current
    val isDark = palette.isDark
    // 未显式指定时，自动取上层提供的模糊源
    val resolvedHaze = hazeState ?: LocalHazeState.current

    // ---- 关闭玻璃：纯色面板 ----
    if (!isLiquidGlass) {
        val solidFill = containerColor ?: if (isDark) palette.surface2 else palette.secondaryGroupedBackground
        val solidBorder = if (isDark) DesignTokens.Line else DesignTokens.LLine
        return@composed this
            .shadow(
                elevation = (elevation * 0.5f).coerceIn(0.dp, 10.dp),
                shape = shape,
                spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.12f),
                ambientColor = Color.Transparent
            )
            .clip(shape)
            .background(solidFill, shape)
            .border(borderWidth, solidBorder, shape)
    }

    // ---- 玻璃层着色 ----
    // containerColor 给定且足够不透明时，视为「实心 CTA」，不做模糊（省 GPU 且符合设计稿）
    val solidThreshold = 0.90f
    val isSolidCta = containerColor != null && containerColor.alpha >= solidThreshold
    val tintColor = containerColor ?: palette.glassBg
    val effectiveNoise = if (noiseFactor > 0f) {
        noiseFactor
    } else if (isDark) {
        LiquidGlassDefaults.NoiseFactorDark
    } else {
        LiquidGlassDefaults.NoiseFactorLight
    }

    // 分别针对暗色与亮色构建高质感光学边框刷子：
    // 暗色：左上亮白高光向右下柔和衰减；
    // 亮色：晶透高光白边叠加微妙冷色立体轮廓，解决白卡在浅底上边界模糊的问题。
    val borderBrush = remember(isDark, palette.glassLine) {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.28f),
                    palette.glassLine,
                    Color.White.copy(alpha = 0.08f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.92f),
                    Color(0xB3FFFFFF),
                    Color(0x240F172A)
                )
            )
        }
    }

    val shadowElevation = elevation.coerceIn(0.dp, 26.dp)
    val spotColor = if (isDark) {
        Color.Black.copy(alpha = 0.52f)
    } else {
        Color(0xFF0F172A).copy(alpha = 0.14f)
    }
    val ambientColor = if (isDark) {
        Color.Black.copy(alpha = 0.24f)
    } else {
        Color(0xFF3B82F6).copy(alpha = 0.08f)
    }

    var modifier = this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            spotColor = spotColor,
            ambientColor = ambientColor
        )

    modifier = if (isSolidCta) {
        // 实心 CTA：直接铺色，不做模糊
        modifier.clip(shape).background(tintColor, shape)
    } else if (resolvedHaze != null) {
        // 真毛玻璃：Haze 捕获源层 → 36dp 高斯模糊 + 光学噪点 → tint 着色
        modifier
            .clip(shape)
            .hazeChild(
                state = resolvedHaze,
                shape = shape,
                style = LiquidGlassDefaults.glassStyle(
                    tint = tintColor,
                    isDark = isDark,
                    blurRadius = blurRadius,
                    noiseFactor = effectiveNoise
                )
            )
            // 叠加极其微妙的顶部到底部光泽渐变层，增强玻璃立体通透感
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.07f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.10f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.06f),
                            Color(0x0A3B82F6)
                        )
                    }
                ),
                shape = shape
            )
    } else {
        // ---- 无可用模糊源兜底（如原生 MapView 之上）：输出高质感磨砂板 ----
        val opaqueFallback = when {
            containerColor != null && containerColor.alpha >= 0.5f -> containerColor.copy(alpha = 1f)
            isDark -> DesignTokens.GlassBgOpaque
            else -> DesignTokens.LGlassBgOpaque
        }
        modifier.clip(shape).background(opaqueFallback, shape)
    }

    modifier
        .border(borderWidth, borderBrush, shape)
        .drawBehind {
            // 顶部镜面内高光，模拟 Apple 连续曲率折射
            val stroke = (if (isDark) 1.dp else 1.25.dp).toPx()
            drawLine(
                color = palette.glassHighlight,
                start = Offset(stroke * 6f, stroke / 2f),
                end = Offset(size.width - stroke * 6f, stroke / 2f),
                strokeWidth = stroke
            )
        }
}

/**
 * 滚动页（功能页 / 关于页）的环境光背景，同时作为 Haze 的模糊源。
 *
 * 返回的 [Box] 已经挂了 [Modifier.hazeSource]，直接把它当作视觉底衬，
 * 上层卡片用 [Modifier.liquidGlass] 即可获得真实的折射毛玻璃。
 */
@Composable
fun FrostedAmbientBackground(
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIosColors.current.isDark

    val baseModifier = modifier
        .fillMaxSize()
        .hazeSource(hazeState)
        .background(if (isDark) DesignTokens.Bg1 else DesignTokens.LBg0)
        .drawBehind {
            val w = size.width
            val h = size.height

            if (isDark) {
                // 1. 右上蓝宝石光晕
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DesignTokens.Brand.copy(alpha = 0.28f),
                            DesignTokens.Brand.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.88f, h * 0.08f),
                        radius = w * 0.85f
                    )
                )
                // 2. 左中紫罗兰
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DesignTokens.Violet.copy(alpha = 0.26f),
                            DesignTokens.Violet.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.10f, h * 0.40f),
                        radius = w * 0.85f
                    )
                )
                // 3. 右下薄荷青
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0D9488).copy(alpha = 0.20f),
                            Color(0xFF065F46).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.90f, h * 0.80f),
                        radius = w * 0.72f
                    )
                )
            } else {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.30f),
                            Color(0xFF93C5FD).copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.88f, h * 0.08f),
                        radius = w * 0.85f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFC084FC).copy(alpha = 0.26f),
                            Color(0xFFE9D5FF).copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.10f, h * 0.40f),
                        radius = w * 0.85f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF34D399).copy(alpha = 0.22f),
                            Color(0xFFA7F3D0).copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.90f, h * 0.80f),
                        radius = w * 0.72f
                    )
                )
            }
        }

    Box(modifier = baseModifier)
}
