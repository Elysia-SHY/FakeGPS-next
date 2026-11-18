package com.mockrun.app.core.data

import android.content.Context
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.RouteMode
import com.mockrun.app.domain.model.WayPoint
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class LastMockSelection(
    val targetKey: String? = null,
    val location: WayPoint? = null
)

/** 记住用户最后聚焦的分流应用和模拟点位；不保存运行中状态，也不在启动时自动开启模拟。 */
@Singleton
class LastMockSelectionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private companion object {
        const val PREFS_NAME = "last_mock_selection"
        const val KEY_TARGET = "target_key"
        const val KEY_LATITUDE_BITS = "latitude_bits"
        const val KEY_LONGITUDE_BITS = "longitude_bits"
        const val KEY_ROUTE_JSON = "route_json"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): LastMockSelection {
        val targetKey = prefs.getString(KEY_TARGET, null)?.takeIf { it.isNotBlank() }
        val location = if (prefs.contains(KEY_LATITUDE_BITS) && prefs.contains(KEY_LONGITUDE_BITS)) {
            val latitude = Double.fromBits(prefs.getLong(KEY_LATITUDE_BITS, 0L))
            val longitude = Double.fromBits(prefs.getLong(KEY_LONGITUDE_BITS, 0L))
            WayPoint(latitude, longitude).takeIf {
                latitude.isFinite() && longitude.isFinite() &&
                    latitude in -90.0..90.0 && longitude in -180.0..180.0
            }
        } else null
        return LastMockSelection(targetKey, location)
    }

    fun saveTargetKey(targetKey: String?) {
        prefs.edit().apply {
            if (targetKey == null) remove(KEY_TARGET) else putString(KEY_TARGET, targetKey)
        }.apply()
    }

    fun saveLocation(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return
        prefs.edit()
            .putLong(KEY_LATITUDE_BITS, latitude.toBits())
            .putLong(KEY_LONGITUDE_BITS, longitude.toBits())
            .apply()
    }

    fun saveRoute(route: Route?) {
        if (route == null || route.waypoints.size < 2) {
            prefs.edit().remove(KEY_ROUTE_JSON).apply()
            return
        }
        val points = JSONArray()
        route.waypoints.forEach { point ->
            points.put(JSONObject().apply {
                put("latitude", point.latitude)
                put("longitude", point.longitude)
                put("altitude", point.altitude)
                point.name?.let { put("name", it) }
            })
        }
        val json = JSONObject().apply {
            put("id", route.id)
            put("name", route.name)
            put("createdAt", route.createdAt)
            put("mode", route.mode.name)
            route.sourceTrackId?.let { put("sourceTrackId", it) }
            put("waypoints", points)
        }.toString()
        prefs.edit().putString(KEY_ROUTE_JSON, json).apply()
    }

    fun loadRoute(): Route? {
        val raw = prefs.getString(KEY_ROUTE_JSON, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val array = json.getJSONArray("waypoints")
            val points = mutableListOf<WayPoint>()
            for (index in 0 until array.length()) {
                val point = array.getJSONObject(index)
                val latitude = point.getDouble("latitude")
                val longitude = point.getDouble("longitude")
                require(
                    latitude.isFinite() && longitude.isFinite() &&
                        latitude in -90.0..90.0 && longitude in -180.0..180.0
                )
                points += WayPoint(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = point.optDouble("altitude", 0.0),
                    name = point.optString("name").takeIf { it.isNotEmpty() }
                )
            }
            require(points.size >= 2)
            Route(
                id = json.optLong("id", 0L),
                name = json.optString("name", "上次路线"),
                waypoints = points,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                mode = runCatching { RouteMode.valueOf(json.optString("mode")) }
                    .getOrDefault(RouteMode.ONE_WAY),
                sourceTrackId = if (json.has("sourceTrackId")) json.optLong("sourceTrackId") else null
            )
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
