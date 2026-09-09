package com.mockrun.app.location

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mockrun.app.MainActivity
import com.mockrun.app.R
import com.mockrun.app.domain.model.SimulatedPoint
import com.mockrun.app.domain.model.SimulationState
import com.mockrun.app.domain.model.SimulationStatus
import com.mockrun.app.domain.model.WayPoint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.math.*

@AndroidEntryPoint
class FloatingJoystickService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_joystick_channel"
        const val NOTIFICATION_ID = 2002
        const val ACTION_SET_LOCATION = "com.mockrun.app.ACTION_SET_JOYSTICK_LOCATION"
        const val ACTION_SET_SIZE = "com.mockrun.app.ACTION_SET_JOYSTICK_SIZE"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_SIZE_DP = "extra_size_dp"
        const val PREFS_NAME = "fake_gps_joystick_prefs"
        const val KEY_JOYSTICK_SIZE = "pref_joystick_size_dp"
    }

    @Inject lateinit var stateRepo: SimulationStateRepository
    @Inject lateinit var rootBridge: RootSuBridge
    @Inject lateinit var sensorEngine: SensorMockEngine

    private var windowManager: WindowManager? = null
    private var rootView: LinearLayout? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var locationManager: LocationManager
    private lateinit var mockEngine: MockLocationEngine
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Coordinates state: defaults to virtual mock location if set, else real physical location
    private var currentLat: Double = 39.9042
    private var currentLon: Double = 116.4074

    // Joystick size state (freely adjustable: 80dp to 240dp)
    private var currentJoystickSizeDp: Int = 130
    private var joystickViewRef: JoystickView? = null
    private var sizeSeekBarRef: SeekBar? = null
    private var sizeValueTextRef: TextView? = null

    // Joystick movement state
    private var currentAngleDeg: Float = 0f
    private var currentPower: Float = 0f
    private var currentSpeedMps: Float = 2.0f
    private var isSimulatingActive = true
    private var isLocked = false
    private var isRootDevice = false

    private var joystickDistanceMeters: Double = 0.0

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mockEngine = MockLocationEngine(this)

        createNotificationChannel()
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
        acquireWakeLock()

        // 1. If user previously set a virtual location on map or mock screen, adopt that as the origin!
        val existingPoint = stateRepo.pointMockLocation.value 
            ?: stateRepo.selectedTargetLocation.value 
            ?: stateRepo.joystickLocation.value
        if (existingPoint != null) {
            currentLat = existingPoint.latitude
            currentLon = existingPoint.longitude
            stateRepo.updateJoystickLocation(currentLat, currentLon)
        } else {
            initRealLocation()
        }

        // 2. Mark joystick as active FIRST so MockLocationService does not unregister test provider!
        stateRepo.setJoystickActive(true)

        // 3. Stop MockLocationService's point mock so only the joystick engine is active
        runCatching {
            val stopIntent = Intent(this, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_STOP_POINT_MOCK
            }
            startService(stopIntent)
        }

        // 4. Register multi-provider mock engine (GPS, NETWORK, FUSED)
        mockEngine.register()

        // 5. Immediately inject current location into system
        injectCurrentLocation()

        serviceScope.launch {
            isRootDevice = rootBridge.isRootAvailable()
        }

        // Restore persisted joystick size preference
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentJoystickSizeDp = prefs.getInt(KEY_JOYSTICK_SIZE, 130).coerceIn(80, 240)

        buildFloatingWindow()
        startMovementLoop()
    }

    private fun initRealLocation() {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            "fused"
        )
        var real: Location? = null
        for (provider in providers) {
            val loc = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            if (loc != null) {
                if (real == null || loc.time > real.time) {
                    real = loc
                }
            }
        }

        if (real != null) {
            currentLat = real.latitude
            currentLon = real.longitude
        } else {
            stateRepo.currentState.currentWayPoint?.let {
                currentLat = it.latitude
                currentLon = it.longitude
            }
        }

        stateRepo.updateJoystickLocation(currentLat, currentLon)
    }

    private fun updateJoystickSize(sizeDp: Int) {
        currentJoystickSizeDp = sizeDp.coerceIn(80, 240)
        val density = resources.displayMetrics.density
        val newPx = (currentJoystickSizeDp * density).toInt()
        joystickViewRef?.layoutParams = LinearLayout.LayoutParams(newPx, newPx)
        joystickViewRef?.requestLayout()
        rootView?.let { windowManager?.updateViewLayout(it, it.layoutParams) }

        sizeValueTextRef?.text = "$currentJoystickSizeDp dp"
        sizeSeekBarRef?.progress = currentJoystickSizeDp - 80

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_JOYSTICK_SIZE, currentJoystickSizeDp)
            .apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_LOCATION -> {
                val newLat = intent.getDoubleExtra(EXTRA_LATITUDE, currentLat)
                val newLon = intent.getDoubleExtra(EXTRA_LONGITUDE, currentLon)
                currentLat = newLat
                currentLon = newLon
                joystickDistanceMeters = 0.0
                stateRepo.updateJoystickLocation(currentLat, currentLon)
                stateRepo.setPointMock(true, WayPoint(currentLat, currentLon))
                injectCurrentLocation()
            }
            ACTION_SET_SIZE -> {
                val newSize = intent.getIntExtra(EXTRA_SIZE_DP, currentJoystickSizeDp)
                updateJoystickSize(newSize)
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (stateRepo.isJoystickActive.value || rootView != null) {
            android.util.Log.d("FloatingJoystickService", "onTaskRemoved: scheduling rapid resurrection alarm")
            val extras = Bundle().apply {
                putDouble(EXTRA_LATITUDE, currentLat)
                putDouble(EXTRA_LONGITUDE, currentLon)
            }
            KeepAliveHelper.scheduleServiceResurrection(
                applicationContext,
                FloatingJoystickService::class.java,
                ACTION_SET_LOCATION,
                extras
            )
        }
    }

    private fun buildFloatingWindow() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 220
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // ==========================================
        // 第一层级：万向摇杆盘 + 展开把手按钮
        // ==========================================
        val joystickSizePx = (currentJoystickSizeDp * resources.displayMetrics.density).toInt()
        val joystickView = JoystickView(this).apply {
            layoutParams = LinearLayout.LayoutParams(joystickSizePx, joystickSizePx)
            listener = object : JoystickView.OnJoystickMoveListener {
                override fun onValueChanged(angleDeg: Float, power: Float) {
                    currentAngleDeg = angleDeg
                    currentPower = power
                }
                override fun onReleased() {
                    if (!isLocked) {
                        currentPower = 0f
                    }
                }
            }
        }
        joystickViewRef = joystickView
        container.addView(joystickView)

        // Pill Button (= ▣) to toggle Layer 2 options
        var isSecondLevelExpanded = false
        val pillBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val pillWidth = (70 * resources.displayMetrics.density).toInt()
            val pillHeight = (28 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(pillWidth, pillHeight).apply {
                topMargin = (6 * resources.displayMetrics.density).toInt()
                bottomMargin = (6 * resources.displayMetrics.density).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 30f
                setStroke(2, Color.parseColor("#DDDDDD"))
            }
            val pillText = TextView(context).apply {
                text = "＝ ▣"
                textSize = 12f
                setTextColor(Color.parseColor("#00796B"))
                gravity = Gravity.CENTER
            }
            addView(pillText)
        }
        container.addView(pillBtn)

        // ==========================================
        // 第二层级：高级设置卡片（默认隐藏）
        // ==========================================
        val cardWidthPx = (240 * resources.displayMetrics.density).toInt()
        val optionsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(cardWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24f
                setStroke(1, Color.parseColor("#E0E0E0"))
            }
            setPadding(30, 24, 30, 24)
            visibility = View.GONE
        }

        fun addDivider() {
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                    topMargin = 16
                    bottomMargin = 16
                }
                setBackgroundColor(Color.parseColor("#EEEEEE"))
            }
            optionsCard.addView(divider)
        }

        // 1. 摇杆方向
        val dirRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val label = TextView(context).apply {
                text = "摇杆方向"
                textSize = 15f
                setTextColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val valueText = TextView(context).apply {
                text = "相对于设备"
                textSize = 15f
                setTextColor(Color.parseColor("#00796B"))
                setOnClickListener {
                    text = if (text == "相对于设备") "相对于地图" else "相对于设备"
                }
            }
            addView(label)
            addView(valueText)
        }
        optionsCard.addView(dirRow)
        addDivider()

        // 2. 摇杆速度
        val speedValueText = TextView(this).apply {
            text = "2.0 m/s"
            textSize = 15f
            setTextColor(Color.parseColor("#00796B"))
        }
        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val label = TextView(context).apply {
                text = "摇杆速度"
                textSize = 15f
                setTextColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(label)
            addView(speedValueText)
        }
        optionsCard.addView(speedRow)

        // Speed SeekBar
        val speedSeekBar = SeekBar(this).apply {
            max = 100
            progress = 4
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    val mps = (prog * 0.5f + 0.5f)
                    currentSpeedMps = mps
                    speedValueText.text = "%.1f m/s".format(mps)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        optionsCard.addView(speedSeekBar)

        // Speed Preset Icons (🚶 🏃 🚴 🚗 ✈️)
        val iconRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
            }
        }
        val presets = listOf("🚶" to 1.2f, "🏃" to 2.5f, "🚴" to 6.0f, "🚗" to 15.0f, "✈️" to 50.0f)
        presets.forEach { (icon, spd) ->
            val btn = TextView(this).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    currentSpeedMps = spd
                    speedSeekBar.progress = ((spd - 0.5f) / 0.5f).toInt()
                    speedValueText.text = "%.1f m/s".format(spd)
                }
            }
            iconRow.addView(btn)
        }
        optionsCard.addView(iconRow)
        addDivider()

        // 3. 摇杆大小调节 (80dp ~ 220dp)
        val sizeValueText = TextView(this).apply {
            text = "$currentJoystickSizeDp dp"
            textSize = 15f
            setTextColor(Color.parseColor("#00796B"))
        }
        sizeValueTextRef = sizeValueText

        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val label = TextView(context).apply {
                text = "摇杆大小"
                textSize = 15f
                setTextColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(label)
            addView(sizeValueText)
        }
        optionsCard.addView(sizeRow)

        val sizeSeekBar = SeekBar(this).apply {
            max = 140 // 80dp to 220dp
            progress = currentJoystickSizeDp - 80
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (fromUser) {
                        updateJoystickSize(prog + 80)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10
            }
        }
        sizeSeekBarRef = sizeSeekBar
        optionsCard.addView(sizeSeekBar)

        // 摇杆大小快捷预设胶囊 (小 90 / 中 130 / 大 170 / 特大 210)
        val sizePresetsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 6
            }
        }
        val sizePresets = listOf("小 90" to 90, "中 130" to 130, "大 170" to 170, "特大 210" to 210)
        sizePresets.forEach { (label, sz) ->
            val btn = TextView(this).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#555555"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F5F5F5"))
                    cornerRadius = 16f
                    setStroke(1, Color.parseColor("#DDDDDD"))
                }
                setPadding(12, 6, 12, 6)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 4
                    marginEnd = 4
                }
                setOnClickListener {
                    updateJoystickSize(sz)
                }
            }
            sizePresetsRow.addView(btn)
        }
        optionsCard.addView(sizePresetsRow)
        addDivider()

        // 4. 摇杆锁定
        val lockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val label = TextView(context).apply {
                text = "摇杆锁定"
                textSize = 15f
                setTextColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val lockSwitch = Switch(context).apply {
                isChecked = false
                setOnCheckedChangeListener { _, checked ->
                    isLocked = checked
                    joystickView.isLocked = checked
                    if (!checked) {
                        joystickView.resetKnob()
                        currentPower = 0f
                    }
                }
            }
            addView(label)
            addView(lockSwitch)
        }
        optionsCard.addView(lockRow)
        addDivider()

        // 4. 步频模拟 (严格判断 ROOT 权限，非 ROOT 不允许开启)
        val stepRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val label = TextView(context).apply {
                text = "步频模拟"
                textSize = 15f
                setTextColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val stepSwitch = Switch(context).apply {
                isEnabled = isRootDevice
                isChecked = false
                setOnCheckedChangeListener { _, _ ->
                    if (!isRootDevice) {
                        isChecked = false
                        Toast.makeText(context, "🔒 该高级拟真功能需要 ROOT 权限", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            addView(label)
            addView(stepSwitch)
        }
        optionsCard.addView(stepRow)
        addDivider()

        // 5. 底部栏：模拟开关 + 关闭摇杆
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val simLabel = TextView(context).apply {
                text = "模拟"
                textSize = 14f
                setTextColor(Color.parseColor("#333333"))
            }
            val simSwitch = Switch(context).apply {
                isChecked = true
                setOnCheckedChangeListener { _, checked ->
                    isSimulatingActive = checked
                }
            }
            val closeBtn = TextView(context).apply {
                text = "关闭摇杆"
                textSize = 14f
                setTextColor(Color.parseColor("#333333"))
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    stopSelf()
                }
            }
            addView(simLabel)
            addView(simSwitch)
            addView(closeBtn)
        }
        optionsCard.addView(bottomRow)

        container.addView(optionsCard)

        pillBtn.setOnClickListener {
            isSecondLevelExpanded = !isSecondLevelExpanded
            optionsCard.visibility = if (isSecondLevelExpanded) View.VISIBLE else View.GONE
        }

        var initialX = 0; var initialY = 0; var touchX = 0f; var touchY = 0f
        pillBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }

        rootView = container
        windowManager?.addView(container, params)
    }

    private fun startMovementLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(100)
                if (!isSimulatingActive) continue

                if (currentPower > 0.05f) {
                    stepMove(currentAngleDeg, currentPower)
                } else {
                    // Continuous 10Hz (100ms) injection even when stationary to prevent Android from reverting to physical location
                    injectCurrentLocation()
                }
            }
        }
    }

    private fun stepMove(angleDeg: Float, power: Float) {
        val actualSpeedMps = currentSpeedMps * power
        val distanceIntervalM = actualSpeedMps * 0.10

        val rad = Math.toRadians(angleDeg.toDouble())
        val deltaLat = (distanceIntervalM * cos(rad)) / 6371000.0 * (180.0 / PI)
        val deltaLon = (distanceIntervalM * sin(rad)) / (6371000.0 * cos(Math.toRadians(currentLat))) * (180.0 / PI)

        currentLat += deltaLat
        currentLon += deltaLon
        joystickDistanceMeters += distanceIntervalM

        val point = SimulatedPoint(
            latitude = currentLat,
            longitude = currentLon,
            altitude = 20.0,
            bearing = angleDeg,
            speed = actualSpeedMps,
            progressPercent = 0f,
            distanceTraveled = joystickDistanceMeters
        )

        stateRepo.updateJoystickLocation(currentLat, currentLon)
        stateRepo.setPointMock(true, WayPoint(currentLat, currentLon))
        injectLocation(point)

        val speedKmh = actualSpeedMps * 3.6f
        sensorEngine.updateTick(speedKmh, 0.10f)
    }

    private fun injectCurrentLocation() {
        // Natural micro-jitter (±0.2m) to mimic authentic GPS drift and bypass anti-cheat
        val jitterLat = (kotlin.random.Random.nextDouble(-1.0, 1.0) * 0.000002)
        val jitterLon = (kotlin.random.Random.nextDouble(-1.0, 1.0) * 0.000002)
        val pt = SimulatedPoint(
            latitude = currentLat + jitterLat,
            longitude = currentLon + jitterLon,
            altitude = 15.0,
            bearing = currentAngleDeg,
            speed = 0f,
            progressPercent = 0f,
            distanceTraveled = joystickDistanceMeters
        )
        injectLocation(pt)
        stateRepo.updateJoystickLocation(currentLat, currentLon)
    }

    private fun injectLocation(point: SimulatedPoint) {
        mockEngine.inject(
            latitude = point.latitude,
            longitude = point.longitude,
            altitude = point.altitude,
            speedMps = point.speed,
            bearingDeg = point.bearing,
            accuracyM = 1.0f
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮摇杆服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持悬浮摇杆后台长久运行，防系统杀后台"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fake GPS 悬浮摇杆")
            .setContentText("悬浮窗与万向摇杆正在前台运行中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FakeGPS::JoystickWakeLock").apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stateRepo.setJoystickActive(false)
        com.mockrun.app.hook.HookStateBridge.update(this, false)
        releaseWakeLock()
        mockEngine.unregister()
        stopForeground(STOP_FOREGROUND_REMOVE)
        rootView?.let { windowManager?.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}