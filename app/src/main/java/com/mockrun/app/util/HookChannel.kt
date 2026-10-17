package com.mockrun.app.util

import android.content.Context

/**
 * 注入通道 —— 与 [InjectionMode] **正交**的第二个维度。
 *
 * [InjectionMode] 回答的是「怎么拿到注入许可」（要不要 su、要不要 appops）；
 * 本枚举回答的是「hook 走哪条路送到目标进程」。两者可以自由组合：
 *
 * | | PROVIDER | LSPOSED |
 * | :--- | :--- | :--- |
 * | ROOT | 只用系统 MockProvider（su 仅用于授权/清残渣） | 框架层改写（system_server + 作用域应用） |
 * | NO_ROOT | 经典免 Root 用法 | 免 Root 也能用框架层改写（本模块的主要优势路径） |
 *
 * 为什么单独成一个维度、而不是塞进 [InjectionMode]：
 * 两条通道的**成功判据、失败面、停机清理方式**完全不同 ——
 * MockProvider 要 appops、结果带 isMock 标记、需要 `register()`；
 * 框架层改写不碰 appops、不带 mock 位、但要发布配置给其它进程消费。
 * 混进同一个开关会让「mock provider 该不该注册」这类判断出现分叉，且升级会静默改变老用户行为。
 *
 * 预留扩展位：将来若加入 root 直注（不需要 LSPosed 的自有装载器），在这里追加一个值即可，
 * 状态判定/清理/文案统一由 [com.mockrun.app.hook.HookChannelStatus] 处理。
 */
enum class HookChannel {
    /** 自动：检测到框架层存活就用 LSPOSED，否则只走 PROVIDER。默认值。 */
    AUTO,

    /** 只用框架层改写。选了它但模块没激活时，状态判定会**显式报警**，而不是假装生效。 */
    LSPOSED,

    /** 只用系统 MockProvider。此通道下不再向 hook 发布任何配置（不写 setprop / 不写 root 文件）。 */
    PROVIDER
}

object HookChannelPrefs {

    private const val PREFS_NAME = "fake_gps_hook_channel_prefs"
    private const val KEY_CHANNEL = "hook_channel"

    fun get(context: Context): HookChannel {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CHANNEL, null) ?: return HookChannel.AUTO
        return HookChannel.values().firstOrNull { it.name == raw } ?: HookChannel.AUTO
    }

    fun set(context: Context, channel: HookChannel) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CHANNEL, channel.name)
            .apply()
    }

    /** UI 点一下循环切换：AUTO → LSPOSED → PROVIDER → AUTO。 */
    fun next(current: HookChannel): HookChannel = when (current) {
        HookChannel.AUTO -> HookChannel.LSPOSED
        HookChannel.LSPOSED -> HookChannel.PROVIDER
        HookChannel.PROVIDER -> HookChannel.AUTO
    }

    /** 便于 UI 展示的短标签。 */
    fun shortLabel(channel: HookChannel): String = when (channel) {
        HookChannel.AUTO -> "自动"
        HookChannel.LSPOSED -> "LSPosed"
        HookChannel.PROVIDER -> "Provider"
    }

    /**
     * 是否需要把伪造坐标发布给 hook（SystemProperties + /data 文件 + Settings.Global）。
     *
     * PROVIDER 通道没有任何消费者：写了只会变成**可被任何进程读到**的坐标残留
     * （`getprop debug.fakegps.lat` 全局可读），所以直接不写。
     */
    fun shouldPublishHookConfig(context: Context): Boolean =
        get(context) != HookChannel.PROVIDER

    /**
     * hook 通道是否能真正接管。
     *
     * 门禁（PermissionCoordinator）和注册（MockLocationEngine.register）都按它判断，
     * 而不是"有没有 root / 有没有在开发者选项里勾选"——因为 LSPosed 通道既不用
     * `addTestProvider` 也不需要 appops `android:mock_location`。
     *
     * 刻意不校验"配置是否已发布"：门禁发生在发布之前，那时还没有配置。
     * 发布是否成功由 HookChannelStatus 的 hookConfigActive 单独反映。
     */
    fun isHookChannelUsable(context: Context, lsposedAlive: Boolean): Boolean =
        get(context) != HookChannel.PROVIDER && lsposedAlive
}
