package com.mockrun.app.core.location.fingerprint

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 在真实地点采集环境指纹。
 *
 * 调用前必须停止虚拟定位 —— 否则读到的是模块自己的伪造值。
 */
class FingerprintCollector(private val context: Context) {

    private companion object {
        const val TAG = "FingerprintCollector"
        /** 只保留最强的 N 个 AP。弱信号 AP 随时间大幅变化，保留它们降低可复现性。 */
        const val MAX_WIFI_APS = 20
        const val MAX_BLE = 30
        const val BLE_SCAN_MS = 8000L
    }

    @SuppressLint("MissingPermission")
    suspend fun collect(originLat: Double, originLon: Double): EnvironmentFingerprint =
        withContext(Dispatchers.IO) {
            // 确保读到真实硬件状态：清掉 hook 状态并等 system_server 缓存刷新
            com.mockrun.app.hook.HookStateBridge.update(context, false)
            com.mockrun.app.core.location.MockLocationEngine.forceCleanAllTestProviders(context)
            delay(1500)

            val wifi = collectWifi()
            val cells = collectCells()
            val ble = collectBle()
            val bonded = collectBonded()
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

            EnvironmentFingerprint(
                originLat = originLat,
                originLon = originLon,
                capturedAt = System.currentTimeMillis(),
                capturedElapsedNanos = SystemClock.elapsedRealtimeNanos(),
                wifi = wifi,
                cells = cells,
                ble = ble,
                bonded = bonded,
                networkOperatorMccMnc = tm?.networkOperator.orEmpty(),
                simOperatorMccMnc = tm?.simOperator.orEmpty(),
                networkOperatorName = tm?.networkOperatorName.orEmpty()
            )
        }

    // ---------------------------------------------------------------- Wi-Fi

    @SuppressLint("MissingPermission")
    private fun collectWifi(): List<WifiAp> {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return emptyList()

        runCatching { wm.startScan() }.logFailure(TAG, "trigger wifi scan", Diag.Level.DEBUG)
        Thread.sleep(3000)

        val results = runCatching { wm.scanResults }
            .logFailure(TAG, "read WifiManager.getScanResults()")
            .getOrDefault(emptyList())

        if (results.isEmpty()) {
            Diag.w(
                TAG,
                "Wi-Fi scan returned 0 APs — check ACCESS_WIFI_STATE / location permission, " +
                    "or the device really has no APs nearby"
            )
        }

        val connectedBssid = runCatching { wm.connectionInfo?.bssid }
            .logFailure(TAG, "read WifiManager.getConnectionInfo()", Diag.Level.DEBUG)
            .getOrNull()

        return results
            .sortedByDescending { it.level }
            .take(MAX_WIFI_APS)
            .map { r ->
                WifiAp(
                    ssid = r.SSID ?: "",
                    bssid = r.BSSID ?: "",
                    capabilities = r.capabilities ?: "",
                    rssi = r.level,
                    frequency = r.frequency,
                    channelWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) r.channelWidth else 0,
                    centerFreq0 = r.centerFreq0,
                    centerFreq1 = r.centerFreq1,
                    wifiStandard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching { r.wifiStandard }.getOrDefault(0)
                    } else 0,
                    isConnected = connectedBssid != null && connectedBssid == r.BSSID
                )
            }
    }

    // ---------------------------------------------------------------- 基站

    @SuppressLint("MissingPermission")
    private fun collectCells(): List<CellTower> {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return emptyList()

        val infos = runCatching { tm.allCellInfo }
            .logFailure(TAG, "read TelephonyManager.getAllCellInfo()")
            .getOrDefault(emptyList())

        return infos.mapNotNull { mapCell(it) }
    }

    private fun mapCell(ci: CellInfo): CellTower? = runCatching {
        val registered = ci.isRegistered
        val status = ci.cellConnectionStatus
        when (ci) {
            is CellInfoLte -> {
                val id = ci.cellIdentity
                val sig = ci.cellSignalStrength
                CellTower(
                    type = "LTE",
                    mcc = id.mcc, mnc = id.mnc, cid = id.ci, pci = id.pci, tac = id.tac,
                    earfcn = id.earfcn,
                    rssi = sig.rssi, rsrp = sig.rsrp, rsrq = sig.rsrq, rssnr = sig.rssnr,
                    isRegistered = registered, connectionStatus = status
                )
            }
            is CellInfoGsm -> {
                val id = ci.cellIdentity
                val sig = ci.cellSignalStrength
                CellTower(
                    type = "GSM",
                    mcc = id.mcc, mnc = id.mnc, cid = id.cid, pci = id.bsic, tac = id.lac,
                    rssi = sig.rssi,
                    isRegistered = registered, connectionStatus = status
                )
            }
            is CellInfoWcdma -> {
                val id = ci.cellIdentity
                val sig = ci.cellSignalStrength
                CellTower(
                    type = "WCDMA",
                    mcc = id.mcc, mnc = id.mnc, cid = id.cid, pci = id.psc, tac = id.lac,
                    // CellSignalStrengthWcdma 没有 getRssi()，只有 getDbm()
                    rssi = sig.dbm.takeIf { it != Int.MAX_VALUE } ?: -95,
                    isRegistered = registered, connectionStatus = status
                )
            }
            else -> null
        }
    }.logFailure(TAG, "map CellInfo to CellTower", Diag.Level.DEBUG).getOrNull()

    // ---------------------------------------------------------------- BLE

    @SuppressLint("MissingPermission")
    private suspend fun collectBle(): List<BleBeacon> = withContext(Dispatchers.IO) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter ?: return@withContext emptyList()
        if (!adapter.isEnabled) {
            Diag.w(TAG, "Bluetooth off — BLE fingerprint will be empty")
            return@withContext emptyList()
        }
        val scanner = adapter.bluetoothLeScanner ?: return@withContext emptyList()

        val found = java.util.Collections.synchronizedMap(LinkedHashMap<String, BleBeacon>())

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val addr = result.device?.address ?: return
                val rec = result.scanRecord
                // 同一 MAC 多次上报，保留 RSSI 最强的那次
                val existing = found[addr]
                if (existing == null || result.rssi > existing.rssi) {
                    found[addr] = BleBeacon(
                        address = addr,
                        name = rec?.deviceName,
                        rssi = result.rssi,
                        scanRecordHex = rec?.bytes?.joinToString("") { "%02x".format(it) } ?: "",
                        txPower = result.txPower,
                        primaryPhy = result.primaryPhy
                    )
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Diag.w(TAG, "BLE scan failed, code=$errorCode")
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return@withContext try {
            scanner.startScan(emptyList(), settings, cb)
            delay(BLE_SCAN_MS)
            found.values.sortedByDescending { it.rssi }.take(MAX_BLE)
        } catch (e: SecurityException) {
            Diag.w(TAG, "BLE scan needs BLUETOOTH_SCAN / location permission", e)
            emptyList()
        } finally {
            runCatching { scanner.stopScan(cb) }
        }
    }

    // ---------------------------------------------------------------- 已配对

    @SuppressLint("MissingPermission")
    private fun collectBonded(): List<BondedDevice> {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.map { d ->
                BondedDevice(
                    address = d.address ?: "",
                    name = runCatching { d.name }.getOrNull(),
                    majorClass = runCatching { d.bluetoothClass?.majorDeviceClass ?: 0 }.getOrDefault(0)
                )
            }
        }.logFailure(TAG, "read BluetoothAdapter.getBondedDevices()").getOrDefault(emptyList())
    }
}
