package com.mockrun.app.feature.mock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.IosColors
import com.mockrun.app.core.designsystem.IosTypography
import com.mockrun.app.core.location.fingerprint.EnvironmentFingerprint
import com.mockrun.app.core.location.fingerprint.FingerprintCoordinator
import com.mockrun.app.core.location.fingerprint.SourceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 采集结果的严重级别。
 *
 * 引入它的原因：原实现只有「成功 / 抛异常」两种结局，而实际至少有四种 ——
 * 四类数据都采到了、采到了但确实周边为空、部分来源失败、以及保存失败。
 * 把后三种都说成「已采集」会误导用户。
 */
enum class FeedbackSeverity { SUCCESS, PARTIAL, WARNING, ERROR }

/**
 * 一次采集的结果反馈，用于在页面上持续展示（而不是闪一下 Toast）。
 *
 * @param id 指纹标识（与文件名一致）
 * @param severity 决定配色与图标
 * @param title 一句话结论
 * @param detail 具体原因或建议
 * @param saved 是否已确认落盘
 * @param sourceLines 各数据源的「已保存 / 发现」明细，逐行展示
 */
data class CaptureFeedback(
    val id: String,
    val severity: FeedbackSeverity,
    val title: String,
    val detail: String,
    val saved: Boolean,
    val sourceLines: List<String> = emptyList()
) {
    /** 从原始采集结果构造反馈，见本文件 `CaptureFeedback.Companion.from`。 */
    companion object
}

/**
 * 把原始采集结果翻译成一条用户能看懂、能据以行动的结果反馈。
 *
 * 判定顺序是有意的：
 *  1. **保存失败优先**——文件没落盘，后面统计再多也没有意义；
 *  2. **模拟未停**——结果可能是自己伪造的，必须显式警告，否则用户会拿着被污染的数据去回放；
 *  3. **四类全空**——这时才去逐源解释原因，而不是笼统说"采集完成"；
 *  4. **部分来源失败**——有数据但也有源出错，标为部分成功；
 *  5. 其余为成功。
 */
fun CaptureFeedback.Companion.from(
    outcome: FingerprintCoordinator.CaptureOutcome,
    id: String
): CaptureFeedback {
    val fp = outcome.fingerprint
    val lines = buildSourceLines(fp)

    if (!outcome.saved) {
        return CaptureFeedback(
            id = id,
            severity = FeedbackSeverity.ERROR,
            title = "采集完成，但保存失败",
            detail = outcome.saveError
                ?: "指纹文件未写入，列表里不会出现这条记录。请检查存储空间后重试。",
            saved = false,
            sourceLines = lines
        )
    }

    if (fp.mockNotStopped == true) {
        return CaptureFeedback(
            id = id,
            severity = FeedbackSeverity.WARNING,
            title = "已保存，但可能被虚拟定位污染",
            detail = "采集期间无法确认虚拟定位已停止，数据可能来自模块自身的伪造值。" +
                "建议停止虚拟定位后重新采集。",
            saved = true,
            sourceLines = lines
        )
    }

    val allEmpty = fp.wifi.isEmpty() && fp.cells.isEmpty() &&
        fp.ble.isEmpty() && fp.bonded.isEmpty()

    if (allEmpty) {
        val reasons = buildEmptyReasons(fp)
        return CaptureFeedback(
            id = id,
            severity = FeedbackSeverity.WARNING,
            title = "已保存，但四类数据都没采到",
            detail = reasons.ifEmpty {
                "周边确实没有可采集的 Wi-Fi、基站与蓝牙信号。"
            },
            saved = true,
            sourceLines = lines
        )
    }

    val failedStates = listOf(
        fp.wifiState, fp.cellsState, fp.bleState, fp.bondedState
    ).count { state ->
        val s = SourceState.fromName(state)
        s != null && s != SourceState.OK && s != SourceState.OK_EMPTY
    }

    if (failedStates > 0) {
        return CaptureFeedback(
            id = id,
            severity = FeedbackSeverity.PARTIAL,
            title = "已保存，但有 $failedStates 类数据源未能正常采集",
            detail = "带「缓存数据 / 权限被拒 / 开关关闭 / 超时 / 失败 / 不支持」标注的来源" +
                "没有取到真实数据，回放时这部分环境会缺失。",
            saved = true,
            sourceLines = lines
        )
    }

    return CaptureFeedback(
        id = id,
        severity = FeedbackSeverity.SUCCESS,
        title = "已采集并保存",
        detail = "指纹已写入，可在下方列表启用。",
        saved = true,
        sourceLines = lines
    )
}

/** 逐源明细：`Wi-Fi  保留 20 / 发现 45  [正常]`。 */
private fun buildSourceLines(fp: EnvironmentFingerprint): List<String> = buildList {
    add(
        sourceLine(
            "Wi-Fi", fp.wifi.size, fp.wifiDiscovered,
            fp.wifiState, if (fp.wifi.isEmpty()) "无 AP" else null
        )
    )
    add(
        sourceLine(
            "基站", fp.cells.size, fp.cellsDiscovered,
            fp.cellsState,
            when {
                fp.cells.isEmpty() && (fp.cellsDropped ?: 0) > 0 ->
                    "丢弃 ${fp.cellsDropped} 条未支持制式"
                fp.cells.isEmpty() -> "无邻区"
                else -> null
            }
        )
    )
    add(
        sourceLine(
            "蓝牙", fp.ble.size, null,
            fp.bleState,
            fp.bleScanError?.let { "扫描失败 code=$it" }
        )
    )
    add(sourceLine("配对", fp.bonded.size, null, fp.bondedState, null))
}

/**
 * 组装一行来源明细。
 *
 * 关键点：`discovered` 与 `state` 都可能为 null（旧指纹包没有这些字段），
 * 此时显示「未知」而不是 0 —— 把未知显示成 0 正是这次要修的病灶。
 */
private fun sourceLine(
    name: String,
    saved: Int,
    discovered: Int?,
    stateName: String?,
    hint: String?
): String {
    val countText = if (discovered != null && discovered > saved) {
        "$saved / 发现 $discovered"
    } else if (discovered != null) {
        "$saved"
    } else {
        "$saved"
    }
    val state = SourceState.fromName(stateName)
    val stateText = state?.label ?: "未知"
    val parts = mutableListOf(name.padEnd(5), countText, "[$stateText]")
    if (hint != null) parts.add(hint)
    return parts.joinToString(" ")
}

/** 四类全空时，逐源给出可能原因。没有状态信息时不下结论。 */
private fun buildEmptyReasons(fp: EnvironmentFingerprint): String {
    val reasons = mutableListOf<String>()
    listOf(
        "Wi-Fi" to fp.wifiState,
        "基站" to fp.cellsState,
        "蓝牙" to fp.bleState,
        "配对" to fp.bondedState
    ).forEach { (name, stateName) ->
        when (SourceState.fromName(stateName)) {
            SourceState.PERMISSION_DENIED -> reasons.add("$name：权限被拒")
            SourceState.RADIO_OFF -> reasons.add("$name：无线电开关关闭")
            SourceState.TIMEOUT -> reasons.add("$name：扫描超时")
            SourceState.FAILED -> reasons.add("$name：扫描失败")
            SourceState.STALE_CACHE -> reasons.add("$name：只有系统缓存")
            SourceState.UNSUPPORTED -> reasons.add("$name：本机不支持")
            else -> Unit
        }
    }
    if ((fp.cellsDropped ?: 0) > 0) {
        reasons.add("基站：丢弃 ${fp.cellsDropped} 条未支持制式")
    }
    return reasons.joinToString("；")
}

/** 采集时刻的可读格式。用于列表里区分「这是哪个地点、什么时候采的」。 */
fun formatCapturedAt(millis: Long): String =
    if (millis <= 0L) {
        "时间未知"
    } else {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
    }

/**
 * 采集结果卡。
 *
 * 与原实现的关键区别：失败和部分成功**留在页面上**，带明确原因和「重新采集」入口。
 * 原来失败只弹一个 Toast，用户既看不到原因，也没有重试路径。
 */
@Composable
fun FingerprintResultCard(
    result: CaptureFeedback,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val accent = when (result.severity) {
        FeedbackSeverity.SUCCESS -> IosColors.SystemGreen
        FeedbackSeverity.PARTIAL -> IosColors.SystemOrange
        FeedbackSeverity.WARNING -> IosColors.SystemOrange
        FeedbackSeverity.ERROR -> IosColors.SystemRed
    }
    val icon = when (result.severity) {
        FeedbackSeverity.SUCCESS -> Icons.Default.CheckCircle
        FeedbackSeverity.PARTIAL -> Icons.Default.Warning
        FeedbackSeverity.WARNING -> Icons.Default.Warning
        FeedbackSeverity.ERROR -> Icons.Default.ErrorOutline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = result.title,
                style = IosTypography.Subheadline,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭结果提示",
                    tint = IosColors.SecondaryLabel,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = result.detail,
            style = IosTypography.Footnote,
            color = IosColors.SecondaryLabel
        )

        if (result.sourceLines.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            result.sourceLines.forEach { line ->
                Text(
                    text = line,
                    style = IosTypography.Mono13,
                    color = IosColors.Label
                )
                Spacer(Modifier.height(2.dp))
            }
        }

        if (!result.saved || result.severity == FeedbackSeverity.ERROR ||
            result.severity == FeedbackSeverity.PARTIAL
        ) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(
                    text = "重新采集",
                    style = IosTypography.ButtonSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
        }
    }
}
