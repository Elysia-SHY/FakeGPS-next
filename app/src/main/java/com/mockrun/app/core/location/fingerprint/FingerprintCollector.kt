package com.mockrun.app.core.location.fingerprint

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult as BleScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult as WifiScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 在真实地点采集环境指纹。
 *
 * 调用前必须停止虚拟定位 —— 否则读到的是模块自己的伪造值。
 *
 * ## 每一类数据都带状态
 *
 * 采集层此前把所有失败压成同一个 `0`：蓝牙关闭、权限被拒、扫描回调报错、
 * 真的没有设备，在指纹里长得完全一样。现在每类数据返回
 * `(条目, 发现数, SourceState)`，由调用方写进 [EnvironmentFingerprint] 的对应字段。
 *
 * 采集窗口本身也可能被污染：MockLocationService 每 500 ms 注入一次、
 * FloatingJoystickService 每 100 ms 一次。本类**不停止**这些服务（那是编排层的职责），
 * 但会通过 [ensureRealState] 判断它们是否仍在写入，并把结论暴露给调用方。
 */
class FingerprintCollector(private val context: Context) {

    private companion object {
        const val TAG = "FingerprintCollector"
        /** 只保留最强的 N 个 AP。弱信号 AP 随时间大幅变化，保留它们降低可复现性。 */
        const val MAX_WIFI_APS = 20
        const val MAX_BLE = 30
        const val BLE_SCAN_MS = 8000L

        /** 等 SCAN_RESULTS_AVAILABLE_ACTION 的上限 */
        const val WIFI_SCAN_TIMEOUT_MS = 6000L

        /** 超过这个年龄的扫描结果视为系统缓存而非本次扫描 */
        const val WIFI_FRESH_MAX_AGE_MS = 15_000L

        /** 等 requestCellInfoUpdate 回调的上限 */
        const val CELL_UPDATE_TIMEOUT_MS = 5000L

        /** 置为「真实态」之后观察是否被重新注入的窗口 */
        const val REAL_STATE_SETTLE_MS = 1500L
    }

    /**
     * 「确保真实态」的结果。
     *
     * [entered] 为 false 表示置为真实态之后，[REAL_STATE_SETTLE_MS] 窗口内
     * 又有别的组件把 hook 状态写回 active —— 采集窗口没有真正隔离，
     * 本次采到的数据可能混有自身注入。
     *
     * [detail] 是给人看的补充信息（日志/诊断用），不参与判断。
     */
    data class RealState(val entered: Boolean, val detail: String?)

    /**
     * 把设备置为真实态并确认隔离成立。
     *
     * 只清 hook 状态与 test provider，**不停止** MockLocationService /
     * FloatingJoystickService —— 停服务属于编排层的职责，采集器越权去停会
     * 与 UI 的生命周期管理打架。
     *
     * 判断依据是「我们自己写入之后，updateElapsed 是否被别人推进过」：
     * 两个注入服务每次注入都会调 `HookStateBridge.update(active = true)`，
     * 从而推进 updateElapsed。这个信号是实测得到的，不依赖任何猜测。
     */
    suspend fun ensureRealState(): RealState {
        com.mockrun.app.hook.HookStateBridge.update(context, false)
        com.mockrun.app.core.location.MockLocationEngine.forceCleanAllTestProviders(context)

        // 记录我们自己这次写入的时间戳，之后任何更大的值都来自别的组件
        val mine = com.mockrun.app.hook.HookStateBridge.updateElapsed
        delay(REAL_STATE_SETTLE_MS)

        val flagStillActive = com.mockrun.app.hook.HookStateBridge.isHookActive
        val elapsedAdvanced = com.mockrun.app.hook.HookStateBridge.updateElapsed > mine
        val reinjected = flagStillActive || elapsedAdvanced

        val detail = buildString {
            append("hookActive=").append(flagStillActive)
            append(", elapsedAdvanced=").append(elapsedAdvanced)
            append(", settleMs=").append(REAL_STATE_SETTLE_MS)
        }

        if (reinjected) {
            Diag.w(
                TAG,
                "real state NOT confirmed: simulation re-injected during capture window ($detail). " +
                    "Caller must stop MockLocationService / FloatingJoystickService before collecting."
            )
        }
        return RealState(entered = !reinjected, detail = detail)
    }

    /**
     * 采集。
     *
     * @param originSource 坐标来源标记，写进 [EnvironmentFingerprint.originSource]：
     *        MANUAL（用户手填）/ DEVICE（设备真实定位）/ MOCK_TARGET（当前模拟目标点）
     */
    @SuppressLint("MissingPermission")
    suspend fun collect(
        originLat: Double,
        originLon: Double,
        originSource: String = "MANUAL"
    ): EnvironmentFingerprint = withContext(Dispatchers.IO) {
        val real = ensureRealState()

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
            wifi = wifi.items,
            cells = cells.items,
            ble = ble.items,
            bonded = bonded.items,
            networkOperatorMccMnc = tm?.networkOperator.orEmpty(),
            simOperatorMccMnc = tm?.simOperator.orEmpty(),
            networkOperatorName = tm?.networkOperatorName.orEmpty(),
            wifiDiscovered = wifi.discovered,
            cellsDiscovered = cells.discovered,
            cellsDropped = cells.dropped,
            wifiState = wifi.state.name,
            cellsState = cells.state.name,
            bleState = ble.state.name,
            bondedState = bonded.state.name,
            bleScanError = ble.scanError,
            originSource = originSource,
            mockNotStopped = !real.entered
        )
    }

    /** 一类数据源的采集结果。 */
    private class Source<T>(
        val items: List<T>,
        val discovered: Int,
        val state: SourceState,
        /** 被丢弃的未支持条目数（目前只用于基站 RAT） */
        val dropped: Int = 0,
        val scanError: Int? = null
    )

    // ---------------------------------------------------------------- 权限

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // ---------------------------------------------------------------- Wi-Fi

    @SuppressLint("MissingPermission")
    private suspend fun collectWifi(): Source<WifiAp> = withContext(Dispatchers.IO) {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return@withContext Source(emptyList(), 0, SourceState.UNSUPPORTED)

        if (!runCatching { wm.isWifiEnabled }.getOrDefault(false)) {
            Diag.w(TAG, "Wi-Fi is off — fingerprint will carry no APs")
            return@withContext Source(emptyList(), 0, SourceState.RADIO_OFF)
        }

        // API 29+ getScanResults 需要位置权限；缺了只会拿到空列表而不报错
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Diag.w(TAG, "ACCESS_FINE_LOCATION not granted — Wi-Fi scan results unavailable")
            return@withContext Source(emptyList(), 0, SourceState.PERMISSION_DENIED)
        }

        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                latch.countDown()
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)

        var started = false
        var broadcastSeen = false
        try {
            // Android 13+ 注册非导出 receiver 必须显式声明，否则抛 SecurityException
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }

            // startScan() 的返回值必须看：false 表示这次扫描没有真正发起
            // （Android 10+ 前台应用被限流到约 4 次 / 2 分钟），此时读到的必然是缓存。
            started = runCatching { wm.startScan() }
                .logFailure(TAG, "trigger wifi scan", Diag.Level.DEBUG)
                .getOrDefault(false)

            broadcastSeen = latch.await(WIFI_SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: SecurityException) {
            Diag.w(TAG, "registerReceiver for SCAN_RESULTS_AVAILABLE_ACTION denied", e)
            return@withContext Source(emptyList(), 0, SourceState.PERMISSION_DENIED)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }

        if (!broadcastSeen) {
            Diag.w(TAG, "no SCAN_RESULTS_AVAILABLE_ACTION within ${WIFI_SCAN_TIMEOUT_MS}ms (startScan=$started)")
        }

        val raw = runCatching { wm.scanResults }
            .logFailure(TAG, "read WifiManager.getScanResults()")
            .getOrNull()
            ?: return@withContext Source(emptyList(), 0, SourceState.FAILED)

        val connectedBssid = runCatching { wm.connectionInfo?.bssid }
            .logFailure(TAG, "read WifiManager.getConnectionInfo()", Diag.Level.DEBUG)
            .getOrNull()

        // 按 BSSID 去重，同一 AP 只保留最强的一次上报。
        // BSSID 缺失（部分机型在无权限时返回 02:00:00:00:00:00）时退回 SSID+频率做键。
        val byKey = LinkedHashMap<String, WifiScanResult>()
        for (r in raw) {
            val bssid = r.BSSID
            val key = if (bssid.isNullOrEmpty() || bssid == "02:00:00:00:00:00") {
                "${r.SSID}@${r.frequency}"
            } else bssid
            val existing = byKey[key]
            if (existing == null || r.level > existing.level) byKey[key] = r
        }
        val deduped = byKey.values.toList()
        val discovered = deduped.size

        val kept = deduped
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

        val ageMs = maxResultAgeMs(deduped)
        // 新鲜度：本次扫描确实发起、确实收到广播、且结果时间戳足够新
        val fresh = started && broadcastSeen && (ageMs == null || ageMs <= WIFI_FRESH_MAX_AGE_MS)

        val state = when {
            kept.isEmpty() -> SourceState.OK_EMPTY
            fresh -> SourceState.OK
            else -> SourceState.STALE_CACHE
        }

        if (state == SourceState.STALE_CACHE) {
            Diag.w(
                TAG,
                "Wi-Fi results are stale: startScan=$started broadcast=$broadcastSeen ageMs=$ageMs — " +
                    "Android 10+ throttles startScan, this is the system cache"
            )
        }

        Source(kept, discovered, state)
    }

    /**
     * 结果里最老一条的年龄（ms）；拿不到时间戳返回 null（＝无法判定）。
     *
     * `ScanResult.timestamp`（开机以来的微秒数）只在 API 30+ 可用，
     * 而 `ScanResult.seen` 在 API 34 的公开 SDK 里已经不存在，
     * 因此 API 29 上无法判定新鲜度，如实返回 null —— 调用方把 null 当作
     * 「没有证据表明是缓存」，不会凭空扣一个 STALE_CACHE 的帽子。
     */
    private fun maxResultAgeMs(results: List<WifiScanResult>): Long? {
        if (results.isEmpty()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val nowMicros = SystemClock.elapsedRealtimeNanos() / 1000L
        return results.maxOfOrNull { nowMicros - it.timestamp }?.let { it / 1000L }
    }

    // ---------------------------------------------------------------- 基站

    /**
     * 读基站。
     *
     * API 29+ 优先走 `requestCellInfoUpdate(executor, callback)`——`getAllCellInfo()`
     * 在 29 之后返回的是缓存，这正是「基站 0」的第二个来源。
     * `allCellInfo` 只作为**明确标注**的 fallback：拿到数据时状态记为 [SourceState.STALE_CACHE]，
     * 而不是假装成一次新鲜读取。
     */
    @SuppressLint("MissingPermission")
    private suspend fun collectCells(): Source<CellTower> = withContext(Dispatchers.IO) {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return@withContext Source(emptyList(), 0, SourceState.UNSUPPORTED)

        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Diag.w(TAG, "ACCESS_FINE_LOCATION not granted — cell info unavailable")
            return@withContext Source(emptyList(), 0, SourceState.PERMISSION_DENIED)
        }

        val async = requestCellInfoAsync(tm)

        val infos: List<CellInfo>
        val state: SourceState
        when {
            async.denied -> return@withContext Source(emptyList(), 0, SourceState.PERMISSION_DENIED)

            async.received -> {
                infos = async.infos
                state = if (infos.isEmpty()) SourceState.OK_EMPTY else SourceState.OK
            }

            else -> {
                // 异步路径失败/超时 —— 退回缓存读，并如实标注
                val cached = runCatching { tm.allCellInfo }
                    .logFailure(TAG, "fallback read TelephonyManager.getAllCellInfo()")
                    .getOrNull()
                    .orEmpty()
                infos = cached
                state = if (cached.isEmpty()) {
                    if (async.timedOut) SourceState.TIMEOUT else SourceState.FAILED
                } else {
                    SourceState.STALE_CACHE
                }
                Diag.w(
                    TAG,
                    "requestCellInfoUpdate did not deliver (timedOut=${async.timedOut}, " +
                        "error=${async.errorCode}); fell back to getAllCellInfo() cache: ${cached.size} entries"
                )
            }
        }

        val mapped = ArrayList<CellTower>(infos.size)
        var dropped = 0
        for (ci in infos) {
            val t = mapCell(ci)
            if (t == null) dropped++ else mapped.add(t)
        }

        if (dropped > 0) {
            Diag.w(
                TAG,
                "$dropped cell(s) dropped: RAT not mapped to CellTower " +
                    "(types=${infos.map { it.javaClass.simpleName }.distinct()})"
            )
        }

        Source(mapped, discovered = infos.size, state = state, dropped = dropped)
    }

    private class CellAsync(
        val received: Boolean,
        val infos: List<CellInfo>,
        val timedOut: Boolean,
        val denied: Boolean,
        val errorCode: Int = -1
    )

    /** 走 requestCellInfoUpdate，带超时；失败/超时由调用方决定是否回落缓存。 */
    @SuppressLint("MissingPermission")
    private fun requestCellInfoAsync(tm: TelephonyManager): CellAsync {
        val latch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val result = java.util.Collections.synchronizedList(ArrayList<CellInfo>())
        val error = AtomicInteger(-1)

        val callback = object : TelephonyManager.CellInfoCallback() {
            override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                result.addAll(cellInfo)
                latch.countDown()
            }

            override fun onError(errorCode: Int, detail: Throwable?) {
                error.set(errorCode)
                latch.countDown()
            }
        }

        return try {
            tm.requestCellInfoUpdate(executor, callback)
            val ok = latch.await(CELL_UPDATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (ok && error.get() >= 0) {
                CellAsync(received = false, infos = emptyList(), timedOut = false, denied = false, errorCode = error.get())
            } else if (ok) {
                CellAsync(received = true, infos = result.toList(), timedOut = false, denied = false)
            } else {
                CellAsync(received = false, infos = emptyList(), timedOut = true, denied = false)
            }
        } catch (e: SecurityException) {
            Diag.w(TAG, "requestCellInfoUpdate denied", e)
            CellAsync(received = false, infos = emptyList(), timedOut = false, denied = true)
        } catch (e: Throwable) {
            Diag.w(TAG, "requestCellInfoUpdate failed", e)
            CellAsync(received = false, infos = emptyList(), timedOut = false, denied = false)
        } finally {
            runCatching { executor.shutdownNow() }
        }
    }

    /**
     * 把一条 CellInfo 映射成 CellTower；不支持的 RAT 返回 null（由调用方计入 dropped）。
     *
     * 此前这里只有 LTE/GSM/WCDMA 三个分支、`else -> null` 静默丢弃 ——
     * 5G 机型上报的 NR 邻区全部落到 else，于是「基站 0」。
     */
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
            is CellInfoNr -> mapNr(ci, registered, status)
            else -> null
        }
    }.logFailure(TAG, "map CellInfo to CellTower", Diag.Level.DEBUG).getOrNull()

    /**
     * NR（5G）分支。
     *
     * NCI 是 36-bit，装不进 Int：`CellIdentityNr.getNci()` 返回 Long，
     * 必须原样存进 [CellTower.nci]。同时保留 `cid` 字段供旧读取端使用，
     * 填 NCI 的低 32 位（旧代码里 NR 本来就到不了这里，因此不破坏既有行为）。
     */
    private fun mapNr(ci: CellInfoNr, registered: Boolean, status: Int): CellTower {
        val id = ci.cellIdentity as? CellIdentityNr
        val sig = ci.cellSignalStrength as? CellSignalStrengthNr

        val nci: Long? = id?.nci
        return CellTower(
            type = "NR",
            mcc = id?.mccString?.toIntOrNull() ?: 0,
            mnc = id?.mncString?.toIntOrNull() ?: 0,
            // 兼容字段：旧读取端只有 Int 的 cid，这里给 NCI 低 32 位
            cid = nci?.let { (it and 0xFFFFFFFFL).toInt() } ?: 0,
            pci = id?.pci ?: 0,
            tac = id?.tac ?: 0,
            earfcn = id?.nrarfcn ?: -1,
            // NR 没有 RSSI 概念，用 getDbm() 作为等价的强度值（与 WCDMA 分支同样的处理）
            rssi = sig?.dbm?.takeIf { it != Int.MAX_VALUE } ?: -95,
            rsrp = sig?.ssRsrp ?: -1,
            rsrq = sig?.ssRsrq ?: -1,
            rssnr = sig?.ssSinr ?: -1,
            isRegistered = registered,
            connectionStatus = status,
            nci = nci
        )
    }

    // ---------------------------------------------------------------- BLE

    @SuppressLint("MissingPermission")
    private suspend fun collectBle(): Source<BleBeacon> = withContext(Dispatchers.IO) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return@withContext Source(emptyList(), 0, SourceState.UNSUPPORTED)

        if (!adapter.isEnabled) {
            Diag.w(TAG, "Bluetooth off — BLE fingerprint will be empty")
            return@withContext Source(emptyList(), 0, SourceState.RADIO_OFF)
        }

        // 按 SDK 版本预检真实权限，而不是等 startScan 抛 SecurityException 再静默变空
        val missing = missingBlePermissions()
        if (missing.isNotEmpty()) {
            Diag.w(TAG, "BLE scan blocked: missing ${missing.joinToString(", ")}")
            return@withContext Source(emptyList(), 0, SourceState.PERMISSION_DENIED)
        }

        val scanner = adapter.bluetoothLeScanner
            ?: return@withContext Source(emptyList(), 0, SourceState.UNSUPPORTED)

        val found = java.util.Collections.synchronizedMap(LinkedHashMap<String, BleBeacon>())
        val failureCode = AtomicInteger(-1)

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: BleScanResult) {
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

            override fun onBatchScanResults(results: MutableList<BleScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                // 以前只写日志，errorCode 被丢掉，UI 只能看到「蓝牙 0」
                failureCode.set(errorCode)
                Diag.w(TAG, "BLE scan failed, code=$errorCode")
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return@withContext try {
            scanner.startScan(emptyList(), settings, cb)
            delay(BLE_SCAN_MS)
            val items = found.values.sortedByDescending { it.rssi }.take(MAX_BLE)
            val err = failureCode.get().takeIf { it >= 0 }
            val state = when {
                err != null -> SourceState.FAILED
                items.isEmpty() -> SourceState.OK_EMPTY
                else -> SourceState.OK
            }
            Source(items, discovered = found.size, state = state, scanError = err)
        } catch (e: SecurityException) {
            Diag.w(TAG, "BLE scan needs BLUETOOTH_SCAN / location permission", e)
            Source(emptyList(), 0, SourceState.PERMISSION_DENIED)
        } finally {
            // 无论成功、失败还是异常，都必须停扫，否则扫描会一直挂着耗电
            runCatching { scanner.stopScan(cb) }
                .logFailure(TAG, "stopScan BLE", Diag.Level.DEBUG)
        }
    }

    /** 列出当前 SDK 下扫描 BLE 真正缺少的权限。 */
    private fun missingBlePermissions(): List<String> {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+：BLUETOOTH_SCAN 是扫描的硬前提；
            // 本应用未声明 neverForLocation，因此位置权限同样是硬前提。
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        return needed.filter { !granted(it) }
    }

    // ---------------------------------------------------------------- 已配对

    @SuppressLint("MissingPermission")
    private fun collectBonded(): Source<BondedDevice> {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return Source(emptyList(), 0, SourceState.UNSUPPORTED)

        // 蓝牙关闭时明确报 RADIO_OFF，而不是和「确实没有配对设备」混成同一个 0
        if (!adapter.isEnabled) {
            Diag.w(TAG, "Bluetooth off — bonded device list unavailable")
            return Source(emptyList(), 0, SourceState.RADIO_OFF)
        }

        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH)
        }
        val missing = needed.filter { !granted(it) }
        if (missing.isNotEmpty()) {
            Diag.w(TAG, "bonded devices blocked: missing ${missing.joinToString(", ")}")
            return Source(emptyList(), 0, SourceState.PERMISSION_DENIED)
        }

        val devices = runCatching { adapter.bondedDevices }
            .logFailure(TAG, "read BluetoothAdapter.getBondedDevices()")
            .getOrNull()
            ?: return Source(emptyList(), 0, SourceState.FAILED)

        val items = devices.map { d ->
            BondedDevice(
                address = d.address ?: "",
                name = runCatching { d.name }.getOrNull(),
                majorClass = runCatching { d.bluetoothClass?.majorDeviceClass ?: 0 }.getOrDefault(0)
            )
        }
        return Source(
            items,
            discovered = items.size,
            state = if (items.isEmpty()) SourceState.OK_EMPTY else SourceState.OK
        )
    }
}
