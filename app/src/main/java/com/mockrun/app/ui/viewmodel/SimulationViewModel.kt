package com.mockrun.app.ui.viewmodel

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.MockSimulationMode
import com.mockrun.app.domain.model.SimulationState
import com.mockrun.app.core.location.CadenceMode
import com.mockrun.app.core.location.MockLocationService
import com.mockrun.app.core.location.RootSuBridge
import com.mockrun.app.core.location.SensorMockData
import com.mockrun.app.core.location.SensorMockEngine
import com.mockrun.app.core.location.SimulationStateRepository
import com.mockrun.app.util.InjectionModePrefs
import com.mockrun.app.core.location.PermissionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mockrun.app.core.data.repository.MultiTargetRepository
import com.mockrun.app.core.data.LastMockSelectionStore
import com.mockrun.app.domain.model.MultiTargetRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SimulationViewModel @Inject constructor(
    private val stateRepo: SimulationStateRepository,
    val sensorEngine: SensorMockEngine,
    val rootBridge: RootSuBridge,
    val multiTargetRepo: MultiTargetRepository,
    val permissionCoordinator: PermissionCoordinator,
    private val lastMockSelectionStore: LastMockSelectionStore
) : ViewModel() {

    val state: StateFlow<SimulationState> = stateRepo.state
    val activeMode: StateFlow<MockSimulationMode> = stateRepo.activeMode
    val isJoystickActive: StateFlow<Boolean> = stateRepo.isJoystickActive
    val joystickLocation: StateFlow<com.mockrun.app.domain.model.WayPoint?> = stateRepo.joystickLocation
    val isPointMockActive: StateFlow<Boolean> = stateRepo.isPointMockActive
    val pointMockLocation: StateFlow<com.mockrun.app.domain.model.WayPoint?> = stateRepo.pointMockLocation
    val selectedTargetLocation: StateFlow<com.mockrun.app.domain.model.WayPoint?> = stateRepo.selectedTargetLocation
    val realPhysicalLocation: StateFlow<com.mockrun.app.domain.model.WayPoint?> = stateRepo.realPhysicalLocation
    val sensorState: StateFlow<SensorMockData> = sensorEngine.sensorState

    // Multi-tenant per-app virtualization rules
    val multiTargetRules: StateFlow<List<MultiTargetRule>> = multiTargetRepo.rules

    private val restoredSelection = lastMockSelectionStore.load()
    private val restoredTargetRule = restoredSelection.targetKey?.let { savedKey ->
        multiTargetRepo.rules.value.firstOrNull { it.key == savedKey }
    }

    // Currently active / focused app rule on map (null = Global Default Aiming)
    private val _activeTargetKey = MutableStateFlow(restoredTargetRule?.key)
    val activeTargetKey: StateFlow<String?> = _activeTargetKey.asStateFlow()

    fun setActiveTargetKey(key: String?) {
        val rule = key?.let { targetKey -> multiTargetRepo.rules.value.firstOrNull { it.key == targetKey } }
        _activeTargetKey.value = rule?.key
        lastMockSelectionStore.saveTargetKey(rule?.key)
        if (rule != null) {
            stateRepo.updateSelectedTarget(rule.latitude, rule.longitude)
            lastMockSelectionStore.saveLocation(rule.latitude, rule.longitude)
        }
    }

    fun addOrUpdateMultiTargetRule(rule: MultiTargetRule) {
        multiTargetRepo.addOrUpdateRule(rule)
    }

    fun removeMultiTargetRule(key: String) {
        multiTargetRepo.removeRule(key)
        if (_activeTargetKey.value == key) {
            _activeTargetKey.value = null
            lastMockSelectionStore.saveTargetKey(null)
        }
    }

    fun toggleMultiTargetRule(key: String, isEnabled: Boolean) {
        multiTargetRepo.toggleRule(key, isEnabled)
    }

    fun updateMultiTargetLocation(key: String, latitude: Double, longitude: Double) {
        multiTargetRepo.updateCoordinates(key, latitude, longitude)
        if (_activeTargetKey.value == key) {
            stateRepo.updateSelectedTarget(latitude, longitude)
            lastMockSelectionStore.saveLocation(latitude, longitude)
        }
    }

    fun setMultiTargetRuleMode(key: String, mode: com.mockrun.app.domain.model.TargetMockMode) {
        multiTargetRepo.setRuleMode(key, mode)
    }

    suspend fun getInstalledUserApps() = multiTargetRepo.getInstalledUserApps()

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    init {
        if (restoredSelection.targetKey != null && restoredTargetRule == null) {
            // 规则已删除时不再恢复失效 key；保留点位和路线记忆，界面回退为全局。
            lastMockSelectionStore.saveTargetKey(null)
        }
        val restoredLocation = restoredTargetRule?.let { it.latitude to it.longitude }
            ?: restoredSelection.location?.let { it.latitude to it.longitude }
        restoredLocation?.let { (latitude, longitude) ->
            stateRepo.updateSelectedTarget(latitude, longitude)
        }
        viewModelScope.launch {
            refreshRootAvailability()
            // root 状态已知后再应用「上次的开关意图」，避免把 false 当成用户选择落盘
            sensorEngine.applyPersistedIntent()
        }
    }

    /** 临时 Root 可能在应用运行期间才就绪；页面恢复时刷新共享状态。 */
    suspend fun refreshRootAvailability(probeResult: Boolean? = null) {
        _isRootAvailable.value = probeResult ?: rootBridge.isRootAvailable()
    }

    fun isRootConfirmed(): Boolean = rootBridge.isRootConfirmed()

    fun setCadenceEnabled(enabled: Boolean, currentSpeedKmh: Float = 8f) {
        viewModelScope.launch {
            val rootOk = rootBridge.isRootAvailable()
            _isRootAvailable.value = rootOk
            // 只传用户意图，root 门控在引擎里做（否则冷启动会把 enabled 落盘成 false）
            sensorEngine.setCadenceEnabled(enabled, currentSpeedKmh)
        }
    }

    fun setCadenceMode(mode: CadenceMode, customValue: Int = sensorEngine.sensorState.value.customCadence, currentSpeedKmh: Float = 8f) {
        sensorEngine.setCadenceMode(mode, customValue, currentSpeedKmh)
    }

    /** 运行中直接改自定义步频，下一个 tick 生效。 */
    fun setCustomCadence(spm: Int, currentSpeedKmh: Float = 8f) {
        sensorEngine.setCustomCadence(spm, currentSpeedKmh)
    }

    fun resetSteps() {
        sensorEngine.reset()
    }

    fun updateSelectedTarget(latitude: Double, longitude: Double) {
        stateRepo.updateSelectedTarget(latitude, longitude)
        lastMockSelectionStore.saveLocation(latitude, longitude)
    }

    fun updateRealPhysicalLocation(latitude: Double, longitude: Double) {
        stateRepo.updateRealPhysicalLocation(latitude, longitude)
    }

    private var startMockJob: Job? = null
    private var startSimJob: Job? = null

    fun startPointMock(context: Context, latitude: Double, longitude: Double) {
        startMockJob?.cancel()
        startMockJob = viewModelScope.launch {
            // Root 模式：注入前自动授予模拟权限（含全局开发者选项开关），避免弹「请前往开发者选项勾选」
            val issue = withContext(Dispatchers.IO) { resolveInjectionPermission(context) }
            if (issue != com.mockrun.app.util.PermissionIssueType.NONE) {
                when (issue) {
                    com.mockrun.app.util.PermissionIssueType.LOCATION_PERMISSION_MISSING ->
                        stateRepo.onError("缺少精确定位权限，请先授予权限")
                    com.mockrun.app.util.PermissionIssueType.MOCK_LOCATION_APP_NOT_SET ->
                        stateRepo.onError("请在手机【开发者选项】中将 Fake GPS 设为「模拟位置信息应用」")
                    else -> {}
                }
                return@launch
            }
            // Mutual exclusion must include paused/completed route state as well as Running.
            val routeStatus = stateRepo.state.value.status
            if (
                stateRepo.activeMode.value == MockSimulationMode.ROUTE ||
                stateRepo.state.value.route != null ||
                routeStatus is com.mockrun.app.domain.model.SimulationStatus.Running ||
                routeStatus is com.mockrun.app.domain.model.SimulationStatus.Paused
            ) {
                stopSimulation(context)
            }

            lastMockSelectionStore.saveLocation(latitude, longitude)
            stateRepo.setPointMock(true, com.mockrun.app.domain.model.WayPoint(latitude, longitude))
            stateRepo.updateJoystickLocation(latitude, longitude)
            com.mockrun.app.hook.HookStateBridge.update(context, true, latitude, longitude)

            if (stateRepo.isJoystickActive.value) {
                val intent = Intent(context, com.mockrun.app.core.location.FloatingJoystickService::class.java).apply {
                    action = com.mockrun.app.core.location.FloatingJoystickService.ACTION_SET_LOCATION
                    putExtra(com.mockrun.app.core.location.FloatingJoystickService.EXTRA_LATITUDE, latitude)
                    putExtra(com.mockrun.app.core.location.FloatingJoystickService.EXTRA_LONGITUDE, longitude)
                }
                context.startService(intent)
            } else {
                val intent = Intent(context, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_START_POINT_MOCK
                    putExtra(MockLocationService.EXTRA_LATITUDE, latitude)
                    putExtra(MockLocationService.EXTRA_LONGITUDE, longitude)
                }
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    fun stopPointMock(context: Context) {
        startMockJob?.cancel()
        stateRepo.setPointMock(false)
        com.mockrun.app.hook.HookStateBridge.update(context, false)
        com.mockrun.app.core.location.MockLocationEngine.forceCleanAllTestProviders(context)
        com.mockrun.app.core.location.CoordinateConverter.flushRealLocation(context)
        viewModelScope.launch(Dispatchers.IO) {
            if (InjectionModePrefs.isRootMode(context) && rootBridge.isRootAvailable()) {
                rootBridge.restoreScanningHardware()
            }
        }
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP_POINT_MOCK
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun setJoystickSize(context: Context, sizeDp: Int) {
        val intent = Intent(context, com.mockrun.app.core.location.FloatingJoystickService::class.java).apply {
            action = com.mockrun.app.core.location.FloatingJoystickService.ACTION_SET_SIZE
            putExtra(com.mockrun.app.core.location.FloatingJoystickService.EXTRA_SIZE_DP, sizeDp)
        }
        context.startService(intent)
    }

    fun startSimulation(context: Context, route: Route, speedKmh: Float) {
        startSimJob?.cancel()
        startSimJob = viewModelScope.launch {
            // Root 模式：注入前自动授予模拟权限（含全局开发者选项开关），避免弹「请前往开发者选项勾选」
            val issue = withContext(Dispatchers.IO) { resolveInjectionPermission(context) }
            if (issue != com.mockrun.app.util.PermissionIssueType.NONE) {
                when (issue) {
                    com.mockrun.app.util.PermissionIssueType.LOCATION_PERMISSION_MISSING ->
                        stateRepo.onError("缺少精确定位权限，请先授予权限")
                    com.mockrun.app.util.PermissionIssueType.MOCK_LOCATION_APP_NOT_SET ->
                        stateRepo.onError("请在手机【开发者选项】中将 Fake GPS 设为「模拟位置信息应用」")
                    else -> {}
                }
                return@launch
            }

            // Mutual Exclusion: Stop Point Mock & Joystick if active
            if (stateRepo.isPointMockActive.value) {
                stopPointMock(context)
            }
            if (stateRepo.isJoystickActive.value) {
                context.stopService(Intent(context, com.mockrun.app.core.location.FloatingJoystickService::class.java))
            }

            lastMockSelectionStore.saveRoute(route)
            stateRepo.prepareRoute(route)
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START
                if (route.waypoints.size <= 50) {
                    putExtra(MockLocationService.EXTRA_ROUTE, route)
                }
                putExtra(MockLocationService.EXTRA_SPEED, speedKmh)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    /**
     * 统一权限与注入环境校验入口。
     * 由 PermissionCoordinator 统一处理 Root 自动授权时序、appops 命令及免 Root 引导。
     */
    suspend fun resolveInjectionPermission(context: Context): com.mockrun.app.util.PermissionIssueType {
        return permissionCoordinator.resolvePermission(context)
    }

    fun pauseSimulation(context: Context) {
        sendCommand(context, MockLocationService.ACTION_PAUSE)
    }

    fun resumeSimulation(context: Context) {
        sendCommand(context, MockLocationService.ACTION_RESUME)
    }

    fun stopSimulation(context: Context) {
        com.mockrun.app.hook.HookStateBridge.update(context, false)
        com.mockrun.app.core.location.MockLocationEngine.forceCleanAllTestProviders(context)
        com.mockrun.app.core.location.CoordinateConverter.flushRealLocation(context)
        viewModelScope.launch(Dispatchers.IO) {
            if (InjectionModePrefs.isRootMode(context) && rootBridge.isRootAvailable()) {
                rootBridge.restoreScanningHardware()
            }
        }
        sendCommand(context, MockLocationService.ACTION_STOP)
    }

    fun setSpeed(context: Context, speedKmh: Float) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_SET_SPEED
            putExtra(MockLocationService.EXTRA_SPEED, speedKmh)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /** 运行中切换单程 / 折返 / 环形：服务端按绝对距离续跑，不会跳回圈首。 */
    fun setRouteMode(context: Context, mode: com.mockrun.app.domain.model.RouteMode) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_SET_MODE
            putExtra(MockLocationService.EXTRA_ROUTE_MODE, mode.name)
        }
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    fun seekTo(context: Context, progressPercent: Float) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_SEEK
            putExtra(MockLocationService.EXTRA_SEEK_PROGRESS, progressPercent)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Checks if this app is designated as the Mock Location app in Developer Options.
     */
    fun isMockLocationEnabled(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: OPSTR_MOCK_LOCATION
                val mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            } else {
                @Suppress("DEPRECATION")
                val mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun sendCommand(context: Context, action: String) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            this.action = action
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
