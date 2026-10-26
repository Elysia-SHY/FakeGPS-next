package com.mockrun.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mockrun.app.core.data.db.TrackSummary
import com.mockrun.app.core.data.repository.RouteRepository
import com.mockrun.app.core.data.repository.TrackRepository
import com.mockrun.app.core.location.SimulationStateRepository
import com.mockrun.app.core.location.TrackRecordService
import com.mockrun.app.core.location.TrackRecordState
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.SimulationStatus
import com.mockrun.app.util.Diag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val routeRepository: RouteRepository,
    private val stateRepo: SimulationStateRepository
) : ViewModel() {

    companion object {
        private const val TAG = "TrackViewModel"
    }

    val tracks: StateFlow<List<TrackSummary>> = trackRepository.summaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isRecording: StateFlow<Boolean> = TrackRecordState.isRecording
    val distanceMeters: StateFlow<Double> = TrackRecordState.distanceMeters
    val pointCount: StateFlow<Int> = TrackRecordState.pointCount
    val startedAt: StateFlow<Long> = TrackRecordState.startedAt

    /** 录制与巡航/单点/摇杆互斥——录到的必须是真的定位。 */
    fun start(context: Context, onBlocked: (String) -> Unit) {
        val status = stateRepo.currentState.status
        val mockPrefs = context.getSharedPreferences(TrackRecordState.PREFS_MOCK_SERVICE, Context.MODE_PRIVATE)
        val mockRunning = status is SimulationStatus.Running ||
            status is SimulationStatus.Paused ||
            stateRepo.isPointMockActive.value ||
            stateRepo.isJoystickActive.value ||
            mockPrefs.getBoolean(TrackRecordState.KEY_IS_SIMULATION_ACTIVE, false) ||
            mockPrefs.getBoolean(TrackRecordState.KEY_IS_POINT_MOCK_ACTIVE, false)
        if (mockRunning) {
            onBlocked("正在巡航 / 单点 / 摇杆中，先停止模拟再录制")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackRecordService::class.java).apply {
                    action = TrackRecordService.ACTION_START
                }
            )
        }.onFailure {
            Diag.w(TAG, "启动录制服务失败", it)
            onBlocked("启动录制失败：${it.message ?: "未知错误"}")
        }
    }

    fun stop(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackRecordService::class.java).apply {
                    action = TrackRecordService.ACTION_STOP
                }
            )
        }.onFailure { Diag.w(TAG, "停止录制失败", it) }
    }

    fun toggleFavorite(item: TrackSummary) {
        viewModelScope.launch { trackRepository.setFavorite(item.id, !item.isFavorite) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { trackRepository.delete(id) }
    }

    /** 轨迹 → 巡航路线；成功回调里拿到的 Route 已带数据库 id。 */
    fun convertToRoute(item: TrackSummary, onResult: (Route?) -> Unit) {
        viewModelScope.launch {
            val full = trackRepository.getTrack(item.id)
            val route = full?.let { trackRepository.toRoute(it) }
            if (route == null) {
                onResult(null)
                return@launch
            }
            val newId = runCatching { routeRepository.saveRoute(route) }.getOrDefault(-1L)
            onResult(if (newId > 0) route.copy(id = newId) else null)
        }
    }
}
