package com.mockrun.app.ui.viewmodel

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.SimulationState
import com.mockrun.app.location.CadenceMode
import com.mockrun.app.location.MockLocationService
import com.mockrun.app.location.SensorMockData
import com.mockrun.app.location.SensorMockEngine
import com.mockrun.app.location.SimulationStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SimulationViewModel @Inject constructor(
    private val stateRepo: SimulationStateRepository,
    val sensorEngine: SensorMockEngine
) : ViewModel() {

    val state: StateFlow<SimulationState> = stateRepo.state
    val isJoystickActive: StateFlow<Boolean> = stateRepo.isJoystickActive
    val joystickLocation: StateFlow<com.mockrun.app.domain.model.WayPoint?> = stateRepo.joystickLocation
    val isPointMockActive: StateFlow<Boolean> = stateRepo.isPointMockActive
    val pointMockLocation: StateFlow<com.mockrun.app.domain.model.WayPoint?> = stateRepo.pointMockLocation
    val selectedTargetLocation: StateFlow<com.mockrun.app.domain.model.WayPoint?> = stateRepo.selectedTargetLocation
    val realPhysicalLocation: StateFlow<com.mockrun.app.domain.model.WayPoint?> = stateRepo.realPhysicalLocation
    val sensorState: StateFlow<SensorMockData> = sensorEngine.sensorState

    fun setCadenceEnabled(enabled: Boolean, currentSpeedKmh: Float = 8f) {
        sensorEngine.setCadenceEnabled(enabled, currentSpeedKmh)
    }

    fun setCadenceMode(mode: CadenceMode, customValue: Int = 165, currentSpeedKmh: Float = 8f) {
        sensorEngine.setCadenceMode(mode, customValue, currentSpeedKmh)
    }

    fun resetSteps() {
        sensorEngine.reset()
    }

    fun updateSelectedTarget(latitude: Double, longitude: Double) {
        stateRepo.updateSelectedTarget(latitude, longitude)
    }

    fun updateRealPhysicalLocation(latitude: Double, longitude: Double) {
        stateRepo.updateRealPhysicalLocation(latitude, longitude)
    }

    fun startPointMock(context: Context, latitude: Double, longitude: Double): Boolean {
        val issue = com.mockrun.app.util.PermissionHelper.checkPrimaryPermissions(context)
        if (issue != com.mockrun.app.util.PermissionIssueType.NONE) {
            when (issue) {
                com.mockrun.app.util.PermissionIssueType.LOCATION_PERMISSION_MISSING ->
                    stateRepo.onError("缺少精确定位权限，请先授予权限")
                com.mockrun.app.util.PermissionIssueType.MOCK_LOCATION_APP_NOT_SET ->
                    stateRepo.onError("请在手机【开发者选项】中将 Fake GPS 设为「模拟位置信息应用」")
                else -> {}
            }
            return false
        }
        if (!com.mockrun.app.location.KeepAliveHelper.isBatteryOptimized(context)) {
            com.mockrun.app.location.KeepAliveHelper.requestIgnoreBatteryOptimization(context)
        }
        stateRepo.setPointMock(true, com.mockrun.app.domain.model.WayPoint(latitude, longitude))
        stateRepo.updateJoystickLocation(latitude, longitude)
        com.mockrun.app.hook.HookStateBridge.update(context, true, latitude, longitude)

        if (stateRepo.isJoystickActive.value) {
            val intent = Intent(context, com.mockrun.app.location.FloatingJoystickService::class.java).apply {
                action = com.mockrun.app.location.FloatingJoystickService.ACTION_SET_LOCATION
                putExtra(com.mockrun.app.location.FloatingJoystickService.EXTRA_LATITUDE, latitude)
                putExtra(com.mockrun.app.location.FloatingJoystickService.EXTRA_LONGITUDE, longitude)
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
        return true
    }

    fun stopPointMock(context: Context) {
        stateRepo.setPointMock(false)
        com.mockrun.app.hook.HookStateBridge.update(context, false)
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP_POINT_MOCK
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun setJoystickSize(context: Context, sizeDp: Int) {
        val intent = Intent(context, com.mockrun.app.location.FloatingJoystickService::class.java).apply {
            action = com.mockrun.app.location.FloatingJoystickService.ACTION_SET_SIZE
            putExtra(com.mockrun.app.location.FloatingJoystickService.EXTRA_SIZE_DP, sizeDp)
        }
        context.startService(intent)
    }

    fun startSimulation(context: Context, route: Route, speedKmh: Float): Boolean {
        val issue = com.mockrun.app.util.PermissionHelper.checkPrimaryPermissions(context)
        if (issue != com.mockrun.app.util.PermissionIssueType.NONE) {
            when (issue) {
                com.mockrun.app.util.PermissionIssueType.LOCATION_PERMISSION_MISSING ->
                    stateRepo.onError("缺少精确定位权限，请先授予权限")
                com.mockrun.app.util.PermissionIssueType.MOCK_LOCATION_APP_NOT_SET ->
                    stateRepo.onError("请在手机【开发者选项】中将 Fake GPS 设为「模拟位置信息应用」")
                else -> {}
            }
            return false
        }
        if (!com.mockrun.app.location.KeepAliveHelper.isBatteryOptimized(context)) {
            com.mockrun.app.location.KeepAliveHelper.requestIgnoreBatteryOptimization(context)
        }
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_ROUTE, route)
            putExtra(MockLocationService.EXTRA_SPEED, speedKmh)
        }
        ContextCompat.startForegroundService(context, intent)
        return true
    }

    fun pauseSimulation(context: Context) {
        sendCommand(context, MockLocationService.ACTION_PAUSE)
    }

    fun resumeSimulation(context: Context) {
        sendCommand(context, MockLocationService.ACTION_RESUME)
    }

    fun stopSimulation(context: Context) {
        sendCommand(context, MockLocationService.ACTION_STOP)
    }

    fun setSpeed(context: Context, speedKmh: Float) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_SET_SPEED
            putExtra(MockLocationService.EXTRA_SPEED, speedKmh)
        }
        ContextCompat.startForegroundService(context, intent)
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
