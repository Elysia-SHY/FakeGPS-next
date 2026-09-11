package com.mockrun.app.domain.model

import java.io.Serializable

enum class TargetMockMode {
    STATIONARY,       // 定点驻留模拟
    ROUTE,            // 路线轨迹模拟
    REAL_PASSTHROUGH  // 透传真实物理位置
}

/**
 * Multi-tenant virtual location routing rule for a specific application or cloned instance.
 */
data class MultiTargetRule(
    val packageName: String,
    val userId: Int = 0,              // 0 = Primary User, 999 = Cloned/Dual app
    val appName: String,
    val isEnabled: Boolean = true,
    val mode: TargetMockMode = TargetMockMode.STATIONARY,
    val latitude: Double = 39.9042,
    val longitude: Double = 116.4074,
    val altitude: Double = 20.0,
    val speedKmh: Float = 8.0f,
    val colorHex: String = "#007AFF"  // Distinct color for multi-Pin display
) : Serializable {
    val key: String get() = "${packageName}_$userId"
    val isCloned: Boolean get() = userId != 0
}
