package com.mockrun.app.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    /** Gson-serialized List<TrackPoint>. */
    val pointsJson: String,
    val distanceMeters: Double,
    val durationMs: Long,
    val avgSpeedKmh: Float,
    val avgCadenceSpm: Int,
    val stepCount: Long,
    val pointCount: Int,
    val startedAt: Long,
    val endedAt: Long,
    @ColumnInfo(defaultValue = "0")
    val isClosed: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val hasAltitude: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false
)
