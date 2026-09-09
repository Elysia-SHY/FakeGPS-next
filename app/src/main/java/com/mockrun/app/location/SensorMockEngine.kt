package com.mockrun.app.location

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

data class SensorMockData(
    val isEnabled: Boolean = true,
    val stepCount: Long = 0L,
    val cadenceStepsPerMin: Int = 160,
    val strideLengthMeters: Float = 0.83f,
    val accelZ: Float = 9.8f,
    val cadenceMode: CadenceMode = CadenceMode.AUTO,
    val customCadence: Int = 165,
    val isRootActive: Boolean = false
)

@Singleton
class SensorMockEngine @Inject constructor(
    private val rootBridge: RootSuBridge
) {
    private val _sensorState = MutableStateFlow(SensorMockData())
    val sensorState: StateFlow<SensorMockData> = _sensorState.asStateFlow()

    private var accumulatedSteps: Long = 0L
    private var fractionalSteps: Double = 0.0
    private var phase: Double = 0.0

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
     * Mathematically calculates stride length (meters) based on speed and cadence.
     * Stride = speed (m/s) / (cadence (steps/s))
     */
    fun calculateStride(speedKmh: Float, cadence: Int): Float {
        if (cadence <= 0) return 0.75f
        val effectiveSpeed = if (speedKmh <= 0.1f) 8f else speedKmh
        val speedMps = effectiveSpeed / 3.6f
        val stepsPerSec = cadence / 60.0f
        return (speedMps / stepsPerSec).coerceIn(0.25f, 2.5f)
    }

    fun setCadenceEnabled(enabled: Boolean, currentSpeedKmh: Float = 8f) {
        val cadence = if (enabled) calculateCadence(currentSpeedKmh) else 0
        val stride = calculateStride(currentSpeedKmh, cadence)
        _sensorState.value = _sensorState.value.copy(
            isEnabled = enabled,
            cadenceStepsPerMin = cadence,
            strideLengthMeters = stride
        )
    }

    fun setCadenceMode(mode: CadenceMode, customValue: Int = 165, currentSpeedKmh: Float = 8f) {
        val safeCustom = customValue.coerceIn(40, 300)
        val newCadence = calculateCadence(currentSpeedKmh, mode, safeCustom)
        val newStride = calculateStride(currentSpeedKmh, newCadence)
        _sensorState.value = _sensorState.value.copy(
            cadenceMode = mode,
            customCadence = safeCustom,
            cadenceStepsPerMin = if (_sensorState.value.isEnabled) newCadence else 0,
            strideLengthMeters = newStride
        )
    }

    /**
     * Periodic sensor update called during actual active simulation.
     * Uses fractional step accumulation so step counts are strictly accurate and never phantom-jump.
     */
    fun updateTick(speedKmh: Float, intervalSeconds: Float) {
        val isEnabled = _sensorState.value.isEnabled
        val cadence = if (isEnabled && speedKmh > 0.1f) calculateCadence(speedKmh) else 0

        if (isEnabled && cadence > 0 && intervalSeconds > 0.01f) {
            val stepsToAdd = (cadence / 60.0) * intervalSeconds
            fractionalSteps += stepsToAdd
            if (fractionalSteps >= 1.0) {
                val intSteps = fractionalSteps.toLong()
                accumulatedSteps += intSteps
                fractionalSteps -= intSteps
            }
        }

        phase = (phase + 0.5) % (2 * PI)
        val bounce = if (cadence > 0) (sin(phase) * 1.5).toFloat() else 0f
        val currentZ = 9.8f + bounce

        val stride = calculateStride(speedKmh, if (cadence > 0) cadence else calculateCadence(speedKmh))

        _sensorState.value = _sensorState.value.copy(
            stepCount = accumulatedSteps,
            cadenceStepsPerMin = cadence,
            strideLengthMeters = stride,
            accelZ = currentZ
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
    }
}