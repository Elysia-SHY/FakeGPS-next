package com.mockrun.app.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mockrun.app.MainActivity
import com.mockrun.app.R
import com.mockrun.app.core.data.repository.TrackRepository
import com.mockrun.app.domain.model.RecordedTrack
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.TrackPoint
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 轨迹录制前台服务。
 *
 * 与 [MockLocationService] 的语义相反：从最近任务划掉不是「明确停止」，而是
 * 「停止采集并保存已采到的点」——口袋录制最怕的就是划一下全丢。
 *
 * 录制期间读的是真实定位，所以启动前必须确保 mock 已停（UI 层做互斥检查，
 * 服务里再兜一层：[SimulationStateRepository] 显示 Running 时直接拒绝）。
 */
@AndroidEntryPoint
class TrackRecordService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "TrackRecordService"
        const val ACTION_START = "com.mockrun.app.ACTION_TRACK_START"
        const val ACTION_STOP = "com.mockrun.app.ACTION_TRACK_STOP"
        const val ACTION_TOGGLE_FAVORITE = "com.mockrun.app.ACTION_TRACK_FAVORITE"

        private const val CHANNEL_ID = "track_record_channel"
        private const val NOTIFICATION_ID = 1002
        private const val WAKE_LOCK_TAG = "FakeGPS::TrackRecordWakeLock"
        private const val MAX_ACCURACY_M = 35f
        private const val MAX_SPEED_MPS = 12.0   // 走路/跑步物理上限，超过视为跳点
        private const val MIN_MOVE_M = 0.6       // 静止抖动合并
        private const val CADENCE_WINDOW_NS = 10_000_000_000L
    }

    @Inject lateinit var trackRepository: TrackRepository
    @Inject lateinit var stateRepo: SimulationStateRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var locationManager: LocationManager
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val points = ArrayList<TrackPoint>()
    private var distanceMeters = 0.0
    private var startedAt = 0L
    private var lastAccepted: Location? = null
    private var lastNotifyAt = 0L

    private var stepBaseline: Long = -1L
    private var stepOffset: Long = 0L
    private var lastTotalSteps: Long = 0L
    private var currentCadence = 0
    private val stepWindow = ArrayDeque<Pair<Long, Long>>()

    private var recording = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = handleLocation(location)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) {
            Diag.w(TAG, "定位提供者被关闭: $provider")
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finishRecording(save = true)
                return START_NOT_STICKY
            }
            else -> Unit
        }

        // 录制读的是真实定位；mock 在跑的时候录到的全是假点，直接拒绝
        val mockRunning = stateRepo.currentState.status is com.mockrun.app.domain.model.SimulationStatus.Running ||
            stateRepo.isPointMockActive.value ||
            stateRepo.isJoystickActive.value
        if (mockRunning) {
            Diag.w(TAG, "拒绝录制：模拟/单点/摇杆正在运行")
            TrackRecordState.onStopped()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat("正在准备录制…")
        if (!recording) beginRecording()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 划掉任务 = 用户走开了，不是要丢数据：停下来并保存
        Diag.i(TAG, "task removed — saving partial track")
        finishRecording(save = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { locationManager.removeUpdates(locationListener) }
        runCatching { sensorManager?.unregisterListener(this) }
        releaseWakeLock()
        TrackRecordState.onStopped()
        scope.cancel()
    }

    // ---- Recording lifecycle ----

    private fun beginRecording() {
        recording = true
        points.clear()
        distanceMeters = 0.0
        lastAccepted = null
        startedAt = System.currentTimeMillis()
        stepBaseline = -1L
        stepOffset = 0L
        stepWindow.clear()
        currentCadence = 0
        TrackRecordState.onStarted(startedAt)

        acquireWakeLock()

        val hasPermission = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Diag.w(TAG, "缺少定位权限，无法录制")
            TrackRecordState.onStopped()
            stopSelf()
            return
        }

        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,              // 1 Hz
                0f,                 // 不能设距离滤波：口袋里慢走会被掐掉
                locationListener,
                Looper.getMainLooper()
            )
        }.logFailure(TAG, "requestLocationUpdates")

        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            val ok = runCatching {
                sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI, 0)
            }.logFailure(TAG, "register STEP_COUNTER（可能需要 ACTIVITY_RECOGNITION 权限）").getOrNull()
            if (ok == false) Diag.w(TAG, "计步传感器注册失败，步频将退化为估算")
        } else {
            Diag.w(TAG, "设备没有 TYPE_STEP_COUNTER，步频将退化为估算")
        }

        updateNotification("录制中 · 0.00 km · 0 点")
    }

    private fun handleLocation(location: Location) {
        if (!recording) return
        if (location.accuracy > MAX_ACCURACY_M) return

        val prev = lastAccepted
        var stepDistance = 0.0
        if (prev != null) {
            stepDistance = Route.haversineDistance(
                WayPoint(prev.latitude, prev.longitude),
                WayPoint(location.latitude, location.longitude)
            )
            val dtSec = (location.time - prev.time) / 1000.0
            if (dtSec > 0.0) {
                val speed = stepDistance / dtSec
                if (speed > MAX_SPEED_MPS) return          // 跳点
                if (stepDistance < MIN_MOVE_M && speed < 0.4) return   // 静止抖动
            }
        }

        distanceMeters += stepDistance
        lastAccepted = location

        val cadence = if (currentCadence > 0) currentCadence else estimateCadence(location)
        val point = TrackPoint(
            lat = location.latitude,
            lon = location.longitude,
            alt = if (location.hasAltitude()) location.altitude else 0.0,
            speedMps = if (location.hasSpeed()) location.speed else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            accuracyM = location.accuracy,
            tMs = location.time,
            cadenceSpm = cadence,
            steps = sessionSteps()
        )
        points += point
        TrackRecordState.onProgress(distanceMeters, points.size)

        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyAt >= 5_000L) {
            lastNotifyAt = now
            updateNotification(
                "录制中 · ${"%.2f".format(distanceMeters / 1000.0)} km · ${points.size} 点" +
                    if (cadence > 0) " · $cadence SPM" else ""
            )
        }
    }

    /** 没有计步传感器时按速度估一个步频，并在 UI 标注「估算」。 */
    private fun estimateCadence(location: Location): Int {
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        if (speedKmh <= 0.3f) return 0
        return when {
            speedKmh < 6.5f -> 110
            speedKmh < 10f -> 160
            else -> 175
        }
    }

    private fun finishRecording(save: Boolean) {
        if (!recording) {
            stopSelf()
            return
        }
        recording = false
        runCatching { locationManager.removeUpdates(locationListener) }
        runCatching { sensorManager?.unregisterListener(this) }
        releaseWakeLock()
        TrackRecordState.onStopped()

        val snapshot = points.toList()
        val distance = distanceMeters
        val start = startedAt

        if (!save || snapshot.size < 2) {
            updateNotification("录制已停止（点数不足，未保存）")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val track = buildTrack(snapshot, distance, start)
        scope.launch {
            runCatching { trackRepository.save(track) }
                .logFailure(TAG, "save track '${track.name}'")
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun buildTrack(snapshot: List<TrackPoint>, distance: Double, start: Long): RecordedTrack {
        val end = System.currentTimeMillis()
        val duration = (end - start).coerceAtLeast(0L)
        val avgKmh = if (duration > 0) (distance / duration * 3600.0).toFloat() else 0f
        val validCadence = snapshot.map { it.cadenceSpm }.filter { it in 30..250 }
        val avgCadence = if (validCadence.isEmpty()) 0 else validCadence.average().roundToInt()
        val closed = snapshot.size > 3 && distance > 300.0 &&
            Route.haversineDistance(
                WayPoint(snapshot.first().lat, snapshot.first().lon),
                WayPoint(snapshot.last().lat, snapshot.last().lon)
            ) < 30.0
        val hasAltitude = snapshot.any { it.alt != 0.0 }
        val maxGap = (snapshot.maxOfOrNull { it.tMs } ?: 0L) - (snapshot.minOfOrNull { it.tMs } ?: 0L)
        val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(start))
        return RecordedTrack(
            name = "轨迹 $stamp",
            points = snapshot,
            distanceMeters = distance,
            durationMs = maxGap.coerceAtLeast(duration),
            avgSpeedKmh = avgKmh,
            avgCadenceSpm = avgCadence,
            stepCount = sessionSteps(),
            startedAt = start,
            endedAt = end,
            isClosed = closed,
            hasAltitude = hasAltitude
        )
    }

    private fun sessionSteps(): Long {
        if (stepBaseline < 0) return 0L
        return stepOffset + (lastTotalSteps - stepBaseline)
    }

    // ---- Step sensor ----

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values[0].toLong()
        if (stepBaseline < 0) {
            stepBaseline = total
            lastTotalSteps = total
            return
        }
        if (total < lastTotalSteps) {
            // 设备重启后 TYPE_STEP_COUNTER 归零：把已累计的部分折进 offset
            stepOffset += lastTotalSteps - stepBaseline
            stepBaseline = total
            stepWindow.clear()
        }
        lastTotalSteps = total
        stepWindow.addLast(event.timestamp to total)
        while (stepWindow.isNotEmpty() && event.timestamp - stepWindow.first().first > CADENCE_WINDOW_NS) {
            stepWindow.removeFirst()
        }
        if (stepWindow.size >= 2) {
            val first = stepWindow.first()
            val seconds = (event.timestamp - first.first) / 1_000_000_000.0
            val deltaSteps = total - first.second
            if (seconds > 3.0) {
                val spm = (deltaSteps * 60.0 / seconds).roundToInt()
                if (spm in 30..250) currentCadence = spm
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ---- Notification / wakelock ----

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "轨迹录制",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TrackRecordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("轨迹录制")
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(0, "停止并保存", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startForegroundCompat(text: String) {
        val notif = buildNotification(text)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
