package com.mockrun.app.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/** High-speed AutoNavi (高德地图) Vector Tile Source (Domestic CDN, No Key Required) */
val AutoNaviVectorTileSource = object : OnlineTileSourceBase(
    "AutoNavi-Vector",
    3, 19, 256, ".png",
    arrayOf(
        "https://wprd01.is.autonavi.com/appmaptile?",
        "https://wprd02.is.autonavi.com/appmaptile?",
        "https://wprd03.is.autonavi.com/appmaptile?",
        "https://wprd04.is.autonavi.com/appmaptile?"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl&lang=zh_cn&size=1&scale=1&style=7&x=$x&y=$y&z=$zoom"
    }
}

/** High-speed AutoNavi (高德地图) Satellite Tile Source */
val AutoNaviSatelliteTileSource = object : OnlineTileSourceBase(
    "AutoNavi-Satellite",
    3, 19, 256, ".png",
    arrayOf(
        "https://wprd01.is.autonavi.com/appmaptile?",
        "https://wprd02.is.autonavi.com/appmaptile?",
        "https://wprd03.is.autonavi.com/appmaptile?",
        "https://wprd04.is.autonavi.com/appmaptile?"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl&lang=zh_cn&size=1&scale=1&style=6&x=$x&y=$y&z=$zoom"
    }
}

enum class MapSourceType(val label: String) {
    AUTONAVI_AUTO("高德路网 (跟随系统)"),
    AUTONAVI_VECTOR("高德路网 (浅色标准)"),
    AUTONAVI_DARK("高德路网 (深色夜间)"),
    AUTONAVI_SATELLITE("高德卫星影像"),
    OPEN_STREET_MAP("OSM 国际地图")
}

/**
 * Authentic Apple Maps Dark / Night Mode ColorMatrix Filter for vector tiles.
 * Inverts base light backgrounds to deep night gray (#141416) while keeping roads clear and readable.
 */
val DarkMapColorMatrix = android.graphics.ColorMatrix().apply {
    val r = -0.82f
    val g = -0.82f
    val b = -0.82f
    val offset = 215f
    set(floatArrayOf(
        r,  0f, 0f, 0f, offset,
        0f, g,  0f, 0f, offset,
        0f, 0f, b,  0f, offset + 15f,
        0f, 0f, 0f, 1f, 0f
    ))
}
val DarkMapColorFilter = android.graphics.ColorMatrixColorFilter(DarkMapColorMatrix)

/**
 * Apple Maps Style Vector Pin & Radar Marker Generator.
 * Creates crisp, high-DPI teardrop pins with ground shadow, white stroke, and concentric core.
 */
object MapPinHelper {
    private var cachedSelectedPin: BitmapDrawable? = null
    private var cachedActiveMockPin: BitmapDrawable? = null
    private var cachedJoystickPin: BitmapDrawable? = null
    private var cachedRunnerPin: BitmapDrawable? = null

    fun getSelectedPin(context: Context): BitmapDrawable {
        cachedSelectedPin?.let { return it }
        val d = createTeardropPin(
            context = context,
            primaryColor = android.graphics.Color.parseColor("#FF3B30"), // Apple iOS Red
            strokeColor = android.graphics.Color.WHITE
        )
        cachedSelectedPin = d
        return d
    }

    fun getActiveMockPin(context: Context): BitmapDrawable {
        cachedActiveMockPin?.let { return it }
        val d = createTeardropPin(
            context = context,
            primaryColor = android.graphics.Color.parseColor("#34C759"), // Apple iOS Green
            strokeColor = android.graphics.Color.WHITE
        )
        cachedActiveMockPin = d
        return d
    }

    fun getJoystickPin(context: Context): BitmapDrawable {
        cachedJoystickPin?.let { return it }
        val d = createRadarPin(
            context = context,
            ringColor = android.graphics.Color.parseColor("#33007AFF"),
            coreColor = android.graphics.Color.parseColor("#007AFF") // iOS Blue
        )
        cachedJoystickPin = d
        return d
    }

    fun getRunnerPin(context: Context): BitmapDrawable {
        cachedRunnerPin?.let { return it }
        val d = createRadarPin(
            context = context,
            ringColor = android.graphics.Color.parseColor("#33FF9500"),
            coreColor = android.graphics.Color.parseColor("#FF9500") // iOS Orange
        )
        cachedRunnerPin = d
        return d
    }

    private var cachedOriginPin: BitmapDrawable? = null
    private var cachedDestinationPin: BitmapDrawable? = null

    fun getOriginPin(context: Context): BitmapDrawable {
        cachedOriginPin?.let { return it }
        val d = createTeardropPin(
            context = context,
            primaryColor = android.graphics.Color.parseColor("#34C759"), // Apple Green (Start)
            strokeColor = android.graphics.Color.WHITE
        )
        cachedOriginPin = d
        return d
    }

    fun getDestinationPin(context: Context): BitmapDrawable {
        cachedDestinationPin?.let { return it }
        val d = createTeardropPin(
            context = context,
            primaryColor = android.graphics.Color.parseColor("#FF9500"), // Apple Orange (Destination)
            strokeColor = android.graphics.Color.WHITE
        )
        cachedDestinationPin = d
        return d
    }

    private val cachedAppPins = mutableMapOf<Int, BitmapDrawable>()

    fun getAppPin(context: Context, color: Int): BitmapDrawable {
        return cachedAppPins.getOrPut(color) {
            createTeardropPin(
                context = context,
                primaryColor = color,
                strokeColor = android.graphics.Color.WHITE
            )
        }
    }

    private var cachedRealLocationPuck: BitmapDrawable? = null

    fun getRealLocationPuck(context: Context): BitmapDrawable {
        cachedRealLocationPuck?.let { return it }
        val density = context.resources.displayMetrics.density
        val size = (38 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        // 1. Soft glowing outer pulse ring (iOS Royal Blue 20% alpha)
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(55, 0, 122, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - 2f * density, haloPaint)

        // 2. Light blue stroke ring
        val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(120, 0, 122, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawCircle(center, center, center - 4.5f * density, ringStroke)

        // 3. Crisp white border
        val whiteBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 9f * density, whiteBorder)

        // 4. Solid vibrant Apple Blue center core
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#007AFF")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 6.5f * density, corePaint)

        val d = BitmapDrawable(context.resources, bitmap)
        cachedRealLocationPuck = d
        return d
    }

    private fun createTeardropPin(
        context: Context,
        primaryColor: Int,
        strokeColor: Int
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val w = (38 * density).toInt()
        val h = (48 * density).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = w / 2f
        val strokeWidth = 2.5f * density
        val shadowHeight = 6f * density
        val tipY = h - shadowHeight - 2f * density
        val topPadding = 2f * density
        val r = (w - strokeWidth * 2f - 4f * density) / 2f
        val cy = topPadding + strokeWidth + r

        // 1. Ground Shadow (soft drop shadow)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(70, 0, 0, 0)
            style = Paint.Style.FILL
        }
        val shadowRect = RectF(
            cx - r * 0.75f,
            h - shadowHeight - 1f * density,
            cx + r * 0.75f,
            h - 1f * density
        )
        canvas.drawOval(shadowRect, shadowPaint)

        // 2. Teardrop Path
        val path = Path().apply {
            moveTo(cx, tipY)
            // Left curve to bulb
            cubicTo(
                cx - r * 0.95f, cy + r * 0.95f,
                cx - r, cy + r * 0.45f,
                cx - r, cy
            )
            // Top circle arc
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f, false)
            // Right curve back to tip
            cubicTo(
                cx + r, cy + r * 0.45f,
                cx + r * 0.95f, cy + r * 0.95f,
                cx, tipY
            )
            close()
        }

        // 3. Body Fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fillPaint)

        // 4. White Outer Stroke
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, strokePaint)

        // 5. Inner White Concentric Circle
        val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val innerRadius = r * 0.44f
        canvas.drawCircle(cx, cy, innerRadius, innerCirclePaint)

        // 6. Inner Dot (Center Target)
        val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, innerRadius * 0.45f, centerDotPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun createRadarPin(
        context: Context,
        ringColor: Int,
        coreColor: Int
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val size = (34 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        // Outer translucent pulse ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ringColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - 2f * density, ringPaint)

        // White halo border
        val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val coreWhiteRadius = 9f * density
        canvas.drawCircle(center, center, coreWhiteRadius, whiteBorderPaint)

        // Inner solid core
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coreColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 6.5f * density, corePaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
