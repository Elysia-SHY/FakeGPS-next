package com.mockrun.app.hook

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
    val speed: Float
)

class XposedLocationHook : IXposedHookLoadPackage {

    private var xSharedPrefs: XSharedPreferences? = null
    private var appContext: Context? = null
    private var cachedLocation: SpoofLocation? = null
    private var lastCacheCheckTime: Long = 0L

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return

        // If loaded in Fake GPS itself, hook XposedStatusHelper to report module active, then return
        if (pkg == "com.mockrun.app") {
            runCatching {
                XposedHelpers.findAndHookMethod(
                    "com.mockrun.app.hook.XposedStatusHelper",
                    lpparam.classLoader,
                    "isModuleActive",
                    de.robv.android.xposed.XC_MethodReplacement.returnConstant(true)
                )
            }
            return
        }

        // Initialize XSharedPreferences
        if (xSharedPrefs == null) {
            xSharedPrefs = runCatching {
                val sp = XSharedPreferences("com.mockrun.app", "hook_config")
                sp.makeWorldReadable()
                sp
            }.getOrNull()
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
        }

        // Universal Anti-Mock detection hook in all processes
        hookMockDetection(lpparam)

        if (pkg == "android") {
            // =========================================================================
            // 核心系统层 Hook (System Framework / system_server)
            // =========================================================================
            XposedBridge.log("[FakeGPS] >>> Hooking Android System Framework (system_server) <<<")
            hookSystemServer(lpparam)
        } else {
            // =========================================================================
            // 客户端应用层 Hook (Client Application Fallback)
            // =========================================================================
            XposedBridge.log("[FakeGPS] Injected into client application: $pkg")
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
    }

    private fun hookLocationProviderManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val lpmClasses = listOfNotNull(
            XposedHelpers.findClassIfExists("com.android.server.location.provider.LocationProviderManager", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.LocationProviderManager", lpparam.classLoader)
        )

        for (lpmClass in lpmClasses) {
            // onReportLocation(LocationResult locationResult)
            runCatching {
                XposedBridge.hookAllMethods(lpmClass, "onReportLocation", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val resultArg = param.args.getOrNull(0) ?: return

                        val providerName = runCatching {
                            XposedHelpers.callMethod(param.thisObject, "getName") as? String
                        }.getOrNull() ?: LocationManager.GPS_PROVIDER

                        val spoofedLoc = createSpoofedLocation(providerName, spoof)
                        val resultClass = resultArg.javaClass
                        val newResult = runCatching {
                            XposedHelpers.callStaticMethod(resultClass, "wrap", arrayOf(spoofedLoc))
                        }.getOrElse {
                            runCatching {
                                XposedHelpers.callStaticMethod(resultClass, "create", listOf(spoofedLoc))
                            }.getOrNull()
                        }
                        if (newResult != null) {
                            param.args[0] = newResult
                        }
                    }
                })
            }

            // getLastLocation(...)
            runCatching {
                XposedBridge.hookAllMethods(lpmClass, "getLastLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = createSpoofedLocation(LocationManager.GPS_PROVIDER, spoof)
                        }
                    }
                })
            }

            // setLastLocation(...)
            runCatching {
                XposedBridge.hookAllMethods(lpmClass, "setLastLocation", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val spoofedLoc = createSpoofedLocation(LocationManager.GPS_PROVIDER, spoof)
                        for (i in param.args.indices) {
                            if (param.args[i] is Location) {
                                param.args[i] = spoofedLoc
                            }
                        }
                    }
                })
            }
        }
    }

    private fun hookLocationManagerService(lpparam: XC_LoadPackage.LoadPackageParam) {
        val lmsClasses = listOfNotNull(
            XposedHelpers.findClassIfExists("com.android.server.location.LocationManagerService", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.LocationManagerService", lpparam.classLoader)
        )

        for (lmsClass in lmsClasses) {
            // 1. getLastLocation(...)
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "getLastLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        if (isCallingPackageSelf(param)) return

                        val provider = param.args.firstOrNull { it is String } as? String ?: LocationManager.GPS_PROVIDER
                        param.result = createSpoofedLocation(provider, spoof)
                    }
                })
            }

            // 2. getCurrentLocation(...) (Android 11+)
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "getCurrentLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        if (isCallingPackageSelf(param)) return

                        if (param.result is Location) {
                            param.result = createSpoofedLocation(LocationManager.GPS_PROVIDER, spoof)
                        }
                    }
                })
            }

            // 3. reportLocation(...) (Android <= 10)
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "reportLocation", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
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
            }

            // 4. handleLocationChanged(...) (All Android versions)
            runCatching {
                XposedBridge.hookAllMethods(lmsClass, "handleLocationChanged", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        for (i in param.args.indices) {
                            if (param.args[i] is Location) {
                                val prov = (param.args[i] as Location).provider ?: LocationManager.GPS_PROVIDER
                                param.args[i] = createSpoofedLocation(prov, spoof)
                            }
                        }
                    }
                })
            }
        }
    }

    private fun hookLmsDispatchers(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Older Android Receiver
        runCatching {
            val receiverClass = XposedHelpers.findClassIfExists(
                "com.android.server.LocationManagerService\$Receiver",
                lpparam.classLoader
            )
            if (receiverClass != null) {
                XposedBridge.hookAllMethods(receiverClass, "callLocationChangedLocked", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val loc = param.args.firstOrNull { it is Location } as? Location ?: return
                        param.args[0] = createSpoofedLocation(loc.provider ?: LocationManager.GPS_PROVIDER, spoof)
                    }
                })
            }
        }

        // Android 11+ LocationRegistration
        runCatching {
            val locRegClass = XposedHelpers.findClassIfExists(
                "com.android.server.location.LocationManagerService\$LocationRegistration",
                lpparam.classLoader
            )
            if (locRegClass != null) {
                XposedBridge.hookAllMethods(locRegClass, "onLocationChanged", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val arg = param.args.getOrNull(0) ?: return
                        val spoofedLoc = createSpoofedLocation(LocationManager.GPS_PROVIDER, spoof)
                        if (arg is Location) {
                            param.args[0] = spoofedLoc
                        } else {
                            val locResultClass = arg.javaClass
                            val newResult = runCatching {
                                XposedHelpers.callStaticMethod(locResultClass, "wrap", arrayOf(spoofedLoc))
                            }.getOrElse {
                                runCatching {
                                    XposedHelpers.callStaticMethod(locResultClass, "create", listOf(spoofedLoc))
                                }.getOrNull()
                            }
                            if (newResult != null) {
                                param.args[0] = newResult
                            }
                        }
                    }
                })
            }
        }
    }

    private fun hookSystemWifiService(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 1. WifiServiceImpl
        runCatching {
            val wifiServiceClass = XposedHelpers.findClassIfExists(
                "com.android.server.wifi.WifiServiceImpl",
                lpparam.classLoader
            )
            if (wifiServiceClass != null) {
                // getScanResults(...) -> Return empty list to prevent neighbor Wi-Fi BSSID sniffing
                XposedBridge.hookAllMethods(wifiServiceClass, "getScanResults", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            if (!isCallingPackageSelf(param)) {
                                param.result = java.util.ArrayList<Any>()
                            }
                        }
                    }
                })

                // getConnectionInfo(...) -> Mask BSSID
                XposedBridge.hookAllMethods(wifiServiceClass, "getConnectionInfo", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            val wifiInfo = param.result ?: return
                            runCatching {
                                XposedHelpers.setObjectField(wifiInfo, "mBSSID", "02:00:00:00:00:00")
                                XposedHelpers.setObjectField(wifiInfo, "mMacAddress", "02:00:00:00:00:00")
                            }
                        }
                    }
                })
            }
        }

        // 2. WifiScanningServiceImpl
        runCatching {
            val wifiScanClass = XposedHelpers.findClassIfExists(
                "com.android.server.wifi.scanner.WifiScanningServiceImpl",
                lpparam.classLoader
            )
            if (wifiScanClass != null) {
                XposedBridge.hookAllMethods(wifiScanClass, "getScanResults", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive && !isCallingPackageSelf(param)) {
                            param.result = java.util.ArrayList<Any>()
                        }
                    }
                })
            }
        }
    }

    private fun hookSystemTelephonyRegistry(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            val telRegistryClass = XposedHelpers.findClassIfExists(
                "com.android.server.TelephonyRegistry",
                lpparam.classLoader
            )
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
        }
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
            val locClass = XposedHelpers.findClassIfExists("android.location.Location", lpparam.classLoader) ?: return
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
        }
    }

    private fun hookLocationManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val lmClass = XposedHelpers.findClassIfExists("android.location.LocationManager", lpparam.classLoader) ?: return

        // 1. getLastKnownLocation(String)
        runCatching {
            XposedBridge.hookAllMethods(lmClass, "getLastKnownLocation", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (spoof.isActive) {
                        val provider = param.args.firstOrNull { it is String } as? String ?: LocationManager.GPS_PROVIDER
                        param.result = createSpoofedLocation(provider, spoof)
                    }
                }
            })
        }

        // 2. getLastLocation() (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                XposedBridge.hookAllMethods(lmClass, "getLastLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = createSpoofedLocation(LocationManager.GPS_PROVIDER, spoof)
                        }
                    }
                })
            }
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
        }
    }

    private val hookedListenerClasses = mutableSetOf<String>()

    private fun hookLocationListener(listenerClass: Class<*>) {
        val className = listenerClass.name
        if (hookedListenerClasses.contains(className)) return
        hookedListenerClasses.add(className)

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
        }
    }

    private fun hookWifiManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val wmClass = XposedHelpers.findClassIfExists("android.net.wifi.WifiManager", lpparam.classLoader) ?: return

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
        }

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
                        }
                    }
                }
            })
        }
    }

    private fun hookTelephonyManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val tmClass = XposedHelpers.findClassIfExists("android.telephony.TelephonyManager", lpparam.classLoader) ?: return

        runCatching {
            XposedBridge.hookAllMethods(tmClass, "getAllCellInfo", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (spoof.isActive) {
                        param.result = java.util.ArrayList<Any>()
                    }
                }
            })
        }

        runCatching {
            XposedBridge.hookAllMethods(tmClass, "getCellLocation", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (spoof.isActive) {
                        param.result = null
                    }
                }
            })
        }

        runCatching {
            XposedBridge.hookAllMethods(tmClass, "getNeighboringCellInfo", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val spoof = getActiveLocation() ?: return
                    if (spoof.isActive) {
                        param.result = java.util.ArrayList<Any>()
                    }
                }
            })
        }
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
                            param.result = spoof.latitude
                        }
                    }
                })
                XposedBridge.hookAllMethods(amapLocationClass, "getLongitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = spoof.longitude
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
            }
        }

        // Baidu Location SDK
        runCatching {
            val bdLocationClass = XposedHelpers.findClassIfExists("com.baidu.location.BDLocation", lpparam.classLoader)
            if (bdLocationClass != null) {
                XposedBridge.hookAllMethods(bdLocationClass, "getLatitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = spoof.latitude
                        }
                    }
                })
                XposedBridge.hookAllMethods(bdLocationClass, "getLongitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = spoof.longitude
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
        }
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
                }.getOrNull()
                if (pkg == "com.mockrun.app") return true
            }
        }
        return false
    }

    private fun getActiveLocation(): SpoofLocation? {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCacheCheckTime < 50 && cachedLocation != null) {
            return cachedLocation
        }
        lastCacheCheckTime = now

        // 1. Fast Path: Read /data/local/tmp/fake_gps_hook.json (0-delay world-readable file)
        runCatching {
            val file = java.io.File("/data/local/tmp/fake_gps_hook.json")
            if (file.exists() && file.canRead()) {
                val text = file.readText()
                if (text.isNotEmpty() && text.contains("\"isActive\":true")) {
                    val lat = text.substringAfter("\"latitude\":").substringBefore(",").toDoubleOrNull() ?: 0.0
                    val lon = text.substringAfter("\"longitude\":").substringBefore(",").toDoubleOrNull() ?: 0.0
                    val alt = text.substringAfter("\"altitude\":").substringBefore(",").toDoubleOrNull() ?: 50.0
                    val bear = text.substringAfter("\"bearing\":").substringBefore(",").toFloatOrNull() ?: 0f
                    val spd = text.substringAfter("\"speed\":").substringBefore(",").toFloatOrNull() ?: 0f
                    if (lat != 0.0 || lon != 0.0) {
                        val loc = SpoofLocation(
                            isActive = true,
                            latitude = lat,
                            longitude = lon,
                            altitude = alt,
                            bearing = bear,
                            speed = spd
                        )
                        cachedLocation = loc
                        return loc
                    }
                } else if (text.contains("\"isActive\":false")) {
                    cachedLocation = null
                    return null
                }
            }
        }

        // 2. Standard Path: XSharedPreferences
        xSharedPrefs?.let { sp ->
            runCatching {
                sp.reload()
                val isActive = sp.getBoolean("is_active", false)
                if (isActive) {
                    val lat = sp.getString("latitude", "0")?.toDoubleOrNull() ?: 0.0
                    val lon = sp.getString("longitude", "0")?.toDoubleOrNull() ?: 0.0
                    val bear = sp.getFloat("bearing", 0f)
                    val spd = sp.getFloat("speed", 0f)
                    if (lat != 0.0 || lon != 0.0) {
                        val loc = SpoofLocation(
                            isActive = true,
                            latitude = lat,
                            longitude = lon,
                            altitude = 50.0,
                            bearing = bear,
                            speed = spd
                        )
                        cachedLocation = loc
                        return loc
                    }
                } else {
                    cachedLocation = null
                    return null
                }
            }
        }

        // 3. Fallback Path: ContentProvider IPC (if appContext available)
        appContext?.let { ctx ->
            runCatching {
                val uri = Uri.parse("content://com.mockrun.app.hook.provider")
                val bundle = ctx.contentResolver.call(uri, "getLocation", null, null)
                if (bundle != null && bundle.getBoolean("is_active", false)) {
                    val lat = bundle.getDouble("latitude")
                    val lon = bundle.getDouble("longitude")
                    val alt = bundle.getDouble("altitude", 50.0)
                    val bear = bundle.getFloat("bearing", 0f)
                    val spd = bundle.getFloat("speed", 0f)
                    val loc = SpoofLocation(
                        isActive = true,
                        latitude = lat,
                        longitude = lon,
                        altitude = alt,
                        bearing = bear,
                        speed = spd
                    )
                    cachedLocation = loc
                    return loc
                }
            }
        }

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

        // Deeply strip any mock flags
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                loc.isMock = false
            }
        }
        runCatching {
            XposedHelpers.callMethod(loc, "setIsFromMockProvider", false)
        }
        runCatching {
            XposedHelpers.setBooleanField(loc, "mIsFromMockProvider", false)
        }

        return loc
    }
}
