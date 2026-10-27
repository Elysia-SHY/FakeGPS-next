package com.mockrun.app.core.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

enum class CadenceMode(val label: String) {
    AUTO("智能自适应"),
    WALK("健步走 (110)"),
    JOG("燃脂慢跑 (160)"),
    RUN("专业跑马 (180)"),
    CUSTOM("自定义")
}

/** 步幅合理性判定：只提示，不静默截断。 */
enum class StrideVerdict { OK, TOO_SHORT, TOO_LONG, UNKNOWN }

data class SensorMockData(
    val isEnabled: Boolean = false,
    val stepCount: Long = 0L,
    val cadenceStepsPerMin: Int = 160,
    val strideLengthMeters: Float = 0.83f,
    val accelZ: Float = 9.8f,
    val cadenceMode: CadenceMode = CadenceMode.AUTO,
    val customCadence: Int = CadencePrefs.DEFAULT_CUSTOM_SPM,
    val isRootActive: Boolean = false,
    val strideVerdict: StrideVerdict = StrideVerdict.OK
)

@Singleton
class SensorMockEngine @Inject constructor(
    private val rootBridge: RootSuBridge,
    @ApplicationContext private val context: Context
) {
    private val _sensorState = MutableStateFlow(SensorMockData())
    val sensorState: StateFlow<SensorMockData> = _sensorState.asStateFlow()

    private var accumulatedSteps: Long = 0L
    private var fractionalSteps: Double = 0.0
    private var phase: Double = 0.0

    /** 用户意图（已落盘）。实际是否注入 = 意图 && rootOk，绝不把 rootOk 写回磁盘。 */
    private var userIntentEnabled: Boolean = false
    private var lastPersistedMode: CadenceMode = CadenceMode.AUTO
    private var lastPersistedCustom: Int = CadencePrefs.DEFAULT_CUSTOM_SPM

    init {
        // SharedPreferences 首读是磁盘 I/O，但这里是轻量文件（3 个键）且只在进程启动时读一次
        val snap = CadencePrefs.load(context)
        userIntentEnabled = snap.enabled
        lastPersistedMode = snap.mode
        lastPersistedCustom = snap.customSpm
        _sensorState.value = _sensorState.value.copy(
            cadenceMode = snap.mode,
            customCadence = snap.customSpm,
            isEnabled = false,            // root 探测完成前不谎报已注入
            cadenceStepsPerMin = 0
        )
    }

    /** Root 探测完成后由 ViewModel 调用：把用户上次的意图重新应用一次。 */
    fun applyPersistedIntent(currentSpeedKmh: Float = 8f) {
        if (userIntentEnabled) setCadenceEnabled(true, currentSpeedKmh)
    }

    fun userWantsEnabled(): Boolean = userIntentEnabled

    /**
     * Calculate cadence (SPM) based on current/preview speed and cadence mode.
     */
    fun calculateCadence(
        speedKmh: Float,
        mode: CadenceMode = _sensorState.value.cadenceMode,
        customValue: Int = _sensorState.value.customCadence
    ): Int {
        val effectiveSpeed = if (speedKmh <= 0.1f) 8f else speedKmh
        return when (mode) {
            CadenceMode.AUTO -> when {
                effectiveSpeed < 3.5f -> 95
                effectiveSpeed < 6.5f -> 120
                effectiveSpeed < 9.5f -> 160
                effectiveSpeed < 13.5f -> 175
                effectiveSpeed < 18.0f -> 185
                else -> 195
            }
            CadenceMode.WALK -> 110
            CadenceMode.JOG -> 160
            CadenceMode.RUN -> 180
            CadenceMode.CUSTOM -> customValue.coerceIn(40, 300)
        }
    }

    /**
     * 步幅 = 配速 / (SPM / 60)。
     *
     * 保留旧签名给现有 UI 用，但**不再 coerceIn(0.25, 2.5)**：静默截断会破坏
     * 「步幅 = 配速 / (SPM/60)」这条恒等式，导致步频与配速在报表上对不上。
     * 不合理值由 [strideVerdict] 提示。
     */
    fun calculateStride(speedKmh: Float, cadence: Int): Float =
        calculateStrideRaw(speedKmh, cadence)

    fun calculateStrideRaw(speedKmh: Float, cadence: Int): Float {
        if (cadence <= 0) return 0.75f          // 暂停/停止时的占位值，避免 UI 闪成 0.00 m
        val effectiveSpeed = if (speedKmh <= 0.1f) 8f else speedKmh
        return (effectiveSpeed / 3.6f) / (cadence / 60f)
    }

    /** 合理步幅区间随配速抬升；固定阈值判不出「步行步频跑跑步配速」。 */
    fun strideVerdict(speedKmh: Float, stride: Float): StrideVerdict {
        if (stride <= 0f || speedKmh <= 0.1f) return StrideVerdict.UNKNOWN
        val (lo, hi) = when {
            speedKmh < 6.5f -> 0.45f to 0.95f
            speedKmh < 11f -> 0.70f to 1.20f
            else -> 0.95f to 1.80f
        }
        return when {
            stride < lo -> StrideVerdict.TOO_SHORT
            stride > hi -> StrideVerdict.TOO_LONG
            else -> StrideVerdict.OK
        }
    }

    fun setCadenceEnabled(enabled: Boolean, currentSpeedKmh: Float = 8f) {
        userIntentEnabled = enabled
        CadencePrefs.saveIntent(context, enabled, _sensorState.value.cadenceMode, _sensorState.value.customCadence)

        val rootOk = rootBridge.isRootConfirmed()
        // 系统属性发布需要 Root；非 Root 只能停留在开关/预览。
        val actuallyEnabled = enabled && rootOk
        val cadence = if (actuallyEnabled) {
            effectiveCadence(currentSpeedKmh, _sensorState.value.cadenceMode, _sensorState.value.customCadence)
        } else {
            0
        }
        val stride = calculateStride(currentSpeedKmh, cadence)
        _sensorState.value = _sensorState.value.copy(
            isEnabled = actuallyEnabled,
            cadenceStepsPerMin = cadence,
            strideLengthMeters = stride,
            strideVerdict = strideVerdict(currentSpeedKmh, stride),
            isRootActive = rootOk
        )
        if (!actuallyEnabled) {
            com.mockrun.app.hook.HookStateBridge.publishSensorState(false, accumulatedSteps, 0, 9.8f)
        }
    }

    fun setCadenceMode(mode: CadenceMode, customValue: Int = _sensorState.value.customCadence, currentSpeedKmh: Float = 8f) {
        val safeCustom = customValue.coerceIn(40, 300)
        lastPersistedMode = mode
        lastPersistedCustom = safeCustom
        CadencePrefs.saveIntent(context, userIntentEnabled, mode, safeCustom)

        val newCadence = effectiveCadence(currentSpeedKmh, mode, safeCustom)
        val newStride = calculateStride(currentSpeedKmh, newCadence)
        _sensorState.value = _sensorState.value.copy(
            cadenceMode = mode,
            customCadence = safeCustom,
            cadenceStepsPerMin = if (_sensorState.value.isEnabled) newCadence else 0,
            strideLengthMeters = newStride,
            strideVerdict = strideVerdict(currentSpeedKmh, newStride)
        )
    }

    /** 直接改自定义 SPM（运行中立即生效）。 */
    fun setCustomCadence(spm: Int, currentSpeedKmh: Float = 8f) {
        setCadenceMode(CadenceMode.CUSTOM, spm, currentSpeedKmh)
    }

    private fun effectiveCadence(speedKmh: Float, mode: CadenceMode, customValue: Int): Int =
        if (mode == CadenceMode.CUSTOM) customValue.coerceIn(40, 300)
        else calculateCadence(speedKmh, mode, customValue)

    /**
     * Periodic sensor update called during actual active simulation.
     *
     * Uses fractional step accumulation so step counts are strictly accurate and never phantom-jump.
     * 相位按真实步频推进（172 SPM → 2.87 Hz）；1 Hz 采样下这个值是欠采样的，
     * 这是显示层采样率的问题，不是相位算法的问题。
     */
    fun updateTick(speedKmh: Float, intervalSeconds: Float, overrideSpm: Int? = null) {
        val state = _sensorState.value
        val isEnabled = state.isEnabled
        val cadence = when {
            !isEnabled -> 0
            speedKmh <= 0.1f || intervalSeconds <= 0.01f -> 0
            overrideSpm != null && overrideSpm > 0 -> overrideSpm
            else -> effectiveCadence(speedKmh, state.cadenceMode, state.customCadence)
        }

        if (isEnabled && cadence > 0 && intervalSeconds > 0.01f) {
            val stepsToAdd = (cadence / 60.0) * intervalSeconds
            fractionalSteps += stepsToAdd
            if (fractionalSteps >= 1.0) {
                val intSteps = fractionalSteps.toLong()
                accumulatedSteps += intSteps
                fractionalSteps -= intSteps
            }
        }

        // 相位按步频推进；停下（cadence == 0）时幅度归零、读数回到 9.8
        phase = (phase + 2 * PI * (cadence / 60.0) * intervalSeconds) % (2 * PI)
        val bounce = if (cadence > 0) {
            (sin(phase) * (1.2 + 0.9 * (speedKmh / 12f).coerceIn(0f, 1.5f))).toFloat()
        } else {
            0f
        }

        val stride = calculateStride(speedKmh, if (cadence > 0) cadence else state.customCadence)
        _sensorState.value = state.copy(
            stepCount = accumulatedSteps,
            cadenceStepsPerMin = cadence,
            strideLengthMeters = stride,
            strideVerdict = strideVerdict(speedKmh, stride),
            accelZ = 9.8f + bounce
        )
        com.mockrun.app.hook.HookStateBridge.publishSensorState(
            active = isEnabled && cadence > 0,
            steps = accumulatedSteps,
            cadence = cadence,
            accelZ = 9.8f + bounce
        )
    }

    fun reset() {
        accumulatedSteps = 0L
        fractionalSteps = 0.0
        phase = 0.0
        _sensorState.value = _sensorState.value.copy(
            stepCount = 0L,
            accelZ = 9.8f
        )
        com.mockrun.app.hook.HookStateBridge.publishSensorState(false, 0L, 0, 9.8f)
    }
}
