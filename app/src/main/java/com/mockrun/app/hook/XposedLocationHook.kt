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
    val timestamp: Long = 0L
)

class XposedLocationHook : IXposedHookLoadPackage {

    private var xSharedPrefs: XSharedPreferences? = null
    private var appContext: Context? = null
    private var cachedLocation: SpoofLocation? = null
    private var lastCacheCheckTime: Long = 0L
    private var lastSpoofedLocation: Location? = null

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

        // Eagerly resolve context from ActivityThread if available
        runCatching { getAnyContext() }

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

        // 1.6 Hook System-level GNSS Status (Synthesize BDS/GPS Constellation)
        hookSystemGnssStatus(lpparam)
    }

    private fun hookSystemGnssStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val gnssClasses = listOfNotNull(
            XposedHelpers.findClassIfExists("com.android.server.location.gnss.GnssStatusProvider", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.GnssStatusProvider", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.gnss.GnssManagerService", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.gnss.GnssLocationProvider", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.GnssLocationProvider", lpparam.classLoader)
        )

        for (cls in gnssClasses) {
            runCatching {
                XposedBridge.hookAllMethods(cls, "onReportGnssStatus", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val synthetic = SyntheticGnssProvider.createSyntheticGnssStatus() ?: return
                        for (i in param.args.indices) {
                            if (param.args[i] is GnssStatus) {
                                param.args[i] = synthetic
                            }
                        }
                    }
                })
            }
        }
    }

    private fun extractLocationFromResult(result: Any?): Location? {
        if (result == null) return null
        if (result is Location) return result
        if (result.javaClass.name.contains("LocationResult")) {
            return runCatching {
                XposedHelpers.callMethod(result, "getLastLocation") as? Location
            }.getOrNull() ?: runCatching {
                val list = XposedHelpers.callMethod(result, "getLocations") as? List<*>
                list?.lastOrNull() as? Location
            }.getOrNull()
        }
        return null
    }

    @Volatile
    private var cachedResultClass: Class<*>? = null
    @Volatile
    private var cachedLocationResultMethod: java.lang.reflect.Method? = null

    private fun createLocationResult(resultClass: Class<*>, location: Location): Any? {
        val method = if (cachedResultClass === resultClass) {
            cachedLocationResultMethod
        } else {
            val lookedUp = runCatching {
                XposedHelpers.findMethodExactIfExists(resultClass, "wrap", Array<Location>::class.java)
                    ?: XposedHelpers.findMethodExactIfExists(resultClass, "wrap", List::class.java)
                    ?: XposedHelpers.findMethodExactIfExists(resultClass, "create", List::class.java)
                    ?: XposedHelpers.findMethodExactIfExists(resultClass, "create", Array<Location>::class.java)
            }.getOrNull()
            cachedResultClass = resultClass
            cachedLocationResultMethod = lookedUp
            lookedUp
        }

        method?.let { m ->
            return runCatching {
                if (m.parameterTypes.firstOrNull() == List::class.java) {
                    m.invoke(null, listOf(location))
                } else {
                    m.invoke(null, arrayOf(location))
                }
            }.getOrNull()
        }

        // Fallback for non-standard framework derivatives
        return runCatching {
            XposedHelpers.callStaticMethod(resultClass, "wrap", arrayOf(location))
        }.getOrElse {
            runCatching {
                XposedHelpers.callStaticMethod(resultClass, "create", listOf(location))
            }.getOrNull()
        }
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
                        val newResult = createLocationResult(resultArg.javaClass, spoofedLoc)
                        if (newResult != null) {
                            param.args[0] = newResult
                        }
                    }
                })
            }

            // getLastLocation(...) - Critical fix for Android 12~16 LocationResult return type!
            runCatching {
                XposedBridge.hookAllMethods(lpmClass, "getLastLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation()
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
            }

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
                        val spoof = getActiveLocation()
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
        // Older Android Receiver (Android <= 10)
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

        // Android 11~16+ LocationRegistration dispatchers
        val locRegClasses = listOfNotNull(
            XposedHelpers.findClassIfExists("com.android.server.location.provider.LocationProviderManager\$LocationRegistration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.LocationProviderManager\$LocationRegistration", lpparam.classLoader),
            XposedHelpers.findClassIfExists("com.android.server.location.LocationManagerService\$LocationRegistration", lpparam.classLoader)
        )

        for (locRegClass in locRegClasses) {
            runCatching {
                val hookDispatch = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (!spoof.isActive) return
                        val arg = param.args.getOrNull(0) ?: return
                        val spoofedLoc = createSpoofedLocation(LocationManager.GPS_PROVIDER, spoof)
                        if (arg is Location) {
                            param.args[0] = spoofedLoc
                        } else if (arg.javaClass.name.contains("LocationResult")) {
                            val newResult = createLocationResult(arg.javaClass, spoofedLoc)
                            if (newResult != null) {
                                param.args[0] = newResult
                            }
                        }
                    }
                }
                XposedBridge.hookAllMethods(locRegClass, "onLocationChanged", hookDispatch)
                XposedBridge.hookAllMethods(locRegClass, "acceptLocationChange", hookDispatch)
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
        }

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
            }
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
            }
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
                    }
                    param.result = status
                }
            })
        }
    }

    private val hookedListenerClasses = mutableSetOf<String>()

    private fun hookLocationListener(listenerClass: Class<*>) {
        val className = listenerClass.name
        if (hookedListenerClasses.contains(className)) return
        hookedListenerClasses.add(className)

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
        }

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
        }
    }

    private val hookedGnssCallbackClasses = mutableSetOf<String>()

    private fun hookGnssStatusCallback(callbackClass: Class<*>) {
        val className = callbackClass.name
        if (hookedGnssCallbackClasses.contains(className)) return
        hookedGnssCallbackClasses.add(className)

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
            }
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

        // Tencent Location SDK (Used by WeChat com.tencent.mm, Tencent Map, QQ, Didi, Meituan)
        runCatching {
            val tencentLocClass = XposedHelpers.findClassIfExists("com.tencent.map.geolocation.TencentLocation", lpparam.classLoader)
            if (tencentLocClass != null) {
                XposedBridge.hookAllMethods(tencentLocClass, "getLatitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = spoof.latitude
                        }
                    }
                })
                XposedBridge.hookAllMethods(tencentLocClass, "getLongitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val spoof = getActiveLocation() ?: return
                        if (spoof.isActive) {
                            param.result = spoof.longitude
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
        }
    }

    private val hookedTencentListenerClasses = mutableSetOf<String>()
    private fun hookTencentListenerClass(listenerClass: Class<*>) {
        val className = listenerClass.name
        if (hookedTencentListenerClasses.contains(className)) return
        hookedTencentListenerClasses.add(className)

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

    private fun getSystemProperty(key: String): String {
        return runCatching {
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
            getMethod.invoke(null, key, "") as String
        }.getOrDefault("")
    }

    private fun getSystemContext(): Context? {
        return runCatching {
            val atClass = Class.forName("android.app.ActivityThread")
            val catMethod = atClass.getMethod("currentActivityThread")
            val at = catMethod.invoke(null)
            val scMethod = atClass.getMethod("getSystemContext")
            scMethod.invoke(at) as? Context
        }.getOrNull()
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
        }.getOrNull()
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

        return SpoofLocation(isActive, lat, lon, alt, bear, spd, time)
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

    private fun getPackageForUid(uid: Int): String? {
        if (uid <= 1000) return "android"
        uidPackageCache[uid]?.let { return it }
        val ctx = getAnyContext() ?: return null
        val pkg = runCatching { ctx.packageManager.getPackagesForUid(uid)?.firstOrNull() }.getOrNull()
        if (pkg != null) {
            uidPackageCache[uid] = pkg
        }
        return pkg
    }

    private fun loadMultiTargetRules() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastMultiTargetReadTime < 800L) return
        lastMultiTargetReadTime = now

        val jsonText = runCatching {
            val file = java.io.File("/data/system/fake_gps_multitarget.json")
            if (file.exists() && file.canRead()) file.readText() else null
        }.getOrNull() ?: runCatching {
            val ctx = getAnyContext()
            ctx?.let { android.provider.Settings.Global.getString(it.contentResolver, "fake_gps_multitarget") }
        }.getOrNull() ?: runCatching {
            val file = java.io.File("/data/local/tmp/fake_gps_multitarget.json")
            if (file.exists() && file.canRead()) file.readText() else null
        }.getOrNull()

        if (jsonText.isNullOrEmpty()) return

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
        }
    }

    private fun getActiveLocation(callingUid: Int = runCatching { Binder.getCallingUid() }.getOrDefault(0)): SpoofLocation? {
        loadMultiTargetRules()

        if (callingUid > 1000 && multiTargetCache.isNotEmpty()) {
            val pkg = getPackageForUid(callingUid)
            if (pkg != null) {
                val userId = callingUid / 100000
                val rule = multiTargetCache["${pkg}_$userId"] ?: multiTargetCache[pkg]
                if (rule != null) {
                    if (!rule.isEnabled || rule.mode == "REAL_PASSTHROUGH") {
                        // Pass through 100% real hardware location
                        return null
                    }
                    return SpoofLocation(
                        isActive = true,
                        latitude = rule.latitude,
                        longitude = rule.longitude,
                        altitude = rule.altitude,
                        bearing = 0f,
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
        if (now - lastCacheCheckTime < 60 && cachedLocation != null) {
            val c = cachedLocation!!
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

            if (activeStr == "1" || activeStr.equals("true", ignoreCase = true)) {
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
        }

        // 2. Channel 2: /data/system/fake_gps_hook.json (Native system_server directory, owned by system:system)
        runCatching {
            val file = java.io.File("/data/system/fake_gps_hook.json")
            if (file.exists() && file.canRead()) {
                val text = file.readText()
                val loc = parseJsonLocation(text)
                if (loc != null) {
                    if (!loc.isActive || (loc.timestamp > 0L && (nowMs - loc.timestamp > 20_000L))) {
                        val inactive = SpoofLocation(false, loc.latitude, loc.longitude, loc.altitude, loc.bearing, loc.speed, 0L)
                        cachedLocation = inactive
                        return inactive
                    }
                    cachedLocation = loc
                    return loc
                }
            }
        }

        // 3. Channel 3: /data/local/tmp/fake_gps_hook.json
        runCatching {
            val file = java.io.File("/data/local/tmp/fake_gps_hook.json")
            if (file.exists() && file.canRead()) {
                val text = file.readText()
                val loc = parseJsonLocation(text)
                if (loc != null) {
                    if (!loc.isActive || (loc.timestamp > 0L && (nowMs - loc.timestamp > 20_000L))) {
                        val inactive = SpoofLocation(false, loc.latitude, loc.longitude, loc.altitude, loc.bearing, loc.speed, 0L)
                        cachedLocation = inactive
                        return inactive
                    }
                    cachedLocation = loc
                    return loc
                }
            }
        }

        // 4. Channel 4: Settings.Global (Zero IPC in system_server, universally accessible)
        runCatching {
            val ctx = getAnyContext()
            if (ctx != null) {
                val configStr = android.provider.Settings.Global.getString(ctx.contentResolver, "fake_gps_config")
                if (!configStr.isNullOrEmpty()) {
                    val loc = parseJsonLocation(configStr)
                    if (loc != null) {
                        if (!loc.isActive || (loc.timestamp > 0L && (nowMs - loc.timestamp > 20_000L))) {
                            val inactive = SpoofLocation(false, loc.latitude, loc.longitude, loc.altitude, loc.bearing, loc.speed, 0L)
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
                val lat = sp.getString("latitude", "0")?.toDoubleOrNull() ?: 0.0
                val lon = sp.getString("longitude", "0")?.toDoubleOrNull() ?: 0.0
                val bear = sp.getFloat("bearing", 0f)
                val spd = sp.getFloat("speed", 0f)
                if (isActive) {
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

        lastSpoofedLocation = loc
        return loc
    }
}
