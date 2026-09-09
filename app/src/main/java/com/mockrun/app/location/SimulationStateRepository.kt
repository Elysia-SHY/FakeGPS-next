package com.mockrun.app.location

import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.SimulatedPoint
import com.mockrun.app.domain.model.SimulationState
import com.mockrun.app.domain.model.SimulationStatus
import com.mockrun.app.domain.model.WayPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton state holder shared between [MockLocationService] (writer)
 * and ViewModels (readers). This avoids the need for service binding.
 */
@Singleton
class SimulationStateRepository @Inject constructor() {

    private val _state = MutableStateFlow(SimulationState())
    val state: StateFlow<SimulationState> = _state.asStateFlow()

    val currentState: SimulationState get() = _state.value

    // Independent Joystick Simulation State (decoupled from route simulation)
    private val _isJoystickActive = MutableStateFlow(false)
    val isJoystickActive: StateFlow<Boolean> = _isJoystickActive.asStateFlow()

    private val _joystickLocation = MutableStateFlow<WayPoint?>(null)
    val joystickLocation: StateFlow<WayPoint?> = _joystickLocation.asStateFlow()

    fun setJoystickActive(active: Boolean) {
        _isJoystickActive.value = active
        if (!active) {
            _joystickLocation.value = null
        }
    }

    fun updateJoystickLocation(latitude: Double, longitude: Double) {
        _joystickLocation.value = WayPoint(latitude, longitude)
    }

    // Single-Point Virtual Mock Location (单点定点驻留模拟)
    private val _isPointMockActive = MutableStateFlow(false)
    val isPointMockActive: StateFlow<Boolean> = _isPointMockActive.asStateFlow()

    private val _pointMockLocation = MutableStateFlow<WayPoint?>(null)
    val pointMockLocation: StateFlow<WayPoint?> = _pointMockLocation.asStateFlow()

    // Selected / Target Virtual Location (persisted across screens even when mock is not active)
    private val _selectedTargetLocation = MutableStateFlow<WayPoint?>(null)
    val selectedTargetLocation: StateFlow<WayPoint?> = _selectedTargetLocation.asStateFlow()

    // Real Device Physical Location (captured from hardware GPS/sensors)
    private val _realPhysicalLocation = MutableStateFlow<WayPoint?>(null)
    val realPhysicalLocation: StateFlow<WayPoint?> = _realPhysicalLocation.asStateFlow()

    fun updateSelectedTarget(latitude: Double, longitude: Double) {
        _selectedTargetLocation.value = WayPoint(latitude, longitude)
    }

    fun updateRealPhysicalLocation(latitude: Double, longitude: Double) {
        _realPhysicalLocation.value = WayPoint(latitude, longitude)
    }

    fun setPointMock(active: Boolean, location: WayPoint? = null) {
        _isPointMockActive.value = active
        _pointMockLocation.value = if (active) location else null
        if (active && location != null) {
            _selectedTargetLocation.value = location
        }
    }

    fun onSimulationStarted(route: Route, speedKmh: Float) {
        _state.value = SimulationState(
            status = SimulationStatus.Running,
            route = route,
            speedKmh = speedKmh,
            startTimeMs = System.currentTimeMillis(),
            pausedElapsedMs = _state.value.pausedElapsedMs  // keep accumulated time on resume
        )
    }

    fun onLocationUpdate(point: SimulatedPoint) {
        val cur = _state.value
        val runElapsed = cur.startTimeMs?.let { System.currentTimeMillis() - it } ?: 0L
        _state.value = cur.copy(
            currentWayPoint = WayPoint(point.latitude, point.longitude, point.altitude),
            progressPercent = point.progressPercent,
            distanceTraveledMeters = point.distanceTraveled,
            elapsedTimeMs = cur.pausedElapsedMs + runElapsed,
            bearing = point.bearing
        )
    }

    fun onPaused(progressPercent: Float) {
        val cur = _state.value
        val runElapsed = cur.startTimeMs?.let { System.currentTimeMillis() - it } ?: 0L
        _state.value = cur.copy(
            status = SimulationStatus.Paused,
            progressPercent = progressPercent,
            pausedElapsedMs = cur.pausedElapsedMs + runElapsed,
            startTimeMs = null
        )
    }

    fun onSimulationCompleted() {
        _state.value = _state.value.copy(
            status = SimulationStatus.Completed,
            progressPercent = 1f
        )
    }

    fun onStopped() {
        _state.value = SimulationState()   // full reset
    }

    fun onError(message: String) {
        _state.value = _state.value.copy(status = SimulationStatus.Error(message))
    }

    fun setSpeed(speedKmh: Float) {
        _state.value = _state.value.copy(speedKmh = speedKmh)
    }
}
