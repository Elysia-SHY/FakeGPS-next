package com.mockrun.app.core.designsystem

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 最小触区 —— 把小于 48dp 的可点击元素抬到可命中尺寸。
 *
 * 背景：各页面存在大量 12sp 文字 + 6dp 间隔的「启用 / 删除」小按钮，
 * 实际可点区域远小于 Material 规范要求的 48dp，拇指难以命中。
 *
 * 实现说明：用 [sizeIn] 只抬升**下限**。已经足够大的元素不会被缩小；
 * 小于下限的元素测量尺寸变大，从而把点按热区一起撑开，视觉内容仍居中。
 *
 * **使用顺序很重要**：本 Modifier 必须放在 clickable 的**外层**，
 * 否则热区只覆盖内层的小尺寸，起不到扩大触区的作用。
 *
 * ```
 * // 正确：minTouchTarget 在外层
 * Text(
 *     text = "启用",
 *     modifier = Modifier.minTouchTarget().bouncyClickable { onEnable() }
 * )
 *
 * // 正确：包住一个 Row 里的按钮
 * Box(modifier = Modifier.minTouchTarget(minWidth = 56.dp)) { IosGhostButton(...) }
 * ```
 */
fun Modifier.minTouchTarget(
    minWidth: Dp = 48.dp,
    minHeight: Dp = 48.dp
): Modifier = this.sizeIn(minWidth = minWidth, minHeight = minHeight)
