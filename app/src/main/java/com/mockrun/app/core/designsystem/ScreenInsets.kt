package com.mockrun.app.core.designsystem

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 底部浮空 Tab 栏的真实几何 —— 与 ui/components/AndroidFloatingBottomBar.kt 的实现逐条对应。
 *
 * 实测来源（AndroidFloatingBottomBar.kt:60-72）：
 *     modifier
 *         .navigationBarsPadding()                        // = WindowInsets.navigationBars
 *         .padding(horizontal = 14.dp, vertical = 6.dp)   // 底距 6dp
 *         .height(72.dp)                                  // 栏体 72dp
 *
 * 注意：AppNavigation.kt:71-73 的注释写「Tab 栏 72dp + 底距 22dp」，与实现里的 6dp 不一致。
 * 本对象以**实现**为准：6dp。旧注释里的 22dp 是历史遗留文案，按 navBar + 94.dp 预留的页面
 * 实际比真实需求多留了 16dp 空白。
 */
object BottomBarMetrics {
    /** 栏体高度（AndroidFloatingBottomBar.kt:66） */
    val barHeight: Dp = 72.dp

    /** 栏体上下外距，上下各 6dp（AndroidFloatingBottomBar.kt:63） */
    val barVerticalMargin: Dp = 6.dp

    /** 栏体左右外距（AndroidFloatingBottomBar.kt:63） */
    val barHorizontalMargin: Dp = 14.dp

    /** 栏体宽度上限（AndroidFloatingBottomBar.kt:64） */
    val barMaxWidth: Dp = 480.dp

    /** 栏体圆角（AppShapes.bar = 36dp） */
    val barCornerRadius: Dp = 36.dp
}

/**
 * 屏幕安全区计算 —— 替代各页面写死的 140 / 160 / 48 / 100 / navBar+94。
 *
 * 所有取值都从 [WindowInsets] 与 [BottomBarMetrics] 推导，设备旋转、手势条高度变化、
 * 平板/手机切换时自动跟随，不再依赖任何硬编码常量。
 *
 * 用法（滚动页内容底部留白）：
 * ```
 * LazyColumn(contentPadding = PaddingValues(bottom = ScreenInsets.bottomInset()))
 * ```
 */
object ScreenInsets {
    /** 系统导航栏（手势条 / 三键）底部高度 */
    val navigationBars: Dp
        @Composable
        get() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    /**
     * 浮空底栏占用的总高度
     * = 系统导航栏 + 栏体 72dp + 上下外距 12dp
     */
    val bottomBarHeight: Dp
        @Composable
        get() = navigationBars + BottomBarMetrics.barHeight + BottomBarMetrics.barVerticalMargin * 2

    /**
     * 滚动内容底部留白。
     *
     * @param extra 内容与底栏之间的额外呼吸距离，默认 24dp
     * @param includeBottomBar 底栏被隐藏的页面（如路线库子页覆盖层）传 false，
     *        此时只保留系统导航栏
     */
    @Composable
    fun bottomInset(
        extra: Dp = 24.dp,
        includeBottomBar: Boolean = true
    ): Dp = if (includeBottomBar) {
        bottomBarHeight + extra
    } else {
        navigationBars + extra
    }
}
