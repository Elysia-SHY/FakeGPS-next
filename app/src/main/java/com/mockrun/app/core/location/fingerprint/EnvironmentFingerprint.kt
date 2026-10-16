package com.mockrun.app.core.location.fingerprint

import java.io.Serializable

/**
 * 一个「地点环境指纹」：在某个真实地点采集到的 Wi-Fi / 基站 / 蓝牙快照。
 *
 * 采集时必须处于「模拟关闭」状态。否则采到的是模块自己的伪造值，
 * 回放时会形成自指（回放自己采集的自己）。
 *
 * 回放时用 [originLat]/[originLon] 重标定 RSSI —— 它们不是元数据，
 * 是参与计算的输入。
 */
data class EnvironmentFingerprint(
    /** 采集点 WGS-84 坐标 */
    val originLat: Double,
    val originLon: Double,
    /** 采集时刻的墙钟时间 */
    val capturedAt: Long,
    /** 采集时的 elapsedRealtime，用于重写时间戳字段 */
    val capturedElapsedNanos: Long,

    val wifi: List<WifiAp>,
    val cells: List<CellTower>,
    val ble: List<BleBeacon>,
    val bonded: List<BondedDevice>,

    /** 采集时的运营商信息，回放时保持一致 */
    val networkOperatorMccMnc: String = "",
    val simOperatorMccMnc: String = "",
    val networkOperatorName: String = "",

    // ---------------------------------------------------------------- 采集诊断
    //
    // 以下字段全部可空且默认 null。两条硬约束决定了必须这么写：
    //
    //  1. **旧 JSON 兼容**：老指纹包里没有这些键。Gson 用 Unsafe 分配对象，
    //     不调用 Kotlin 构造器，因此 Kotlin 默认值在反序列化时**不生效**——
    //     非空类型的缺字段会变成 null 或 0。可空类型是唯一能让「缺字段」
    //     与「值为 0」区分开的写法。
    //  2. **null 表示「未知」，不是「0」**：把未知显示成 0 正是本次要修的病灶。
    //     UI 侧判断 null 时应显示「未知」，而不是「0 个」。

    /** 截断前 Wi-Fi 原始发现数（去重后）；[wifi].size 是保留的最强 N 条 */
    val wifiDiscovered: Int? = null,

    /** 截断前基站原始条数（含被丢弃的未支持 RAT） */
    val cellsDiscovered: Int? = null,

    /** 因 RAT 不受支持（NR/CDMA/TD-SCDMA 等未映射分支）被丢弃的条数 */
    val cellsDropped: Int? = null,

    /** Wi-Fi 采集状态，取值见 [SourceState.name] */
    val wifiState: String? = null,

    /** 基站采集状态，取值见 [SourceState.name] */
    val cellsState: String? = null,

    /** 蓝牙扫描状态，取值见 [SourceState.name] */
    val bleState: String? = null,

    /** 已配对设备读取状态，取值见 [SourceState.name] */
    val bondedState: String? = null,

    /** ScanCallback.onScanFailed 的 errorCode；null 表示未失败或未收到回调 */
    val bleScanError: Int? = null,

    /** 采集坐标来源：MANUAL / DEVICE / MOCK_TARGET */
    val originSource: String? = null,

    /** 采集期间无法确认模拟已停止时为 true（采集结果可能被自身注入污染） */
    val mockNotStopped: Boolean? = null
) : Serializable

data class WifiAp(
    val ssid: String,
    val bssid: String,
    /** 例如 "[WPA2-PSK-CCMP][ESS]" */
    val capabilities: String,
    /** 采集时的原始 RSSI（dBm） */
    val rssi: Int,
    /** 2412 / 5180 这类，MHz */
    val frequency: Int,
    val channelWidth: Int = 0,
    val centerFreq0: Int = 0,
    val centerFreq1: Int = 0,
    val wifiStandard: Int = 0,
    /** 是否当前连接的那个 */
    val isConnected: Boolean = false
) : Serializable

data class CellTower(
    /** "LTE" / "GSM" / "WCDMA" / "NR" */
    val type: String,
    val mcc: Int,
    val mnc: Int,
    /** LTE: ci / GSM: cid / WCDMA: cid / NR: NCI 的低 32 位（兼容旧读取端，可能截断） */
    val cid: Int,
    /** LTE: pci / GSM: bsic / WCDMA: psc / NR: pci */
    val pci: Int,
    /** LTE: tac / GSM: lac / WCDMA: lac / NR: tac */
    val tac: Int,
    val earfcn: Int = -1,
    val rssi: Int = -1,
    val rsrp: Int = -1,
    val rsrq: Int = -1,
    val rssnr: Int = -1,
    val isRegistered: Boolean = false,
    /** CellInfo.CONNECTION_PRIMARY_SERVING / NONE */
    val connectionStatus: Int = 0,
    /**
     * NR 专属：36-bit NCI。
     *
     * 必须独立成 Long 字段——36 bit 装不进 Int（NR NCI 上限 68719476735 > Int.MAX_VALUE），
     * 塞进 [cid] 会被截断成负数或错误值。非 NR 制式保持 null。
     * 旧 JSON 没有这个键，读出来是 null，回放端据此判定「无有效 NCI」。
     */
    val nci: Long? = null
) : Serializable

data class BleBeacon(
    val address: String,
    val name: String?,
    val rssi: Int,
    /** 原始广播包（hex），回放时用 ScanRecord.parseFromBytes 还原 */
    val scanRecordHex: String,
    val txPower: Int = 127,
    val primaryPhy: Int = 1
) : Serializable

data class BondedDevice(
    val address: String,
    val name: String?,
    val majorClass: Int = 0
) : Serializable
