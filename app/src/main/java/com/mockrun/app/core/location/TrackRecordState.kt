package com.mockrun.app.core.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 录制会话的进程内可观察状态，供 UI 与服务共享。
 *
 * 真正的落盘在 Room；这里只放「当前是否在录」这类瞬时状态。
 */
object TrackRecordState {
    const val PREFS_MOCK_SERVICE = "mock_location_service_prefs"
    const val KEY_IS_SIMULATION_ACTIVE = "key_is_sim_active"
    const val KEY_IS_POINT_MOCK_ACTIVE = "key_is_point_mock_active"

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _distanceMeters = MutableStateFlow(0.0)
    val distanceMeters: StateFlow<Double> = _distanceMeters.asStateFlow()

    private val _pointCount = MutableStateFlow(0)
    val pointCount: StateFlow<Int> = _pointCount.asStateFlow()

    private val _startedAt = MutableStateFlow(0L)
    val startedAt: StateFlow<Long> = _startedAt.asStateFlow()

    fun onStarted(startedAtMs: Long) {
        _startedAt.value = startedAtMs
        _distanceMeters.value = 0.0
        _pointCount.value = 0
        _isRecording.value = true
    }

    fun onProgress(distance: Double, points: Int) {
        _distanceMeters.value = distance
        _pointCount.value = points
    }

    fun onStopped() {
        _isRecording.value = false
    }
}
