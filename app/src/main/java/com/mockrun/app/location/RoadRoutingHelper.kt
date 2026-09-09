package com.mockrun.app.location

import com.mockrun.app.domain.model.WayPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

enum class RoadMode(val profile: String, val label: String, val emoji: String) {
    DRIVING("driving", "驾车", "🚗"),
    WALKING("walking", "步行", "🚶"),
    CYCLING("cycling", "骑行", "🚴")
}

data class RoadRouteResult(
    val distanceKm: Double,
    val durationMinutes: Double,
    val waypoints: List<WayPoint>,
    val mode: RoadMode
)

object RoadRoutingHelper {

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000
    private const val USER_AGENT = "MockRunApp/1.8"

    suspend fun calculateRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: RoadMode = RoadMode.DRIVING
    ): RoadRouteResult? = withContext(Dispatchers.IO) {
        // Boundary check: ensure all coordinates are valid geographic points
        if (!isValidCoordinate(originLat, originLon) || !isValidCoordinate(destLat, destLon)) {
            return@withContext null
        }

        var connection: HttpURLConnection? = null
        try {
            val urlStr = "https://router.project-osrm.org/route/v1/${mode.profile}/$originLon,$originLat;$destLon,$destLat?overview=full&geometries=geojson"
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(responseText)
            if (json.optString("code") != "Ok") {
                return@withContext null
            }

            parseRoute(json, mode)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseRoute(json: JSONObject, mode: RoadMode): RoadRouteResult? {
        val routes = json.optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null

        val routeObj = routes.optJSONObject(0) ?: return null
        val distanceMeters = routeObj.optDouble("distance", 0.0)
        val durationSeconds = routeObj.optDouble("duration", 0.0)

        val geometry = routeObj.optJSONObject("geometry") ?: return null
        val coordinates = geometry.optJSONArray("coordinates") ?: return null
        val waypoints = parseCoordinates(coordinates)

        if (waypoints.isEmpty()) return null

        return RoadRouteResult(
            distanceKm = distanceMeters / 1000.0,
            durationMinutes = durationSeconds / 60.0,
            waypoints = waypoints,
            mode = mode
        )
    }

    private fun parseCoordinates(coordinates: JSONArray): List<WayPoint> {
        val count = coordinates.length()
        if (count == 0) return emptyList()

        val waypoints = ArrayList<WayPoint>(count)
        for (i in 0 until count) {
            val coordPair = coordinates.optJSONArray(i) ?: continue
            if (coordPair.length() >= 2) {
                val lon = coordPair.optDouble(0)
                val lat = coordPair.optDouble(1)
                if (isValidCoordinate(lat, lon)) {
                    waypoints.add(WayPoint(latitude = lat, longitude = lon))
                }
            }
        }
        return waypoints
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return !lat.isNaN() && !lon.isNaN() && lat in -90.0..90.0 && lon in -180.0..180.0
    }
}
