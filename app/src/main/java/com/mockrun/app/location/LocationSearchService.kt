package com.mockrun.app.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SearchResultItem(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

object LocationSearchService {

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 6000
    private const val USER_AGENT = "MockRunApp/1.8"

    private val COORD_REGEX = Regex("""^\s*([+-]?\d+(?:\.\d+)?)\s*[,，\s]\s*([+-]?\d+(?:\.\d+)?)\s*$""")

    suspend fun search(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 1. Check if user entered direct coordinates (e.g. 39.9042, 116.4074)
        val coordinateMatch = tryParseDirectCoordinates(trimmed)
        if (coordinateMatch != null) {
            return@withContext listOf(coordinateMatch)
        }

        // 2. Query Photon open geocoding API
        queryPhotonGeocoding(trimmed)
    }

    private fun tryParseDirectCoordinates(trimmed: String): SearchResultItem? {
        val match = COORD_REGEX.find(trimmed) ?: return null
        val (v1Str, v2Str) = match.destructured
        val v1 = v1Str.toDoubleOrNull() ?: return null
        val v2 = v2Str.toDoubleOrNull() ?: return null

        val (lat, lon) = when {
            kotlin.math.abs(v1) <= 90.0 && kotlin.math.abs(v2) <= 180.0 -> v1 to v2
            kotlin.math.abs(v2) <= 90.0 && kotlin.math.abs(v1) <= 180.0 -> v2 to v1
            else -> v1 to v2
        }

        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null

        return SearchResultItem(
            name = "指定经纬度坐标",
            address = "纬度: ${"%.5f".format(lat)}, 经度: ${"%.5f".format(lon)}",
            latitude = lat,
            longitude = lon
        )
    }

    private fun queryPhotonGeocoding(trimmedQuery: String): List<SearchResultItem> {
        var connection: HttpURLConnection? = null
        return try {
            val encodedQuery = URLEncoder.encode(trimmedQuery, "UTF-8")
            val url = URL("https://photon.komoot.io/api/?q=$encodedQuery&limit=10")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return emptyList()
            }

            val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(responseText)
            val features = json.optJSONArray("features") ?: return emptyList()

            parseFeatures(features, trimmedQuery)
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseFeatures(features: JSONArray, query: String): List<SearchResultItem> {
        val results = ArrayList<SearchResultItem>(features.length())
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val item = parseFeature(feature, query) ?: continue
            results.add(item)
        }
        return results
    }

    private fun parseFeature(feature: JSONObject, query: String): SearchResultItem? {
        val geometry = feature.optJSONObject("geometry") ?: return null
        val coordinates = geometry.optJSONArray("coordinates") ?: return null
        if (coordinates.length() < 2) return null

        val lon = coordinates.optDouble(0)
        val lat = coordinates.optDouble(1)
        if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null

        val props = feature.optJSONObject("properties") ?: JSONObject()
        val name = props.optString("name").ifBlank {
            props.optString("street").ifBlank { query }
        }

        val addrParts = mutableListOf<String>()
        val country = props.optString("country")
        val state = props.optString("state")
        val city = props.optString("city")
        val district = props.optString("district")
        val street = props.optString("street")

        if (city.isNotBlank()) addrParts.add(city)
        else if (state.isNotBlank()) addrParts.add(state)

        if (district.isNotBlank()) addrParts.add(district)
        if (street.isNotBlank() && street != name) addrParts.add(street)
        if (country.isNotBlank() && addrParts.isEmpty()) addrParts.add(country)

        val address = if (addrParts.isNotEmpty()) {
            addrParts.joinToString(" · ")
        } else {
            "坐标: ${"%.4f".format(lat)}, ${"%.4f".format(lon)}"
        }

        return SearchResultItem(
            name = name,
            address = address,
            latitude = lat,
            longitude = lon
        )
    }
}
