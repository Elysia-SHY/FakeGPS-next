package com.mockrun.app.core.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mockrun.app.core.data.db.TrackDao
import com.mockrun.app.core.data.db.TrackEntity
import com.mockrun.app.core.data.db.TrackSummary
import com.mockrun.app.domain.model.RecordedTrack
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.RouteMode
import com.mockrun.app.domain.model.TrackPoint
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import kotlinx.coroutines.flow.Flow
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val gson: Gson
) {
    /** 列表页只读元数据（不碰 pointsJson）。 */
    val summaries: Flow<List<TrackSummary>> = trackDao.observeSummaries()

    suspend fun save(track: RecordedTrack): Long = trackDao.insertTrack(track.toEntity())

    suspend fun getTrack(id: Long): RecordedTrack? = trackDao.getTrackById(id)?.toDomain()

    suspend fun setFavorite(id: Long, favorite: Boolean) = trackDao.setFavorite(id, favorite)

    suspend fun delete(id: Long) = trackDao.deleteTrackById(id)

    /**
     * 轨迹 → 巡航路线。
     *
     * - 闭合轨迹（首尾 < 30 m 且 > 300 m）默认环形，并补首点闭合；
     * - 否则默认折返；
     * - 按 [resampleMeters] 重采样并封顶 2000 点，避免插值退化成 O(n)。
     */
    fun toRoute(track: RecordedTrack, resampleMeters: Double = 8.0): Route? {
        if (track.points.size < 2) return null
        val waypoints = resample(track.points, resampleMeters)
        if (waypoints.size < 2) return null
        val mode = if (track.isClosed) RouteMode.LOOP else RouteMode.PING_PONG
        val closed = if (track.isClosed && waypoints.first() != waypoints.last()) {
            waypoints + waypoints.first()
        } else {
            waypoints
        }
        return Route(
            name = track.name,
            waypoints = closed.take(MAX_WAYPOINTS),
            mode = mode,
            sourceTrackId = track.id.takeIf { it > 0L }
        )
    }

    private fun resample(points: List<TrackPoint>, stepMeters: Double): List<WayPoint> {
        val out = ArrayList<WayPoint>(minOf(points.size, MAX_WAYPOINTS))
        var lastKept: WayPoint? = null
        var acc = 0.0
        for (p in points) {
            val wp = WayPoint(p.lat, p.lon, p.alt)
            val prev = lastKept
            if (prev == null) {
                out += wp
                lastKept = wp
                continue
            }
            acc += Route.haversineDistance(prev, wp)
            if (acc >= stepMeters) {
                out += wp
                lastKept = wp
                acc = 0.0
            }
        }
        val lastPoint = points.last()
        val tail = WayPoint(lastPoint.lat, lastPoint.lon, lastPoint.alt)
        if (out.lastOrNull() != tail) out += tail
        return out
    }

    private fun TrackEntity.toDomain(): RecordedTrack {
        val pts: List<TrackPoint> = runCatching {
            val parsed: List<TrackPoint>? = gson.fromJson(pointsJson, TRACK_POINT_LIST_TYPE)
            parsed ?: emptyList()
        }.logFailure(TAG, "parse points for track '$name'", Diag.Level.DEBUG)
            .getOrDefault(emptyList())
        return RecordedTrack(
            id = id,
            name = name,
            points = pts,
            distanceMeters = distanceMeters,
            durationMs = durationMs,
            avgSpeedKmh = avgSpeedKmh,
            avgCadenceSpm = avgCadenceSpm,
            stepCount = stepCount,
            startedAt = startedAt,
            endedAt = endedAt,
            isClosed = isClosed,
            hasAltitude = hasAltitude,
            isFavorite = isFavorite
        )
    }

    private fun RecordedTrack.toEntity() = TrackEntity(
        id = id,
        name = name,
        pointsJson = gson.toJson(points),
        distanceMeters = distanceMeters,
        durationMs = durationMs,
        avgSpeedKmh = avgSpeedKmh,
        avgCadenceSpm = avgCadenceSpm,
        stepCount = stepCount,
        pointCount = points.size,
        startedAt = startedAt,
        endedAt = endedAt,
        isClosed = isClosed,
        hasAltitude = hasAltitude,
        isFavorite = isFavorite
    )

    private companion object {
        private const val TAG = "TrackRepository"
        private const val MAX_WAYPOINTS = 2000

        /** 与 RouteRepository 同理：不用匿名 TypeToken，避免 R8 丢泛型签名。 */
        private val TRACK_POINT_LIST_TYPE: Type =
            TypeToken.getParameterized(List::class.java, TrackPoint::class.java).type
    }
}
