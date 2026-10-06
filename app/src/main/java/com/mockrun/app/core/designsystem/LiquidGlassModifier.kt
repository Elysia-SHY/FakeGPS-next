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

    /** 设计稿唯一规范：blur 30 · saturate 180% */
    val BlurRadius = 30.dp

    fun isEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_LIQUID_GLASS_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_LIQUID_GLASS_ENABLED, enabled).apply()
    }

    /**
     * 构造设计稿规范的 HazeStyle。
     * @param tint 玻璃着色（默认 rgba(20,26,42,.72)）
     */
    fun glassStyle(
        tint: Color,
        blurRadius: Dp = BlurRadius,
        noiseFactor: Float = 0f
    ): HazeStyle {
        val hazeTint = HazeTint(tint)
        return HazeStyle(
            Color.Transparent,
            hazeTint,
            blurRadius,
            noiseFactor,
            hazeTint
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

    val borderColor = if (isDark) palette.glassLine else palette.glassLine

    // 投影：设计稿 0 18px 44px -20px rgba(0,0,0,.85)
    val shadowElevation = elevation.coerceIn(0.dp, 26.dp)
    val spotAlpha = if (isDark) 0.55f else 0.16f

    var modifier = this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            spotColor = Color.Black.copy(alpha = spotAlpha),
            ambientColor = Color.Transparent
        )

    modifier = if (isSolidCta) {
        // 实心 CTA：直接铺色，不做模糊
        modifier.clip(shape).background(tintColor, shape)
    } else if (resolvedHaze != null) {
        // 真毛玻璃：Haze 捕获源层 → 高斯模糊 → tint 着色
        modifier
            .hazeChild(
                resolvedHaze,
                shape,
                LiquidGlassDefaults.glassStyle(tintColor, blurRadius, noiseFactor)
            )
            .clip(shape)
    } else {
        // ---- 无可用模糊源兜底：输出**不透明**磨砂板 ----
        //
        // 触发场景有两条：
        //   ① 上层根本没提供源（LocalHazeState 为 null 且未显式传 hazeState）；
        //   ② 提供了源，但源层是 AndroidView 承载的**原生 View**（如 OSMDroid MapView）。
        //      Haze 仅捕获 Compose 自己绘制的内容，此时录到的图层是空的，模糊结果为空。
        //
        // 这两种情况下若仍用 72% 半透明底板，背后的真实内容会硬生生透出来 ——
        // 观感就是「穿模」（例如右侧 FAB 列从底部抽屉里透出来）。
        // 因此这里改用不透明磨砂底板 + 描边 + 内高光，读起来仍是一整块实心玻璃。
        val opaqueFallback = when {
            containerColor != null && containerColor.alpha >= 0.5f -> containerColor.copy(alpha = 1f)
            isDark -> DesignTokens.GlassBgOpaque
            else -> DesignTokens.LGlassBgOpaque
        }
        modifier.clip(shape).background(opaqueFallback, shape)
    }

    modifier
        .border(borderWidth, borderColor, shape)
        .drawBehind {
            // inset 0 1px 0 rgba(255,255,255,.07) —— 顶部内高光，模拟 Apple 连续曲率
            val stroke = 1.dp.toPx()
            drawLine(
                color = palette.glassHighlight,
                start = Offset(stroke * 4f, stroke / 2f),
                end = Offset(size.width - stroke * 4f, stroke / 2f),
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
