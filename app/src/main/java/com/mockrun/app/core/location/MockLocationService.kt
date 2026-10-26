package com.mockrun.app.core.location

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mockrun.app.MainActivity
import com.mockrun.app.R
import com.google.gson.Gson
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.RouteMode
import com.mockrun.app.domain.model.SimulatedPoint
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import kotlin.math.abs

/**
 * Foreground Service responsible for injecting mock GPS coordinates into the Android OS.
 *
 * Requirements:
 * 1. User must have set this app as the "Mock location app" in Developer Options.
 * 2. Manifest must declare FOREGROUND_SERVICE and FOREGROUND_SERVICE_LOCATION (Android 14+).
 * 3. Holds a PARTIAL_WAKE_LOCK to prevent CPU sleep during background simulation.
 */
@AndroidEntryPoint
class MockLocationService : Service() {

    companion object {
        const val CHANNEL_ID = "mock_location_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.mockrun.app.ACTION_START"
        const val ACTION_PAUSE = "com.mockrun.app.ACTION_PAUSE"
        const val ACTION_RESUME = "com.mockrun.app.ACTION_RESUME"
        const val ACTION_STOP = "com.mockrun.app.ACTION_STOP"
        const val ACTION_SET_SPEED = "com.mockrun.app.ACTION_SET_SPEED"
        /** 旧接口：按进度 0..1 定位。保留只为向后兼容，新代码用 [ACTION_SEEK_DISTANCE]。 */
        const val ACTION_SEEK = "com.mockrun.app.ACTION_SEEK"
        /** v2.3.0 新增：切换运行方式（单程 / 折返 / 环形）。 */
        const val ACTION_SET_MODE = "com.mockrun.app.ACTION_SET_MODE"
        /** v2.3.0 新增：按绝对距离（米）定位，循环模式下用它才不会跳回第一轮。 */
        const val ACTION_SEEK_DISTANCE = "com.mockrun.app.ACTION_SEEK_DISTANCE"

        // Single-Point Virtual Location Actions
        const val ACTION_START_POINT_MOCK = "com.mockrun.app.ACTION_START_POINT_MOCK"
        const val ACTION_STOP_POINT_MOCK = "com.mockrun.app.ACTION_STOP_POINT_MOCK"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"

        const val EXTRA_ROUTE = "extra_route"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_SEEK_PROGRESS = "extra_seek_progress"
        const val EXTRA_ROUTE_MODE = "extra_route_mode"
        const val EXTRA_SEEK_DISTANCE_M = "extra_seek_distance_m"

        const val PROVIDER_NAME = LocationManager.GPS_PROVIDER

        private const val PREFS_MOCK_SERVICE = "mock_location_service_prefs"

        private const val TAG = "MockLocationService"
        private const val KEY_IS_POINT_MOCK_ACTIVE = "key_is_point_mock_active"
        private const val KEY_SAVED_LAT = "key_saved_lat"
        private const val KEY_SAVED_LON = "key_saved_lon"
        private const val KEY_IS_SIMULATION_ACTIVE = "key_is_sim_active"
        private const val KEY_SIM_SPEED = "key_sim_speed"
        private const val KEY_SIM_PROGRESS = "key_sim_progress"
        private const val KEY_SIM_DISTANCE_M = "key_sim_distance_m"
        private const val KEY_SIM_MODE = "key_sim_mode"
        private const val KEY_SAVED_ROUTE_B64 = "key_saved_route_b64"
        /** 路线快照格式前缀：Gson JSON（v2 起）。旧的无前缀 Base64 是 v2.2.0 的 Java 序列化，读不回来了。 */
        private const val ROUTE_SNAPSHOT_V2_PREFIX = "v2:"
    }

    @Inject lateinit var simulator: RouteSimulator
    @Inject lateinit var stateRepo: SimulationStateRepository
    @Inject lateinit var sensorEngine: SensorMockEngine

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var simulationJob: Job? = null
    private var pointMockJob: Job? = null

    private lateinit var locationManager: LocationManager
    private lateinit var mockEngine: MockLocationEngine
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentRoute: Route? = null
    private var currentSpeedKmh: Float = 8f
    private var currentProgress: Float = 0f
    /** 绝对续跑位置（米，跨轮累计）。changeSpeed / seek / 复活全部按它续跑。 */
    private var currentDistanceMeters: Double = 0.0
    private var currentMode: RouteMode = RouteMode.ONE_WAY
    private var currentSessionId: Long = 0L
    private var userStopped: Boolean = false

    // 通知节流：循环模式永不结束，每秒 notify 会整夜点亮息屏并耗电
    private var lastNotifyAt: Long = 0L
    private var lastNotifiedPercent: Float = -1f

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        mockEngine = MockLocationEngine(this)
        createNotificationChannel()
        val notif = buildNotification("Fake GPS 模拟服务已就绪")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            // Service was resurrected by Android OS after being killed (START_STICKY)
            restorePersistedState()
            return START_STICKY
        }

        val action = intent.action ?: return START_STICKY

        // 录制必须读取真实 GPS；开始或恢复任何模拟前先把已采轨迹正常保存。
        if (TrackRecordState.isRecording.value && action in setOf(
                ACTION_START, ACTION_RESUME, ACTION_START_POINT_MOCK
            )) {
            Diag.i(TAG, "模拟请求与轨迹录制冲突，先停止并保存轨迹")
            startService(Intent(this, TrackRecordService::class.java).apply {
                this.action = TrackRecordService.ACTION_STOP
            })
        }

        when (action) {
            ACTION_START -> {
                val route = stateRepo.pendingRoute ?: run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        runCatching { intent.getSerializableExtra(EXTRA_ROUTE, Route::class.java) }.logFailure(TAG, "read EXTRA_ROUTE (T+)").getOrNull()
                    } else {
                        @Suppress("DEPRECATION")
                        runCatching { intent.getSerializableExtra(EXTRA_ROUTE) as? Route }.logFailure(TAG, "read EXTRA_ROUTE (legacy)").getOrNull()
                    }
                }
                val speed = intent.getFloatExtra(EXTRA_SPEED, 8f)
                if (route != null) {
                    val notif = buildNotification("正在模拟: ${route.name} (${speed} km/h)")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    } else {
                        startForeground(NOTIFICATION_ID, notif)
                    }
                    startSimulation(route, speed, 0.0, modeOf(intent, route))
                }
            }
            ACTION_PAUSE -> pauseSimulation()
            ACTION_RESUME -> resumeSimulation()
            ACTION_STOP -> stopSimulation()
            ACTION_SET_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED, currentSpeedKmh)
                changeSpeed(speed)
            }
            ACTION_SET_MODE -> {
                val mode = RouteMode.fromName(intent.getStringExtra(EXTRA_ROUTE_MODE))
                setRouteMode(mode)
            }
            ACTION_SEEK_DISTANCE -> {
                val distance = intent.getDoubleExtra(EXTRA_SEEK_DISTANCE_M, currentDistanceMeters)
                seekToDistance(distance)
            }
            ACTION_SEEK -> {
                // 旧接口：进度 0..1，折算成距离后统一走距离续跑
                val progress = intent.getFloatExtra(EXTRA_SEEK_PROGRESS, 0f)
                seekToDistance(progressToDistance(progress))
            }
            ACTION_START_POINT_MOCK -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 39.9042)
                val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, 116.4074)
                startPointMock(lat, lon)
            }
            ACTION_STOP_POINT_MOCK -> {
                stopPointMock()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        userStopped = true
        // 用户从最近任务里划掉卡片是一个明确的结束意图。
        //
        // 此前这里会调度复活闹钟把服务拉回来继续模拟，而恢复出来的状态又会持续刷新 hook 的租约，
        // 于是表现为「应用已经关掉了，位置却还停在伪造点上，怎么都回不来」。现在改成真正停下来：
        // 停掉两个注入协程、把停止状态写回所有持久通道，再结束前台服务。
        val sp = getSharedPreferences(PREFS_MOCK_SERVICE, Context.MODE_PRIVATE)
        val wasActive = pointMockJob?.isActive == true ||
            simulationJob?.isActive == true ||
            sp.getBoolean(KEY_IS_POINT_MOCK_ACTIVE, false) ||
            sp.getBoolean(KEY_IS_SIMULATION_ACTIVE, false)
        if (!wasActive) return

        Diag.i(TAG, "task removed by user — stopping mock and clearing hook state")
        runCatching { stopSimulation() }.logFailure(TAG, "stopSimulation on task removal")
        runCatching { stopPointMock() }.logFailure(TAG, "stopPointMock on task removal")
        runCatching { com.mockrun.app.hook.HookStateBridge.update(this, false) }
            .logFailure(TAG, "clear hook state on task removal")
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }.logFailure(TAG, "stopForeground on task removal")
        runCatching { stopSelf() }.logFailure(TAG, "stopSelf on task removal")
    }

    private fun restorePersistedState() {
        val sp = getSharedPreferences(PREFS_MOCK_SERVICE, Context.MODE_PRIVATE)
        val isPointActive = sp.getBoolean(KEY_IS_POINT_MOCK_ACTIVE, false)
        if (isPointActive) {
            val latBits = sp.getLong(KEY_SAVED_LAT, java.lang.Double.doubleToRawLongBits(39.9042))
            val lonBits = sp.getLong(KEY_SAVED_LON, java.lang.Double.doubleToRawLongBits(116.4074))
            val lat = java.lang.Double.longBitsToDouble(latBits)
            val lon = java.lang.Double.longBitsToDouble(lonBits)
            Diag.i(TAG, "Resurrected: successfully restoring point mock $lat, $lon")
            startPointMock(lat, lon)
            return
        }

        val isSimActive = sp.getBoolean(KEY_IS_SIMULATION_ACTIVE, false)
        if (isSimActive) {
            val routeB64 = sp.getString(KEY_SAVED_ROUTE_B64, null)
            val savedRoute = routeB64?.let { deserializeRoute(it) } ?: currentRoute
            if (savedRoute != null) {
                val speed = sp.getFloat(KEY_SIM_SPEED, currentSpeedKmh)
                val mode = RouteMode.fromName(sp.getString(KEY_SIM_MODE, savedRoute.mode.name))
                // 新格式按米续跑；只有旧版本写入的库才退回百分比折算（读一次就删，避免语义串台）
                val legacyProgress = if (sp.contains(KEY_SIM_PROGRESS)) {
                    sp.getFloat(KEY_SIM_PROGRESS, 0f).also {
                        sp.edit().remove(KEY_SIM_PROGRESS).apply()
                    }
                } else {
                    null
                }
                val distance = if (sp.contains(KEY_SIM_DISTANCE_M)) {
                    sp.getFloat(KEY_SIM_DISTANCE_M, 0f).toDouble()
                } else {
                    savedRoute.totalDistanceMeters * (legacyProgress ?: 0f)
                }
                Diag.i(TAG, "Resurrected: restoring simulation at ${distance.toInt()} m, mode=$mode")
                startSimulation(savedRoute.copy(mode = mode), speed, distance, mode)
            } else {
                // 旧 Java 快照无法跨隐式 UID 读取；失败必须收尾，不能 sticky 空转并持有 WakeLock。
                Diag.w(TAG, "路线快照不可恢复，清理模拟状态并停止空服务")
                userStopped = true
                saveSimulationState(active = false)
                stateRepo.onStopped()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun savePointMockState(active: Boolean, lat: Double = 0.0, lon: Double = 0.0) {
        getSharedPreferences(PREFS_MOCK_SERVICE, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_IS_POINT_MOCK_ACTIVE, active)
            if (active) {
                putLong(KEY_SAVED_LAT, java.lang.Double.doubleToRawLongBits(lat))
                putLong(KEY_SAVED_LON, java.lang.Double.doubleToRawLongBits(lon))
            }
            apply()
        }
    }

    private fun saveSimulationState(
        active: Boolean,
        route: Route? = null,
        speed: Float = 8f,
        distanceMeters: Double = 0.0,
        mode: RouteMode = RouteMode.ONE_WAY
    ) {
        getSharedPreferences(PREFS_MOCK_SERVICE, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_IS_SIMULATION_ACTIVE, active)
            if (active) {
                putFloat(KEY_SIM_SPEED, speed)
                putFloat(KEY_SIM_DISTANCE_M, distanceMeters.toFloat())
                putString(KEY_SIM_MODE, mode.name)
                route?.let { putString(KEY_SAVED_ROUTE_B64, serializeRoute(it)) }
            } else {
                remove(KEY_SAVED_ROUTE_B64)
                remove(KEY_SIM_DISTANCE_M)
                remove(KEY_SIM_MODE)
            }
            apply()
        }
    }

    private fun saveSimulationProgress(distanceMeters: Double, speed: Float, progressPercent: Float) {
        getSharedPreferences(PREFS_MOCK_SERVICE, Context.MODE_PRIVATE).edit().apply {
            putFloat(KEY_SIM_DISTANCE_M, distanceMeters.toFloat())
            putFloat(KEY_SIM_PROGRESS, progressPercent)   // 仅用于 UI 恢复显示，不再参与续跑
            putFloat(KEY_SIM_SPEED, speed)
            apply()
        }
    }

    /**
     * 路线快照：Gson JSON + `v2:` 前缀。
     *
     * 不能再用 Java 序列化：`Route` 加了 `mode` 字段后隐式 serialVersionUID 变化，
     * 旧快照反序列化必抛 `InvalidClassException`，被 runCatching 吞成 null，
     * 表现就是「服务被杀后复活，路线没了」。
     */
    private fun serializeRoute(route: Route): String = runCatching {
        ROUTE_SNAPSHOT_V2_PREFIX + Gson().toJson(route)
    }.logFailure(TAG, "serializeRoute", Diag.Level.DEBUG).getOrDefault("")

    private fun deserializeRoute(str: String): Route? = runCatching {
        when {
            str.startsWith(ROUTE_SNAPSHOT_V2_PREFIX) ->
                Gson().fromJson(str.removePrefix(ROUTE_SNAPSHOT_V2_PREFIX), Route::class.java)
            // v2.2.0 的旧 Base64 快照：UID 已变，读不回来，只能返回 null 走兜底路线
            else -> null
        }
    }.logFailure(TAG, "deserializeRoute", Diag.Level.DEBUG).getOrNull()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        simulationJob?.cancel()
        pointMockJob?.cancel()
        serviceJob.cancel()
        if (userStopped) {
            savePointMockState(active = false)
            saveSimulationState(active = false)
        }
        if (!stateRepo.isJoystickActive.value) {
            mockEngine.unregister()
            MockLocationEngine.forceCleanAllTestProviders(this)
        }
        releaseWakeLock()
        stateRepo.setPointMock(false)
        stateRepo.onStopped()
        com.mockrun.app.hook.HookStateBridge.setRouteSimulationMode(false)
        com.mockrun.app.hook.HookStateBridge.update(this, false)
        CoordinateConverter.flushRealLocation(this)
    }

    // ---- Simulation Control ----

    private fun startSimulation(
        route: Route,
        speedKmh: Float,
        startDistanceMeters: Double,
        mode: RouteMode = route.mode
    ) {
        userStopped = false
        val sessionId = ++currentSessionId

        // Mutual exclusion: cancel point mock
        pointMockJob?.cancel()
        savePointMockState(active = false)
        stateRepo.setPointMock(false)

        val simRoute = route.copy(mode = mode)
        currentRoute = simRoute
        currentSpeedKmh = speedKmh
        currentMode = mode
        currentDistanceMeters = startDistanceMeters.coerceAtLeast(0.0)
        lastNotifyAt = 0L
        lastNotifiedPercent = -1f

        if (!mockEngine.isRegistered() && !mockEngine.register()) {
            stateRepo.onError("权限不足：请在手机【开发者选项】中将 Fake GPS 设为「模拟位置信息应用」")
            stopSelf()
            return
        }

        saveSimulationState(
            active = true, route = simRoute, speed = speedKmh,
            distanceMeters = currentDistanceMeters, mode = mode
        )
        stateRepo.onSimulationStarted(simRoute, speedKmh)
        com.mockrun.app.hook.HookStateBridge.setRouteSimulationMode(true)

        launchSimulationLoop(sessionId, simRoute, speedKmh, currentDistanceMeters, mode)
    }

    private fun launchSimulationLoop(
        sessionId: Long,
        route: Route,
        speedKmh: Float,
        startDistanceMeters: Double,
        mode: RouteMode
    ) {
        simulationJob?.cancel()
        simulationJob = serviceScope.launch {
            var completed = false
            simulator.simulateRoute(route, speedKmh, startDistanceMeters, mode).collectLatest { point ->
                if (sessionId != currentSessionId || !isActive) return@collectLatest
                injectLocation(point)
                currentProgress = point.progressPercent
                currentDistanceMeters = point.distanceTraveled
                stateRepo.onLocationUpdate(point)
                val dynamicSpeedKmh = point.speed * 3.6f
                sensorEngine.updateTick(dynamicSpeedKmh, 1.0f)
                saveSimulationProgress(
                    distanceMeters = point.distanceTraveled,
                    speed = currentSpeedKmh,
                    progressPercent = point.progressPercent
                )
                if (shouldRefreshNotification(point.progressPercent)) {
                    updateNotification(notificationText(mode, point))
                }

                if (point.isCompleted) {
                    completed = true
                    userStopped = true
                    saveSimulationState(active = false)
                    sensorEngine.updateTick(0f, 0f)
                    stateRepo.onSimulationCompleted()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            // 兜底：flow 正常结束却没发 isCompleted（路线不足两点、路线被删、ADB 推了空路线），
            // 不收拾的话前台服务与 24h WakeLock 会一直挂着。
            if (!completed && !userStopped && sessionId == currentSessionId) {
                Diag.w(TAG, "simulateRoute 已结束但没收到 isCompleted，主动收尾")
                stopSimulation()
            }
        }
    }

    /** 通知节流：循环模式永不结束，每秒 notify 会整夜点亮息屏。 */
    private fun shouldRefreshNotification(progressPercent: Float): Boolean {
        val now = SystemClock.elapsedRealtime()
        val percentJumped = abs(progressPercent - lastNotifiedPercent) >= 0.01f
        if (now - lastNotifyAt >= 5_000L && (percentJumped || lastNotifiedPercent < 0f)) {
            lastNotifyAt = now
            lastNotifiedPercent = progressPercent
            return true
        }
        return false
    }

    private fun notificationText(mode: RouteMode, point: SimulatedPoint): String {
        val km = point.distanceTraveled / 1000.0
        return if (mode == RouteMode.ONE_WAY) {
            "进度: ${(point.progressPercent * 100).toInt()}% | ${km.format(2)} km"
        } else {
            val speed = point.speed * 3.6f
            "第 ${point.lapIndex + 1} 轮 · 累计 ${km.format(2)} km · ${speed.toDouble().format(1)} km/h"
        }
    }

    private fun pauseSimulation() {
        currentSessionId++
        simulationJob?.cancel()
        sensorEngine.updateTick(0f, 0f)
        stateRepo.onPaused(currentProgress)
        updateNotification("模拟已暂停 (进度: ${(currentProgress * 100).toInt()}%)")
    }

    private fun resumeSimulation() {
        val route = currentRoute ?: return
        val sessionId = ++currentSessionId
        stateRepo.onSimulationStarted(route, currentSpeedKmh)
        launchSimulationLoop(sessionId, route, currentSpeedKmh, currentDistanceMeters, currentMode)
    }

    private fun stopSimulation() {
        userStopped = true
        currentSessionId++
        simulationJob?.cancel()
        sensorEngine.updateTick(0f, 0f)
        saveSimulationState(active = false)
        if (!stateRepo.isJoystickActive.value) {
            mockEngine.unregister()
            MockLocationEngine.forceCleanAllTestProviders(this)
        }
        stateRepo.onStopped()
        com.mockrun.app.hook.HookStateBridge.setRouteSimulationMode(false)
        com.mockrun.app.hook.HookStateBridge.update(this, false)
        CoordinateConverter.flushRealLocation(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun changeSpeed(newSpeedKmh: Float) {
        currentSpeedKmh = newSpeedKmh
        stateRepo.setSpeed(newSpeedKmh)
        val route = currentRoute ?: return
        val sessionId = ++currentSessionId
        launchSimulationLoop(sessionId, route, newSpeedKmh, currentDistanceMeters, currentMode)
    }

    /** 按绝对距离续跑；进度条与旧广播都折算到米之后走这里。 */
    private fun seekToDistance(distanceMeters: Double) {
        val route = currentRoute ?: return
        currentDistanceMeters = distanceMeters.coerceAtLeast(0.0)
        val sessionId = ++currentSessionId
        launchSimulationLoop(sessionId, route, currentSpeedKmh, currentDistanceMeters, currentMode)
    }

    private fun setRouteMode(mode: RouteMode) {
        val route = currentRoute
        currentMode = mode
        if (route == null) {
            Diag.i(TAG, "运行方式切换为 $mode（当前无路线，仅记录）")
            return
        }
        val updated = route.copy(mode = mode)
        currentRoute = updated
        Diag.i(TAG, "运行方式切换为 $mode")
        saveSimulationState(
            active = true, route = updated, speed = currentSpeedKmh,
            distanceMeters = currentDistanceMeters, mode = mode
        )
        if (simulationJob?.isActive == true) {
            val sessionId = ++currentSessionId
            launchSimulationLoop(sessionId, updated, currentSpeedKmh, currentDistanceMeters, mode)
        }
    }

    /** 兼容旧的「进度 0..1」入参：折算成绝对距离。 */
    private fun progressToDistance(progress: Float): Double {
        val route = currentRoute ?: return currentDistanceMeters
        val lapDistance = if (currentMode == RouteMode.LOOP && route.closingGapMeters <= RouteSimulator.LOOP_MAX_GAP_M) {
            route.totalDistanceMeters + route.closingGapMeters
        } else {
            route.totalDistanceMeters
        }
        if (lapDistance <= 0.0) return 0.0
        val lapBase = if (currentMode == RouteMode.ONE_WAY) 0.0 else {
            kotlin.math.floor(currentDistanceMeters / lapDistance) * lapDistance
        }
        return lapBase + lapDistance * progress.coerceIn(0f, 1f)
    }

    private fun modeOf(intent: Intent, route: Route): RouteMode {
        val raw = intent.getStringExtra(EXTRA_ROUTE_MODE)
        return if (raw != null) RouteMode.fromName(raw) else route.mode
    }

    private fun startPointMock(lat: Double, lon: Double) {
        userStopped = false
        val sessionId = ++currentSessionId

        // Mutual exclusion: cancel route simulation
        simulationJob?.cancel()
        saveSimulationState(active = false)
        stateRepo.onStopped()
        com.mockrun.app.hook.HookStateBridge.setRouteSimulationMode(false)
        pointMockJob?.cancel()

        if (!mockEngine.isRegistered() && !mockEngine.register()) {
            stateRepo.onError("权限不足：请在手机【开发者选项】中将 Fake GPS 设为「模拟位置信息应用」")
            stopSelf()
            return
        }

        savePointMockState(active = true, lat = lat, lon = lon)
        stateRepo.setPointMock(true, com.mockrun.app.domain.model.WayPoint(lat, lon))
        stateRepo.updateJoystickLocation(lat, lon)
        com.mockrun.app.hook.HookStateBridge.update(this, true, lat, lon)
        val notif = buildNotification("单点虚拟定位已生效: ${"%.4f".format(lat)}, ${"%.4f".format(lon)}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }

        // IMMEDIATELY inject upon start!
        mockEngine.inject(
            latitude = lat,
            longitude = lon,
            altitude = 25.0,
            speedMps = 0f,
            bearingDeg = 0f,
            accuracyM = 1.0f
        )

        pointMockJob = serviceScope.launch {
            var tick = 0
            // 真实 GNSS 静止时的水平误差典型值 3-8 m，而且是连续游走（有惯性），
            // 不是每 tick 独立重摇的白噪声。原实现用 ±0.000002°（约 ±0.22 m）
            // 的独立随机 —— 散点是一个半径 0.22 m 的圆，比真实 GPS 稳定得多。
            //
            // 改用 Ornstein-Uhlenbeck 过程近似：每 tick 保留大部分上一次偏移，
            // 只注入少量新扰动。稳态半径约 ±4 m，且是连续游走。
            var ouLat = 0.0
            var ouLon = 0.0
            while (isActive && sessionId == currentSessionId) {
                runCatching {
                    val ouStep = 0.000005
                    val ouRetain = 0.92
                    ouLat = ouLat * ouRetain + kotlin.random.Random.nextDouble(-0.6, 0.6) * ouStep
                    ouLon = ouLon * ouRetain + kotlin.random.Random.nextDouble(-0.6, 0.6) * ouStep
                    val currentLat = lat + ouLat
                    val currentLon = lon + ouLon
                    mockEngine.inject(
                        latitude = currentLat,
                        longitude = currentLon,
                        altitude = 25.0,
                        speedMps = 0f,
                        bearingDeg = 0f,
                        accuracyM = 1.0f
                    )

                    tick++
                    if (tick % 2 == 0) {
                        // Keep hook heartbeat alive across processes
                        com.mockrun.app.hook.HookStateBridge.update(
                            this@MockLocationService,
                            true,
                            currentLat,
                            currentLon,
                            25.0,
                            0f,
                            0f
                        )
                    }
                    if (tick % 20 == 0) {
                        updateNotification("单点虚拟定位生效中: ${"%.4f".format(lat)}, ${"%.4f".format(lon)}")
                    }
                }.logFailure(TAG, "point-mock tick", Diag.Level.DEBUG)
                delay(500) // 2Hz continuous injection
            }
        }
    }

    private fun stopPointMock() {
        userStopped = true
        currentSessionId++
        pointMockJob?.cancel()
        savePointMockState(active = false)
        stateRepo.setPointMock(false)
        com.mockrun.app.hook.HookStateBridge.update(this, false)
        CoordinateConverter.flushRealLocation(this)
        if (!stateRepo.isJoystickActive.value) {
            mockEngine.unregister()
            MockLocationEngine.forceCleanAllTestProviders(this)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun injectLocation(point: SimulatedPoint) {
        mockEngine.inject(
            latitude = point.latitude,
            longitude = point.longitude,
            altitude = point.altitude,
            speedMps = point.speed,
            bearingDeg = point.bearing,
            accuracyM = 2.0f
        )
        com.mockrun.app.hook.HookStateBridge.update(
            context = this,
            active = true,
            lat = point.latitude,
            lon = point.longitude,
            alt = point.altitude,
            bear = point.bearing,
            spd = point.speed
        )
    }

    // ---- Foreground Notification & WakeLock ----

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "模拟定位常驻通知与后台保活"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 102, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fake GPS 定位模拟运行中")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止模拟", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FakeGPS::SimulationWakeLock").apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours keep-alive
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}
