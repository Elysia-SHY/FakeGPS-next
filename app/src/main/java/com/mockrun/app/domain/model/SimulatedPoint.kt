package com.mockrun.app.domain.model

/**
 * A single interpolated GPS point emitted by RouteSimulator.
 *
 * [progressPercent] 在单程模式下等价于整体进度；在折返/环形模式下表示「本轮进度」，每轮归零。
 * 整体里程看 [distanceTraveled]（累计，不按轮清零），轮数看 [lapIndex]。
 */
data class SimulatedPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val progressPercent: Float = 0f,
    val distanceTraveled: Double = 0.0,
    val isCompleted: Boolean = false,
    /** 已完成的轮数，从 0 开始。折返模式下每个单程 +1。 */
    val lapIndex: Int = 0
)
