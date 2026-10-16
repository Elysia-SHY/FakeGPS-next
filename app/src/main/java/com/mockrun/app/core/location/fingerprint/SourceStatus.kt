package com.mockrun.app.core.location.fingerprint

/**
 * 单类数据源（Wi-Fi / 基站 / 蓝牙 / 配对）的采集结果状态。
 *
 * 存在的理由：采集层此前把所有失败压成同一个 `0`——
 * 蓝牙关闭、权限被拒、扫描回调报错、真的没有设备，四者在指纹里长得一模一样，
 * UI 无法区分「这里确实没有基站」和「我们根本没读到基站」。
 * 每一类数据现在都带一个 [SourceState]，[label] 是给 UI 直接显示的中文短语。
 *
 * 约定：[name] 是持久化进 JSON 的形式（见 EnvironmentFingerprint.wifiState 等字段），
 * 读取端一律经 [fromName] 解析，遇到未知或缺失值返回 null（未知），而不是猜一个状态。
 */
enum class SourceState(val label: String) {
    /** 读取成功且有数据 */
    OK("正常"),

    /** 读取成功，但确实没有数据（无 AP / 无邻区 / 无广播包） */
    OK_EMPTY("无结果"),

    /** 有数据，但来自系统缓存而非本次扫描（结果不新鲜） */
    STALE_CACHE("缓存数据"),

    /** 缺少所需权限（Wi-Fi 扫描需位置权限；BLE 扫描需 BLUETOOTH_SCAN + 位置权限） */
    PERMISSION_DENIED("权限被拒"),

    /** 对应无线电开关关闭（Wi-Fi 关闭 / 蓝牙关闭） */
    RADIO_OFF("开关关闭"),

    /** 等待扫描结果或基站回调超时 */
    TIMEOUT("超时"),

    /** 发起扫描失败或被系统拒绝（例如扫描限流、回调 onScanFailed） */
    FAILED("失败"),

    /** 当前设备/系统不支持该数据源 */
    UNSUPPORTED("不支持");

    companion object {
        /**
         * 从持久化名解析状态。缺失、空串、拼写未知一律返回 null（＝未知），
         * 不回落成 OK —— 把未知显示成「正常」正是要消除的那种假象。
         */
        fun fromName(name: String?): SourceState? {
            if (name.isNullOrEmpty()) return null
            return entries.firstOrNull { it.name == name }
        }
    }
}
