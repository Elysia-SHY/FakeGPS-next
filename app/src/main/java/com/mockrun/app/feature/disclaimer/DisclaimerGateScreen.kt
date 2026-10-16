package com.mockrun.app.feature.disclaimer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mockrun.app.core.designsystem.AppShapes
import com.mockrun.app.core.designsystem.AppSpacing
import com.mockrun.app.core.designsystem.IosColors
import com.mockrun.app.core.designsystem.IosPrimaryButton
import com.mockrun.app.core.designsystem.IosTypography
import com.mockrun.app.core.designsystem.bouncyClickable
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 首次启动的免责声明门禁。
 *
 * 设计要求（来自项目所有者）：
 *  1. 正文必须**滚动到底部**才能解锁输入框 —— 不允许直接勾选/直接确认；
 *  2. 解锁后由用户**手动输入**确认短语，而不是点一下复选框；
 *  3. 输入完全匹配才放行，拒绝则退出应用。
 *
 * 这不是可以跳过的形式条款：本应用会修改系统定位行为，误用后果由使用者承担，
 * 因此必须留下「用户确实读过并主动键入确认」的记录。
 */
object DisclaimerPrefs {
    private const val PREFS_NAME = "disclaimer_prefs"
    private const val KEY_ACCEPTED_VERSION = "accepted_version"

    /** 确认短语。改这里即可换文案；[requiredPhrase] 是唯一真源，UI 与校验共用。 */
    const val requiredPhrase = "我已完整阅读并同意"

    /** 当前声明版本。文案有实质修改时递增，会让所有用户重新确认一次。 */
    const val currentVersion = 1

    fun hasAccepted(context: android.content.Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getInt(KEY_ACCEPTED_VERSION, 0) >= currentVersion

    fun markAccepted(context: android.content.Context) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACCEPTED_VERSION, currentVersion)
            .apply()
    }
}

/** 免责声明的正文段落。分开存放便于将来单独增删条目。 */
private val disclaimerSections: List<Pair<String, String>> = listOf(
    "一、软件性质" to
        "本软件是一款用于位置调试与自动化测试的开发辅助工具，仅供具备相应技术能力的" +
        "人员在合法、合规的场景下自行使用。软件本身不提供任何位置数据，也不代任何" +
        "第三方应用作出行为。",
    "二、使用场景限制" to
        "您承诺仅将本软件用于自有设备的功能测试、应用兼容性调试、自动化测试脚本验证、" +
        "以及依法取得授权的安全评估等用途。不得用于考勤代打卡、位置欺诈、规避监管、" +
        "侵犯他人隐私、伪造行程凭证，或任何违反您所在地法律法规与第三方服务条款的行为。",
    "三、账号与第三方服务风险" to
        "修改设备定位可能被第三方应用或平台判定为异常行为。由此引发的账号封禁、限权、" +
        "数据清除、信用记录受损、劳动关系纠纷或其他后果，均由使用者自行承担，软件作者" +
        "不承担任何责任，亦不提供任何形式的申诉支持或结果担保。",
    "四、设备与数据风险" to
        "本软件需要系统级权限（部分功能需要 Root 或 LSPosed 框架）。授予此类权限可能" +
        "影响设备稳定性、系统更新、保修状态及其他应用运行。您应在操作前自行备份重要" +
        "数据。因使用本软件造成的设备异常、数据丢失、系统损坏或第三方索赔，由使用者" +
        "自行承担。",
    "五、权限说明" to
        "为实现功能，本软件会申请定位、通知、悬浮窗等权限，并在您主动触发时读取" +
        "Wi-Fi、基站、蓝牙等环境信息用于本机指纹采集。这些数据仅保存在本机应用私有" +
        "目录内，不会上传至任何服务器。",
    "六、无担保声明" to
        "本软件按「现状」提供，不附带任何明示或默示的担保，包括但不限于适销性、" +
        "特定用途适用性与不侵权担保。作者不保证软件无错误、不中断，也不保证其能满足" +
        "您的特定需求。",
    "七、责任限制" to
        "在适用法律允许的最大范围内，作者对因使用或无法使用本软件而产生的任何直接、" +
        "间接、附带、特殊或后果性损害均不承担责任，即使已被告知此类损害的可能性。",
    "八、条款变更与接受" to
        "继续使用本软件即表示您已阅读、理解并同意上述全部条款。若您不同意其中任何" +
        "一条，请立即停止使用并卸载本软件。本声明可能随版本更新调整，实质变更后您将" +
        "需要重新确认。",
    "九、法律适用" to
        "您应自行确认在所在国家或地区使用本软件的合法性。若本软件在您所在司法辖区" +
        "被认定为不合法或受限，请勿使用。因违反当地法律法规产生的一切后果，由使用者" +
        "独立承担。"
)

/**
 * 全屏免责声明门禁。
 *
 * @param onAccepted 用户完整阅读并正确输入确认短语后回调
 * @param onDecline 用户选择不同意时回调（由调用方决定退出应用）
 */
@Composable
fun DisclaimerGateScreen(
    onAccepted: () -> Unit,
    onDecline: () -> Unit
) {
    val scrollState = rememberScrollState()
    var reachedBottom by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var showMismatch by remember { mutableStateOf(false) }

    // 监听滚动：只有真正完成首次测量（maxValue != Int.MAX_VALUE）且到达底部
    // （含 24px 容差，或超大屏下内容本身不足一屏 max == 0）时才解锁输入框。
    LaunchedEffect(scrollState) {
        snapshotFlow {
            val max = scrollState.maxValue
            max != Int.MAX_VALUE && (max == 0 || scrollState.value >= max - 24 || !scrollState.canScrollForward)
        }
            .distinctUntilChanged()
            .collect { atBottom -> if (atBottom) reachedBottom = true }
    }

    val inputMatches by remember {
        derivedStateOf { input.trim() == DisclaimerPrefs.requiredPhrase }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(IosColors.SystemGroupedBackground, IosColors.Bg1)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            // ---------------- 顶部标题 ----------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.pageH)
                    .padding(top = 20.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = IosColors.SystemOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "使用前请阅读",
                        style = IosTypography.Title2,
                        color = IosColors.Label,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "本软件会修改设备的定位行为，请在充分理解后果后使用。",
                    style = IosTypography.Footnote,
                    color = IosColors.SecondaryLabel
                )
            }

            // ---------------- 正文（可滚动） ----------------
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = AppSpacing.pageH)
                ) {
                    disclaimerSections.forEach { (title, body) ->
                        Text(
                            text = title,
                            style = IosTypography.Headline,
                            color = IosColors.Label,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = body,
                            style = IosTypography.Footnote,
                            color = IosColors.SecondaryLabel
                        )
                        Spacer(Modifier.height(18.dp))
                    }

                    // 正文末尾的落款，滚动到这里即代表读完
                    Text(
                        text = "—— 以上条款构成您与本软件作者之间的完整约定 ——",
                        style = IosTypography.Caption2,
                        color = IosColors.TertiaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(28.dp))
                }

                // 未读到底时的提示浮层，明确告诉用户还差什么
                if (!reachedBottom) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        IosColors.SystemGroupedBackground.copy(alpha = 0.92f),
                                        IosColors.SystemGroupedBackground
                                    )
                                )
                            )
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↓ 请向下滚动阅读至底部",
                            style = IosTypography.Caption1,
                            color = IosColors.SystemOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ---------------- 底部确认区 ----------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IosColors.SecondaryGroupedBackground)
                    .navigationBarsPadding()
                    .padding(horizontal = AppSpacing.pageH)
                    .padding(top = 16.dp, bottom = 20.dp)
            ) {
                if (!reachedBottom) {
                    Text(
                        text = "阅读到最底部后即可输入确认短语。",
                        style = IosTypography.Caption1,
                        color = IosColors.TertiaryLabel,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "请输入以下短语以确认（须完全一致）：",
                        style = IosTypography.Caption1,
                        color = IosColors.SecondaryLabel
                    )
                    Spacer(Modifier.height(6.dp))
                    // 把待输入的短语显著展示出来，避免用户猜测或复制困难
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.chip)
                            .background(IosColors.SystemBlue.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = DisclaimerPrefs.requiredPhrase,
                            style = IosTypography.Mono13,
                            color = IosColors.SystemBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            if (showMismatch) showMismatch = false
                        },
                        placeholder = { Text("在此输入上方短语", style = IosTypography.Footnote) },
                        singleLine = true,
                        isError = showMismatch,
                        supportingText = {
                            if (showMismatch) {
                                Text(
                                    "输入与要求不一致，请逐字核对",
                                    style = IosTypography.Caption2
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(14.dp))

                IosPrimaryButton(
                    text = "确认并继续",
                    containerColor = if (inputMatches) IosColors.SystemGreen else IosColors.SystemGray,
                    icon = if (inputMatches) Icons.Default.CheckCircle else null,
                    enabled = reachedBottom && inputMatches,
                    onClick = {
                        if (inputMatches) onAccepted() else showMismatch = true
                    }
                )

                Spacer(Modifier.height(8.dp))

                // 不同意也必须是一个可达的出口，而不是让用户卡死在这个页面
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(AppShapes.button)
                        .border(1.dp, IosColors.Line, AppShapes.button)
                        .bouncyClickable(onClick = onDecline),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "不同意，退出应用",
                        style = IosTypography.ButtonSmall,
                        color = IosColors.SecondaryLabel,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
