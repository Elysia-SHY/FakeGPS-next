package com.mockrun.app.domain.model

sealed class SimulationStatus {
    object Idle : SimulationStatus()
    object Running : SimulationStatus()
    object Paused : SimulationStatus()
    object Completed : SimulationStatus()
    data class Error(val message: String) : SimulationStatus()
}

/**
 * Full simulation state exposed to the UI via StateFlow.
 * Elapsed time survives pause by accumulating in [pausedElapsedMs].
 */
data class SimulationState(
    val status: SimulationStatus = SimulationStatus.Idle,
    val currentWayPoint: WayPoint? = null,
    val progressPercent: Float = 0f,
    val distanceTraveledMeters: Double = 0.0,
    val elapsedTimeMs: Long = 0L,
    val speedKmh: Float = 8f,
    val bearing: Float = 0f,
    val route: Route? = null,
    val startTimeMs: Long? = null,
    val pausedElapsedMs: Long = 0L
) {
    val formattedElapsedTime: String
        get() {
            val s = elapsedTimeMs / 1000
            val m = s / 60; val h = m / 60
            return if (h > 0) "%02d:%02d:%02d".format(h, m % 60, s % 60)
            else "%02d:%02d".format(m, s % 60)
        }
}
