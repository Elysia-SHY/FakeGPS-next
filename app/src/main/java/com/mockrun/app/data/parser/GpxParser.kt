package com.mockrun.app.data.parser

import android.util.Xml
import com.mockrun.app.domain.model.WayPoint
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses GPX files using Android XmlPullParser.
 * Supports trkpt (track), rtept (route), and wpt (waypoint) elements.
 * All output coordinates are WGS-84.
 */
@Singleton
class GpxParser @Inject constructor() {

    fun parse(inputStream: InputStream): List<WayPoint> {
        val result = mutableListOf<WayPoint>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var lat = 0.0; var lon = 0.0; var alt = 0.0
        var ptName: String? = null
        var inPoint = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> when (tag) {
                    "trkpt", "rtept", "wpt" -> {
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        alt = 0.0; ptName = null; inPoint = true
                    }
                    "ele" -> if (inPoint) alt = parser.nextText().trim().toDoubleOrNull() ?: 0.0
                    "name" -> if (inPoint) ptName = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> when (tag) {
                    "trkpt", "rtept", "wpt" -> {
                        if (inPoint) {
                            result.add(WayPoint(lat, lon, alt, ptName))
                            inPoint = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }
}
