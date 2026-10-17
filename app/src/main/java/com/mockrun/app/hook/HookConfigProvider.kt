package com.mockrun.app.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import kotlinx.coroutines.*

object HookStateBridge {
    private const val TAG = "HookState"

    @Volatile
    var isHookActive: Boolean = false
        private set

    @Volatile
    var latitude: Double = 39.9042
        private set

    @Volatile
    var longitude: Double = 116.4074
        private set

    @Volatile
    var altitude: Double = 50.0
        private set

    @Volatile
    var bearing: Float = 0f
        private set

    @Volatile
    var speed: Float = 0f
        private set

    @Volatile
    var updateTimestamp: Long = 0L
        private set

    /**
     * 最近一次写入时的 `SystemClock.elapsedRealtime()`。
     *
     * 钩子用它判断这份配置是否来自本次开机：进程被杀或重启之后，残留配置里的这个值要么过期，
     * 要么比当前开机时长还大，两种情况都会判为无效，从而停止伪造、让真实定位回来。
     */
    @Volatile
    var updateElapsed: Long = 0L
        private set

    @Volatile
    var lastSystemHookHeartbeat: Long = 0L
        private set

    @Volatile
    private var lastSuSyncTime: Long = 0L

    @Volatile
    private var lastSyncedActive: Boolean? = null

    /** 只有显式清理（clearPublishedState）才擦坐标，见该方法注释。 */
    @Volatile
    private var wipeCoordsOnNextInactive = false

    @Volatile
    var multiTargetRulesJson: String = ""
        private set

    fun setMultiTargetRules(json: String) {
        multiTargetRulesJson = json
    }

    fun recordSystemHookHeartbeat() {
        lastSystemHookHeartbeat = System.currentTimeMillis()
    }

    /**
     * 执行 su 命令并**捕获 stdout**。
     *
     * RootSuBridge.executeCommand 只返回成败布尔值，无法拿到输出；
     * 环境指纹导出需要确认文件真的写成功（对比 `ls -l` 的输出）。
     *
     * 同步阻塞调用 —— 调用方必须在后台线程上执行。
     *
     * @return stdout 文本；su 不可用或执行失败返回 null
     */
    fun runSu(cmd: String): String? {
        var process: Process? = null
        return runCatching {
            // 不依赖 PATH：KernelSU 的 su 挂载在 /system/bin/su，
            // 但部分机型只在动态路径暴露，这里显式优先绝对路径。
            val suBin = listOf("/system/bin/su", "/system/xbin/su", "su")
                .firstOrNull { it == "su" || java.io.File(it).exists() } ?: "su"
            process = Runtime.getRuntime().exec(arrayOf(suBin, "-c", cmd))
            process.outputStream.close()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            val err = process.errorStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            if (code != 0) {
                Diag.w(TAG, "runSu exit=$code :: ${cmd.take(120)} :: $err")
                null
            } else {
                out
            }
        }.logFailure(TAG, "runSu :: ${cmd.take(120)}").getOrNull()
            .also { runCatching { process?.destroy() } }
    }

    fun isSystemHookAlive(): Boolean {
        return (System.currentTimeMillis() - lastSystemHookHeartbeat) < 60_000L
    }

    /**
     * 立刻清掉已发布的 hook 配置（SystemProperties / /data 文件 / Settings.Global）。
     *
     * 使用场景：用户切到 [com.mockrun.app.util.HookChannel.PROVIDER] 通道 ——
     * 此后没有消费者，残留必须当场消失，而不是等下一次模拟停止或重启。
     */
    fun clearPublishedState(context: Context) {
        // 显式清理（切到 Provider 通道 / 真正停止）才擦坐标；
        // 普通的 update(false) 只把 active 置 0，不动坐标 ——
        // 否则"发布 active=1 后又被某处 update(false)"会把坐标清空，
        // 表现为 hook 永远拿不到坐标（真机实测：active=0 且 lat/lon 为空）。
        wipeCoordsOnNextInactive = true
        // active 由 true 变 false，update() 里的节流条件 (stateChanged) 必然成立，不会被 250ms 窗口挡住。
        update(context, false)
    }

    @Volatile
    var isRouteSimulation: Boolean = false
        private set

    fun setRouteSimulationMode(isRoute: Boolean) {
        isRouteSimulation = isRoute
        asyncScope.launch {
            com.mockrun.app.core.location.RootSuBridge().executeCommand("setprop debug.fakegps.is_route ${if (isRoute) "1" else "0"}")
        }
    }

    private val asyncScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun update(
        context: Context?,
        active: Boolean,
        lat: Double = latitude,
        lon: Double = longitude,
        alt: Double = 50.0,
        bear: Float = 0f,
        spd: Float = 0f
    ) {
        // 溯源：真机上出现过"update(true) 之后又被某处 update(false) 清零"，
        // 导致 active=0 / time=0 而坐标还在 —— hook 于是永远判定过期、不接管。
        // 这条日志把调用者打出来，用来定位那个多余的 update(false)。
        runCatching {
            val c = Throwable().stackTrace.getOrNull(2)
            Diag.i(TAG, "update(active=$active) from ${c?.className?.substringAfterLast('.')}.${c?.methodName}")
        }
        isHookActive = active
        latitude = lat
        longitude = lon
        altitude = alt
        bearing = bear
        speed = spd
        updateTimestamp = System.currentTimeMillis()
        updateElapsed = android.os.SystemClock.elapsedRealtime()
        val elapsed = updateElapsed

        context?.let { ctx ->
            // PROVIDER 通道没有任何消费者：不再往 setprop / /data 文件 / Settings.Global 发布坐标。
            // 这些位置对任何进程可读（getprop 全局可读），写了就是纯粹的坐标残留。
            // 注意：
            //  1) 内存态（HookStateBridge）仍然更新 —— 它只在本进程内被 getLocation 读取；
            //  2) **active=false 的清理路径必须放行**，否则「切到 Provider 时清残留」会被自己挡住。
            if (active && !com.mockrun.app.util.HookChannelPrefs.shouldPublishHookConfig(ctx)) {
                return@let
            }
            // 1. SharedPreferences update (Immediate)
            runCatching {
                val sp = ctx.getSharedPreferences("hook_config", Context.MODE_PRIVATE)
                sp.edit()
                    .putBoolean("is_active", active)
                    .putString("latitude", lat.toString())
                    .putString("longitude", lon.toString())
                    .putFloat("bearing", bear)
                    .putFloat("speed", spd)
                    .putLong("timestamp", updateTimestamp)
                    .putLong("elapsed", elapsed)
                    .apply()

                val prefsFile = java.io.File(ctx.applicationInfo.dataDir, "shared_prefs/hook_config.xml")
                if (prefsFile.exists()) {
                    prefsFile.setReadable(true, false)
                }
            }.logFailure(TAG, "channel 1: write hook_config SharedPreferences", Diag.Level.DEBUG)

            val jsonStr =
                """{"isActive":$active,"latitude":$lat,"longitude":$lon,"altitude":$alt,"bearing":$bear,"speed":$spd,"time":$updateTimestamp,"elapsed":$elapsed}"""

            // 2. Settings.Global update (zero IPC overhead in system_server, readable by all apps)
            runCatching {
                if (active) {
                    android.provider.Settings.Global.putString(ctx.contentResolver, "fake_gps_config", jsonStr)
                } else {
                    android.provider.Settings.Global.putString(
                        ctx.contentResolver,
                        "fake_gps_config",
                        """{"isActive":false,"latitude":$lat,"longitude":$lon,"time":$updateTimestamp}"""
                    )
                }
            }.logFailure(TAG, "channel 2: write Settings.Global fake_gps_config", Diag.Level.DEBUG)

            // 3. Local file writes (cacheDir)
            runCatching {
                val cacheDir = ctx.cacheDir
                val tmpFile = java.io.File(cacheDir, "current_hook.json")
                if (active) {
                    tmpFile.writeText(
                        """{"active":$active,"lat":$lat,"lon":$lon,"alt":$alt,"bearing":$bear,"speed":$spd,"time":$updateTimestamp}"""
                    )
                    tmpFile.setReadable(true, false)
                } else {
                    if (tmpFile.exists()) {
                        tmpFile.delete()
                    }
                }
                Unit
            }.logFailure(TAG, "channel 3: write cacheDir/current_hook.json", Diag.Level.DEBUG)

            // 4. Multi-Channel System Sync via Root (Asynchronous, NEVER blocks UI thread)
            val now = System.currentTimeMillis()
            val stateChanged = (lastSyncedActive != active)
            // 节流窗口从 1200ms 收紧到 250ms：摇杆/路线模拟下位置每秒变多次，
            // 1.2s 的窗口会让 system_server 读到明显滞后的点。
            // su 有进程创建开销，250ms 已是实用下限。
            if (stateChanged || (now - lastSuSyncTime > 250L)) {
                lastSuSyncTime = now
                lastSyncedActive = active

                asyncScope.launch {
                    runCatching {
                        val activeInt = if (active) 1 else 0
                        val pkgDir = ctx.applicationInfo.dataDir
                        val suCmd = if (active) {
                            buildString {
                                append("setprop debug.fakegps.active $activeInt; ")
                                append("setprop debug.fakegps.time $updateTimestamp; ")
                                append("setprop debug.fakegps.elapsed $elapsed; ")
                                append("setprop debug.fakegps.lat $lat; ")
                                append("setprop debug.fakegps.lon $lon; ")
                                append("setprop debug.fakegps.alt $alt; ")
                                append("setprop debug.fakegps.bearing $bear; ")
                                append("setprop debug.fakegps.speed $spd; ")
                                append("echo '$jsonStr' > /data/system/fake_gps_hook.json 2>/dev/null; ")
                                // 0644 rather than 0666. The hook has to be able to READ these from
                                // any process, so world-readable is load-bearing — but world-WRITABLE
                                // is not. With 0666 any app on the device could rewrite the
                                // coordinates this hook serves.
                                append("chmod 644 /data/system/fake_gps_hook.json 2>/dev/null; ")
                                append("echo '$jsonStr' > /data/local/tmp/fake_gps_hook.json 2>/dev/null; ")
                                // /data/local/tmp 是 shell_data_file，system_server 可读 —— 这是
                                // Android 11+ 上唯一对 system_server 可靠的通道。
                                // XSharedPreferences 读 /data/data/<pkg>/shared_prefs 会被
                                // SELinux 拒绝（u:r:system_server:s0 读 app_data_file）。
                                append("chmod 644 /data/local/tmp/fake_gps_hook.json 2>/dev/null; ")
                                append("settings put global fake_gps_config '$jsonStr' 2>/dev/null; ")
                                append("chmod 755 $pkgDir 2>/dev/null; ")
                                append("chmod 755 $pkgDir/shared_prefs 2>/dev/null; ")
                                // Owner is this app (it is the one writing the prefs), so 0644 still
                                // lets it write while keeping other apps read-only.
                                append("chmod 644 $pkgDir/shared_prefs/hook_config.xml 2>/dev/null")
                            }
                        } else {
                            buildString {
                                append("setprop debug.fakegps.active 0; ")
                                append("setprop debug.fakegps.time 0; ")
                                append("setprop debug.fakegps.elapsed 0; ")
                                // 坐标本身也必须清：`setprop` 的属性对任何进程可读，
                                // 只清 active 而把 lat/lon 留到重启，等于把"用户上次伪造到哪"一直挂在系统里。
                                if (wipeCoordsOnNextInactive) {
                                    append("setprop debug.fakegps.lat \"\" 2>/dev/null; ")
                                    append("setprop debug.fakegps.lon \"\" 2>/dev/null; ")
                                    append("setprop debug.fakegps.alt \"\" 2>/dev/null; ")
                                    append("setprop debug.fakegps.bearing \"\" 2>/dev/null; ")
                                    append("setprop debug.fakegps.speed \"\" 2>/dev/null; ")
                                    wipeCoordsOnNextInactive = false
                                }
                                append("setprop debug.fakegps.is_route 0 2>/dev/null; ")
                                append("rm -f /data/system/fake_gps_hook.json 2>/dev/null; ")
                                append("rm -f /data/local/tmp/fake_gps_hook.json 2>/dev/null; ")
                                append("settings delete global fake_gps_config 2>/dev/null; ")
                                append("settings put global fake_gps_config '{\"isActive\":false,\"latitude\":$lat,\"longitude\":$lon}' 2>/dev/null; ")
                                append("settings put secure location_mode 3 2>/dev/null; ")
                                append("settings put global wifi_scan_always_enabled 1 2>/dev/null; ")
                                append("settings put global ble_scan_always_enabled 1 2>/dev/null; ")
                                append("settings put global assisted_gps_enabled 1 2>/dev/null")
                            }
                        }
                        var process: Process? = null
                        try {
                            process = Runtime.getRuntime().exec(arrayOf("su", "-c", suCmd))
                            process.outputStream.close()
                            process.waitFor()
                        } finally {
                            // Best-effort resource cleanup — deliberately not logged, same
                            // reasoning as RootSuBridge: failures here are harmless.
                            runCatching { process?.inputStream?.close() }
                            runCatching { process?.errorStream?.close() }
                            runCatching { process?.destroy() }
                        }
                    }.logFailure(
                        TAG,
                        "root sync of hook state (SystemProperties + /data files + Settings.Global) — " +
                            "hook will fall back to the non-root channels"
                    )
                }
            }
        }
    }
}

class HookConfigProvider : ContentProvider() {

    private companion object {
        const val TAG = "HookProvider"
    }

    override fun onCreate(): Boolean = true

    /**
     * Gate every inbound call on the caller's uid.
     *
     * ## Why not a permission
     *
     * This provider is deliberately `exported` because the hook runs inside `system_server`,
     * which cannot hold an app-signature permission — a `protectionLevel="signature"` guard
     * would lock the framework out of its own config channel.
     *
     * ## Why uid gating
     *
     * Before this check **any installed app** could call `getLocation` on
     * `content://com.mockrun.app.hook.provider` and read the current spoofed coordinates plus
     * the hook-active flag. That leaks the user's spoofed position and reveals that this app is
     * installed and active.
     *
     * Allowed callers: this app itself, the system uid, root and shell. Everything else is
     * refused and recorded.
     *
     * ## Accepted trade-off
     *
     * The hook's ContentProvider channels (used as a fallback in `loadMultiTargetRules()` and
     * `getGlobalActiveLocation()`) stop working when the hook runs inside a third-party app
     * process, because that process's uid is not on the list. Those channels are already
     * last-resort — SystemProperties, the `/data/system` and `/data/local/tmp` files,
     * `Settings.Global` and XSharedPreferences are all tried first — and a refusal is now
     * logged instead of passing silently. If this turns out to matter on a real device, the
     * fix is to have the app push state into XSharedPreferences rather than relaxing this check.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // 分流规则**不**走 uid 门禁。
        //
        // 规则内容（包名 + 用户自己选的坐标）本来就已经通过三条通道暴露给任何进程：
        // 0644 的 hook_config.xml、0644 的 /data/local/tmp/fake_gps_multitarget.json、
        // 以及 Settings.Global —— 在这里加门禁保护不了任何东西，却会让 hook 在**目标应用进程**里
        // 的 ContentProvider 通道必然失效（该 uid 不在白名单里），于是"必须把应用也加进 LSPosed
        // 作用域才能生效"。真正敏感的是实时伪造坐标（getLocation），那一个仍然门禁。
        if (method == "getMultiTargetRules") {
            return Bundle().apply { putString("json", readMultiTargetRulesJson()) }
        }
        if (!isTrustedCaller()) {
            Diag.w(TAG, "refused call '$method' from untrusted uid ${Binder.getCallingUid()}")
            return null
        }
        return when (method) {
            "getLocation" -> {
                HookStateBridge.recordSystemHookHeartbeat()
                Bundle().apply {
                    putBoolean("is_active", HookStateBridge.isHookActive)
                    putDouble("latitude", HookStateBridge.latitude)
                    putDouble("longitude", HookStateBridge.longitude)
                    putDouble("altitude", HookStateBridge.altitude)
                    putFloat("bearing", HookStateBridge.bearing)
                    putFloat("speed", HookStateBridge.speed)
                    putLong("timestamp", HookStateBridge.updateTimestamp)
                }
            }
            "pingSystemServer" -> {
                HookStateBridge.recordSystemHookHeartbeat()
                Bundle().apply {
                    putBoolean("ack", true)
                }
            }
            "getMultiTargetRules" -> {
                // 保留分支：上面的提前返回已经处理，这里只为可读性留一个显式出口。
                Bundle().apply {
                    putString("json", readMultiTargetRulesJson())
                }
            }
            "isHookActive" -> {
                Bundle().apply {
                    putBoolean("is_active", HookStateBridge.isSystemHookAlive() || XposedStatusHelper.isModuleActive())
                }
            }
            "ping" -> {
                Bundle().apply {
                    putBoolean("available", true)
                    putString("version", com.mockrun.app.BuildConfig.VERSION_NAME)
                }
            }
            else -> null
        }
    }

    /**
     * 读分流规则 JSON，按「内存 → 主副本 → XSharedPreferences 兼容副本 → 缓存文件」顺序回落。
     *
     * 为什么不能只读内存：`HookStateBridge.multiTargetRulesJson` 是由 Hilt 单例
     * `MultiTargetRepository` 在构造时灌进去的，而 ContentProvider 可能在任何组件之前被调用
     * （system_server 或目标应用进程的第一次查询）。旧实现只回内存态，于是**模块应用重启之后**，
     * provider 会回答空字符串，system_server 侧读不到规则 → per-app 分流静默退化成真实定位。
     * 这里直接从磁盘副本读，重启不再丢规则。
     */
    private fun readMultiTargetRulesJson(): String {
        val inMemory = HookStateBridge.multiTargetRulesJson
        if (inMemory.isNotEmpty()) return inMemory

        val ctx = runCatching { context }.getOrNull()
        if (ctx == null) {
            Diag.w(TAG, "getMultiTargetRules: no provider context — rules channel degraded")
            return ""
        }

        val fromPrefs = runCatching {
            ctx.getSharedPreferences("multi_target_prefs", Context.MODE_PRIVATE)
                .getString("key_rules_json", null)
        }.logFailure(TAG, "read rules from multi_target_prefs", Diag.Level.DEBUG).getOrNull()
        if (!fromPrefs.isNullOrEmpty()) return fromPrefs

        val fromHookPrefs = runCatching {
            ctx.getSharedPreferences("hook_config", Context.MODE_PRIVATE)
                .getString("multitarget_rules_json", null)
        }.logFailure(TAG, "read rules from hook_config", Diag.Level.DEBUG).getOrNull()
        if (!fromHookPrefs.isNullOrEmpty()) return fromHookPrefs

        val fromCache = runCatching {
            val f = java.io.File(ctx.cacheDir, "multitarget_rules.json")
            if (f.exists() && f.canRead()) f.readText() else null
        }.logFailure(TAG, "read rules from cacheDir copy", Diag.Level.DEBUG).getOrNull()
        return fromCache.orEmpty()
    }

    /** See [call] for why gating happens here rather than via a manifest permission. */
    private fun isTrustedCaller(): Boolean {
        val uid = Binder.getCallingUid()
        // Fully qualified on purpose: this file also uses java.lang.Process for `su` execution,
        // so importing android.os.Process here would shadow it and break those call sites.
        return uid == android.os.Process.myUid() ||
            uid == android.os.Process.SYSTEM_UID ||
            uid == android.os.Process.ROOT_UID ||
            uid == android.os.Process.SHELL_UID
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
