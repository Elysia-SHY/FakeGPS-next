package com.mockrun.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 列表页只读元数据，避免把 pointsJson 整行拉进内存。 */
data class TrackSummary(
    val id: Long,
    val name: String,
    val distanceMeters: Double,
    val durationMs: Long,
    val avgSpeedKmh: Float,
    val avgCadenceSpm: Int,
    val stepCount: Long,
    val pointCount: Int,
    val startedAt: Long,
    val isClosed: Boolean,
    val isFavorite: Boolean
)

@Dao
interface TrackDao {

    @Query(
        "SELECT id, name, distanceMeters, durationMs, avgSpeedKmh, avgCadenceSpm, stepCount, pointCount, startedAt, isClosed, isFavorite " +
            "FROM tracks ORDER BY isFavorite DESC, startedAt DESC"
    )
    fun observeSummaries(): Flow<List<TrackSummary>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)
}
