package com.mockrun.app.domain.model

/** 录制得到的一个采样点。 */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val alt: Double = 0.0,
    val speedMps: Float = 0f,
    val bearing: Float = 0f,
    val accuracyM: Float = 0f,
    val tMs: Long = 0L,
    /** 该点时刻的步频（SPM）；0 表示没有有效读数。 */
    val cadenceSpm: Int = 0,
    /** 会话累计步数。 */
    val steps: Long = 0L
)

/** 录制轨迹（一次录制会话的产物）。 */
data class RecordedTrack(
    val id: Long = 0L,
    val name: String,
    val points: List<TrackPoint>,
    val distanceMeters: Double = 0.0,
    val durationMs: Long = 0L,
    val avgSpeedKmh: Float = 0f,
    val avgCadenceSpm: Int = 0,
    val stepCount: Long = 0L,
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    /** 首尾距离 < 30 m 且总里程 > 300 m 视为闭合，转路线时默认环形。 */
    val isClosed: Boolean = false,
    val hasAltitude: Boolean = false,
    val isFavorite: Boolean = false
)
