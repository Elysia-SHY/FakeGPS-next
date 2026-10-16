package com.mockrun.app.hook

import android.bluetooth.BluetoothDevice
import android.location.Location
import android.os.Build
import android.os.SystemClock
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 环境指纹回放。
 *
 * 把采集到的 Wi-Fi / 基站 / 蓝牙快照按目标坐标回放给目标应用，
 * 使「周边环境」与虚拟坐标自洽。
 *
 * 全部用 org.json 手写解析 —— hook 运行在 system_server / 目标应用进程，
 * 那些进程里没有 Gson（compileOnly 依赖不会被打进去），
 * 用 Gson 会抛 NoClassDefFoundError。
 */
object FingerprintReplayer {

    private const val TAG = "FingerprintReplay"

    /** 与 app 侧 FingerprintStore 的导出路径约定一致 */
    private const val EXPORT_DIR = "/data/local/tmp"

    // ---------------------------------------------------------------- 缓存

    /**
     * 缓存快照。三个字段必须一起替换，不能分别写 ——
     * 分发线程（binder）与 fakegps-ble-push 守护线程会并发调用 getActive()，
     * 分别写 volatile 时可能出现「新 id + 旧指纹」的组合，
     * 那样就会把**上一个坐标**的 Wi-Fi/基站列表回放给新坐标，
     * 正是本文件要消除的那种物理矛盾。
     */
    private class Cached(
        val id: String,
        val at: Long,
        val fp: ParsedFingerprint
    )

    @Volatile private var cache: Cached? = null

    /** 解析后的指纹（避免每次回放都重新解析 JSON） */
    class ParsedFingerprint(
        val originLat: Double,
        val originLon: Double,
        val wifi: List<ParsedWifi>,
        val cells: List<ParsedCell>,
        val ble: List<ParsedBle>,
        val bonded: List<ParsedBonded>
    )

    class ParsedWifi(
        val ssid: String, val bssid: String, val capabilities: String,
        val rssi: Int, val frequency: Int, val channelWidth: Int,
        val centerFreq0: Int, val centerFreq1: Int, val wifiStandard: Int,
        val isConnected: Boolean
    )

    class ParsedCell(
        val type: String, val mcc: Int, val mnc: Int, val cid: Int,
        val pci: Int, val tac: Int, val earfcn: Int, val rssi: Int,
        val rsrp: Int, val rsrq: Int, val rssnr: Int,
        val isRegistered: Boolean, val connectionStatus: Int,
        /**
         * NR 专属：36-bit NCI。
         *
         * JSON 里缺这个键时必须是 **null**，不能是 0 —— 0 是「没有 NCI」和
         * 「NCI 恰好是 0」的混淆点，旧指纹包（无 NR 字段）全部落到前者。
         * 解析时用 `has()` 判断键是否存在，而不是靠 optLong 的默认值。
         */
        val nci: Long? = null
    )

    class ParsedBle(
        val address: String, val name: String?, val rssi: Int,
        val scanRecordHex: String, val txPower: Int, val primaryPhy: Int
    )

    class ParsedBonded(val address: String, val name: String?)

    /** 由外部（XposedLocationHook）注入的「当前指纹 id」读取器 */
    @Volatile var fingerprintIdProvider: (() -> String)? = null

    // ---------------------------------------------------------------- 读取

    /**
     * 取当前生效的指纹。
     *
     * 通道：/data/local/tmp/fakegps_fp_<id>.json
     * 这条路径是 shell_data_file，system_server 与普通应用都可读。
     * （XSharedPreferences 走 app 私有目录，会被 SELinux 拒绝。）
     */
    fun getActive(): ParsedFingerprint? {
        val id = fingerprintIdProvider?.invoke().orEmpty()
        if (id.isEmpty()) {
            cache = null
            return null
        }

        val now = SystemClock.elapsedRealtime()
        // 只读一次 volatile，命中判断全部基于这一份快照
        val hit = cache
        if (hit != null && hit.id == id && now - hit.at < 2000L) return hit.fp

        val json = runCatching {
            val f = java.io.File("$EXPORT_DIR/fakegps_fp_$id.json")
            if (f.exists() && f.canRead()) f.readText() else null
        }.logFailure(TAG, "read fingerprint file for id=$id", Diag.Level.DEBUG).getOrNull()

        if (json.isNullOrEmpty()) return null

        val parsed = runCatching { parse(json) }
            .logFailure(TAG, "parse fingerprint JSON", Diag.Level.WARN)
            .getOrNull() ?: return null

        // 原子发布：id / 时间 / 指纹一起替换，不会出现交叉组合
        cache = Cached(id, now, parsed)
        return parsed
    }

    /**
     * 解析指纹 JSON。
     *
     * 可见性为 `internal` 而非 `private`：单元测试需要直接喂各种形状的 JSON
     * （旧格式、缺 nci、nci 超 32 位）来锁住解析行为，而 `getActive()` 依赖
     * /data/local/tmp 下的真实文件，测试里没法用。
     */
    internal fun parse(json: String): ParsedFingerprint {
        val o = JSONObject(json)
        val originLat = o.optDouble("originLat", 0.0)
        val originLon = o.optDouble("originLon", 0.0)

        val wifi = ArrayList<ParsedWifi>()
        o.optJSONArray("wifi")?.let { arr ->
            for (i in 0 until arr.length()) {
                val w = arr.optJSONObject(i) ?: continue
                wifi.add(
                    ParsedWifi(
                        ssid = w.optString("ssid"),
                        bssid = w.optString("bssid"),
                        capabilities = w.optString("capabilities"),
                        rssi = w.optInt("rssi", -70),
                        frequency = w.optInt("frequency", 2412),
                        channelWidth = w.optInt("channelWidth", 0),
                        centerFreq0 = w.optInt("centerFreq0", 0),
                        centerFreq1 = w.optInt("centerFreq1", 0),
                        wifiStandard = w.optInt("wifiStandard", 0),
                        isConnected = w.optBoolean("isConnected", false)
                    )
                )
            }
        }

        val cells = ArrayList<ParsedCell>()
        o.optJSONArray("cells")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                cells.add(
                    ParsedCell(
                        type = c.optString("type", "LTE"),
                        mcc = c.optInt("mcc", 460),
                        mnc = c.optInt("mnc", 0),
                        cid = c.optInt("cid", 0),
                        pci = c.optInt("pci", 0),
                        tac = c.optInt("tac", 0),
                        earfcn = c.optInt("earfcn", -1),
                        rssi = c.optInt("rssi", -80),
                        rsrp = c.optInt("rsrp", -100),
                        rsrq = c.optInt("rsrq", -10),
                        rssnr = c.optInt("rssnr", 10),
                        isRegistered = c.optBoolean("isRegistered", false),
                        connectionStatus = c.optInt("connectionStatus", 0),
                        // 必须用 has() 判断键存在性：缺键时 optLong 返回 0，
                        // 而 0 会被当成一个有效的 NCI 回放出去。
                        nci = if (c.has("nci") && !c.isNull("nci")) c.optLong("nci") else null
                    )
                )
            }
        }

        val ble = ArrayList<ParsedBle>()
        o.optJSONArray("ble")?.let { arr ->
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                ble.add(
                    ParsedBle(
                        address = b.optString("address"),
                        name = b.optString("name").takeIf { it.isNotEmpty() },
                        rssi = b.optInt("rssi", -80),
                        scanRecordHex = b.optString("scanRecordHex"),
                        txPower = b.optInt("txPower", 127),
                        primaryPhy = b.optInt("primaryPhy", 1)
                    )
                )
            }
        }

        val bonded = ArrayList<ParsedBonded>()
        o.optJSONArray("bonded")?.let { arr ->
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                bonded.add(
                    ParsedBonded(
                        address = b.optString("address"),
                        name = b.optString("name").takeIf { it.isNotEmpty() }
                    )
                )
            }
        }

        return ParsedFingerprint(originLat, originLon, wifi, cells, ble, bonded)
    }

    // ---------------------------------------------------------------- RSSI 重标定

    private const val EARTH_R = 6371000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).let { it * it }
        return 2 * EARTH_R * kotlin.math.asin(kotlin.math.sqrt(a))
    }

    /**
     * 把采集点的 RSSI 重标定到目标点。
     *
     * 依据自由空间路径损耗：频率不变时两点间 RSSI 差只取决于距离比
     *   RSSI_B = RSSI_A - 20·log10(d_B / d_A)
     * 这里把 d_A 取为室内 AP 典型可见半径（30 m），并给衰减设 35 dB 上限，
     * 避免 3 km 外算出 -140 dBm 这种不可能值。
     */
    fun rescaleRssi(
        originalRssi: Int,
        originLat: Double, originLon: Double,
        targetLat: Double, targetLon: Double,
        isConnected: Boolean = false
    ): Int {
        val d = haversineMeters(originLat, originLon, targetLat, targetLon)
        if (d < 1.0) return originalRssi

        val ratio = (d / 30.0).coerceAtLeast(1.0)
        val attenuation = (20.0 * kotlin.math.log10(ratio)).coerceAtMost(35.0)
        val adjusted = originalRssi - attenuation
        val floor = if (isConnected) -55 else -92
        return adjusted.toInt().coerceIn(-95, -28).coerceAtLeast(floor)
    }

    private fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()

    // ---------------------------------------------------------------- Wi-Fi 构造

    /**
     * 按参数个数解析构造器并实例化。
     *
     * 为什么需要：`CellIdentityGsm` / `CellIdentityWcdma` 的隐藏构造器签名在
     * Android 13 (T) 变更 —— 追加了 `Set<ClosedSubscriberGroupInfo>` 参数，从 9 参变 10 参。
     * 直接按 9 参调用在 API 33/34 上抛 `NoSuchMethodError`，
     * 被外层 runCatching 吞掉后该基站会被静默丢弃，
     * 于是回放出的指纹又变成「有 GPS 星数却没有 GSM/WCDMA 基站」——
     * 正是本文件要消除的矛盾。
     *
     * @param preferArity 优先尝试的参数个数；不存在时按 [fallbackArity] 再试
     * @param baseArgs    两种签名共有的前置参数
     * @param csgArg      仅 10 参签名需要的尾部参数
     */
    private fun newCellIdentity(
        clazz: Class<*>,
        baseArgs: Array<Any?>,
        csgArg: Any,
        fallbackArity: Int
    ): Any? {
        // 先看类里实际有哪些构造器
        val arities = runCatching { clazz.declaredConstructors.map { it.parameterTypes.size }.toSet() }
            .getOrDefault(emptySet())

        // 10 参（Android 13+）：baseArgs + CSG set
        if (baseArgs.size + 1 in arities) {
            val r = runCatching {
                val ctor = clazz.declaredConstructors.first { it.parameterTypes.size == baseArgs.size + 1 }
                ctor.isAccessible = true
                ctor.newInstance(*baseArgs, csgArg)
            }.logFailure(TAG, "newCellIdentity via ${baseArgs.size + 1}-arg ctor", Diag.Level.DEBUG)
                .getOrNull()
            if (r != null) return r
        }

        // 回退：历史签名
        return runCatching { XposedHelpers.newInstance(clazz, *baseArgs) }
            .logFailure(
                TAG,
                "newCellIdentity fell back to ${baseArgs.size}-arg (declared arities=$arities, " +
                    "expected fallback=$fallbackArity)",
                Diag.Level.WARN
            )
            .getOrNull()
    }

    /**
     * 构造伪造的 ScanResult 列表。
     *
     * 用无参构造 + 公开字段赋值，不用带参构造 —— ScanResult 的带参构造
     * 在 AOSP 各版本签名不同，走无参构造 + 字段写入最稳。
     */
    fun buildScanResultList(
        fp: ParsedFingerprint,
        scanResultClass: Class<*>?,
        wifiSsidClass: Class<*>?,
        targetLat: Double,
        targetLon: Double
    ): List<Any> {
        if (scanResultClass == null) return emptyList()
        val out = ArrayList<Any>(fp.wifi.size)
        for (ap in fp.wifi) {
            runCatching {
                val sr = XposedHelpers.newInstance(scanResultClass)
                XposedHelpers.setObjectField(sr, "SSID", ap.ssid)
                XposedHelpers.setObjectField(sr, "BSSID", ap.bssid)
                XposedHelpers.setObjectField(sr, "capabilities", ap.capabilities)
                XposedHelpers.setIntField(
                    sr, "level",
                    rescaleRssi(ap.rssi, fp.originLat, fp.originLon, targetLat, targetLon, ap.isConnected)
                )
                XposedHelpers.setIntField(sr, "frequency", ap.frequency)
                XposedHelpers.setIntField(sr, "channelWidth", ap.channelWidth)
                XposedHelpers.setIntField(sr, "centerFreq0", ap.centerFreq0)
                XposedHelpers.setIntField(sr, "centerFreq1", ap.centerFreq1)
                XposedHelpers.setLongField(sr, "flags", 0L)
                // 时间戳必须重写：留采集时刻会被一眼看出是旧数据。
                // ScanResult.timestamp 单位是微秒 since boot。
                XposedHelpers.setLongField(sr, "timestamp", nowNanos() / 1000L)
                XposedHelpers.setLongField(sr, "seen", System.currentTimeMillis())
                XposedHelpers.setIntField(sr, "distanceCm", -1)
                XposedHelpers.setIntField(sr, "distanceSdCm", -1)

                if (wifiSsidClass != null) {
                    runCatching {
                        val ssid = XposedHelpers.callStaticMethod(
                            wifiSsidClass, "createFromAsciiEncoded", ap.ssid
                        )
                        XposedHelpers.setObjectField(sr, "wifiSsid", ssid)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching { XposedHelpers.callMethod(sr, "setWifiStandard", ap.wifiStandard) }
                }
                out.add(sr)
            }.logFailure(TAG, "build ScanResult for ${ap.bssid}", Diag.Level.DEBUG)
        }
        return out
    }

    // ---------------------------------------------------------------- 基站构造

    fun buildCellInfoList(
        fp: ParsedFingerprint,
        targetLat: Double, targetLon: Double,
        cellInfoLteClass: Class<*>?, cellIdentityLteClass: Class<*>?,
        cellSignalLteClass: Class<*>?, cellConfigLteClass: Class<*>?,
        cellInfoGsmClass: Class<*>?, cellIdentityGsmClass: Class<*>?,
        cellSignalGsmClass: Class<*>?,
        cellInfoWcdmaClass: Class<*>?, cellIdentityWcdmaClass: Class<*>?, cellSignalWcdmaClass: Class<*>?,
        // NR（5G）参数。带默认值是为了让旧调用点仍然编译得过；
        // 传 null 时 NR 条目会被跳过，而不是构造出一条残缺的 CellInfo。
        cellInfoNrClass: Class<*>? = null,
        cellIdentityNrClass: Class<*>? = null,
        cellSignalNrClass: Class<*>? = null
    ): List<Any> {
        val out = ArrayList<Any>(fp.cells.size)

        for (t in fp.cells) {
            runCatching {
                when (t.type) {
                    "LTE" -> {
                        if (cellInfoLteClass == null || cellIdentityLteClass == null ||
                            cellSignalLteClass == null || cellConfigLteClass == null
                        ) return@runCatching

                        val rssi = rescaleRssi(t.rssi, fp.originLat, fp.originLon, targetLat, targetLon, t.isRegistered)
                        val delta = rssi - t.rssi
                        val rsrp = (t.rsrp + delta).coerceIn(-140, -43)
                        val rsrq = (t.rsrq + delta / 2).coerceIn(-34, 3)
                        val rssnr = (t.rssnr + delta / 3).coerceIn(-20, 30)

                        val sig = XposedHelpers.newInstance(
                            cellSignalLteClass,
                            Integer.valueOf(rssi), Integer.valueOf(rsrp), Integer.valueOf(rsrq),
                            Integer.valueOf(rssnr), Integer.valueOf(14), Integer.valueOf(0)
                        )
                        val id = XposedHelpers.newInstance(
                            cellIdentityLteClass,
                            Integer.valueOf(t.mcc), Integer.valueOf(t.mnc), Integer.valueOf(t.cid),
                            Integer.valueOf(t.pci), Integer.valueOf(t.tac)
                        )
                        val info = XposedHelpers.newInstance(cellInfoLteClass)
                        XposedHelpers.callMethod(info, "setCellIdentity", id)
                        XposedHelpers.callMethod(info, "setCellSignalStrength", sig)
                        XposedHelpers.callMethod(info, "setCellConfig", XposedHelpers.newInstance(cellConfigLteClass))
                        stampCellInfo(info, t)
                        out.add(info)
                    }
                    "GSM" -> {
                        if (cellInfoGsmClass == null || cellIdentityGsmClass == null || cellSignalGsmClass == null) return@runCatching
                        val rssi = rescaleRssi(t.rssi, fp.originLat, fp.originLon, targetLat, targetLon, t.isRegistered)
                        val sig = XposedHelpers.newInstance(
                            cellSignalGsmClass, Integer.valueOf(rssi), Integer.valueOf(0), Integer.valueOf(0)
                        )
                        val id = newCellIdentity(
                            cellIdentityGsmClass,
                            arrayOf(
                                Integer.valueOf(t.tac), Integer.valueOf(t.cid),
                                Integer.valueOf(if (t.earfcn > 0) t.earfcn else 0), Integer.valueOf(t.pci),
                                t.mcc.toString(), t.mnc.toString(), null, null,
                                java.util.Collections.emptyList<String>()
                            ),
                            java.util.Collections.emptySet<Any>(),
                            fallbackArity = 9
                        )
                        if (id == null) return@runCatching
                        val info = XposedHelpers.newInstance(cellInfoGsmClass)
                        XposedHelpers.callMethod(info, "setCellIdentity", id)
                        XposedHelpers.callMethod(info, "setCellSignalStrength", sig)
                        stampCellInfo(info, t)
                        out.add(info)
                    }
                    "WCDMA" -> {
                        if (cellInfoWcdmaClass == null || cellIdentityWcdmaClass == null || cellSignalWcdmaClass == null) return@runCatching
                        val rssi = rescaleRssi(t.rssi, fp.originLat, fp.originLon, targetLat, targetLon, t.isRegistered)
                        val sig = XposedHelpers.newInstance(
                            cellSignalWcdmaClass,
                            Integer.valueOf(rssi), Integer.valueOf(0),
                            Integer.valueOf((rssi - 20).coerceIn(-120, -25)), Integer.valueOf(-10)
                        )
                        val id = newCellIdentity(
                            cellIdentityWcdmaClass,
                            arrayOf(
                                Integer.valueOf(t.tac), Integer.valueOf(t.cid), Integer.valueOf(t.pci),
                                Integer.valueOf(if (t.earfcn > 0) t.earfcn else 0),
                                t.mcc.toString(), t.mnc.toString(), null, null,
                                java.util.Collections.emptyList<String>()
                            ),
                            java.util.Collections.emptySet<Any>(),
                            fallbackArity = 9
                        )
                        if (id == null) return@runCatching
                        val info = XposedHelpers.newInstance(cellInfoWcdmaClass)
                        XposedHelpers.callMethod(info, "setCellIdentity", id)
                        XposedHelpers.callMethod(info, "setCellSignalStrength", sig)
                        stampCellInfo(info, t)
                        out.add(info)
                    }
                    "NR" -> {
                        if (cellInfoNrClass == null || cellIdentityNrClass == null || cellSignalNrClass == null) {
                            Diag.w(
                                TAG,
                                "NR replay unavailable (classes missing) — dropping NR cell nci=${t.nci}"
                            )
                            return@runCatching
                        }
                        val id = buildCellIdentityNr(cellIdentityNrClass, t) ?: return@runCatching
                        val sig = buildCellSignalStrengthNr(
                            cellSignalNrClass, t, fp.originLat, fp.originLon, targetLat, targetLon
                        ) ?: return@runCatching
                        val info = newCellInfoNr(cellInfoNrClass, id, sig, t) ?: return@runCatching
                        stampCellInfo(info, t)
                        out.add(info)
                    }
                }
            }.logFailure(TAG, "build CellInfo (${t.type})", Diag.Level.DEBUG)
        }
        return out
    }

    /**
     * 写 CellInfo 公共字段。时间戳必须重写
     * （AOSP CellInfo.setTimeStamp 单位是纳秒）。
     */
    private fun stampCellInfo(info: Any, t: ParsedCell) {
        runCatching { XposedHelpers.callMethod(info, "setRegistered", t.isRegistered) }
        runCatching { XposedHelpers.callMethod(info, "setTimeStamp", nowNanos()) }
        runCatching { XposedHelpers.callMethod(info, "setCellConnectionStatus", t.connectionStatus) }
    }

    // ---------------------------------------------------------------- NR 构造

    /**
     * NR（5G）专用构造。
     *
     * 为什么不能照抄 LTE 的写法：`CellInfoNr` **没有** `setCellSignalStrength`，
     * 且 `mCellSignalStrength` 在 API 29–34 全程是 `final`。
     * LTE 那条「无参构造 + setCellIdentity + setCellSignalStrength」的路径在 NR 上根本走不通。
     * 因此这里按各版本**真实存在的构造器参数个数**分派（个数取自 AOSP 源码）：
     *
     *  - `CellInfoNr(int, boolean, long, CellIdentityNr, CellSignalStrengthNr)` — 5 参，API 32+（Android 12L/13+），
     *    identity 与 signal 一次到位，这是首选。
     *  - `CellInfoNr()` — 无参，API 31+（Android 12）。只有它可用时，
     *    用 `setCellIdentity` 写 identity，再用反射直写 final 字段 `mCellSignalStrength`。
     *  - API 29–30 的 `CellInfoNr` **没有任何公开构造器**（只有 private Parcel / copy 构造），
     *    因此这两版无法构造 NR 条目 —— 返回 null 并由调用方记日志丢弃，
     *    不伪造一条信号强度为 UNAVAILABLE 的残条目。
     */
    internal fun newCellInfoNr(clazz: Class<*>, id: Any, sig: Any, t: ParsedCell): Any? {
        val arities = runCatching { clazz.declaredConstructors.map { it.parameterTypes.size }.toSet() }
            .getOrDefault(emptySet())

        // 首选：5 参完整构造（API 32+）
        if (5 in arities) {
            val r = runCatching {
                val ctor = clazz.declaredConstructors.first { it.parameterTypes.size == 5 }
                ctor.isAccessible = true
                ctor.newInstance(
                    Integer.valueOf(t.connectionStatus),
                    java.lang.Boolean.valueOf(t.isRegistered),
                    java.lang.Long.valueOf(nowNanos()),
                    id,
                    sig
                )
            }.logFailure(TAG, "newCellInfoNr via 5-arg ctor", Diag.Level.DEBUG).getOrNull()
            if (r != null) return r
        }

        // 回退：无参构造 + setCellIdentity + 反射直写 final 字段（API 31）
        if (0 in arities) {
            return runCatching {
                val info = XposedHelpers.newInstance(clazz)
                XposedHelpers.callMethod(info, "setCellIdentity", id)
                runCatching { XposedHelpers.setObjectField(info, "mCellSignalStrength", sig) }
                    .logFailure(TAG, "set final mCellSignalStrength on CellInfoNr", Diag.Level.DEBUG)
                info
            }.logFailure(TAG, "newCellInfoNr via no-arg ctor", Diag.Level.DEBUG).getOrNull()
        }

        Diag.w(
            TAG,
            "CellInfoNr has no usable constructor on this API " +
                "(declared arities=$arities) — NR cell dropped, nci=${t.nci}"
        )
        return null
    }

    /**
     * 构造 `CellIdentityNr`。
     *
     * NCI 是 36-bit，**必须**用 Long 传：`newInstance` 收到 `Integer` 会去匹配 int 形参，
     * 而这里要的是 `long`，传错类型会 NoSuchMethod 直接丢掉这条基站。
     * [ParsedCell.nci] 为 null（旧指纹包）时退回 cid 的无符号值，保持旧包可回放。
     *
     * 参数个数按 AOSP 源码分派：
     *  - 10 参 `(int pci, int tac, int nrArfcn, int[] bands, String mcc, String mnc, long nci, String alphal, String alphas, Collection)` — API 31+
     *  - 8 参 `(int pci, int tac, int nrArfcn, String mcc, String mnc, long nci, String alphal, String alphas)` — API 29/30
     */
    internal fun buildCellIdentityNr(clazz: Class<*>, t: ParsedCell): Any? {
        val nci = t.nci ?: (t.cid.toLong() and 0xFFFFFFFFL)
        val arities = runCatching { clazz.declaredConstructors.map { it.parameterTypes.size }.toSet() }
            .getOrDefault(emptySet())

        if (10 in arities) {
            val r = runCatching {
                val ctor = clazz.declaredConstructors.first { it.parameterTypes.size == 10 }
                ctor.isAccessible = true
                ctor.newInstance(
                    Integer.valueOf(t.pci), Integer.valueOf(t.tac), Integer.valueOf(t.earfcn),
                    IntArray(0),
                    t.mcc.toString(), t.mnc.toString(),
                    java.lang.Long.valueOf(nci),
                    null, null,
                    java.util.Collections.emptyList<String>()
                )
            }.logFailure(TAG, "newCellIdentityNr via 10-arg ctor", Diag.Level.DEBUG).getOrNull()
            if (r != null) return r
        }

        if (8 in arities) {
            return runCatching {
                val ctor = clazz.declaredConstructors.first { it.parameterTypes.size == 8 }
                ctor.isAccessible = true
                ctor.newInstance(
                    Integer.valueOf(t.pci), Integer.valueOf(t.tac), Integer.valueOf(t.earfcn),
                    t.mcc.toString(), t.mnc.toString(),
                    java.lang.Long.valueOf(nci),
                    null, null
                )
            }.logFailure(TAG, "newCellIdentityNr via 8-arg ctor", Diag.Level.DEBUG).getOrNull()
        }

        Diag.w(TAG, "CellIdentityNr has no usable constructor (declared arities=$arities)")
        return null
    }

    /**
     * 构造 `CellSignalStrengthNr`。
     *
     * 6 参构造 `(csiRsrp, csiRsrq, csiSinr, ssRsrp, ssRsrq, ssSinr)` 在 API 29–34 全程存在，首选；
     * 9 参是 API 31+ 的扩展签名，作为回退。
     *
     * 取值域各版本不同（API 29 的 ssRsrp 是 [-140,-44]，API 31+ 放宽到 [-156,-31]），
     * 这里按**最窄交集**裁剪，避免构造器把越界值静默替换成 `UNAVAILABLE`(Int.MAX_VALUE)——
     * 那会让一条本该有信号的基站显示成「无信号」。
     * CSI 三值一律传 `UNAVAILABLE`：NR 采集侧只拿到 SS 系列，CSI 本就没有数据。
     */
    internal fun buildCellSignalStrengthNr(
        clazz: Class<*>,
        t: ParsedCell,
        originLat: Double, originLon: Double,
        targetLat: Double, targetLon: Double
    ): Any? {
        val rssi = rescaleRssi(t.rssi, originLat, originLon, targetLat, targetLon, t.isRegistered)
        val delta = rssi - t.rssi
        val ssRsrp = (t.rsrp + delta).coerceIn(-140, -44)
        val ssRsrq = (t.rsrq + delta / 2).coerceIn(-20, -3)
        val ssSinr = (t.rssnr + delta / 3).coerceIn(-23, 23)

        val arities = runCatching { clazz.declaredConstructors.map { it.parameterTypes.size }.toSet() }
            .getOrDefault(emptySet())

        if (6 in arities) {
            val r = runCatching {
                val ctor = clazz.declaredConstructors.first { it.parameterTypes.size == 6 }
                ctor.isAccessible = true
                ctor.newInstance(
                    Integer.valueOf(Int.MAX_VALUE), Integer.valueOf(Int.MAX_VALUE), Integer.valueOf(Int.MAX_VALUE),
                    Integer.valueOf(ssRsrp), Integer.valueOf(ssRsrq), Integer.valueOf(ssSinr)
                )
            }.logFailure(TAG, "newCellSignalStrengthNr via 6-arg ctor", Diag.Level.DEBUG).getOrNull()
            if (r != null) return r
        }

        if (9 in arities) {
            return runCatching {
                val ctor = clazz.declaredConstructors.first { it.parameterTypes.size == 9 }
                ctor.isAccessible = true
                ctor.newInstance(
                    Integer.valueOf(Int.MAX_VALUE), Integer.valueOf(Int.MAX_VALUE), Integer.valueOf(Int.MAX_VALUE),
                    Integer.valueOf(Int.MAX_VALUE),
                    java.util.Collections.emptyList<Byte>(),
                    Integer.valueOf(ssRsrp), Integer.valueOf(ssRsrq), Integer.valueOf(ssSinr),
                    Integer.valueOf(Int.MAX_VALUE)
                )
            }.logFailure(TAG, "newCellSignalStrengthNr via 9-arg ctor", Diag.Level.DEBUG).getOrNull()
        }

        Diag.w(TAG, "CellSignalStrengthNr has no usable constructor (declared arities=$arities)")
        return null
    }

    // ---------------------------------------------------------------- BLE 构造

    /**
     * 构造 BluetoothDevice。
     *
     * 首选包私有构造（反射可绕过可见性），回退到公开 API
     * BluetoothAdapter.getRemoteDevice(String)。
     */
    fun buildBluetoothDevice(deviceClass: Class<*>, address: String): Any? {
        if (!address.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) return null
        runCatching { return XposedHelpers.newInstance(deviceClass, address) }
        runCatching {
            val adapterClass = XposedHelpers.findClassIfExists(
                "android.bluetooth.BluetoothAdapter", deviceClass.classLoader
            ) ?: return null
            val adapter = XposedHelpers.callStaticMethod(adapterClass, "getDefaultAdapter") ?: return null
            return XposedHelpers.callMethod(adapter, "getRemoteDevice", address)
        }
        return null
    }

    /**
     * 构造伪造的 BLE ScanResult。
     *
     * 用公开构造器：
     *   ScanResult(BluetoothDevice, int eventType, int primaryPhy, int secondaryPhy,
     *              int advertisingSid, int txPower, int rssi,
     *              int periodicAdvertisingInterval, ScanRecord, long timestampNanos)
     */
    fun buildBleScanResult(
        b: ParsedBle?,
        scanResultClass: Class<*>?,
        scanRecordClass: Class<*>?,
        deviceClass: Class<*>?,
        originLat: Double, originLon: Double,
        targetLat: Double, targetLon: Double
    ): Any? {
        if (b == null || scanResultClass == null || deviceClass == null) return null
        return runCatching {
            val device = buildBluetoothDevice(deviceClass, b.address) ?: return@runCatching null

            val record = if (scanRecordClass != null && b.scanRecordHex.isNotEmpty()) {
                runCatching {
                    val bytes = ByteArray(b.scanRecordHex.length / 2) {
                        ((Character.digit(b.scanRecordHex[it * 2], 16) shl 4) +
                            Character.digit(b.scanRecordHex[it * 2 + 1], 16)).toByte()
                    }
                    XposedHelpers.callStaticMethod(scanRecordClass, "parseFromBytes", bytes)
                }.getOrNull()
            } else null

            val rssi = rescaleRssi(b.rssi, originLat, originLon, targetLat, targetLon)
            // (DATA_COMPLETE << 5) | ET_LEGACY_MASK | ET_CONNECTABLE_MASK = 0x11
            val eventType = 0x11

            XposedHelpers.newInstance(
                scanResultClass,
                device, Integer.valueOf(eventType),
                Integer.valueOf(b.primaryPhy), Integer.valueOf(0),
                Integer.valueOf(0xFF),
                Integer.valueOf(b.txPower),
                Integer.valueOf(rssi),
                Integer.valueOf(0),
                record,
                java.lang.Long.valueOf(nowNanos())
            )
        }.logFailure(TAG, "build BLE ScanResult for ${b.address}", Diag.Level.DEBUG).getOrNull()
    }
}
