package com.mockrun.app.hook

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
// 坐标系转换
//
// 为什么 hook 侧要转：
//   AMapLocation  对外契约是 GCJ-02（国内），BDLocation 是 BD-09，
//   TencentLocation 是 GCJ-02。而 mock 注入的系统 Location 是 WGS-84。
//   直接塞 WGS-84 会被 SDK 再转一次，偏差被放大一倍。
//
// 算法本体**只有一份**：com.mockrun.app.util.GeoTransform（纯函数、无 Android/Xposed 依赖）。
// 旧实现把同一套公式在本文件里又抄了一遍，而文件私有的函数无法被单元测试覆盖 ——
// 两份实现一旦漂移（少一个 isInfinite 判断、常数抄错），注入坐标与地图显示就会不一致，
// 且没有任何测试能发现。这里只保留两个转发函数，公式一律走 GeoTransform。
// ============================================================================

/** WGS-84 -> GCJ-02（高德 / 腾讯 SDK 对外契约）。 */
private fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> =
    com.mockrun.app.util.GeoTransform.wgs84ToGcj02(lat, lon)

/** WGS-84 -> BD-09（百度 SDK 对外契约）。 */
private fun wgs84ToBd09(lat: Double, lon: Double): Pair<Double, Double> =
    com.mockrun.app.util.GeoTransform.wgs84ToBd09(lat, lon)

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
        // 这两条必须在 runCatching 里：任一抛异常会中断 handleLoadPackage，
        // 导致整个模块在 system_server 里"静默不加载"（真机症状：LSPosed 日志零记录）。
        runCatching { hookLocationProviderManager(lpparam) }
            .onFailure { Diag.w(TAG, "hookLocationProviderManager failed: ${'$'}{it.javaClass.simpleName}: ${'$'}{it.message}") }
        runCatching { hookLmsDispatchers(lpparam) }
        runCatching { hookProviderReportLayer(lpparam) }
            .onFailure { Diag.w(TAG, "hookLmsDispatchers failed: ${'$'}{it.javaClass.simpleName}: ${'$'}{it.message}") }

        // 1.2 Hook LocationManagerService (All Android versions)
        hookLocationManagerService(lpparam)
        // 这两条此前是死代码（有定义、无调用）：Provider 层改写与 Registration 分发钩子
        // 从未安装，所以只有 getLastLocation 一次性查询被改写 —— 表现为"首次跳到假点、之后不跟随"。

        // 1.3 Hook LMS Listener Dispatchers (Receiver / LocationRegistration)

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
                        // 旧实现在这里「只要存在任何分流规则就整体 return」，把 provider 层的替换全部关掉：
                        // 分流能否生效就完全押在 Registration 分发钩子上 —— 钩子没落上（ROM 改名/混淆）、
                        // 或身份解析失败时，**整台设备所有应用**都会退回真实位置；
                        // 而且一条「暂停(REAL_PASSTHROUGH)」的规则同样会填进 multiTargetCache，连全局模拟都被关掉。
                        // 现在照常按全局配置替换：per-app 规则仍由分发钩子（那里能拿到 uid/包名）覆盖成该应用自己的点，
                        // 没有规则命中的应用按产品设计拿到全局点；全局模拟未开启时这里什么都不做。
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
                        // 同 onReportLocation：不再因为「存在分流规则」而整体关掉这一层的替换。
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
                        // 同 onReportLocation：不再因为「存在分流规则」而整体关掉这一层的替换。
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
        // SensorManager client hooks (only for apps explicitly added to LSPosed scope)
        hookSensorClient(lpparam)

        // LocationManager client API hooks
        hookLocationManager(lpparam)

        // WifiManager client hooks
        hookWifiManager(lpparam)

        // TelephonyManager client hooks
        hookTelephonyManager(lpparam)

        // Map SDK hooks (AMap / Baidu)
        hookProprietaryMapSdks(lpparam)
    }

    /**
     * 只对显式加入 LSPosed scope 的目标应用生效。
     *
     * Android 的 SensorService 是 native 的，system_server 的 Java hook 无法拦截每个
     * SensorEvent；因此在目标应用进程里包装 SensorEventListener，把步数/加速度换成
     * Root 侧发布的合成值。
     */
    private val sensorListenerProxies: MutableMap<SensorEventListener, SyntheticSensorListener> =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap())

    private fun hookSensorClient(lpparam: XC_LoadPackage.LoadPackageParam) {
        val smClass = XposedHelpers.findClassIfExists(
            "android.hardware.SensorManager", lpparam.classLoader
        ) ?: return

        runCatching {
            XposedBridge.hookAllMethods(smClass, "registerListener", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val listener = param.args.firstOrNull { it is SensorEventListener } as? SensorEventListener
                        ?: return
                    val sensor = param.args.firstOrNull { it is Sensor } as? Sensor ?: return
                    if (listener is SyntheticSensorListener) return
                    val proxy = sensorListenerProxies[listener]
                        ?: SyntheticSensorListener(listener).also {
                            sensorListenerProxies[listener] = it
                        }
                    if (sensor.type == Sensor.TYPE_STEP_COUNTER) {
                        proxy.attachStepSensor(sensor)
                    }
                    for (i in param.args.indices) {
                        if (param.args[i] === listener) param.args[i] = proxy
                    }
                }
            })
        }.logFailure(TAG, "hook SensorManager.registerListener")

        runCatching {
            XposedBridge.hookAllMethods(smClass, "unregisterListener", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val listener = param.args.firstOrNull { it is SensorEventListener } as? SensorEventListener
                        ?: return
                    val proxy = sensorListenerProxies.remove(listener) ?: return
                    proxy.stopStepTicker()
                    for (i in param.args.indices) {
                        if (param.args[i] === listener) param.args[i] = proxy
                    }
                }
            })
        }.logFailure(TAG, "hook SensorManager.unregisterListener")
    }

    private inner class SyntheticSensorListener(
        private val delegate: SensorEventListener
    ) : SensorEventListener {
        private var cachedAt = 0L
        private var active = false
        private var steps = 0f
        private var accelZ = 9.8f
        private var stepSensor: Sensor? = null
        private val handler = Handler(Looper.getMainLooper())
        private val stepTicker = object : Runnable {
            override fun run() {
                emitStep()
                if (stepSensor != null) handler.postDelayed(this, 1000L)
            }
        }

        fun attachStepSensor(sensor: Sensor) {
            if (stepSensor == null) {
                stepSensor = sensor
                handler.postDelayed(stepTicker, 500L)
            }
        }

        fun stopStepTicker() {
            handler.removeCallbacks(stepTicker)
            stepSensor = null
        }

        private fun refresh() {
            val now = SystemClock.elapsedRealtime()
            if (now - cachedAt < 500L) return
            cachedAt = now
            val publishedAt = getSystemProperty("debug.fakegps.sensor_time").toLongOrNull() ?: 0L
            active = getSystemProperty("debug.fakegps.sensor_active") == "1" &&
                publishedAt > 0L && System.currentTimeMillis() - publishedAt in 0..5_000L
            steps = getSystemProperty("debug.fakegps.sensor_steps").toFloatOrNull() ?: 0f
            accelZ = getSystemProperty("debug.fakegps.sensor_accel").toFloatOrNull() ?: 9.8f
        }

        private fun emitStep() {
            refresh()
            val sensor = stepSensor ?: return
            if (!active) return
            runCatching {
                // SensorEvent(int) 是 package-private；LSPosed 进程里用 XposedHelpers
                // 走隐藏构造器，避免直接编译引用。
                val event = XposedHelpers.newInstance(SensorEvent::class.java, 1) as SensorEvent
                event.sensor = sensor
                event.values[0] = steps
                event.timestamp = SystemClock.elapsedRealtimeNanos()
                event.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                delegate.onSensorChanged(event)
            }.logFailure(TAG, "emit synthetic STEP_COUNTER", Diag.Level.DEBUG)
        }

        override fun onSensorChanged(event: SensorEvent) {
            refresh()
            // STEP_COUNTER 硬件通常只在真实步伐时出事件；激活时由 ticker 主动合成，
            // 这里丢弃原事件，避免硬件 0 与合成值交替。
            if (active && event.sensor.type == Sensor.TYPE_STEP_COUNTER) return
            if (active && event.values != null) {
                // 同一个 listener 往往同时注册 Step 和 Accel，不能按注册时的类型判；
                // 必须看这条事件本身的 sensor type。
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        event.values[0] = 0f
                        event.values[1] = 0f
                        event.values[2] = accelZ
                    }
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        event.values[0] = 0f
                        event.values[1] = 0f
                        event.values[2] = (accelZ - 9.8f)
                    }
                    Sensor.TYPE_GRAVITY -> {
                        event.values[0] = 0f
                        event.values[1] = 0f
                        event.values[2] = 9.8f
                    }
                }
            }
            delegate.onSensorChanged(event)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            delegate.onAccuracyChanged(sensor, accuracy)
        }
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
        // NR (5G). 缺失时 buildCellInfoList 会跳过 NR 条目并记日志，不影响 LTE/GSM/WCDMA。
        val cellInfoNrClass = XposedHelpers.findClassIfExists("android.telephony.CellInfoNr", lpparam.classLoader)
        val cellIdentityNrClass = XposedHelpers.findClassIfExists("android.telephony.CellIdentityNr", lpparam.classLoader)
        val cellSignalNrClass = XposedHelpers.findClassIfExists("android.telephony.CellSignalStrengthNr", lpparam.classLoader)

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
                            cellInfoWcdmaClass, cellIdentityWcdmaClass, cellSignalWcdmaClass,
                            cellInfoNrClass, cellIdentityNrClass, cellSignalNrClass
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
                            cellInfoWcdmaClass, cellIdentityWcdmaClass, cellSignalWcdmaClass,
                            cellInfoNrClass, cellIdentityNrClass, cellSignalNrClass
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
        //
        // 这里**不能**用「注册的这一刻模拟是否已经开启」做门槛。
        // 应用普遍在启动阶段就注册监听器，而模拟是用户之后才开的；旧写法在这种情况下直接 return，
        // 于是该进程的监听器类永远没被挂上，此后无论模拟是否开启都一直收到真实位置
        // —— 表现为「开了独立分流，目标应用却还是真实定位」。
        // 是否伪造改由回调内部逐次调用 getActiveLocation() 判定（见 hookLocationListener）。
        runCatching {
            XposedBridge.hookAllMethods(lmClass, "requestLocationUpdates", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
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

        // 7. Provider 可用性查询（客户端侧）
        //
        // 系统侧虽然已经挂了 LMS.isProviderEnabled / getBestProvider / getProviders，但
        // android.location.LocationManager 在**客户端进程**里还有一份自己的 provider 状态缓存
        // （AOSP 13 的 LocationManager 会缓存并本地失效），而模块此前完全没有挂客户端这几个方法。
        // 后果：应用启动时先问一句"GPS 开着吗"，拿到"没开"就直接不做定位 —— 后面的注入根本没机会发生。
        // 这里只在「确实存在模拟配置」时把 GPS 报成可用；未配置时一律透传系统真实状态。
        runCatching {
            XposedBridge.hookAllMethods(lmClass, "isProviderEnabled", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result != false) return
                    if (!hasSpoofConfig()) return
                    val provider = param.args.firstOrNull { it is String } as? String
                    if (provider == null || isLocationProviderName(provider)) {
                        param.result = true
                    }
                }
            })

            XposedBridge.hookAllMethods(lmClass, "getBestProvider", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!hasSpoofConfig()) return
                    val current = param.result as? String
                    if (current == null || current == LocationManager.PASSIVE_PROVIDER) {
                        param.result = LocationManager.GPS_PROVIDER
                    }
                }
            })

            XposedBridge.hookAllMethods(lmClass, "getProviders", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!hasSpoofConfig()) return
                    @Suppress("UNCHECKED_CAST")
                    val list = param.result as? MutableList<String> ?: return
                    if (!list.contains(LocationManager.GPS_PROVIDER)) {
                        list.add(LocationManager.GPS_PROVIDER)
                    }
                }
            })
        }.logFailure(TAG, "hook LocationManager provider-state queries")
    }

    /**
     * 是否「存在模拟配置」：全局模拟已开启，或至少配了一条分流规则。
     *
     * 只用于 provider 可用性查询这类"要不要告诉应用 GPS 可用"的判断 ——
     * 没有配置时必须原样透传系统状态，不能影响任何未使用模拟的应用。
     */
    private fun hasSpoofConfig(): Boolean {
        loadMultiTargetRules()
        if (multiTargetCache.isNotEmpty()) return true
        val spoof = getGlobalActiveLocation()
        return spoof != null && spoof.isActive
    }

    private val hookedListenerClasses: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /**
     * 已经挂成功的「类#方法」键。
     *
     * 旧实现按「类名」记录，而且在**尝试挂载之前**就把类名写进集合：某个方法一旦挂失败
     * （抽象方法、被 ROM 改写、被混淆），这个类就被永久标记成"已处理"，之后再也不会重试 ——
     * 应用于是整个进程生命周期都在收真实位置。改成按方法记录，且只在挂成功后才保留。
     */
    private val hookedListenerMethods: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun hookLocationListener(listenerClass: Class<*>) {
        val className = listenerClass.name

        // Hook onLocationChanged(Location)
        val locKey = "$className#onLocationChanged(Location)"
        if (hookedListenerMethods.add(locKey)) {
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
            }.onFailure { hookedListenerMethods.remove(locKey) }
                .logFailure(TAG, "hook ${className}.onLocationChanged(Location)")
        }

        // Hook onLocationChanged(List<Location>) - Android 12~16
        val listKey = "$className#onLocationChanged(List)"
        if (hookedListenerMethods.add(listKey)) {
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
            }.onFailure { hookedListenerMethods.remove(listKey) }
                .logFailure(TAG, "hook ${className}.onLocationChanged(List<Location>)")
        }

        // Hook onLocationAvailability(LocationAvailability)
        //
        // AOSP 在"当前拿不到定位"时会回调它（例如只注册了 GPS 而此刻无星）。不挂这个回调时，
        // 应用可以据此把界面切成"无法定位/定位失败"，即使 onLocationChanged 已经被替换成模拟点。
        //
        // 注意：`android.location.LocationAvailability` **不在公开 SDK 桩里**（它只存在于隐藏 API），
        // 所以这里不能用 findAndHookMethod 传参数类型 —— 那样会直接编译不过（这也正是本模块此前
        // 从未挂上这个回调的原因）。改用 hookAllMethods 按方法名挂载，参数对象只做反射改字段。
        val availKey = "$className#onLocationAvailability"
        if (hookedListenerMethods.add(availKey)) {
            runCatching {
                XposedBridge.hookAllMethods(
                    listenerClass,
                    "onLocationAvailability",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val spoof = getActiveLocation() ?: return
                            if (!spoof.isActive) return
                            val availability = param.args.getOrNull(0) ?: return
                            runCatching {
                                XposedHelpers.setBooleanField(availability, "mIsLocationAvailable", true)
                            }.logFailure(TAG, "force LocationAvailability.mIsLocationAvailable", Diag.Level.DEBUG)
                        }
                    }
                )
            }.onFailure { hookedListenerMethods.remove(availKey) }
                .logFailure(TAG, "hook ${className}.onLocationAvailability")
        }
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

        // 注意：这里**不再**把 getScanResults 置空。
        //
        // 与 hookSystemWifiService 同因：「有 GPS 星数 + 零 Wi-Fi」在物理上不成立，
        // 风控 SDK 拿这个矛盾一测一个准，比暴露真实 AP 更可疑。
        // 需要遮蔽时应回放指纹（见 hookFingerprintReplay）；读不到指纹就什么都不做，
        // 让真实值透出。

        // getConnectionInfo() -> 遮蔽 BSSID / MAC
        runCatching {
            XposedBridge.hookAllMethods(wmClass, "getConnectionInfo", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (!spoof.isActive) return
                    val wifiInfo = param.result ?: return
                    runCatching {
                        // 不能再用 02:00:00:00:00:00 —— AOSP 明确把它定义为
                        // 「调用者权限不足」的哨兵值（WifiInfo.DEFAULT_MAC_ADDRESS）。
                        // 保留真实 OUI 前三字节、只随机化后三字节，
                        // 看起来像「换了一个同厂商的 AP」。
                        val real = XposedHelpers.getObjectField(wifiInfo, "mBSSID") as? String
                        val newBssid = if (real != null && real.length == 17 &&
                            real != "02:00:00:00:00:00"
                        ) {
                            val r = java.util.Random(
                                spoof.latitude.hashCode().toLong() xor spoof.longitude.hashCode().toLong()
                            )
                            "%s:%02X:%02X:%02X".format(
                                real.substring(0, 8), r.nextInt(256), r.nextInt(256), r.nextInt(256)
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
                    }.logFailure(
                        TAG,
                        "mask WifiInfo BSSID/MAC — field names absent on this ROM, real BSSID leaked",
                        Diag.Level.WARN
                    )
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

    /** 已挂钩过的 AMapLocation Class 引用，防止 loadClass 监听重复挂钩。 */
    private val hookedAmapClasses = java.util.Collections.synchronizedSet(mutableSetOf<Class<*>>())

    private fun hookProprietaryMapSdks(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Amap Location SDK —— 立即尝试（类可能已在 bootclasspath 关联路径）
        runCatching {
            val amapLocationClass = XposedHelpers.findClassIfExists("com.amap.api.location.AMapLocation", lpparam.classLoader)
            if (amapLocationClass != null) {
                hookAmapLocationGetters(amapLocationClass)
            }
        }.logFailure(TAG, "hook AMapLocation immediate")

        // 其余地图 SDK（AMapLocationQualityReport / Baidu / Tencent）保持原有立即尝试逻辑
        hookOtherMapSdks(lpparam)
    }

    /** 对已加载的 AMapLocation Class 挂钩全部 getter（幂等，由 hookedAmapClasses 去重）。 */
    private fun hookAmapLocationGetters(amapLocationClass: Class<*>) {
        runCatching {
            run {
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
                        if (spoof.isActive) {
                            // 境外不做 GCJ 偏移（GeoTransform 的国界判断），此时若仍宣称 GCJ02，
                            // 信任该字段的应用会把一个 WGS-84 值按 GCJ 解释 —— 又是 ~500m。
                            param.result =
                                if (com.mockrun.app.util.GeoTransform.outOfChina(spoof.latitude, spoof.longitude)) {
                                    "WGS84"
                                } else {
                                    "GCJ02"
                                }
                        }
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
        }.logFailure(TAG, "hook AMapLocation getters")
    }

    /** AMapLocationQualityReport / Baidu / Tencent 等其他 SDK 的挂钩（保持原立即尝试逻辑）。 */
    private fun hookOtherMapSdks(lpparam: XC_LoadPackage.LoadPackageParam) {
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

    // TargetAppRule / RuleDecision / TargetRuleResolver 见同包 TargetRuleResolver.kt ——
    // 匹配逻辑抽成纯函数是为了能被单元测试覆盖（这里原本埋着一个 2600 行类里的私有判断）。

    private val multiTargetCache = java.util.concurrent.ConcurrentHashMap<String, TargetAppRule>()

    /** 「无规则」这条日志只在状态翻转时打一次，避免高频刷屏。 */
    @Volatile
    private var loggedRoutingInactive = false
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
        val pkg = resolvePackageViaContext(uid) ?: resolvePackageViaPackageManagerService(uid)
        if (pkg != null) {
            uidPackageCache[uid] = pkg
        } else {
            Diag.d(TAG, "cannot resolve package for uid $uid — per-app rule lookup degrades to global/real")
        }
        return pkg
    }

    private fun resolvePackageViaContext(uid: Int): String? {
        val ctx = getAnyContext() ?: return null
        return runCatching { ctx.packageManager.getPackagesForUid(uid)?.firstOrNull() }
            .logFailure(TAG, "resolve package for uid $uid via Context", Diag.Level.DEBUG)
            .getOrNull()
    }

    /**
     * 不依赖 Context 的兜底：直接问 PackageManagerService。
     *
     * system_server 里 `getAnyContext()` 走的是 ActivityThread 的 hidden API，在部分 ROM（MIUI 等）
     * 上拿不到；一旦拿不到，uid→包名解析整体失败，per-app 规则匹配就静默退化成"全局或真实位置"，
     * 表现正是「必须把目标应用也加进 LSPosed 作用域才能生效」。
     * IPackageManager 是 system_server 自己持有的对象，绕开 Context 更可靠。
     */
    private fun resolvePackageViaPackageManagerService(uid: Int): String? = runCatching {
        val ipm = Class.forName("android.app.AppGlobals")
            .getMethod("getPackageManager")
            .invoke(null)
            ?: return@runCatching null
        val method = Class.forName("android.content.pm.IPackageManager")
            .getMethod("getPackagesForUid", Int::class.javaPrimitiveType)
        @Suppress("UNCHECKED_CAST")
        (method.invoke(ipm, uid) as? Array<String>)?.firstOrNull()
    }.logFailure(TAG, "resolve package for uid $uid via IPackageManager", Diag.Level.DEBUG).getOrNull()

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
        // Android 17 的 system_server 不允许对 App 发起同步 Binder（会抛
        // "Outgoing transactions from this process must be FLAG_ONEWAY"）；system_server
        // 改走文件/属性/Settings，客户端进程仍走 Provider。
        if (jsonText.isNullOrEmpty() && currentProcessPackage != "android") {
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
            // 五个通道全空：规则被删掉，或所有通道都读不到。
            // 旧实现直接 return，**保留上一次读到的规则** —— 用户删除/停用规则后，已 hook 的进程
            // 仍按旧规则继续伪造，直到进程重启。这里显式清空，让"删除即失效"成立。
            if (multiTargetCache.isNotEmpty()) {
                multiTargetCache.clear()
                loggedRoutingInactive = false
            Diag.i(TAG, "no multi-target rules from any of the 5 channels — cache cleared, per-app routing off")
            } else {
                // 只在状态翻转时打一次：这段在每次 getActiveLocation 都会走到，
            // 无节流时会 1ms 内连打 5 行，真机 logcat 直接被刷爆。
            if (!loggedRoutingInactive) {
                loggedRoutingInactive = true
                Diag.d(TAG, "no multi-target rules from any of the 5 channels — routing inactive")
            }
            }
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
        Diag.i(TAG, "[diag] getActiveLocation called pkg=$targetPackage uid=$targetUid proc=$currentProcessPackage")
        loadMultiTargetRules()

        if (multiTargetCache.isNotEmpty()) {
            val uid = targetUid ?: runCatching { Binder.getCallingUid() }.getOrDefault(0)
            val resolvedPkg = targetPackage
                ?: (if (uid > 1000) getPackageForUid(uid) else null)
                ?: (if (currentProcessPackage.isNotEmpty() && currentProcessPackage != "android") currentProcessPackage else null)

            if (resolvedPkg != null) {
                val userId = if (uid > 1000) uid / 100000 else 0
                when (val decision = TargetRuleResolver.resolve(multiTargetCache, resolvedPkg, userId)) {
                    is RuleDecision.PassThrough -> {
                        // 命中但暂停 / 显式透传：透出 100% 真实硬件位置（绝不回落到全局模拟点）
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

                    is RuleDecision.Spoof -> {
                        val rule = decision.rule
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

                    RuleDecision.NoRule -> {
                        // 没命中：交给下面的全局逻辑
                    }
                }
            }
        }

        val g = getGlobalActiveLocation()
        Diag.i(TAG, "[diag] getActiveLocation -> global result: active=${g?.isActive} lat=${g?.latitude} lon=${g?.longitude} ts=${g?.timestamp} nowMs=${System.currentTimeMillis()}")
        return g
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
        // system_server 在 Android 17 禁止同步 Binder 到 App；跳过，避免每 tick 抛异常。
        if (currentProcessPackage != "android") runCatching {
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

    // ================= Provider 上报层改写 =================
    // 参考开源实现的核心思路（不照搬代码）：
    //  1) AbstractLocationProvider.reportLocation 是所有 provider（gps/network/fused）上报的唯一咽喉；
    //  2) LocationProviderManager.onReportLocation 里必须**自己驱动分发**：
    //     acceptLocationChange 返回的是惰性 ListenerOperation，必须再调 executeOperation 才会回调；
    //  3) 每个伪造点显式 isMock = false（否则反作弊一眼看穿）。
    // 这三点正是"首次跳到假点、之后不跟随"的解药。
    private fun hookProviderReportLayer(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cl = lpparam.classLoader
        val locationResultClass = XposedHelpers.findClassIfExists("android.location.LocationResult", cl)
        val abstractProvider = XposedHelpers.findClassIfExists(
            "com.android.server.location.provider.AbstractLocationProvider", cl
        )
        if (locationResultClass != null && abstractProvider != null) {
            runCatching {
                val m = XposedHelpers.findMethodExactIfExists(abstractProvider, "reportLocation", locationResultClass)
                if (m != null) {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val spoof = getActiveLocation() ?: return
                            if (!spoof.isActive) return
                            rewriteLocationResult(param.args.getOrNull(0), LocationManager.GPS_PROVIDER, spoof)
                        }
                    })
                    Diag.i(TAG, "reportLayer: AbstractLocationProvider.reportLocation hooked")
                }
            }.onFailure { Diag.w(TAG, "reportLayer: hook reportLocation failed: ${it.message}") }
        }

        val lpm = XposedHelpers.findClassIfExists(
            "com.android.server.location.provider.LocationProviderManager", cl
        ) ?: run {
            Diag.w(TAG, "reportLayer: LocationProviderManager not found")
            return
        }
        runCatching {
            XposedBridge.hookAllMethods(lpm, "onReportLocation", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (!spoof.isActive) return
                    val result = param.args.getOrNull(0) ?: return
                    val regsField = XposedHelpers.findFieldIfExists(lpm, "mRegistrations") ?: return
                    regsField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val regs = regsField.get(param.thisObject) as? MutableMap<Any?, Any?> ?: return
                    if (regs.isEmpty()) return
                    rewriteLocationResult(result, LocationManager.GPS_PROVIDER, spoof)
                    for (v in regs.values.toList()) {
                        if (v == null) continue
                        val op = runCatching {
                            XposedHelpers.callMethod(v, "acceptLocationChange", result)
                        }.getOrNull() ?: continue
                        runCatching { XposedHelpers.callMethod(v, "executeOperation", op) }
                    }
                    // 清空原 map：原方法不再向这些 registration 投递真实位置
                    runCatching { regsField.set(param.thisObject, HashMap<Any?, Any?>()) }
                }
            })
            Diag.i(TAG, "reportLayer: LocationProviderManager.onReportLocation hooked")
        }.onFailure { Diag.w(TAG, "reportLayer: hook onReportLocation failed: ${it.message}") }
    }

    /** 把 LocationResult.mLocations 换成一个伪造 Location（含 isMock=false）。 */
    private fun rewriteLocationResult(result: Any?, provider: String, spoof: SpoofLocation) {
        if (result == null) return
        runCatching {
            val field = XposedHelpers.findFieldIfExists(result.javaClass, "mLocations") ?: return
            field.isAccessible = true
            val list = field.get(result) as? List<*> ?: return
            val origin = list.firstOrNull() as? Location
            val loc = createSpoofedLocation(origin?.provider ?: provider, spoof)
            runCatching { loc.isMock = false }
            origin?.let {
                loc.time = it.time
                loc.elapsedRealtimeNanos = it.elapsedRealtimeNanos
                loc.accuracy = it.accuracy
                loc.speed = it.speed
                runCatching { loc.extras = it.extras }
            }
            field.set(result, arrayListOf(loc))
        }.onFailure { Diag.d(TAG, "reportLayer: rewrite LocationResult failed: ${it.message}") }
    }
}
