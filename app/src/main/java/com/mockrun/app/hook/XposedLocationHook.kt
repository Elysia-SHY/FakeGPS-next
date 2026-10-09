package com.mockrun.app.hook

import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

data class SpoofLocation(
    val isActive: Boolean,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val bearing: Float,
    val speed: Float,
    val timestamp: Long = 0L,
    /**
     * 应用侧写入时的 `SystemClock.elapsedRealtime()`（单调时钟，含睡眠）。
     *
     * 只有它才能回答"这份配置是不是本次开机之后写的"：墙钟 `timestamp` 在开机未同步时间、
     * 或设备时间被改到过去时会算出负数差值，导致过期判断失效，陈旧配置被当成有效一直伪造下去。
     */
    val elapsed: Long = 0L
)

/** Logcat/Xposed tag for this hook. Kept short — it appears in `system_server` logs. */
private const val TAG = "LocationHook"

// ============================================================================
// 坐标系转换（内联实现）
//
// 为什么不复用 core.location.CoordinateConverter：那个 object 反向引用了
// hook.HookStateBridge（CoordinateConverter.kt:88），而本文件属于 hook 包。
// 直接 import 会形成 hook -> core.location -> hook 的循环依赖。
//
// 为什么 hook 侧必须自己转：
//   AMapLocation  对外契约是 GCJ-02（国内），BDLocation 是 BD-09，
//   TencentLocation 是 GCJ-02。而 mock 注入的是 WGS-84。
//   直接塞 WGS-84 会被 SDK 再转一次，偏差被放大一倍。
// ============================================================================

private const val GCJ_A = 6378245.0
private const val GCJ_EE = 0.00669342162296594323
private const val BD_X_PI = Math.PI * 3000.0 / 180.0

private fun outOfChina(lat: Double, lon: Double): Boolean =
    lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

private fun gcjTransformLat(x: Double, y: Double): Double {
    var r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * kotlin.math.sqrt(kotlin.math.abs(x))
    r += (20.0 * kotlin.math.sin(6.0 * x * Math.PI) + 20.0 * kotlin.math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    r += (20.0 * kotlin.math.sin(y * Math.PI) + 40.0 * kotlin.math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
    r += (160.0 * kotlin.math.sin(y / 12.0 * Math.PI) + 320.0 * kotlin.math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
    return r
}

private fun gcjTransformLon(x: Double, y: Double): Double {
    var r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * kotlin.math.sqrt(kotlin.math.abs(x))
    r += (20.0 * kotlin.math.sin(6.0 * x * Math.PI) + 20.0 * kotlin.math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    r += (20.0 * kotlin.math.sin(x * Math.PI) + 40.0 * kotlin.math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
    r += (150.0 * kotlin.math.sin(x / 12.0 * Math.PI) + 300.0 * kotlin.math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
    return r
}

/** WGS-84 -> GCJ-02 */
private fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
    if (lat.isNaN() || lon.isNaN() || outOfChina(lat, lon)) return lat to lon
    var dLat = gcjTransformLat(lon - 105.0, lat - 35.0)
    var dLon = gcjTransformLon(lon - 105.0, lat - 35.0)
    val rLat = Math.toRadians(lat)
    var magic = kotlin.math.sin(rLat); magic = 1 - GCJ_EE * magic * magic
    val sqrtMagic = kotlin.math.sqrt(magic)
    dLat = dLat * 180.0 / (GCJ_A * (1 - GCJ_EE) / (magic * sqrtMagic) * Math.PI)
    dLon = dLon * 180.0 / (GCJ_A / sqrtMagic * kotlin.math.cos(rLat) * Math.PI)
    return (lat + dLat) to (lon + dLon)
}

/** GCJ-02 -> BD-09 */
private fun gcj02ToBd09(lat: Double, lon: Double): Pair<Double, Double> {
    val z = kotlin.math.sqrt(lon * lon + lat * lat) + 0.00002 * kotlin.math.sin(lat * BD_X_PI)
    val theta = kotlin.math.atan2(lat, lon) + 0.000003 * kotlin.math.cos(lon * BD_X_PI)
    return (z * kotlin.math.sin(theta) + 0.006) to (z * kotlin.math.cos(theta) + 0.0065)
}

/** WGS-84 -> BD-09 */
private fun wgs84ToBd09(lat: Double, lon: Double): Pair<Double, Double> {
    val (gLat, gLon) = wgs84ToGcj02(lat, lon)
    return gcj02ToBd09(gLat, gLon)
}

/**
 * 硬件侧超过这个时长没有任何一次真实分发，就判定为"静默"（GPS 关闭、室内无信号、
 * 或 ROM 根本不上报），此时开始主动向已注册的分发对象喂点。
 */
private const val PUSH_IDLE_THRESHOLD_MS = 2_000L

/** 主动喂点的间隔。 */
private const val PUSH_INTERVAL_MS = 1_000L

class XposedLocationHook : IXposedHookLoadPackage {

    private var xSharedPrefs: XSharedPreferences? = null
    private var appContext: Context? = null

    // 这两个字段被多线程并发读写：getGlobalActiveLocation() 同时跑在 binder 线程、
    // fakeloc-push 守护线程与 fakegps-ble-push 守护线程上。
    // 不加 @Volatile 时，检查与使用之间存在竞态 —— 另一线程在判断后把 cachedLocation
    // 置空（失效/过期分支都会写），随后的 !! 就会抛 NPE，且是抛在 system_server 或被
    // hook 的应用进程里。ARM64 上非 volatile 字段的重复读取本身也不保证稳定。
    @Volatile
    private var cachedLocation: SpoofLocation? = null

    @Volatile
    private var lastCacheCheckTime: Long = 0L

    private var lastSpoofedLocation: Location? = null
    @Volatile
    private var currentProcessPackage: String = ""

    /**
     * 已注册的定位分发对象（`LocationManagerService$Receiver` / `LocationProviderManager$*Registration`）。
     *
     * 弱引用：这些对象由 system_server 自己持有，进程内数量随应用注册/注销变化，用强引用会阻止 GC 并
     * 让已销毁的注册者继续被喂点。随 GC 自动失效正好符合"注销即停止"的语义。
     */
    private val dispatchTargets: MutableSet<Any> = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<Any, Boolean>())
    )

    /** 各 AOSP 版本里"把定位结果交给某个注册者"的方法名不同，逐个尝试。 */
    private val dispatchMethodNames = listOf("acceptLocationChange", "onLocationChanged", "callLocationChangedLocked")

    /** 最近一次成功分发的时间（elapsedRealtime）。为 0 表示还没有过任何分发。 */
    @Volatile
    private var lastDispatchAt: Long = 0L

    @Volatile
    private var pushThreadStarted = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        currentProcessPackage = pkg

        // If loaded in Fake GPS itself, hook XposedStatusHelper to report module active, then return
        if (pkg == "com.mockrun.app") {
            runCatching {
                XposedHelpers.findAndHookMethod(
                    "com.mockrun.app.hook.XposedStatusHelper",
                    lpparam.classLoader,
                    "isModuleActive",
                    de.robv.android.xposed.XC_MethodReplacement.returnConstant(true)
                )
            }.logFailure(TAG, "hook XposedStatusHelper.isModuleActive")
            return
        }

        // Initialize XSharedPreferences
        if (xSharedPrefs == null) {
            xSharedPrefs = runCatching {
                val sp = XSharedPreferences("com.mockrun.app", "hook_config")
                sp.makeWorldReadable()
                sp
            }
                // When this fails the entire XSharedPreferences channel is dead. It used to be silent.
                .logFailure(TAG, "init XSharedPreferences(hook_config) — config channel dead", Diag.Level.ERROR)
                .getOrNull()
        }

        // Capture Application Context for ContentProvider IPC fallback
        runCatching {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        appContext = param.thisObject as? Context
                    }
                }
            )
        }.logFailure(TAG, "hook Application.onCreate — context capture lost, IPC fallback degraded")

        // Universal Anti-Mock detection hook in all processes
        hookMockDetection(lpparam)

        // 环境指纹回放（Wi-Fi / 基站 / 蓝牙）
        // 指纹 id 由 app 侧通过 setprop debug.fakegps.fingerprint_id 发布
        FingerprintReplayer.fingerprintIdProvider = {
            runCatching { getSystemProperty("debug.fakegps.fingerprint_id") }.getOrDefault("")
        }
        hookFingerprintReplay(lpparam)

        // Eagerly resolve context from ActivityThread if available
        runCatching { getAnyContext() }.logFailure(TAG, "eager getAnyContext()")

        if (pkg == "android") {
            // =========================================================================
            // 核心系统层 Hook (System Framework / system_server)
            // =========================================================================
            Diag.i(TAG, ">>> hooked system framework (system_server) <<<")
            hookSystemServer(lpparam)
        } else {
            // =========================================================================
            // 客户端应用层 Hook (Client Application Fallback)
            // =========================================================================
            Diag.i(TAG, "injected into client application: $pkg")
            hookClientApp(lpparam)
        }
    }

    // =========================================================================
    // 1. 系统层 Hook 核心逻辑 (system_server)
    // =========================================================================

    private fun hookSystemServer(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 1.1 Hook LocationProviderManager (Android 11 - 15+)
        hookLocationProviderManager(lpparam)

        // 1.2 Hook LocationManagerService (All Android versions)
        hookLocationManagerService(lpparam)

        // 1.3 Hook LMS Listener Dispatchers (Receiver / LocationRegistration)
        hookLmsDispatchers(lpparam)

        // 1.4 Hook System-level WifiServiceImpl (Cut off Wi-Fi BSSID scanner)
        hookSystemWifiService(lpparam)

        // 1.5 Hook System-level TelephonyRegistry (Cut off Cell Tower updates)
        hookSystemTelephonyRegistry(lpparam)

        // 1.6 Hook System-level GNSS Status (Synthesize BDS/GPS Constellation)
        hookSystemGnssStatus(lpparam)

        // 1.7 Provider 状态查询 + 注册登记（伪造状态下让"GPS 已开启"成立，并记住分发对象）
        hookLmsProviderState(lpparam)

        // 1.8 硬件静默时的主动喂点线程（常驻守护线程，只在需要时才真正推送）
        startProactivePush()
    }

    /**
     * 系统侧 GNSS 星历合成。
     *
     * 方法名按 AOSP 版本分化（已核对 AOSP 11/12/13/14 源码）：
     *   Android 12+ : com.android.server.location.gnss.GnssStatusProvider
     *                 -> onReportSvStatus(GnssStatus)
     *   Android 11  : com.android.server.location.gnss.GnssStatusListenerHelper
     *                 -> onSvStatusChanged(int, int[], float[], float[], float[], float[], float[])
     *
     * 旧代码用的 "onReportGnssStatus" 在任何一个 AOSP 版本里都不存在，
     * 导致 system_server 侧星历合成从未生效，室内读到的是真实 0 星。
     */
    private fun hookSystemGnssStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Diag.d(TAG, "GNSS status hook skipped: SDK_INT < N")
            return
        }

        // ---- Android 12+：GnssStatusProvider.onReportSvStatus(GnssStatus) ----
        val providerClass = XposedHelpers.findClassIfExists(
            "com.android.server.location.gnss.GnssStatusProvider", lpparam.classLoader
        )
        if (providerClass != null) {
            runCatching {
                XposedBridge.hookAllMethods(providerClass, "onReportSvStatus", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getGlobalActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val synthetic = SyntheticGnssProvider.createSyntheticGnssStatus() ?: return
                        for (i in param.args.indices) {
                            if (param.args[i] is GnssStatus) {
                                param.args[i] = synthetic
                            }
                        }
                    }
                })
            }.logFailure(TAG, "hook GnssStatusProvider.onReportSvStatus", Diag.Level.WARN)
        }

        // ---- Android 11：GnssStatusListenerHelper.onSvStatusChanged(7 参数) ----
        val helperClass = XposedHelpers.findClassIfExists(
            "com.android.server.location.gnss.GnssStatusListenerHelper", lpparam.classLoader
        )
        if (helperClass != null) {
            runCatching {
                XposedBridge.hookAllMethods(helperClass, "onSvStatusChanged", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // hookAllMethods 会挂上该名字的**全部重载**。厂商分支常同时提供
                        // 1 参的 onSvStatusChanged(GnssStatus) 与 7 参版本，
                        // 对 1 参版本写 args[1..6] 会抛 ArrayIndexOutOfBoundsException
                        // 并抛进框架的 GNSS 分发链。
                        if (param.args.size < 7) return
                        val spoof = getGlobalActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val synthetic = SyntheticGnssProvider.createSyntheticGnssStatus() ?: return
                        val n = synthetic.satelliteCount
                        if (n <= 0) return

                        // 位编码对照 AOSP 11 GnssStatus.java:64-72 / 472-479
                        //   SVID_SHIFT_WIDTH = 12
                        //   CONSTELLATION_TYPE_SHIFT_WIDTH = 8, MASK = 0xf
                        //   HAS_EPHEMERIS = 1<<0, HAS_ALMANAC = 1<<1, USED_IN_FIX = 1<<2
                        val prnWithFlags = IntArray(n)
                        val cn0 = FloatArray(n)
                        val elev = FloatArray(n)
                        val azim = FloatArray(n)
                        val carrier = FloatArray(n)
                        val baseband = FloatArray(n)
                        for (i in 0 until n) {
                            val svid = synthetic.getSvid(i)
                            val const = synthetic.getConstellationType(i)
                            var flags = (svid shl 12) or ((const and 0xf) shl 8)
                            if (synthetic.hasEphemerisData(i)) flags = flags or (1 shl 0)
                            if (synthetic.hasAlmanacData(i)) flags = flags or (1 shl 1)
                            if (synthetic.usedInFix(i)) flags = flags or (1 shl 2)
                            flags = flags or (1 shl 3)
                            flags = flags or (1 shl 4)
                            prnWithFlags[i] = flags
                            cn0[i] = synthetic.getCn0DbHz(i)
                            elev[i] = synthetic.getElevationDegrees(i)
                            azim[i] = synthetic.getAzimuthDegrees(i)
                            carrier[i] = synthetic.getCarrierFrequencyHz(i)
                            baseband[i] = (synthetic.getCn0DbHz(i) - 2.5f).coerceAtLeast(15f)
                        }
                        param.args[0] = Integer.valueOf(n)
                        param.args[1] = prnWithFlags
                        param.args[2] = cn0
                        param.args[3] = elev
                        param.args[4] = azim
                        param.args[5] = carrier
                        param.args[6] = baseband
                    }
                })
            }.logFailure(TAG, "hook GnssStatusListenerHelper.onSvStatusChanged", Diag.Level.WARN)
        }

        if (providerClass == null && helperClass == null) {
            Diag.w(TAG, "GNSS status hook: neither GnssStatusProvider nor GnssStatusListenerHelper resolved")
        }
    }

    private fun extractLocationFromResult(result: Any?): Location? {
        if (result == null) return null
        if (result is Location) return result
        if (result.javaClass.name.contains("LocationResult")) {
            // Both unwrap strategies failing means we could not read the framework's own
            // location back out. Previously this returned null without a trace.
            return runCatching {
                XposedHelpers.callMethod(result, "getLastLocation") as? Location
            }.logFailure(TAG, "unwrap LocationResult.getLastLocation()", Diag.Level.DEBUG)
                .getOrNull()
                ?: runCatching {
                    val list = XposedHelpers.callMethod(result, "getLocations") as? List<*>
                    list?.lastOrNull() as? Location
                }.logFailure(TAG, "unwrap LocationResult.getLocations()", Diag.Level.DEBUG)
                    .getOrNull()
        }
        return null
    }

    @Volatile
    private var cachedResultClass: Class<*>? = null
    @Volatile
    private var cachedLocationResultMethod: java.lang.reflect.Method? = null

    /**
     * Wrap a [Location] into whatever `LocationResult`-shaped type this ROM's framework expects.
     *
     * **This is the single most failure-prone point in the whole hook.** Every caller does
     * `if (result != null) param.result = result` / `param.args[0] = result`, so returning
     * `null` means the spoof is silently *not applied* — no exception, no log, no symptom
     * other than "the fake location didn't take". Each failure branch below therefore
     * reports which class and which strategy gave up.
     */
    private fun createLocationResult(resultClass: Class<*>, location: Location): Any? {
        val method: java.lang.reflect.Method? = if (cachedResultClass === resultClass && cachedLocationResultMethod != null) {
            cachedLocationResultMethod
        } else {
            val lookedUp = runCatching {
                XposedHelpers.findMethodExactIfExists(resultClass, "wrap", Location::class.java)
                    ?: XposedHelpers.findMethodExactIfExists(resultClass, "wrap", Array<Location>::class.java)
                    ?: XposedHelpers.findMethodExactIfExists(resultClass, "wrap", List::class.java)
                    ?: XposedHelpers.findMethodExactIfExists(resultClass, "create", List::class.java)
                    ?: XposedHelpers.findMethodExactIfExists(resultClass, "create", Array<Location>::class.java)
            }.logFailure(TAG, "look up wrap()/create() factory on ${resultClass.name}", Diag.Level.DEBUG)
                .getOrNull()
            cachedResultClass = resultClass
            cachedLocationResultMethod = lookedUp
            lookedUp
        }

        if (method != null) {
            val res = runCatching {
                val paramType = method.parameterTypes.firstOrNull()
                when {
                    paramType == Location::class.java -> method.invoke(null, location)
                    paramType == List::class.java -> method.invoke(null, listOf(location))
                    else -> method.invoke(null, arrayOf(location))
                }
            }.logFailure(TAG, "invoke ${method.name}() on ${resultClass.name}")
                .getOrNull()
            if (res != null) return res
        }

        // Fallback for non-standard framework derivatives
        val fallback = runCatching {
            XposedHelpers.callStaticMethod(resultClass, "wrap", location)
        }.logFailure(TAG, "fallback callStaticMethod(wrap, Location) on ${resultClass.name}", Diag.Level.DEBUG)
            .getOrElse {
                runCatching {
                    XposedHelpers.callStaticMethod(resultClass, "wrap", arrayOf(location))
                }.logFailure(TAG, "fallback callStaticMethod(wrap, Array<Location>) on ${resultClass.name}", Diag.Level.DEBUG)
                    .getOrElse {
                        runCatching {
                            XposedHelpers.callStaticMethod(resultClass, "create", listOf(location))
                        }.logFailure(TAG, "fallback callStaticMethod(create, List<Location>) on ${resultClass.name}", Diag.Level.DEBUG)
                            .getOrNull()
                    }
            }

        if (fallback == null) {
            // The whole purpose of this function is to hand the framework a spoofed location.
            // Naming the offending class is exactly what is needed to fix it on a new ROM.
            Diag.w(
                TAG,
                "could not wrap Location into ${resultClass.name} — spoof NOT applied for this provider"
            )
        }
        return fallback
    }

    private fun hookLocationProviderManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val lpmClasses = listOfNotNull(
            // Android 12+：独立类，位于 provider 子包
            XposedHelpers.findClassIfExists("com.android.server.location.provider.LocationProviderManager", lpparam.classLoader),
            // Android 11：LocationManagerService 的内部类（非静态）
            XposedHelpers.findClassIfExists("com.android.server.location.LocationManagerService\$LocationProviderManager", lpparam.classLoader),
            // Android 10 及更早
            XposedHelpers.findClassIfExists("com.android.server.location.LocationProviderManager", lpparam.classLoader)
        )

        // Silent-zero guard: when every candidate name misses, LocationProviderManager is never
        // hooked and the whole dispatch path is inert. This used to produce no output whatsoever,
        // so "the ROM renamed the class" looked identical to "nothing to do here".
        if (lpmClasses.isEmpty()) {
            Diag.w(TAG, "LocationProviderManager hook: neither candidate class resolved on this ROM")
        } else {
            Diag.d(TAG, "LocationProviderManager hook: ${lpmClasses.size} candidate class(es) resolved")
        }

        for (lpmClass in lpmClasses) {
            // onReportLocation(LocationResult locationResult)
            runCatching {
                XposedBridge.hookAllMethods(lpmClass, "onReportLocation", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        loadMultiTargetRules()
                        // If per-app multi-target rules exist, do NOT replace raw hardware location globally!
                        // Allow raw location to reach LocationRegistration so each app can be routed independently.
                        if (multiTargetCache.isNotEmpty()) {
                            return
                        }

                        val spoof = getGlobalActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val resultArg = param.args.getOrNull(0) ?: return

                        val providerName = runCatching {
                            XposedHelpers.callMethod(param.thisObject, "getName") as? String
                        }.getOrNull() ?: LocationManager.GPS_PROVIDER

                        val spoofedLoc = createSpoofedLocation(providerName, spoof)
                        val newResult = createLocationResult(resultArg.javaClass, spoofedLoc)
                        if (newResult != null) {
                            param.args[0] = newResult
                        }
                    }
                })
            }.logFailure(TAG, "hookAllMethods(onReportLocation) on ${lpmClass.simpleName}")

            // getLastLocation(...) - Critical fix for Android 12~16 LocationResult return type!
            runCatching {
                XposedBridge.hookAllMethods(lpmClass, "getLastLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val (targetPkg, targetUid) = extractIdentityFromArgs(param.args)
                        val spoof = getActiveLocation(targetPkg, targetUid)
                        if (spoof != null && spoof.isActive) {
                            val providerName = runCatching {
                                XposedHelpers.callMethod(param.thisObject, "getName") as? String
                            }.getOrNull() ?: LocationManager.GPS_PROVIDER

                            val spoofedLoc = createSpoofedLocation(providerName, spoof)
                            val method = param.method as? java.lang.reflect.Method
                            val returnType = method?.returnType

                            if (returnType != null && returnType.name.contains("LocationResult")) {
                                val res = createLocationResult(returnType, spoofedLoc)
                                if (res != null) {
                                    param.result = res
                                    return
                                }
                            }
                            param.result = spoofedLoc
                        } else {
                            // Spoof is inactive: purge stale fake coordinates from system_server's mLastLocation cache
                            val fakeLat = if (spoof != null && (spoof.latitude != 0.0 || spoof.longitude != 0.0)) spoof.latitude else lastSpoofedLocation?.latitude
                            val fakeLon = if (spoof != null && (spoof.latitude != 0.0 || spoof.longitude != 0.0)) spoof.longitude else lastSpoofedLocation?.longitude
                            val loc = extractLocationFromResult(param.result)
                            if (loc != null && fakeLat != null && fakeLon != null) {
                                if (Math.abs(loc.latitude - fakeLat) < 0.0001 &&
                                    Math.abs(loc.longitude - fakeLon) < 0.0001) {
                                    param.result = null
                                }
                            }
                        }
                    }
                })
            }.logFailure(TAG, "hookAllMethods(getLastLocation) on ${lpmClass.simpleName}")

            // setLastLocation(...)
            runCatching {
                XposedBridge.hookAllMethods(lpmClass, "setLastLocation", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val providerName = runCatching {
                            XposedHelpers.callMethod(param.thisObject, "getName") as? String
                        }.getOrNull() ?: LocationManager.GPS_PROVIDER
                        val spoofedLoc = createSpoofedLocation(providerName, spoof)
                        for (i in param.args.indices) {
                            val arg = param.args[i] ?: continue
                            if (arg is Location) {
                                param.args[i] = spoofedLoc
                            } else if (arg.javaClass.name.contains("LocationResult")) {
                                val newResult = createLocationResult(arg.javaClass, spoofedLoc)
                                if (newResult != null) {
                                    param.args[i] = newResult
                                }
                            }
                        }
                    }
                })
            }.logFailure(TAG, "hookAllMethods(setLastLocation) on ${lpmClass.simpleName}")
        }
    }

    private fun hookLocationManagerService(lpparam: XC_LoadPackage.LoadPackageParam) {
        val lmsClasses = listOfNotNull(
            XposedHelpers.findClassIfExists("com.android.server.location.LocationManagerService", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.LocationManagerService", lpparam.classLoader)
        )

        if (lmsClasses.isEmpty()) {
            Diag.w(TAG, "LocationManagerService hook: neither candidate class resolved on this ROM")
        } else {
            Diag.d(TAG, "LocationManagerService hook: ${lmsClasses.size} candidate class(es) resolved")
        }

        for (lmsClass in lmsClasses) {
            // 1. getLastLocation(...)
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "getLastLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val (targetPkg, targetUid) = extractIdentityFromArgs(param.args)
                        val spoof = getActiveLocation(targetPkg, targetUid)
                        if (spoof != null && spoof.isActive) {
                            if (isCallingPackageSelf(param)) return
                            val provider = param.args.firstOrNull { it is String } as? String ?: LocationManager.GPS_PROVIDER
                            param.result = createSpoofedLocation(provider, spoof)
                        } else {
                            val fakeLat = if (spoof != null && (spoof.latitude != 0.0 || spoof.longitude != 0.0)) spoof.latitude else lastSpoofedLocation?.latitude
                            val fakeLon = if (spoof != null && (spoof.latitude != 0.0 || spoof.longitude != 0.0)) spoof.longitude else lastSpoofedLocation?.longitude
                            val loc = extractLocationFromResult(param.result)
                            if (loc != null && fakeLat != null && fakeLon != null) {
                                if (Math.abs(loc.latitude - fakeLat) < 0.0001 &&
                                    Math.abs(loc.longitude - fakeLon) < 0.0001) {
                                    param.result = null
                                }
                            }
                        }
                    }
                })
            }.logFailure(TAG, "hook LocationManagerService.getLastLocation on ${lmsClass.simpleName}")

            // 2. getCurrentLocation(...) (Android 11+)
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "getCurrentLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val (targetPkg, targetUid) = extractIdentityFromArgs(param.args)
                        val spoof = getActiveLocation(targetPkg, targetUid) ?: return
                        if (!spoof.isActive) return
                        if (isCallingPackageSelf(param)) return

                        if (param.result is Location) {
                            param.result = createSpoofedLocation(LocationManager.GPS_PROVIDER, spoof)
                        }
                    }
                })
            }.logFailure(TAG, "hook LocationManagerService.getCurrentLocation on ${lmsClass.simpleName}")

            // 3. reportLocation(...) (Android <= 10)
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "reportLocation", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        loadMultiTargetRules()
                        if (multiTargetCache.isNotEmpty()) return // Let per-app dispatch handle it
                        val spoof = getGlobalActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val originalLoc = param.args.firstOrNull { it is Location } as? Location
                        val provider = originalLoc?.provider ?: LocationManager.GPS_PROVIDER
                        val spoofed = createSpoofedLocation(provider, spoof)
                        for (i in param.args.indices) {
                            if (param.args[i] is Location) {
                                param.args[i] = spoofed
                            }
                        }
                    }
                })
            }.logFailure(TAG, "hook LocationManagerService.reportLocation on ${lmsClass.simpleName}")

            // 4. handleLocationChanged(...) (All Android versions)
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "handleLocationChanged", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        loadMultiTargetRules()
                        if (multiTargetCache.isNotEmpty()) return // Let per-app dispatch handle it
                        val spoof = getGlobalActiveLocation() ?: return
                        if (!spoof.isActive) return
                        for (i in param.args.indices) {
                            if (param.args[i] is Location) {
                                val prov = (param.args[i] as Location).provider ?: LocationManager.GPS_PROVIDER
                                param.args[i] = createSpoofedLocation(prov, spoof)
                            }
                        }
                    }
                })
            }.logFailure(TAG, "hook LocationManagerService.handleLocationChanged on ${lmsClass.simpleName}")
        }
    }

    private fun hookLmsDispatchers(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Older Android Receiver (Android <= 10)
        runCatching {
            val receiverClass = XposedHelpers.findClassIfExists(
                "com.android.server.LocationManagerService\$Receiver",
                lpparam.classLoader
            )
            if (receiverClass == null) {
                Diag.d(TAG, "LocationManagerService\$Receiver absent (expected on Android 11+)")
            }
            if (receiverClass != null) {
                XposedBridge.hookAllMethods(receiverClass, "callLocationChangedLocked", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        dispatchTargets.add(param.thisObject)
                        lastDispatchAt = SystemClock.elapsedRealtime()
                        val (targetPkg, targetUid) = extractIdentityFromObject(param.thisObject)
                        val spoof = getActiveLocation(targetPkg, targetUid) ?: return
                        if (!spoof.isActive) return
                        val loc = param.args.firstOrNull { it is Location } as? Location ?: return
                        param.args[0] = createSpoofedLocation(loc.provider ?: LocationManager.GPS_PROVIDER, spoof)
                    }
                })
            }
        }.logFailure(TAG, "hook LocationManagerService\$Receiver.callLocationChangedLocked")

        // Android 11~16+ LocationRegistration dispatchers
        val locRegClasses = listOfNotNull(
            XposedHelpers.findClassIfExists("com.android.server.location.provider.LocationProviderManager\$Registration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.provider.LocationProviderManager\$LocationRegistration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.provider.LocationProviderManager\$LocationListenerRegistration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.provider.LocationProviderManager\$LocationPendingIntentRegistration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.provider.LocationProviderManager\$GetCurrentLocationListenerRegistration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.LocationProviderManager\$Registration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.LocationProviderManager\$LocationRegistration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.LocationProviderManager\$LocationListenerRegistration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.LocationProviderManager\$LocationPendingIntentRegistration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.LocationProviderManager\$GetCurrentLocationListenerRegistration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.LocationManagerService\$LocationRegistration", lpparam.classLoader)
        ).distinct()

        // Silent-zero guard: 11 candidate names spanning AOSP versions and vendor forks. If none
        // resolve, the per-app dispatch path is never hooked and every app quietly keeps
        // receiving the raw hardware location instead of its routed one.
        if (locRegClasses.isEmpty()) {
            Diag.w(TAG, "LocationRegistration dispatch hook: none of the 11 candidate classes resolved")
        } else {
            Diag.d(TAG, "LocationRegistration dispatch hook: ${locRegClasses.size} candidate class(es) resolved")
        }

        for (locRegClass in locRegClasses) {
            runCatching {
                val hookDispatch = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        dispatchTargets.add(param.thisObject)
                        lastDispatchAt = SystemClock.elapsedRealtime()
                        val (targetPkg, targetUid) = extractIdentityFromObject(param.thisObject)
                        val spoof = getActiveLocation(targetPkg, targetUid) ?: return
                        if (!spoof.isActive) {
                            // Target is in REAL_PASSTHROUGH mode or rule disabled:
                            // Do NOT modify anything, pass through 100% genuine hardware location!
                            return
                        }
                        val arg = param.args.getOrNull(0) ?: return
                        val providerName = runCatching {
                            val owner = XposedHelpers.callMethod(param.thisObject, "getOwner")
                            XposedHelpers.callMethod(owner, "getName") as? String
                        }.getOrNull() ?: LocationManager.GPS_PROVIDER

                        val spoofedLoc = createSpoofedLocation(providerName, spoof)
                        if (arg is Location) {
                            param.args[0] = spoofedLoc
                        } else if (arg.javaClass.name.contains("LocationResult")) {
                            val newResult = createLocationResult(arg.javaClass, spoofedLoc)
                            if (newResult != null) {
                                param.args[0] = newResult
                            }
                        }

                        // Prevent AOSP smallestDisplacement drop filter when stationary
                        runCatching {
                            val req = XposedHelpers.callMethod(param.thisObject, "getRequest")
                            if (req != null) {
                                val minDistance = runCatching {
                                    XposedHelpers.callMethod(req, "getMinUpdateDistanceMeters") as? Float
                                }.logFailure(TAG, "read getMinUpdateDistanceMeters()", Diag.Level.DEBUG)
                                    .getOrNull() ?: 0f
                                if (minDistance > 0f) {
                                    // findClass() (not findClassIfExists) throws when the ROM lacks
                                    // this class — which used to abort the whole suppression silently.
                                    val builderClass = XposedHelpers.findClass("android.location.LocationRequest\$Builder", lpparam.classLoader)
                                    val builder = XposedHelpers.findConstructorExact(builderClass, req.javaClass).newInstance(req)
                                    XposedHelpers.callMethod(builder, "setMinUpdateDistanceMeters", 0.0f)
                                    val zeroDistReq = XposedHelpers.callMethod(builder, "build")
                                    XposedHelpers.setObjectField(param.thisObject, "mProviderLocationRequest", zeroDistReq)
                                }
                            }
                        }.logFailure(
                            TAG,
                            "suppress smallestDisplacement filter — stationary updates may be dropped by AOSP"
                        )
                    }
                }
                XposedBridge.hookAllMethods(locRegClass, "acceptLocationChange", hookDispatch)
                XposedBridge.hookAllMethods(locRegClass, "onLocationChanged", hookDispatch)
            }.logFailure(TAG, "register dispatch hooks on ${locRegClass.name}")
        }
    }

    private fun isLocationProviderName(name: String): Boolean =
        name == LocationManager.GPS_PROVIDER ||
            name == LocationManager.NETWORK_PROVIDER ||
            name == LocationManager.PASSIVE_PROVIDER ||
            name == "fused"

    /**
     * Provider 状态查询与注册登记。
     *
     * 只替换定位结果是不够的：相当一部分应用先查"GPS 是否可用 / 哪个 provider 最好"，查询失败就直接
     * 不请求定位，伪造点根本没有机会被投递。所以伪造生效时必须让这几个查询返回一致的结果，否则会
     * 出现"位置明明是对的，但某个应用不动"的现象。
     */
    private fun hookLmsProviderState(lpparam: XC_LoadPackage.LoadPackageParam) {
        val lmsClasses = listOfNotNull(
            XposedHelpers.findClassIfExists("com.android.server.location.LocationManagerService", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.LocationManagerService", lpparam.classLoader)
        )
        if (lmsClasses.isEmpty()) {
            Diag.w(TAG, "provider-state hook: LocationManagerService not resolved on this ROM")
            return
        }

        for (lmsClass in lmsClasses) {
            // 1. isProviderEnabled / isProviderEnabledForUser
            val enabledHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result !is Boolean) return
                    val provider = param.args.firstOrNull { it is String } as? String ?: return
                    if (!isLocationProviderName(provider)) return
                    val spoof = getGlobalActiveLocation() ?: return
                    if (!spoof.isActive) return
                    param.result = true
                }
            }
            runCatching { XposedBridge.hookAllMethods(lmsClass, "isProviderEnabled", enabledHook) }
                .logFailure(TAG, "hook ${lmsClass.simpleName}.isProviderEnabled")
            runCatching { XposedBridge.hookAllMethods(lmsClass, "isProviderEnabledForUser", enabledHook) }
                .logFailure(TAG, "hook ${lmsClass.simpleName}.isProviderEnabledForUser")

            // 2. getBestProvider：应用据此挑 provider，挑不到定位类 provider 就不会走到我们的分支
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "getBestProvider", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getGlobalActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val current = param.result as? String
                        if (current == null || !isLocationProviderName(current)) {
                            param.result = LocationManager.GPS_PROVIDER
                        }
                    }
                })
            }.logFailure(TAG, "hook ${lmsClass.simpleName}.getBestProvider")

            // 3. getProviders：保证列表里有 gps，否则部分应用认为"无可用 provider"直接放弃
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "getProviders", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getGlobalActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val raw = param.result as? List<*> ?: return
                        val providers = ArrayList<String>()
                        for (item in raw) {
                            if (item is String) providers.add(item)
                        }
                        if (!providers.contains(LocationManager.GPS_PROVIDER)) {
                            providers.add(LocationManager.GPS_PROVIDER)
                            param.result = providers
                        }
                    }
                })
            }.logFailure(TAG, "hook ${lmsClass.simpleName}.getProviders")

            // 4. requestLocationUpdatesLocked：注册阶段就把分发对象记下来。
            //    只依赖分发钩子的话，硬件从不上报时我们永远拿不到这个对象，也就无从主动喂点。
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "requestLocationUpdatesLocked", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val registration = param.args.firstOrNull { arg ->
                            arg != null && (
                                arg.javaClass.name.contains("Receiver") ||
                                    arg.javaClass.name.contains("Registration")
                                )
                        } ?: return
                        dispatchTargets.add(registration)
                    }
                })
            }.logFailure(TAG, "hook ${lmsClass.simpleName}.requestLocationUpdatesLocked")
        }
    }

    /**
     * 启动主动喂点线程。常驻守护线程，每 [PUSH_INTERVAL_MS] 醒来一次；没有可推送对象时不做任何事。
     *
     * 必要性：分发钩子是"来一个换一个"，依赖硬件真的上报。GPS 关闭、室内无信号、或 ROM 不上报时，
     * 钩子一次都不触发，应用会一直停在旧坐标上。这条线程补的就是这个空档。
     */
    private fun startProactivePush() {
        if (pushThreadStarted) return
        pushThreadStarted = true
        Thread({
            while (true) {
                runCatching { Thread.sleep(PUSH_INTERVAL_MS) }
                    .logFailure(TAG, "proactive push: sleep interrupted", Diag.Level.DEBUG)
                runCatching { pushSpoofedFixIfIdle() }
                    .logFailure(TAG, "proactive push tick", Diag.Level.DEBUG)
            }
        }, "fakeloc-push").apply {
            isDaemon = true
            start()
        }
    }

    private fun pushSpoofedFixIfIdle() {
        if (dispatchTargets.isEmpty()) return

        // 硬件在正常上报时不要重复喂，否则等于把刷新率翻倍
        val idleFor = SystemClock.elapsedRealtime() - lastDispatchAt
        if (lastDispatchAt != 0L && idleFor < PUSH_IDLE_THRESHOLD_MS) return

        loadMultiTargetRules()
        for (target in dispatchTargets.toList()) {
            runCatching {
                val (pkg, uid) = extractIdentityFromObject(target)
                val spoof = getActiveLocation(pkg, uid) ?: return@runCatching
                if (!spoof.isActive) return@runCatching

                val location = createSpoofedLocation(LocationManager.GPS_PROVIDER, spoof)
                for (name in dispatchMethodNames) {
                    val method = target.javaClass.declaredMethods
                        .firstOrNull { it.name == name && it.parameterTypes.size == 1 } ?: continue
                    val argType = method.parameterTypes[0]
                    val arg = when {
                        Location::class.java.isAssignableFrom(argType) -> location
                        argType.name.contains("LocationResult") ->
                            createLocationResult(argType, location) ?: continue
                        else -> continue
                    }
                    method.isAccessible = true
                    // 反射调用会再次进入本文件的分发钩子，把入参换成同一个伪造点，因此这一步是幂等的
                    method.invoke(target, arg)
                    lastDispatchAt = SystemClock.elapsedRealtime()
                    break
                }
            }.logFailure(TAG, "proactive push to ${target.javaClass.simpleName}", Diag.Level.DEBUG)
        }
    }

    private fun hookSystemWifiService(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 1. WifiServiceImpl
        runCatching {
            val wifiServiceClass = XposedHelpers.findClassIfExists(
                "com.android.server.wifi.WifiServiceImpl",
                lpparam.classLoader
            )
            if (wifiServiceClass == null) {
                Diag.w(TAG, "WifiServiceImpl not found — Wi-Fi BSSID masking inactive")
            }
            if (wifiServiceClass != null) {
                // 注意：这里**不再**把 getScanResults 置空。
                //
                // 清空扫描结果会造成物理上自相矛盾的状态：有 GPS 星数、零 Wi-Fi、
                // 零基站。风控 SDK 拿这个矛盾一测一个准，比暴露真实 AP 更可疑。
                // 需要遮蔽时应当合成与目标坐标一致的 AP 列表（见 EnvironmentFingerprint），
                // 而不是清空。

                // getConnectionInfo(...) -> 遮蔽 BSSID
                XposedBridge.hookAllMethods(wifiServiceClass, "getConnectionInfo", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            val wifiInfo = param.result ?: return
                            runCatching {
                                // 不能再用 02:00:00:00:00:00 —— AOSP 明确把它定义为
                                // 「调用者权限不足」的哨兵值
                                // （frameworks/base/wifi/java/android/net/wifi/WifiInfo.java:59, 485）。
                                // 保留真实 OUI 前三字节、只随机化后三字节，
                                // 看起来像「换了一个同厂商的 AP」。
                                val real = XposedHelpers.getObjectField(wifiInfo, "mBSSID") as? String
                                val newBssid = if (real != null && real.length == 17 &&
                                    real != "02:00:00:00:00:00"
                                ) {
                                    val oui = real.substring(0, 8)
                                    val r = java.util.Random(
                                        spoof.latitude.hashCode().toLong() xor
                                            spoof.longitude.hashCode().toLong()
                                    )
                                    "%s:%02X:%02X:%02X".format(
                                        oui, r.nextInt(256), r.nextInt(256), r.nextInt(256)
                                    )
                                } else {
                                    val r = java.util.Random(SystemClock.elapsedRealtime())
                                    "%02X:%02X:%02X:%02X:%02X:%02X".format(
                                        (r.nextInt(256) and 0xFE), r.nextInt(256), r.nextInt(256),
                                        r.nextInt(256), r.nextInt(256), r.nextInt(256)
                                    )
                                }
                                XposedHelpers.setObjectField(wifiInfo, "mBSSID", newBssid)
                                XposedHelpers.setObjectField(wifiInfo, "mMacAddress", newBssid)
                            }.logFailure(TAG, "mask WifiInfo BSSID/MAC", Diag.Level.WARN)
                        }
                    }
                })
            }
        }.logFailure(TAG, "hook WifiServiceImpl getConnectionInfo")
    }

    private fun hookSystemTelephonyRegistry(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            val telRegistryClass = XposedHelpers.findClassIfExists(
                "com.android.server.TelephonyRegistry",
                lpparam.classLoader
            )
            if (telRegistryClass == null) {
                Diag.w(TAG, "com.android.server.TelephonyRegistry not found — cell tower masking inactive")
            }
            if (telRegistryClass != null) {
                val emptyCellHook = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            for (i in param.args.indices) {
                                if (param.args[i] is List<*>) {
                                    param.args[i] = java.util.ArrayList<Any>()
                                }
                            }
                        }
                    }
                }
                XposedBridge.hookAllMethods(telRegistryClass, "notifyCellInfo", emptyCellHook)
                XposedBridge.hookAllMethods(telRegistryClass, "notifyCellInfoForSubscriber", emptyCellHook)

                XposedBridge.hookAllMethods(telRegistryClass, "notifyCellLocation", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            for (i in param.args.indices) {
                                if (param.args[i] is Bundle) {
                                    param.args[i] = Bundle()
                                }
                            }
                        }
                    }
                })
            }
        }.logFailure(TAG, "hook TelephonyRegistry cell info/location masking")
    }

    // =========================================================================
    // 2. 客户端应用层 Hook 逻辑 (Client Application Fallback)
    // =========================================================================

    private fun hookClientApp(lpparam: XC_LoadPackage.LoadPackageParam) {
        // LocationManager client API hooks
        hookLocationManager(lpparam)

        // WifiManager client hooks
        hookWifiManager(lpparam)

        // TelephonyManager client hooks
        hookTelephonyManager(lpparam)

        // Map SDK hooks (AMap / Baidu)
        hookProprietaryMapSdks(lpparam)
    }

    private fun hookMockDetection(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            val locClass = XposedHelpers.findClassIfExists("android.location.Location", lpparam.classLoader)
            if (locClass == null) {
                // Same control flow as the previous `?: return`, but the miss is now visible.
                Diag.w(TAG, "android.location.Location not found — isMock masking inactive")
                return
            }
            XposedBridge.hookAllMethods(locClass, "isFromMockProvider", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation()
                    if (spoof != null && spoof.isActive) {
                        param.result = false
                    }
                }
            })

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                XposedBridge.hookAllMethods(locClass, "isMock", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation()
                        if (spoof != null && spoof.isActive) {
                            param.result = false
                        }
                    }
                })
            }

            XposedBridge.hookAllMethods(locClass, "setIsFromMockProvider", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation()
                    if (spoof != null && spoof.isActive && param.args.isNotEmpty()) {
                        param.args[0] = false
                    }
                }
            })

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                XposedBridge.hookAllMethods(locClass, "setMock", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation()
                        if (spoof != null && spoof.isActive && param.args.isNotEmpty()) {
                            param.args[0] = false
                        }
                    }
                })
            }
        }.logFailure(TAG, "install mock-flag masking hooks")
    }

    // =========================================================================
    // 环境指纹回放（Wi-Fi / 基站 / 蓝牙）
    //
    // 设计要点：
    //  - 读不到指纹包时**什么都不做**，让真实值透出。
    //    返回空列表比返回真实值更可疑（零 Wi-Fi + 有 GPS 星数物理上不成立）。
    //  - 时间戳一律重写为当前时刻，否则 SDK 一算 age 就知道是旧数据。
    //  - RSSI 按采集点到目标点的距离重标定（FingerprintReplayer.rescaleRssi）。
    // =========================================================================

    private val hookedBleCallbackClasses: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val bleCallbacks: MutableSet<Any> = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<Any, Boolean>())
    )
    @Volatile private var blePushThreadStarted = false

    private fun hookFingerprintReplay(lpparam: XC_LoadPackage.LoadPackageParam) {
        val wifiManagerClass = XposedHelpers.findClassIfExists("android.net.wifi.WifiManager", lpparam.classLoader)
        val scanResultClass = XposedHelpers.findClassIfExists("android.net.wifi.ScanResult", lpparam.classLoader)
        val wifiSsidClass = XposedHelpers.findClassIfExists("android.net.wifi.WifiSsid", lpparam.classLoader)
        val wifiInfoClass = XposedHelpers.findClassIfExists("android.net.wifi.WifiInfo", lpparam.classLoader)

        val cellInfoLteClass = XposedHelpers.findClassIfExists("android.telephony.CellInfoLte", lpparam.classLoader)
        val cellIdentityLteClass = XposedHelpers.findClassIfExists("android.telephony.CellIdentityLte", lpparam.classLoader)
        val cellSignalLteClass = XposedHelpers.findClassIfExists("android.telephony.CellSignalStrengthLte", lpparam.classLoader)
        val cellConfigLteClass = XposedHelpers.findClassIfExists("android.telephony.CellConfigLte", lpparam.classLoader)
        val cellInfoGsmClass = XposedHelpers.findClassIfExists("android.telephony.CellInfoGsm", lpparam.classLoader)
        val cellIdentityGsmClass = XposedHelpers.findClassIfExists("android.telephony.CellIdentityGsm", lpparam.classLoader)
        val cellSignalGsmClass = XposedHelpers.findClassIfExists("android.telephony.CellSignalStrengthGsm", lpparam.classLoader)
        val cellInfoWcdmaClass = XposedHelpers.findClassIfExists("android.telephony.CellInfoWcdma", lpparam.classLoader)
        val cellIdentityWcdmaClass = XposedHelpers.findClassIfExists("android.telephony.CellIdentityWcdma", lpparam.classLoader)
        val cellSignalWcdmaClass = XposedHelpers.findClassIfExists("android.telephony.CellSignalStrengthWcdma", lpparam.classLoader)

        // ---------------- Wi-Fi ----------------
        if (wifiManagerClass != null) {
            // getScanResults() -> 用指纹包替换
            runCatching {
                XposedBridge.hookAllMethods(wifiManagerClass, "getScanResults", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isCallingPackageSelf(param)) return
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val fp = FingerprintReplayer.getActive() ?: return
                        val built = FingerprintReplayer.buildScanResultList(
                            fp, scanResultClass, wifiSsidClass, spoof.latitude, spoof.longitude
                        )
                        // 空列表不赋值：宁可透出真实值，也不制造「零 Wi-Fi」这种矛盾状态
                        if (built.isNotEmpty()) param.result = built
                    }
                })
            }.logFailure(TAG, "hook WifiManager.getScanResults for fingerprint replay", Diag.Level.WARN)

            // getConnectionInfo() -> 重写 BSSID / SSID / RSSI
            runCatching {
                XposedBridge.hookAllMethods(wifiManagerClass, "getConnectionInfo", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isCallingPackageSelf(param)) return
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val fp = FingerprintReplayer.getActive() ?: return
                        val info = param.result ?: return
                        if (wifiInfoClass == null || !wifiInfoClass.isInstance(info)) return
                        val connected = fp.wifi.firstOrNull { it.isConnected }
                            ?: fp.wifi.maxByOrNull { it.rssi } ?: return

                        runCatching {
                            XposedHelpers.callMethod(info, "setBSSID", connected.bssid)
                            XposedHelpers.callMethod(
                                info, "setRssi",
                                FingerprintReplayer.rescaleRssi(
                                    connected.rssi, fp.originLat, fp.originLon,
                                    spoof.latitude, spoof.longitude, true
                                )
                            )
                            XposedHelpers.callMethod(info, "setFrequency", connected.frequency)
                            XposedHelpers.callMethod(info, "setMacAddress", connected.bssid)
                            if (wifiSsidClass != null) {
                                val ssid = XposedHelpers.callStaticMethod(
                                    wifiSsidClass, "createFromAsciiEncoded", connected.ssid
                                )
                                XposedHelpers.callMethod(info, "setSSID", ssid)
                            }
                        }.logFailure(TAG, "rewrite WifiInfo for fingerprint replay", Diag.Level.WARN)
                    }
                })
            }.logFailure(TAG, "hook WifiManager.getConnectionInfo for fingerprint replay", Diag.Level.WARN)
        }

        // ---------------- 基站 ----------------
        val tmClass = XposedHelpers.findClassIfExists("android.telephony.TelephonyManager", lpparam.classLoader)
        if (tmClass != null) {
            // getAllCellInfo()
            runCatching {
                XposedBridge.hookAllMethods(tmClass, "getAllCellInfo", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val fp = FingerprintReplayer.getActive() ?: return
                        val list = FingerprintReplayer.buildCellInfoList(
                            fp, spoof.latitude, spoof.longitude,
                            cellInfoLteClass, cellIdentityLteClass, cellSignalLteClass, cellConfigLteClass,
                            cellInfoGsmClass, cellIdentityGsmClass, cellSignalGsmClass,
                            cellInfoWcdmaClass, cellIdentityWcdmaClass, cellSignalWcdmaClass
                        )
                        if (list.isNotEmpty()) param.result = list
                    }
                })
            }.logFailure(TAG, "hook TelephonyManager.getAllCellInfo for fingerprint replay", Diag.Level.WARN)

            // requestCellInfoUpdate(Executor, CellInfoCallback) —— 新 SDK 优先走这条异步路径
            runCatching {
                XposedBridge.hookAllMethods(tmClass, "requestCellInfoUpdate", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val fp = FingerprintReplayer.getActive() ?: return

                        val callback = param.args.firstOrNull {
                            it != null && it.javaClass.name.contains("CellInfoCallback")
                        } ?: return
                        val executor = param.args.firstOrNull {
                            it != null && java.util.concurrent.Executor::class.java.isAssignableFrom(it.javaClass)
                        } as? java.util.concurrent.Executor ?: return

                        val list = FingerprintReplayer.buildCellInfoList(
                            fp, spoof.latitude, spoof.longitude,
                            cellInfoLteClass, cellIdentityLteClass, cellSignalLteClass, cellConfigLteClass,
                            cellInfoGsmClass, cellIdentityGsmClass, cellSignalGsmClass,
                            cellInfoWcdmaClass, cellIdentityWcdmaClass, cellSignalWcdmaClass
                        )
                        if (list.isEmpty()) return

                        runCatching {
                            executor.execute {
                                runCatching {
                                    XposedHelpers.callMethod(callback, "onCellInfo", list)
                                }.logFailure(TAG, "deliver spoofed CellInfo to callback", Diag.Level.DEBUG)
                            }
                        }
                        // 阻止真实回调覆盖
                        param.result = null
                    }
                })
            }.logFailure(TAG, "hook TelephonyManager.requestCellInfoUpdate for fingerprint replay", Diag.Level.WARN)
        }

        // ---------------- 蓝牙 ----------------
        val scannerClass = XposedHelpers.findClassIfExists(
            "android.bluetooth.le.BluetoothLeScanner", lpparam.classLoader
        )
        val bleScanResultClass = XposedHelpers.findClassIfExists(
            "android.bluetooth.le.ScanResult", lpparam.classLoader
        )
        val scanRecordClass = XposedHelpers.findClassIfExists(
            "android.bluetooth.le.ScanRecord", lpparam.classLoader
        )
        val deviceClass = XposedHelpers.findClassIfExists(
            "android.bluetooth.BluetoothDevice", lpparam.classLoader
        )

        if (scannerClass != null) {
            for (name in listOf("startScan", "startScanFromSource")) {
                runCatching {
                    XposedBridge.hookAllMethods(scannerClass, name, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val spoof = getActiveLocation() ?: return
                            if (!spoof.isActive) return
                            val cb = param.args.lastOrNull { it is android.bluetooth.le.ScanCallback } ?: return
                            bleCallbacks.add(cb)
                            hookBleCallbackClass(
                                cb.javaClass, bleScanResultClass, scanRecordClass, deviceClass
                            )
                        }
                    })
                }.logFailure(TAG, "hook BluetoothLeScanner.$name", Diag.Level.DEBUG)
            }
        }

        // 已配对设备
        val adapterClass = XposedHelpers.findClassIfExists(
            "android.bluetooth.BluetoothAdapter", lpparam.classLoader
        )
        if (adapterClass != null && deviceClass != null) {
            runCatching {
                XposedBridge.hookAllMethods(adapterClass, "getBondedDevices", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val fp = FingerprintReplayer.getActive() ?: return
                        if (fp.bonded.isEmpty()) return
                        val set = HashSet<Any>()
                        for (b in fp.bonded) {
                            FingerprintReplayer.buildBluetoothDevice(deviceClass, b.address)?.let { set.add(it) }
                        }
                        if (set.isNotEmpty()) param.result = set
                    }
                })
            }.logFailure(TAG, "hook BluetoothAdapter.getBondedDevices", Diag.Level.DEBUG)
        }

        // 主动喂点：SDK 的扫描窗口很短，被动 hook 可能来不及
        startBlePush(bleScanResultClass, scanRecordClass, deviceClass)
    }

    private fun hookBleCallbackClass(
        cbClass: Class<*>,
        scanResultClass: Class<*>?,
        scanRecordClass: Class<*>?,
        deviceClass: Class<*>
    ) {
        val name = cbClass.name
        if (!hookedBleCallbackClasses.add(name)) return
        if (scanResultClass == null) return

        // onScanResult(int, ScanResult)
        runCatching {
            XposedHelpers.findAndHookMethod(
                cbClass, "onScanResult", Int::class.javaPrimitiveType, scanResultClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val fp = FingerprintReplayer.getActive() ?: return
                        val pick = fp.ble.firstOrNull() ?: return
                        val synthetic = FingerprintReplayer.buildBleScanResult(
                            pick, scanResultClass, scanRecordClass, deviceClass,
                            fp.originLat, fp.originLon, spoof.latitude, spoof.longitude
                        )
                        if (synthetic != null) param.args[1] = synthetic
                    }
                }
            )
        }.logFailure(TAG, "hook $name.onScanResult", Diag.Level.DEBUG)

        // onBatchScanResults(List<ScanResult>)
        runCatching {
            XposedHelpers.findAndHookMethod(
                cbClass, "onBatchScanResults", List::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val fp = FingerprintReplayer.getActive() ?: return
                        val list = ArrayList<Any>()
                        for (b in fp.ble) {
                            FingerprintReplayer.buildBleScanResult(
                                b, scanResultClass, scanRecordClass, deviceClass,
                                fp.originLat, fp.originLon, spoof.latitude, spoof.longitude
                            )?.let { list.add(it) }
                        }
                        if (list.isNotEmpty()) param.args[0] = list
                    }
                }
            )
        }.logFailure(TAG, "hook $name.onBatchScanResults", Diag.Level.DEBUG)
    }

    private fun startBlePush(
        scanResultClass: Class<*>?,
        scanRecordClass: Class<*>?,
        deviceClass: Class<*>?
    ) {
        if (blePushThreadStarted) return
        if (scanResultClass == null || deviceClass == null) return
        blePushThreadStarted = true
        Thread({
            while (true) {
                runCatching { Thread.sleep(1200L) }
                runCatching { pushBleResults(scanResultClass, scanRecordClass, deviceClass) }
                    .logFailure(TAG, "BLE push tick", Diag.Level.DEBUG)
            }
        }, "fakegps-ble-push").apply {
            isDaemon = true
            start()
        }
    }

    private fun pushBleResults(
        scanResultClass: Class<*>,
        scanRecordClass: Class<*>?,
        deviceClass: Class<*>
    ) {
        if (bleCallbacks.isEmpty()) return
        val spoof = getActiveLocation() ?: return
        if (!spoof.isActive) return
        val fp = FingerprintReplayer.getActive() ?: return
        if (fp.ble.isEmpty()) return

        for (cb in bleCallbacks.toList()) {
            runCatching {
                // 每次只喂 2-4 个信标：全喂 30 个会明显异常
                val pick = fp.ble.shuffled().take(2 + kotlin.random.Random.nextInt(3))
                for (b in pick) {
                    val sr = FingerprintReplayer.buildBleScanResult(
                        b, scanResultClass, scanRecordClass, deviceClass,
                        fp.originLat, fp.originLon, spoof.latitude, spoof.longitude
                    ) ?: continue
                    XposedHelpers.callMethod(
                        cb, "onScanResult", Integer.valueOf(1), sr
                    )
                }
            }.logFailure(TAG, "push BLE result to ${cb.javaClass.simpleName}", Diag.Level.DEBUG)
        }
    }

    private fun hookLocationManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val lmClass = XposedHelpers.findClassIfExists("android.location.LocationManager", lpparam.classLoader)
        if (lmClass == null) {
            Diag.w(TAG, "android.location.LocationManager not found — client-side location hooks skipped")
            return
        }

        // 1. getLastKnownLocation(String)
        runCatching {
            XposedBridge.hookAllMethods(lmClass, "getLastKnownLocation", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation()
                    if (spoof != null && spoof.isActive) {
                        val provider = param.args.firstOrNull { it is String } as? String ?: LocationManager.GPS_PROVIDER
                        param.result = createSpoofedLocation(provider, spoof)
                    } else {
                        val fakeLat = if (spoof != null && (spoof.latitude != 0.0 || spoof.longitude != 0.0)) spoof.latitude else lastSpoofedLocation?.latitude
                        val fakeLon = if (spoof != null && (spoof.latitude != 0.0 || spoof.longitude != 0.0)) spoof.longitude else lastSpoofedLocation?.longitude
                        val loc = extractLocationFromResult(param.result)
                        if (loc != null && fakeLat != null && fakeLon != null) {
                            if (Math.abs(loc.latitude - fakeLat) < 0.0001 &&
                                Math.abs(loc.longitude - fakeLon) < 0.0001) {
                                param.result = null
                            }
                        }
                    }
                }
            })
        }.logFailure(TAG, "hook LocationManager.getLastKnownLocation")

        // 2. getLastLocation() (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                XposedBridge.hookAllMethods(lmClass, "getLastLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation()
                        if (spoof != null && spoof.isActive) {
                            param.result = createSpoofedLocation(LocationManager.GPS_PROVIDER, spoof)
                        } else {
                            val fakeLat = if (spoof != null && (spoof.latitude != 0.0 || spoof.longitude != 0.0)) spoof.latitude else lastSpoofedLocation?.latitude
                            val fakeLon = if (spoof != null && (spoof.latitude != 0.0 || spoof.longitude != 0.0)) spoof.longitude else lastSpoofedLocation?.longitude
                            val loc = extractLocationFromResult(param.result)
                            if (loc != null && fakeLat != null && fakeLon != null) {
                                if (Math.abs(loc.latitude - fakeLat) < 0.0001 &&
                                    Math.abs(loc.longitude - fakeLon) < 0.0001) {
                                    param.result = null
                                }
                            }
                        }
                    }
                })
            }.logFailure(TAG, "hook LocationManager.getLastLocation")
        }

        // 3. requestLocationUpdates listener hook
        runCatching {
            XposedBridge.hookAllMethods(lmClass, "requestLocationUpdates", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (!spoof.isActive) return

                    for (arg in param.args) {
                        if (arg is LocationListener) {
                            hookLocationListener(arg.javaClass)
                        }
                    }
                }
            })
        }.logFailure(TAG, "hook LocationManager.requestLocationUpdates")

        // 4. getCurrentLocation(...) (Android 11~16)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                XposedBridge.hookAllMethods(lmClass, "getCurrentLocation", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return

                        val provider = param.args.firstOrNull { it is String } as? String ?: LocationManager.GPS_PROVIDER
                        val spoofed = createSpoofedLocation(provider, spoof)

                        for (i in param.args.indices) {
                            val arg = param.args[i] ?: continue
                            if (arg.javaClass.name.contains("Consumer") || java.util.function.Consumer::class.java.isAssignableFrom(arg.javaClass)) {
                                val originalConsumer = arg
                                val newConsumer = java.util.function.Consumer<Location> { _ ->
                                    @Suppress("UNCHECKED_CAST")
                                    (originalConsumer as java.util.function.Consumer<Location>).accept(spoofed)
                                }
                                param.args[i] = newConsumer
                            }
                        }
                    }
                })
            }.logFailure(TAG, "hook LocationManager.getCurrentLocation")
        }

        // 5. registerGnssStatusCallback(...) (Android 7.0 - 15+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                XposedBridge.hookAllMethods(lmClass, "registerGnssStatusCallback", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return

                        for (arg in param.args) {
                            if (arg != null && (arg is GnssStatus.Callback || arg.javaClass.name.contains("GnssStatus"))) {
                                hookGnssStatusCallback(arg.javaClass)
                            }
                        }
                    }
                })
            }.logFailure(TAG, "hook LocationManager.registerGnssStatusCallback")
        }

        // 6. getGpsStatus (Legacy GpsStatus fix)
        runCatching {
            XposedBridge.hookAllMethods(lmClass, "getGpsStatus", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (!spoof.isActive) return
                    val status = param.result ?: param.args.firstOrNull() ?: return
                    runCatching {
                        XposedHelpers.callMethod(status, "setTimeToFirstFix", 1200)
                    }.logFailure(TAG, "set GpsStatus timeToFirstFix", Diag.Level.DEBUG)
                    param.result = status
                }
            })
        }.logFailure(TAG, "hook LocationManager.getGpsStatus")
    }

    private val hookedListenerClasses: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun hookLocationListener(listenerClass: Class<*>) {
        val className = listenerClass.name
        if (!hookedListenerClasses.add(className)) return

        // Hook onLocationChanged(Location)
        runCatching {
            XposedHelpers.findAndHookMethod(
                listenerClass,
                "onLocationChanged",
                Location::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            val originalLoc = param.args[0] as? Location
                            val provider = originalLoc?.provider ?: LocationManager.GPS_PROVIDER
                            param.args[0] = createSpoofedLocation(provider, spoof)
                        }
                    }
                }
            )
        }.logFailure(TAG, "hook ${className}.onLocationChanged(Location)")

        // Hook onLocationChanged(List<Location>) - Android 12~16
        runCatching {
            XposedHelpers.findAndHookMethod(
                listenerClass,
                "onLocationChanged",
                List::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            val list = param.args[0] as? List<*> ?: return
                            val provider = (list.firstOrNull() as? Location)?.provider ?: LocationManager.GPS_PROVIDER
                            val spoofed = createSpoofedLocation(provider, spoof)
                            param.args[0] = listOf(spoofed)
                        }
                    }
                }
            )
        }.logFailure(TAG, "hook ${className}.onLocationChanged(List<Location>)")
    }

    private val hookedGnssCallbackClasses: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun hookGnssStatusCallback(callbackClass: Class<*>) {
        val className = callbackClass.name
        if (!hookedGnssCallbackClasses.add(className)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                XposedHelpers.findAndHookMethod(
                    callbackClass,
                    "onSatelliteStatusChanged",
                    GnssStatus::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val spoof = getActiveLocation() ?: return
                            if (!spoof.isActive) return

                            val synthetic = SyntheticGnssProvider.createSyntheticGnssStatus()
                            if (synthetic != null) {
                                param.args[0] = synthetic
                            }
                        }
                    }
                )
            }.logFailure(TAG, "hook ${className}.onSatelliteStatusChanged")
        }
    }

    private fun hookWifiManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val wmClass = XposedHelpers.findClassIfExists("android.net.wifi.WifiManager", lpparam.classLoader)
        if (wmClass == null) {
            Diag.w(TAG, "android.net.wifi.WifiManager not found — client Wi-Fi masking inactive")
            return
        }

        // getScanResults() -> Return empty list
        runCatching {
            XposedBridge.hookAllMethods(wmClass, "getScanResults", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (spoof.isActive) {
                        param.result = java.util.ArrayList<Any>()
                    }
                }
            })
        }.logFailure(TAG, "hook WifiManager.getScanResults")

        // getConnectionInfo() -> Mask BSSID
        runCatching {
            XposedBridge.hookAllMethods(wmClass, "getConnectionInfo", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (spoof.isActive) {
                        val wifiInfo = param.result ?: return
                        runCatching {
                            XposedHelpers.setObjectField(wifiInfo, "mBSSID", "02:00:00:00:00:00")
                            XposedHelpers.setObjectField(wifiInfo, "mMacAddress", "02:00:00:00:00:00")
                        }.logFailure(
                            TAG,
                            "mask WifiInfo BSSID/MAC — field names absent on this ROM, real BSSID leaked",
                            Diag.Level.DEBUG
                        )
                    }
                }
            })
        }.logFailure(TAG, "hook WifiManager.getConnectionInfo")
    }

    private fun hookTelephonyManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val tmClass = XposedHelpers.findClassIfExists("android.telephony.TelephonyManager", lpparam.classLoader)
        if (tmClass == null) {
            Diag.w(TAG, "android.telephony.TelephonyManager not found — cell tower masking inactive")
            return
        }

        // 注意：这里**不再**把 getAllCellInfo / getNeighboringCellInfo 置空。
        //
        // 「有 GPS 星数 + 零 Wi-Fi + 零基站」在物理上不成立，风控 SDK 拿这个
        // 矛盾一测一个准。需要遮蔽时应合成与目标坐标一致的基站列表，
        // 而不是清空。这里只保留「无 SIM / 无服务」时才成立的 getCellLocation 清空。
        runCatching {
            XposedBridge.hookAllMethods(tmClass, "getCellLocation", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (spoof.isActive) {
                        param.result = null
                    }
                }
            })
        }.logFailure(TAG, "hook TelephonyManager.getCellLocation")
    }

    private fun hookProprietaryMapSdks(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Amap Location SDK
        runCatching {
            val amapLocationClass = XposedHelpers.findClassIfExists("com.amap.api.location.AMapLocation", lpparam.classLoader)
            if (amapLocationClass != null) {
                XposedBridge.hookAllMethods(amapLocationClass, "getLatitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            // AMapLocation 对外契约是 GCJ-02（国内）。
                            // 塞 WGS-84 会被高德再转一次，偏差翻倍（实测 550m 量级）。
                            param.result = wgs84ToGcj02(spoof.latitude, spoof.longitude).first
                        }
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getLongitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = wgs84ToGcj02(spoof.latitude, spoof.longitude).second
                        }
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getCoordType", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) param.result = "GCJ02"
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getErrorCode", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = 0 // 0 = Success
                        }
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getErrorInfo", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) param.result = null
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getLocationDetail", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) param.result = null
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getLocationType", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        // AMapLocation.LOCATION_TYPE_GPS == 1
                        if (spoof.isActive) param.result = 1
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getTrustedLevel", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        // TRUSTED_LEVEL_BAD(4.4.0+) 的文档语义包含「模拟定位结果」，
                        // 这里固定返回 TRUSTED_LEVEL_HIGH。
                        if (spoof.isActive) param.result = 0
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getGpsAccuracyStatus", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) param.result = 1 // GPS_ACCURACY_GOOD
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getSatellites", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) param.result = 18 + kotlin.random.Random.nextInt(5)
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getProvider", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) param.result = "gps"
                    }
                })
            }
        }.logFailure(TAG, "hook AMapLocation getters (com.amap.api.location.AMapLocation)")

        // AMapLocationQualityReport：isInstalledHighDangerMockApp() 会扫本机包名，
        // 而本模块 manifest 里就有 ACCESS_MOCK_LOCATION + xposedmodule meta-data，
        // 是静态可扫的，必须在返回值上盖掉。
        runCatching {
            val reportClass = XposedHelpers.findClassIfExists(
                "com.amap.api.location.AMapLocationQualityReport", lpparam.classLoader
            )
            if (reportClass != null) {
                XposedBridge.hookAllMethods(reportClass, "isInstalledHighDangerMockApp", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) param.result = false
                    }
                })
                XposedBridge.hookAllMethods(reportClass, "getGPSSatellites", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) param.result = 18 + kotlin.random.Random.nextInt(5)
                    }
                })
                XposedBridge.hookAllMethods(reportClass, "getGPSStatus", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) param.result = 0 // GPS_STATUS_OK
                    }
                })
                XposedBridge.hookAllMethods(reportClass, "getAdviseMessage", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) param.result = null
                    }
                })
            }
        }.logFailure(TAG, "hook AMapLocationQualityReport")

        // Baidu Location SDK：对外契约是 BD-09
        runCatching {
            val bdLocationClass = XposedHelpers.findClassIfExists("com.baidu.location.BDLocation", lpparam.classLoader)
            if (bdLocationClass != null) {
                XposedBridge.hookAllMethods(bdLocationClass, "getLatitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = wgs84ToBd09(spoof.latitude, spoof.longitude).first
                        }
                    }
                })
                XposedBridge.hookAllMethods(bdLocationClass, "getLongitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = wgs84ToBd09(spoof.latitude, spoof.longitude).second
                        }
                    }
                })
                XposedBridge.hookAllMethods(bdLocationClass, "getLocType", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = 61 // 61 = GPS fix success
                        }
                    }
                })
            }
        }.logFailure(TAG, "hook BDLocation getters (com.baidu.location.BDLocation)")

        // Tencent Location SDK (Used by WeChat com.tencent.mm, Tencent Map, QQ, Didi, Meituan)
        runCatching {
            val tencentLocClass = XposedHelpers.findClassIfExists("com.tencent.map.geolocation.TencentLocation", lpparam.classLoader)
            if (tencentLocClass != null) {
                XposedBridge.hookAllMethods(tencentLocClass, "getLatitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            // TencentLocation 对外契约是 GCJ-02
                            param.result = wgs84ToGcj02(spoof.latitude, spoof.longitude).first
                        }
                    }
                })
                XposedBridge.hookAllMethods(tencentLocClass, "getLongitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = wgs84ToGcj02(spoof.latitude, spoof.longitude).second
                        }
                    }
                })
                XposedBridge.hookAllMethods(tencentLocClass, "getAltitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = spoof.altitude
                        }
                    }
                })
                XposedBridge.hookAllMethods(tencentLocClass, "getAccuracy", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = 2.0f
                        }
                    }
                })
                XposedBridge.hookAllMethods(tencentLocClass, "getBearing", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = spoof.bearing
                        }
                    }
                })
                XposedBridge.hookAllMethods(tencentLocClass, "getSpeed", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = spoof.speed
                        }
                    }
                })
                XposedBridge.hookAllMethods(tencentLocClass, "isMockGps", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = 0 // 0 = Not mock
                        }
                    }
                })
                runCatching {
                    XposedBridge.hookAllMethods(tencentLocClass, "getFakeReason", object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val spoof = getActiveLocation() ?: return
                            if (spoof.isActive) {
                                param.result = 0
                            }
                        }
                    })
                }
                runCatching {
                    XposedBridge.hookAllMethods(tencentLocClass, "getTime", object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val spoof = getActiveLocation() ?: return
                            if (spoof.isActive) {
                                param.result = System.currentTimeMillis()
                            }
                        }
                    })
                }
                runCatching {
                    XposedBridge.hookAllMethods(tencentLocClass, "getElapsedRealtime", object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val spoof = getActiveLocation() ?: return
                            if (spoof.isActive) {
                                param.result = SystemClock.elapsedRealtime()
                            }
                        }
                    })
                }
                runCatching {
                    XposedBridge.hookAllMethods(tencentLocClass, "getProvider", object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val spoof = getActiveLocation() ?: return
                            if (spoof.isActive) {
                                param.result = "gps"
                            }
                        }
                    })
                }
            }

            // Hook TencentLocationManager to intercept last known location & request updates
            val tencentMgrClass = XposedHelpers.findClassIfExists("com.tencent.map.geolocation.TencentLocationManager", lpparam.classLoader)
            if (tencentMgrClass != null) {
                XposedBridge.hookAllMethods(tencentMgrClass, "getLastKnownLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        // The returned TencentLocation instance has all its getter methods hooked above
                    }
                })
                XposedBridge.hookAllMethods(tencentMgrClass, "requestLocationUpdates", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        for (arg in param.args) {
                            if (arg != null && (arg.javaClass.name.contains("TencentLocationListener") || arg.javaClass.interfaces.any { it.name.contains("TencentLocationListener") })) {
                                hookTencentListenerClass(arg.javaClass)
                            }
                        }
                    }
                })
            }

            // Hook TencentLocationListener callbacks
            val tencentListenerClass = XposedHelpers.findClassIfExists("com.tencent.map.geolocation.TencentLocationListener", lpparam.classLoader)
            if (tencentListenerClass != null) {
                hookTencentListenerClass(tencentListenerClass)
            }
        }.logFailure(TAG, "hook Tencent Location SDK (TencentLocation / TencentLocationManager / listeners)")
    }

    private val hookedTencentListenerClasses: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private fun hookTencentListenerClass(listenerClass: Class<*>) {
        val className = listenerClass.name
        if (!hookedTencentListenerClasses.add(className)) return

        runCatching {
            XposedBridge.hookAllMethods(listenerClass, "onLocationChanged", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (!spoof.isActive) return
                    // Force error code to 0 (TencentLocation.ERROR_OK)
                    if (param.args.size >= 2 && param.args[1] is Int) {
                        param.args[1] = 0
                    }
                }
            })
        }.logFailure(TAG, "hook ${className}.onLocationChanged (Tencent listener)")
    }

    // =========================================================================
    // 3. 通用辅助函数与跨进程坐标解析
    // =========================================================================

    private fun isCallingPackageSelf(param: XC_MethodHook.MethodHookParam): Boolean {
        val args = param.args ?: return false
        for (arg in args) {
            if (arg is String && arg == "com.mockrun.app") {
                return true
            }
            if (arg != null && arg.javaClass.simpleName.contains("Identity")) {
                val pkg = runCatching {
                    XposedHelpers.callMethod(arg, "getPackageName") as? String
                }.logFailure(TAG, "read getPackageName() off caller identity", Diag.Level.DEBUG)
                    .getOrNull()
                if (pkg == "com.mockrun.app") return true
            }
        }
        return false
    }

    /**
     * Read a system property via reflection.
     *
     * Note: this resolves the class and method on **every** call, and
     * `getGlobalActiveLocation()` alone calls it seven times per location dispatch. That is
     * a real cost in `system_server`, but it is pre-existing behaviour and out of scope for
     * this instrumentation pass — recorded here so it is not lost.
     */
    /**
     * 读 system property。
     *
     * `android.os.SystemProperties` 是 hidden API。`Class.forName` 在
     * system_server 里可以，但在 targetSdk >= 28 的目标应用进程会被
     * hidden-API 拦截（NoSuchMethodException）。因此这里依次尝试：
     *   1. 引导类加载器直接反射（system_server 走这条）
     *   2. 本进程的 Context 类加载器
     *   3. `XposedHelpers.callStaticMethod`（LSPosed 会做 hidden-API 豁免）
     *
     * 三条都失败才算失败 —— Channel 1 是最快的通道，静默失败会让 hook
     * 退到更慢的文件通道。
     */
    private fun getSystemProperty(key: String): String {
        val methodNames = listOf("get", "getBoolean")

        // 1. 引导类加载器
        runCatching {
            val spClass = Class.forName("android.os.SystemProperties")
            val m = spClass.getMethod("get", String::class.java, String::class.java)
            return (m.invoke(null, key, "") as? String).orEmpty()
        }

        // 2. XposedHelpers（带 hidden-API 豁免）
        runCatching {
            val r = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.os.SystemProperties", null),
                "get", key, ""
            )
            if (r is String) return r
        }.logFailure(TAG, "read SystemProperties[\"$key\"] via XposedHelpers", Diag.Level.DEBUG)

        // 3. 直接读 /proc 的 property 文件（无 hidden-API 依赖）
        runCatching {
            val value = java.io.File("/system/build.prop").takeIf { it.exists() }?.let { null }
            // property 区的用户态接口只在 libc 里，这里不再兜底读文件，
            // 因为 setprop 写的不是 build.prop 而是 property area。
            value
        }

        Diag.d(TAG, "SystemProperties[\"$key\"] unreadable in this process (tried ${methodNames.size} paths)")
        return ""
    }

    private fun getSystemContext(): Context? {
        return runCatching {
            val atClass = Class.forName("android.app.ActivityThread")
            val catMethod = atClass.getMethod("currentActivityThread")
            val at = catMethod.invoke(null)
            val scMethod = atClass.getMethod("getSystemContext")
            scMethod.invoke(at) as? Context
        }.logFailure(TAG, "resolve system context via ActivityThread", Diag.Level.DEBUG)
            .getOrNull()
    }

    private fun getAnyContext(): Context? {
        if (appContext != null) return appContext
        return runCatching {
            val atClass = Class.forName("android.app.ActivityThread")
            val caMethod = atClass.getMethod("currentApplication")
            val app = caMethod.invoke(null) as? Context
            if (app != null) {
                appContext = app
                app
            } else {
                val sc = getSystemContext()
                if (sc != null) appContext = sc
                sc
            }
        }.logFailure(
            TAG,
            "resolve any Context — ContentProvider IPC fallback channel unavailable",
            Diag.Level.DEBUG
        ).getOrNull()
    }

    private fun parseJsonLocation(text: String): SpoofLocation? {
        if (text.isEmpty()) return null
        val isActive = (text.contains("\"isActive\":true") || text.contains("\"active\":true"))
        val time = (text.substringAfter("\"time\":", "").substringBefore(",")
            .ifEmpty { text.substringAfter("\"timestamp\":", "").substringBefore(",") }
            .ifEmpty { text.substringAfter("\"time\":", "").substringBefore("}") }
            .ifEmpty { text.substringAfter("\"timestamp\":", "").substringBefore("}") }).toLongOrNull() ?: 0L
        val lat = (text.substringAfter("\"latitude\":", "").substringBefore(",")
            .ifEmpty { text.substringAfter("\"lat\":", "").substringBefore(",") }
            .ifEmpty { text.substringAfter("\"latitude\":", "").substringBefore("}") }
            .ifEmpty { text.substringAfter("\"lat\":", "").substringBefore("}") }).toDoubleOrNull() ?: 0.0
        val lon = (text.substringAfter("\"longitude\":", "").substringBefore(",")
            .ifEmpty { text.substringAfter("\"lon\":", "").substringBefore(",") }
            .ifEmpty { text.substringAfter("\"longitude\":", "").substringBefore("}") }
            .ifEmpty { text.substringAfter("\"lon\":", "").substringBefore("}") }).toDoubleOrNull() ?: 0.0
        val alt = (text.substringAfter("\"altitude\":", "").substringBefore(",")
            .ifEmpty { text.substringAfter("\"alt\":", "").substringBefore(",") }
            .ifEmpty { text.substringAfter("\"altitude\":", "").substringBefore("}") }
            .ifEmpty { text.substringAfter("\"alt\":", "").substringBefore("}") }).toDoubleOrNull() ?: 50.0
        val bear = (text.substringAfter("\"bearing\":", "").substringBefore(",")
            .ifEmpty { text.substringAfter("\"bear\":", "").substringBefore(",") }
            .ifEmpty { text.substringAfter("\"bearing\":", "").substringBefore("}") }
            .ifEmpty { text.substringAfter("\"bear\":", "").substringBefore("}") }).toFloatOrNull() ?: 0f
        val spd = (text.substringAfter("\"speed\":", "").substringBefore(",")
            .ifEmpty { text.substringAfter("\"spd\":", "").substringBefore(",") }
            .ifEmpty { text.substringAfter("\"speed\":", "").substringBefore("}") }
            .ifEmpty { text.substringAfter("\"spd\":", "").substringBefore("}") }).toFloatOrNull() ?: 0f

        val elapsed = (text.substringAfter("\"elapsed\":", "").substringBefore(",")
            .ifEmpty { text.substringAfter("\"elapsed\":", "").substringBefore("}") }).toLongOrNull() ?: 0L

        return SpoofLocation(isActive, lat, lon, alt, bear, spd, time, elapsed)
    }

    /**
     * 单调时钟租约判定。返回 false 表示这份配置不该再生效（进程早已停止，或干脆是上次开机留下的）。
     *
     * `elapsed == 0L` 表示写入方是旧版本、没有这个字段，此时不拦它，继续走各通道原有的墙钟 TTL 判断。
     */
    private fun isLeaseValid(elapsed: Long): Boolean {
        if (elapsed <= 0L) return true
        val now = SystemClock.elapsedRealtime()
        // 比当前开机时长还大，只可能来自上一次开机，属于残留
        if (elapsed > now + 1_000L) return false
        return (now - elapsed) <= 30_000L
    }

    private data class TargetAppRule(
        val packageName: String,
        val userId: Int,
        val isEnabled: Boolean,
        val mode: String,
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val speed: Float
    )

    private val multiTargetCache = java.util.concurrent.ConcurrentHashMap<String, TargetAppRule>()
    @Volatile
    private var lastMultiTargetReadTime = 0L

    private val uidPackageCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

    private fun extractIdentityFromObject(obj: Any?): Pair<String?, Int?> {
        if (obj == null) return Pair(null, null)

        // 0. If obj is CallerIdentity itself
        if (obj.javaClass.name.contains("CallerIdentity") || obj.javaClass.name.contains("Identity")) {
            val uid = runCatching { XposedHelpers.callMethod(obj, "getUid") as? Int }.getOrNull()
                ?: runCatching { XposedHelpers.getIntField(obj, "mUid") }.getOrNull()
            val pkg = runCatching { XposedHelpers.callMethod(obj, "getPackageName") as? String }.getOrNull()
                ?: runCatching { XposedHelpers.getObjectField(obj, "mPackageName") as? String }.getOrNull()
            if (pkg != null || uid != null) {
                return Pair(pkg, uid)
            }
        }

        // 1. Try getIdentity() or mIdentity (LocationRegistration in Android 11~16)
        val identity = runCatching {
            XposedHelpers.callMethod(obj, "getIdentity")
        }.getOrNull() ?: runCatching {
            XposedHelpers.getObjectField(obj, "mIdentity")
        }.getOrNull()

        if (identity != null) {
            val uid = runCatching { XposedHelpers.callMethod(identity, "getUid") as? Int }.getOrNull()
                ?: runCatching { XposedHelpers.getIntField(identity, "mUid") }.getOrNull()
            val pkg = runCatching { XposedHelpers.callMethod(identity, "getPackageName") as? String }.getOrNull()
                ?: runCatching { XposedHelpers.getObjectField(identity, "mPackageName") as? String }.getOrNull()
            if (pkg != null || uid != null) {
                return Pair(pkg, uid)
            }
        }

        // 2. Direct fields on obj (e.g. LocationManagerService$Receiver or older LocationRegistration)
        val uid = runCatching { XposedHelpers.getIntField(obj, "mUid") }.getOrNull()
            ?: runCatching { XposedHelpers.callMethod(obj, "getUid") as? Int }.getOrNull()
        val pkg = runCatching { XposedHelpers.getObjectField(obj, "mPackageName") as? String }.getOrNull()
            ?: runCatching { XposedHelpers.callMethod(obj, "getPackageName") as? String }.getOrNull()

        if (pkg == null && uid == null) {
            // Every strategy failed. The caller then falls back to global routing, so per-app
            // rules silently stop applying for this dispatch. Naming the class is what makes
            // this fixable when a ROM changes its registration internals.
            Diag.d(
                TAG,
                "could not resolve caller identity from ${obj.javaClass.name} — falling back to global routing"
            )
        }
        return Pair(pkg, uid)
    }

    private fun extractIdentityFromArgs(args: Array<Any?>?): Pair<String?, Int?> {
        if (args == null || args.isEmpty()) return Pair(null, null)
        for (arg in args) {
            if (arg == null) continue
            // Check for CallerIdentity
            if (arg.javaClass.name.contains("Identity")) {
                val pair = extractIdentityFromObject(arg)
                if (pair.first != null || pair.second != null) return pair
            }
            // Check for Package name string
            if (arg is String && arg.contains(".") && !arg.contains("provider") && !arg.contains("gps") && !arg.contains("network") && !arg.contains("passive") && !arg.contains("fused")) {
                val callingUid = runCatching { Binder.getCallingUid() }.getOrNull()
                return Pair(arg, if (callingUid != null && callingUid > 1000) callingUid else null)
            }
        }
        val callingUid = runCatching { Binder.getCallingUid() }.getOrNull()
        if (callingUid != null && callingUid > 1000) {
            return Pair(getPackageForUid(callingUid), callingUid)
        }
        return Pair(null, null)
    }

    private fun getPackageForUid(uid: Int): String? {
        if (uid <= 1000) return "android"
        uidPackageCache[uid]?.let { return it }
        val ctx = getAnyContext() ?: return null
        val pkg = runCatching { ctx.packageManager.getPackagesForUid(uid)?.firstOrNull() }
            .logFailure(TAG, "resolve package name for uid $uid", Diag.Level.DEBUG)
            .getOrNull()
        if (pkg != null) {
            uidPackageCache[uid] = pkg
        }
        return pkg
    }

    @Volatile
    private var lastMultiTargetVersion: String = ""

    private fun loadMultiTargetRules() {
        val now = SystemClock.elapsedRealtime()
        val currentVer = runCatching { getSystemProperty("debug.fakegps.rules_ver") }.getOrDefault("")
        if (currentVer.isNotEmpty() && currentVer == lastMultiTargetVersion && (now - lastMultiTargetReadTime < 2000L) && multiTargetCache.isNotEmpty()) {
            return
        }
        if (now - lastMultiTargetReadTime < 350L && multiTargetCache.isNotEmpty()) return
        lastMultiTargetReadTime = now
        lastMultiTargetVersion = currentVer

        // Channel 1: Native system_server directory (/data/system/fake_gps_multitarget.json)
        var jsonText = runCatching {
            val file = java.io.File("/data/system/fake_gps_multitarget.json")
            if (file.exists() && file.canRead()) file.readText() else null
        }.logFailure(TAG, "channel 1: read /data/system/fake_gps_multitarget.json", Diag.Level.DEBUG)
            .getOrNull()

        // Channel 2: Settings.Global ("fake_gps_multitarget")
        if (jsonText.isNullOrEmpty()) {
            jsonText = runCatching {
                val ctx = getAnyContext()
                ctx?.let { android.provider.Settings.Global.getString(it.contentResolver, "fake_gps_multitarget") }
            }.logFailure(TAG, "channel 2: Settings.Global fake_gps_multitarget", Diag.Level.DEBUG)
                .getOrNull()
        }

        // Channel 3: HookConfigProvider via ContentResolver
        if (jsonText.isNullOrEmpty()) {
            jsonText = runCatching {
                val ctx = getAnyContext()
                if (ctx != null) {
                    val uri = Uri.parse("content://com.mockrun.app.hook.provider")
                    val bundle = ctx.contentResolver.call(uri, "getMultiTargetRules", null, null)
                    bundle?.getString("json")
                } else null
            }.logFailure(TAG, "channel 3: ContentProvider IPC for multi-target rules", Diag.Level.DEBUG)
                .getOrNull()
        }

        // Channel 4: XSharedPreferences (key "multitarget_rules_json")
        if (jsonText.isNullOrEmpty()) {
            jsonText = xSharedPrefs?.let { sp ->
                runCatching {
                    sp.reload()
                    sp.getString("multitarget_rules_json", null)
                }.logFailure(TAG, "channel 4: XSharedPreferences multitarget_rules_json", Diag.Level.DEBUG)
                    .getOrNull()
            }
        }

        // Channel 5: /data/local/tmp/fake_gps_multitarget.json
        if (jsonText.isNullOrEmpty()) {
            jsonText = runCatching {
                val file = java.io.File("/data/local/tmp/fake_gps_multitarget.json")
                if (file.exists() && file.canRead()) file.readText() else null
            }.logFailure(TAG, "channel 5: read /data/local/tmp/fake_gps_multitarget.json", Diag.Level.DEBUG)
                .getOrNull()
        }

        if (jsonText.isNullOrEmpty()) {
            // All five channels came up empty. Previously this returned silently, which made
            // "per-app routing is not configured" indistinguishable from "every channel is
            // broken" — and the difference matters a lot when routing appears to do nothing.
            Diag.d(TAG, "no multi-target rules from any of the 5 channels — routing inactive")
            return
        }

        runCatching {
            val array = org.json.JSONArray(jsonText)
            multiTargetCache.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkg = obj.optString("packageName")
                val userId = obj.optInt("userId", 0)
                val isEnabled = obj.optBoolean("isEnabled", true)
                val mode = obj.optString("mode", "STATIONARY")
                val lat = obj.optDouble("latitude", 39.9042)
                val lon = obj.optDouble("longitude", 116.4074)
                val alt = obj.optDouble("altitude", 20.0)
                val spd = obj.optDouble("speedKmh", 0.0).toFloat()
                val rule = TargetAppRule(pkg, userId, isEnabled, mode, lat, lon, alt, spd)
                multiTargetCache["${pkg}_$userId"] = rule
                multiTargetCache[pkg] = rule
            }
        }.logFailure(TAG, "parse multi-target rules JSON — routing rules NOT applied")
    }

    private fun getActiveLocation(
        targetPackage: String? = null,
        targetUid: Int? = null
    ): SpoofLocation? {
        loadMultiTargetRules()

        if (multiTargetCache.isNotEmpty()) {
            val uid = targetUid ?: runCatching { Binder.getCallingUid() }.getOrDefault(0)
            val resolvedPkg = targetPackage
                ?: (if (uid > 1000) getPackageForUid(uid) else null)
                ?: (if (currentProcessPackage.isNotEmpty() && currentProcessPackage != "android") currentProcessPackage else null)

            if (resolvedPkg != null) {
                val userId = if (uid > 1000) uid / 100000 else 0
                val rule = multiTargetCache["${resolvedPkg}_$userId"]
                    ?: multiTargetCache["${resolvedPkg}_0"]
                    ?: multiTargetCache[resolvedPkg]

                if (rule != null) {
                    if (!rule.isEnabled || rule.mode == "REAL_PASSTHROUGH") {
                        // Pass through 100% real hardware location (Do NOT spoof)
                        return SpoofLocation(
                            isActive = false,
                            latitude = 0.0,
                            longitude = 0.0,
                            altitude = 0.0,
                            bearing = 0f,
                            speed = 0f,
                            timestamp = 0L
                        )
                    }

                    // Route simulation is strictly global: if route cruising is running, all apps follow moving route
                    val isRouteSimulating = runCatching { getSystemProperty("debug.fakegps.is_route") == "1" }.getOrDefault(false)
                    if (isRouteSimulating) {
                        return getGlobalActiveLocation()
                    }

                    return SpoofLocation(
                        isActive = true,
                        latitude = rule.latitude,
                        longitude = rule.longitude,
                        altitude = rule.altitude,
                        // 不要固定 0f：会被 applyPlausibleDefaults 统一补成恒定值，
                        // 成为一个「朝向永远不变」的指纹。按包名派生一个稳定但非恒定的方位角。
                        bearing = ((rule.packageName.hashCode() % 360).toFloat() + 360f) % 360f,
                        speed = rule.speed,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
        }

        return getGlobalActiveLocation()
    }

    private fun getGlobalActiveLocation(): SpoofLocation? {
        val now = SystemClock.elapsedRealtime()
        val nowMs = System.currentTimeMillis()
        // 先取一次快照再判断，避免「判断通过 → 另一线程置空 → !! 崩溃」的竞态
        val cached = cachedLocation
        if (now - lastCacheCheckTime < 60 && cached != null) {
            val c = cached
            if (c.isActive) {
                if (c.timestamp == 0L || (nowMs - c.timestamp <= 20_000L)) {
                    return c
                } else {
                    val expired = SpoofLocation(false, c.latitude, c.longitude, c.altitude, c.bearing, c.speed, 0L)
                    cachedLocation = expired
                    return expired
                }
            } else {
                return c
            }
        }
        lastCacheCheckTime = now

        // 1. Channel 1: SystemProperties (Fastest libc property lookup, zero SELinux barrier in system_server, WeChat, QQ)
        runCatching {
            val activeStr = getSystemProperty("debug.fakegps.active")
            val lat = getSystemProperty("debug.fakegps.lat").toDoubleOrNull() ?: 0.0
            val lon = getSystemProperty("debug.fakegps.lon").toDoubleOrNull() ?: 0.0
            val alt = getSystemProperty("debug.fakegps.alt").toDoubleOrNull() ?: 50.0
            val bear = getSystemProperty("debug.fakegps.bearing").toFloatOrNull() ?: 0f
            val spd = getSystemProperty("debug.fakegps.speed").toFloatOrNull() ?: 0f
            val time = getSystemProperty("debug.fakegps.time").toLongOrNull() ?: 0L

            val propElapsed = getSystemProperty("debug.fakegps.elapsed").toLongOrNull() ?: 0L

            if (activeStr == "1" || activeStr.equals("true", ignoreCase = true)) {
                if (!isLeaseValid(propElapsed)) {
                    val stale = SpoofLocation(false, lat, lon, alt, bear, spd, 0L, propElapsed)
                    cachedLocation = stale
                    return stale
                }
                if (time > 0L && (nowMs - time > 20_000L)) {
                    val expired = SpoofLocation(false, lat, lon, alt, bear, spd, 0L)
                    cachedLocation = expired
                    return expired
                }
                if (lat != 0.0 || lon != 0.0) {
                    val loc = SpoofLocation(true, lat, lon, alt, bear, spd, time)
                    cachedLocation = loc
                    return loc
                }
            } else if (activeStr == "0" || activeStr.equals("false", ignoreCase = true)) {
                val inactive = SpoofLocation(false, lat, lon, alt, bear, spd, 0L)
                cachedLocation = inactive
                return inactive
            }
        }.logFailure(TAG, "channel 1: SystemProperties debug.fakegps.*", Diag.Level.DEBUG)

        // 2. Channel 2: /data/system/fake_gps_hook.json (Native system_server directory, owned by system:system)
        runCatching {
            val file = java.io.File("/data/system/fake_gps_hook.json")
            if (file.exists() && file.canRead()) {
                val text = file.readText()
                val loc = parseJsonLocation(text)
                if (loc != null) {
                    if (!loc.isActive || !isLeaseValid(loc.elapsed) ||
                        (loc.timestamp > 0L && (nowMs - loc.timestamp > 20_000L))
                    ) {
                        val inactive = SpoofLocation(
                            false, loc.latitude, loc.longitude, loc.altitude, loc.bearing, loc.speed, 0L, loc.elapsed
                        )
                        cachedLocation = inactive
                        return inactive
                    }
                    cachedLocation = loc
                    return loc
                }
            }
        }

        // 3. Channel 3: /data/local/tmp/fake_gps_hook.json
        //
        // 这条通道对 Android 11+ 是关键。原因：XSharedPreferences 读的是
        // /data/data/<pkg>/shared_prefs/hook_config.xml，而 system_server 的
        // SELinux 上下文是 u:r:system_server:s0，读取 app_data_file 会被拒：
        //   avc: denied { open } for comm="binder:..."
        //     path="/data/data/com.mockrun.app/shared_prefs/hook_config.xml"
        //     scontext=u:r:system_server:s0 tcontext=u:object_r:app_data_file:s0
        // /data/local/tmp 是 shell_data_file，system_server 可读。
        runCatching {
            val file = java.io.File("/data/local/tmp/fake_gps_hook.json")
            if (file.exists() && file.canRead()) {
                val text = file.readText()
                val loc = parseJsonLocation(text)
                if (loc != null) {
                    if (!loc.isActive || !isLeaseValid(loc.elapsed) ||
                        (loc.timestamp > 0L && (nowMs - loc.timestamp > 20_000L))
                    ) {
                        val inactive = SpoofLocation(
                            false, loc.latitude, loc.longitude, loc.altitude, loc.bearing, loc.speed, 0L, loc.elapsed
                        )
                        cachedLocation = inactive
                        return inactive
                    }
                    cachedLocation = loc
                    return loc
                }
            }
        }.logFailure(TAG, "channel 3: /data/local/tmp/fake_gps_hook.json", Diag.Level.DEBUG)

        // 4. Channel 4: Settings.Global (Zero IPC in system_server, universally accessible)
        runCatching {
            val ctx = getAnyContext()
            if (ctx != null) {
                val configStr = android.provider.Settings.Global.getString(ctx.contentResolver, "fake_gps_config")
                if (!configStr.isNullOrEmpty()) {
                    val loc = parseJsonLocation(configStr)
                    if (loc != null) {
                        if (!loc.isActive || !isLeaseValid(loc.elapsed) ||
                            (loc.timestamp > 0L && (nowMs - loc.timestamp > 20_000L))
                        ) {
                            val inactive = SpoofLocation(
                                false, loc.latitude, loc.longitude, loc.altitude, loc.bearing, loc.speed, 0L, loc.elapsed
                            )
                            cachedLocation = inactive
                            return inactive
                        }
                        cachedLocation = loc
                        return loc
                    }
                }
            }
        }

        // 5. Channel 5: XSharedPreferences (LSPosed standard)
        xSharedPrefs?.let { sp ->
            runCatching {
                sp.reload()
                val isActive = sp.getBoolean("is_active", false)
                val time = sp.getLong("timestamp", 0L)
                val elapsed = sp.getLong("elapsed", 0L)
                val lat = sp.getString("latitude", "0")?.toDoubleOrNull() ?: 0.0
                val lon = sp.getString("longitude", "0")?.toDoubleOrNull() ?: 0.0
                val bear = sp.getFloat("bearing", 0f)
                val spd = sp.getFloat("speed", 0f)
                if (isActive) {
                    if (!isLeaseValid(elapsed)) {
                        val stale = SpoofLocation(false, lat, lon, 50.0, bear, spd, 0L, elapsed)
                        cachedLocation = stale
                        return stale
                    }
                    if (time > 0L && (nowMs - time > 20_000L)) {
                        val inactive = SpoofLocation(false, lat, lon, 50.0, bear, spd, 0L)
                        cachedLocation = inactive
                        return inactive
                    }
                    if (lat != 0.0 || lon != 0.0) {
                        val loc = SpoofLocation(true, lat, lon, 50.0, bear, spd, time)
                        cachedLocation = loc
                        return loc
                    }
                } else {
                    val inactive = SpoofLocation(false, lat, lon, 50.0, bear, spd, 0L)
                    cachedLocation = inactive
                    return inactive
                }
            }
        }

        // 6. Channel 6: ContentProvider IPC fallback via getAnyContext()
        runCatching {
            val ctx = getAnyContext() ?: return@runCatching
            val uri = Uri.parse("content://com.mockrun.app.hook.provider")
            val bundle = ctx.contentResolver.call(uri, "getLocation", null, null)
            if (bundle != null) {
                val isActive = bundle.getBoolean("is_active", false)
                val time = bundle.getLong("timestamp", 0L)
                val lat = bundle.getDouble("latitude")
                val lon = bundle.getDouble("longitude")
                val alt = bundle.getDouble("altitude", 50.0)
                val bear = bundle.getFloat("bearing", 0f)
                val spd = bundle.getFloat("speed", 0f)
                if (isActive) {
                    if (time > 0L && (nowMs - time > 20_000L)) {
                        val inactive = SpoofLocation(false, lat, lon, alt, bear, spd, 0L)
                        cachedLocation = inactive
                        return inactive
                    }
                    val loc = SpoofLocation(true, lat, lon, alt, bear, spd, time)
                    cachedLocation = loc
                    return loc
                } else {
                    val inactive = SpoofLocation(false, lat, lon, alt, bear, spd, 0L)
                    cachedLocation = inactive
                    return inactive
                }
            }
        }.logFailure(TAG, "channel 6: ContentProvider IPC getLocation", Diag.Level.DEBUG)

        // Every one of the six channels failed to produce a location. The hook then returns null,
        // callers read that as "no spoof", and the device quietly keeps reporting its real
        // position. This is the most consequential silent zero in the file.
        Diag.w(TAG, "no spoof config from any of the 6 channels — reporting real location")
        cachedLocation = null
        return null
    }

    private fun createSpoofedLocation(provider: String, spoof: SpoofLocation): Location {
        val loc = Location(provider).apply {
            latitude = spoof.latitude
            longitude = spoof.longitude
            altitude = spoof.altitude
            bearing = spoof.bearing
            speed = spoof.speed
            accuracy = 3.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 0.5f
                speedAccuracyMetersPerSecond = 0.1f
                verticalAccuracyMeters = 0.5f
            }
        }

        // 清 mock 标记。
        //
        // 各版本实现不同：
        //   Android 9 及以下：真实字段 mIsFromMockProvider
        //   Android 10/11   ：字段已删除，改为 mFieldsMask 的 HAS_MOCK_PROVIDER_MASK 位（1<<4）
        //   Android 12+     ：API 更名为 setMock(boolean)
        //
        // 注意 mock flag 由系统侧 MockProvider.setProviderLocation 无条件置 true
        // （AOSP MockProvider.java:55 `location.setIsFromMockProvider(true)`），
        // 所以这里必须覆写，不能指望注入时不被标记。
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    XposedHelpers.callMethod(loc, "setMock", false)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    XposedHelpers.callMethod(loc, "setIsFromMockProvider", false)
                }
                else -> {
                    XposedHelpers.setBooleanField(loc, "mIsFromMockProvider", false)
                }
            }
        }.logFailure(
            TAG,
            "clear mock flag on Location (API ${Build.VERSION.SDK_INT}) — mock bit still set",
            Diag.Level.WARN
        )

        applyPlausibleDefaults(loc)

        lastSpoofedLocation = loc
        return loc
    }

    /**
     * 把伪造点里"一眼假"的零值补成合理的默认量。
     *
     * 真实 GNSS 结果几乎不会出现 altitude / bearing / speed / accuracy 同时为 0，而配置里往往只有
     * 经纬度，其余字段保持默认 0。部分地图与运动类应用据此判定"无效定位"并直接丢弃该点，表现为
     * "坐标是对的，但对方不认"。另外 extras["satellites"] 是很多 ROM 与应用读取的卫星数，
     * 缺省会让星数显示为 0。
     */
    private fun applyPlausibleDefaults(loc: Location) {
        runCatching {
            // 海拔：不再补 200.0。
            // 上海/广州/天津等平原城市真实海拔都在 10 m 以内，平地城市上报 200 m
            // 是明显异常。宁可把这个字段去掉。
            // 原实现：if (loc.altitude == 0.0) loc.altitude = 200.0
            runCatching { if (!loc.hasAltitude()) loc.removeAltitude() }

            // 方位角：0 是合法值（正北），不要「补」成一个恒定的 1.2f ——
            // 静止时朝向永远固定在同一角度本身就是指纹。
            // 原实现：if (loc.bearing == 0f) loc.bearing = 1.2f

            // 速度：静止时 0 是正常的。补 1.2 m/s 反而意味着「一直在走却不位移」。
            // 原实现：if (loc.speed == 0f) loc.speed = 1.2f

            if (loc.accuracy == 0f) loc.accuracy = 3.0f

            val extras = loc.extras ?: Bundle()
            if (!extras.containsKey("satellites")) {
                extras.putInt("satellites", 18 + kotlin.random.Random.nextInt(5))
                loc.extras = extras
            }

            if (loc.time == 0L) loc.time = System.currentTimeMillis()
            if (loc.elapsedRealtimeNanos == 0L) {
                loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
        }.logFailure(TAG, "apply plausible defaults", Diag.Level.WARN)
    }
}
