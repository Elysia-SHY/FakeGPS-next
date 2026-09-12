package com.mockrun.app.location

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
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.SimulatedPoint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

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
        const val ACTION_SEEK = "com.mockrun.app.ACTION_SEEK"

        // Single-Point Virtual Location Actions
        const val ACTION_START_POINT_MOCK = "com.mockrun.app.ACTION_START_POINT_MOCK"
        const val ACTION_STOP_POINT_MOCK = "com.mockrun.app.ACTION_STOP_POINT_MOCK"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"

        const val EXTRA_ROUTE = "extra_route"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_SEEK_PROGRESS = "extra_seek_progress"

        const val PROVIDER_NAME = LocationManager.GPS_PROVIDER

        private const val PREFS_MOCK_SERVICE = "mock_location_service_prefs"
        private const val KEY_IS_POINT_MOCK_ACTIVE = "key_is_point_mock_active"
        private const val KEY_SAVED_LAT = "key_saved_lat"
        private const val KEY_SAVED_LON = "key_saved_lon"
        private const val KEY_IS_SIMULATION_ACTIVE = "key_is_sim_active"
        private const val KEY_SIM_SPEED = "key_sim_speed"
        private const val KEY_SIM_PROGRESS = "key_sim_progress"
        private const val KEY_SAVED_ROUTE_B64 = "key_saved_route_b64"
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

        when (action) {
            ACTION_START -> {
                val route = stateRepo.pendingRoute ?: run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        runCatching { intent.getSerializableExtra(EXTRA_ROUTE, Route::class.java) }.getOrNull()
                    } else {
                        @Suppress("DEPRECATION")
                        runCatching { intent.getSerializableExtra(EXTRA_ROUTE) as? Route }.getOrNull()
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
                    startSimulation(route, speed, 0f)
                }
            }
            ACTION_PAUSE -> pauseSimulation()
            ACTION_RESUME -> resumeSimulation()
            ACTION_STOP -> stopSimulation()
            ACTION_SET_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED, currentSpeedKmh)
                changeSpeed(speed)
            }
            ACTION_SEEK -> {
                val progress = intent.getFloatExtra(EXTRA_SEEK_PROGRESS, 0f)
                seekTo(progress)
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
        val sp = getSharedPreferences(PREFS_MOCK_SERVICE, Context.MODE_PRIVATE)
        val isPointActive = sp.getBoolean(KEY_IS_POINT_MOCK_ACTIVE, false)
        val isSimActive = sp.getBoolean(KEY_IS_SIMULATION_ACTIVE, false)
        if (isPointActive || isSimActive || pointMockJob?.isActive == true || simulationJob?.isActive == true) {
            android.util.Log.d("MockLocationService", "onTaskRemoved detected active mock, scheduling resurrection alarm")
            KeepAliveHelper.scheduleServiceResurrection(applicationContext, MockLocationService::class.java)
        }
    }

    private fun restorePersistedState() {
        val sp = getSharedPreferences(PREFS_MOCK_SERVICE, Context.MODE_PRIVATE)
        val isPointActive = sp.getBoolean(KEY_IS_POINT_MOCK_ACTIVE, false)
        if (isPointActive) {
            val latBits = sp.getLong(KEY_SAVED_LAT, java.lang.Double.doubleToRawLongBits(39.9042))
            val lonBits = sp.getLong(KEY_SAVED_LON, java.lang.Double.doubleToRawLongBits(116.4074))
            val lat = java.lang.Double.longBitsToDouble(latBits)
            val lon = java.lang.Double.longBitsToDouble(lonBits)
            android.util.Log.d("MockLocationService", "Resurrected: successfully restoring point mock $lat, $lon")
            startPointMock(lat, lon)
            return
        }

        val isSimActive = sp.getBoolean(KEY_IS_SIMULATION_ACTIVE, false)
        if (isSimActive) {
            val routeB64 = sp.getString(KEY_SAVED_ROUTE_B64, null)
            val savedRoute = routeB64?.let { deserializeRoute(it) } ?: currentRoute
            if (savedRoute != null) {
                val speed = sp.getFloat(KEY_SIM_SPEED, currentSpeedKmh)
                val progress = sp.getFloat(KEY_SIM_PROGRESS, currentProgress)
                android.util.Log.d("MockLocationService", "Resurrected: successfully restoring simulation at $progress")
                startSimulation(savedRoute, speed, progress)
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

    private fun saveSimulationState(active: Boolean, route: Route? = null, speed: Float = 8f, progress: Float = 0f) {
        getSharedPreferences(PREFS_MOCK_SERVICE, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_IS_SIMULATION_ACTIVE, active)
            if (active) {
                putFloat(KEY_SIM_SPEED, speed)
                putFloat(KEY_SIM_PROGRESS, progress)
                route?.let { putString(KEY_SAVED_ROUTE_B64, serializeRoute(it)) }
            } else {
                remove(KEY_SAVED_ROUTE_B64)
            }
            apply()
        }
    }

    private fun serializeRoute(route: Route): String = runCatching {
        val baos = java.io.ByteArrayOutputStream()
        java.io.ObjectOutputStream(baos).use { it.writeObject(route) }
        android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT)
    }.getOrDefault("")

    private fun deserializeRoute(str: String): Route? = runCatching {
        val bytes = android.util.Base64.decode(str, android.util.Base64.DEFAULT)
        val bais = java.io.ByteArrayInputStream(bytes)
        java.io.ObjectInputStream(bais).use { it.readObject() as Route }
    }.getOrNull()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        simulationJob?.cancel()
        pointMockJob?.cancel()
        serviceJob.cancel()
        savePointMockState(active = false)
        saveSimulationState(active = false)
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

    private fun startSimulation(route: Route, speedKmh: Float, startProgress: Float) {
        // Mutual exclusion: cancel point mock
        pointMockJob?.cancel()
        savePointMockState(active = false)
        stateRepo.setPointMock(false)

        currentRoute = route
        currentSpeedKmh = speedKmh
        currentProgress = startProgress

        if (!mockEngine.register()) {
            stateRepo.onError("权限不足：请在手机【开发者选项】中将 Fake GPS 设为「模拟位置信息应用」")
            stopSelf()
            return
        }

        saveSimulationState(active = true, route = route, speed = speedKmh, progress = startProgress)
        stateRepo.onSimulationStarted(route, speedKmh)
        com.mockrun.app.hook.HookStateBridge.setRouteSimulationMode(true)

        simulationJob?.cancel()
        simulationJob = serviceScope.launch {
            simulator.simulateRoute(route, speedKmh, startProgress).collectLatest { point ->
                injectLocation(point)
                currentProgress = point.progressPercent
                stateRepo.onLocationUpdate(point)
                val currentSpeedKmh = point.speed * 3.6f
                sensorEngine.updateTick(currentSpeedKmh, 1.0f)
                saveSimulationState(active = true, route = route, speed = speedKmh, progress = currentProgress)
                updateNotification("进度: ${(point.progressPercent * 100).toInt()}% | ${(point.distanceTraveled / 1000.0).format(2)} km")

                if (point.isCompleted) {
                    saveSimulationState(active = false)
                    sensorEngine.updateTick(0f, 0f)
                    stateRepo.onSimulationCompleted()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun pauseSimulation() {
        simulationJob?.cancel()
        sensorEngine.updateTick(0f, 0f)
        stateRepo.onPaused(currentProgress)
        updateNotification("模拟已暂停 (进度: ${(currentProgress * 100).toInt()}%)")
    }

    private fun resumeSimulation() {
        val route = currentRoute ?: return
        startSimulation(route, currentSpeedKmh, currentProgress)
    }

    private fun stopSimulation() {
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
        // Restart simulator at current progress with new speed
        simulationJob?.cancel()
        startSimulation(route, newSpeedKmh, currentProgress)
    }

    private fun seekTo(progress: Float) {
        val route = currentRoute ?: return
        currentProgress = progress.coerceIn(0f, 1f)
        simulationJob?.cancel()
        startSimulation(route, currentSpeedKmh, currentProgress)
    }

    private fun startPointMock(lat: Double, lon: Double) {
        // Mutual exclusion: cancel route simulation
        simulationJob?.cancel()
        saveSimulationState(active = false)
        stateRepo.onStopped()
        com.mockrun.app.hook.HookStateBridge.setRouteSimulationMode(false)
        pointMockJob?.cancel()

        if (!mockEngine.register()) {
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
            while (isActive) {
                runCatching {
                    // Natural micro-jitter (±0.2m) to mimic authentic GPS drift and bypass anti-cheat
                    val jitterLat = (kotlin.random.Random.nextDouble(-1.0, 1.0) * 0.000002)
                    val jitterLon = (kotlin.random.Random.nextDouble(-1.0, 1.0) * 0.000002)
                    val currentLat = lat + jitterLat
                    val currentLon = lon + jitterLon
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
                }
                delay(500) // 2Hz continuous injection
            }
        }
    }

    private fun stopPointMock() {
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
