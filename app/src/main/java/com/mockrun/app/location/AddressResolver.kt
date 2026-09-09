package com.mockrun.app.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale

/**
 * Reverse geocoding utility to resolve human-readable street/area address
 * from GPS coordinates.
 */
object AddressResolver {

    private const val MAX_CACHE_SIZE = 128

    // Thread-safe bounded LRU cache to prevent memory leaks during long-running simulations
    private val cache: MutableMap<Pair<Long, Long>, String> = Collections.synchronizedMap(
        object : LinkedHashMap<Pair<Long, Long>, String>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, Long>, String>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    )

    suspend fun resolveAddress(context: Context, latitude: Double, longitude: Double): String {
        // Guard against invalid coordinates
        if (latitude.isNaN() || longitude.isNaN() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return "无效坐标"
        }

        // Quantize coordinates to ~50 meters to hit cache for nearby micro-drifts
        val keyLat = (latitude * 1000).toLong()
        val keyLon = (longitude * 1000).toLong()
        val key = keyLat to keyLon

        cache[key]?.let { return it }

        return withContext(Dispatchers.IO) {
            val (gcjLat, gcjLon) = CoordinateConverter.wgs84ToGcj02(latitude, longitude)
            val resolved = querySystemGeocoder(context, gcjLat, gcjLon, latitude, longitude)
                ?: queryOsmFallback(latitude, longitude)
                ?: formatFallbackCoordinate(latitude, longitude)

            cache[key] = resolved
            resolved
        }
    }

    private fun querySystemGeocoder(
        context: Context,
        gcjLat: Double,
        gcjLon: Double,
        origLat: Double,
        origLon: Double
    ): String? = runCatching {
        val geocoder = Geocoder(context, Locale.CHINA)
        @Suppress("DEPRECATION")
        val list = geocoder.getFromLocation(gcjLat, gcjLon, 1)
        if (!list.isNullOrEmpty()) {
            formatAddress(list[0], origLat, origLon)
        } else null
    }.getOrNull()

    private fun queryOsmFallback(lat: Double, lon: Double): String? {
        var connection: HttpURLConnection? = null
        return runCatching {
            val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18")
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "FakeGPSApp/1.5")
            }
            if (connection?.responseCode == 200) {
                val text = connection?.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = JSONObject(text)
                json.optString("display_name").takeIf { it.isNotBlank() }
            } else null
        }.getOrNull().also {
            connection?.disconnect()
        }
    }

    private fun formatAddress(addr: Address, lat: Double, lon: Double): String {
        val sb = StringBuilder()
        val province = addr.adminArea
        val city = addr.locality
        val district = addr.subLocality
        val road = addr.thoroughfare
        val feature = addr.featureName

        if (!province.isNullOrBlank()) sb.append(province)
        if (!city.isNullOrBlank() && city != province) sb.append(city)
        if (!district.isNullOrBlank()) sb.append(district)
        if (!road.isNullOrBlank()) sb.append(road)
        if (!feature.isNullOrBlank() && feature != road && feature != district) sb.append(feature)

        val text = sb.toString().trim()
        if (text.isNotBlank()) return text

        val line0 = addr.getAddressLine(0)
        if (!line0.isNullOrBlank()) return line0

        return formatFallbackCoordinate(lat, lon)
    }

    private fun formatFallbackCoordinate(lat: Double, lon: Double): String =
        "${"%.4f".format(lat)}, ${"%.4f".format(lon)}"
}
