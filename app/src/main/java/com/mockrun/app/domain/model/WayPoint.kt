package com.mockrun.app.domain.model

import java.io.Serializable

/**
 * A single GPS coordinate point (WGS-84).
 */
data class WayPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val name: String? = null
) : Serializable
