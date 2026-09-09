package com.mockrun.app.domain.model

/**
 * A single interpolated GPS point emitted by RouteSimulator.
 */
data class SimulatedPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val progressPercent: Float = 0f,
    val distanceTraveled: Double = 0.0,
    val isCompleted: Boolean = false
)
