package com.mockrun.app.hook

import com.mockrun.app.util.HookChannel

/** 实际正在生效的注入通道。 */
enum class ActiveChannel {
    /** 框架层改写（system_server / 作用域应用）在跑。 */
    LSPOSED,

    /** 只有应用层 MockProvider：系统服务、地图/社交 SDK 仍可能拿到真实位置。 */
    PROVIDER_ONLY,

    /** 什么都没生效。 */
    NONE
}

/**
 * 「当前到底靠什么在伪造」的唯一判定 —— 纯函数，可单测。
 *
 * 存在的理由：用户在地图页看到"虚拟定位中"，但 hook 可能根本没生效
 * （LSPosed 未激活 / 作用域没勾 / 进程被回收），于是出现"以为在伪造、实际是真值"，
 * 这类问题最难自查。这里把「偏好 × 存活检测 × 是否在模拟」收敛成一个可展示的状态，
 * 让 UI 永远说的是真话。
 *
 * @param preference 用户在设置里选的通道（见 [HookChannel]）
 * @param isLsposedAlive 框架层是否真的活着（模块自报激活 或 system_server 心跳未过期）
 * @param isMockActive 当前是否正在模拟定位（应用层状态）
 */
data class HookChannelStatus(
    val active: ActiveChannel,
    /** 状态胶囊里显示的短标签。 */
    val shortLabel: String,
    /** 功能页/日志用的完整说明。 */
    val detail: String,
    /** true = 用户以为在伪造，但很可能没有生效（UI 需要显式提示）。 */
    val warn: Boolean
) {
    companion object {

        fun resolve(
            preference: HookChannel,
            isLsposedAlive: Boolean,
            hookConfigActive: Boolean,
            isMockActive: Boolean
        ): HookChannelStatus {
            // 1. 用户明确只要 Provider：即使框架层恰好活着也不算数 ——
            //    该通道下我们不发布配置，hook 拿不到坐标，不会真的伪造。
            if (preference == HookChannel.PROVIDER) {
                return if (isMockActive) {
                    HookChannelStatus(
                        ActiveChannel.PROVIDER_ONLY,
                        "仅 Provider",
                        "按设置仅使用系统 MockProvider：系统服务与地图/社交 SDK 可能仍返回真实位置",
                        warn = false
                    )
                } else {
                    HookChannelStatus(ActiveChannel.NONE, "未启动", "当前没有正在运行的模拟定位", warn = false)
                }
            }

            // 2. 框架层活着 **且** hook 确实拿到了生效配置 —— 两个条件都成立才算数。
            //    只判存活会撒谎：真机实测过 system_server 里模块在跑、
            //    但 [debug.fakegps.active]=0（没拿到坐标），此时实际仍只有 Provider 在生效。
            if (isLsposedAlive && hookConfigActive) {
                return HookChannelStatus(
                    ActiveChannel.LSPOSED,
                    "LSPosed",
                    "system_server 框架层改写运行中（含按 uid 定向分流）",
                    warn = false
                )
            }

            // 3. 用户点名要 LSPosed，但模块没起来 —— 只有**正在模拟**时才报警。
            //    没有在模拟时什么都不用伪造，此时报警就是"狼来了"，会让用户习惯性忽略提示。
            if (preference == HookChannel.LSPOSED && isMockActive) {
                return HookChannelStatus(
                    ActiveChannel.NONE,
                    "未生效",
                    if (isLsposedAlive) {
                        "框架层在跑，但 hook 没有拿到生效配置（坐标/规则未同步）—— 当前实际只有 Provider 生效"
                    } else {
                        "已选择 LSPosed 通道但模块未激活：请在 LSPosed 中勾选本模块与【系统框架】后重启"
                    },
                    warn = true
                )
            }

            // 4. AUTO 且框架层不在：退化成只有 MockProvider（能伪造，但覆盖不到 SDK/系统服务）。
            if (isMockActive) {
                return HookChannelStatus(
                    ActiveChannel.PROVIDER_ONLY,
                    "仅 Provider",
                    "未检测到 LSPosed，已自动回退为系统 MockProvider：地图/社交 SDK 可能仍显示真实位置",
                    warn = false
                )
            }

            return HookChannelStatus(ActiveChannel.NONE, "未启动", "当前没有正在运行的模拟定位", warn = false)
        }
    }
}
