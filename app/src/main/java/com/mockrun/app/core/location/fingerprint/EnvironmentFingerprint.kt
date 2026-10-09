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
    val networkOperatorName: String = ""
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
    /** "LTE" / "GSM" / "WCDMA" */
    val type: String,
    val mcc: Int,
    val mnc: Int,
    /** LTE: ci / GSM: cid / WCDMA: cid */
    val cid: Int,
    /** LTE: pci / GSM: bsic / WCDMA: psc */
    val pci: Int,
    /** LTE: tac / GSM: lac / WCDMA: lac */
    val tac: Int,
    val earfcn: Int = -1,
    val rssi: Int = -1,
    val rsrp: Int = -1,
    val rsrq: Int = -1,
    val rssnr: Int = -1,
    val isRegistered: Boolean = false,
    /** CellInfo.CONNECTION_PRIMARY_SERVING / NONE */
    val connectionStatus: Int = 0
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
