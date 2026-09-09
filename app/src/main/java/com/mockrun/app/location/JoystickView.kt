package com.mockrun.app.location

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * 360° Omnidirectional Joystick View (万向摇杆)
 * Exactly styled as Fake Location's joystick:
 * - Circular dark base with N, S, W, E direction indicators
 * - Smooth dragging center knob with boundary constraint
 * - 360-degree angle & power calculation
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnJoystickMoveListener {
        fun onValueChanged(angleDeg: Float, power: Float)
        fun onReleased()
    }

    var listener: OnJoystickMoveListener? = null
    var isLocked: Boolean = false

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#424242")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val knobInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var knobRadius = 0f
    private var knobX = 0f
    private var knobY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = (min(w, h) / 2f) * 0.95f
        knobRadius = baseRadius * 0.28f
        textPaint.textSize = max(14f, baseRadius * 0.22f)
        linePaint.strokeWidth = max(2f, baseRadius * 0.025f)
        knobInnerPaint.strokeWidth = max(2f, baseRadius * 0.035f)
        if (knobX == 0f && knobY == 0f || !isLocked) {
            knobX = centerX
            knobY = centerY
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Base circle
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)

        // 2. Cross lines
        canvas.drawLine(centerX, centerY - baseRadius * 0.85f, centerX, centerY + baseRadius * 0.85f, linePaint)
        canvas.drawLine(centerX - baseRadius * 0.85f, centerY, centerX + baseRadius * 0.85f, centerY, linePaint)

        // 3. Direction labels & triangles
        val arrowSize = max(10f, baseRadius * 0.14f)
        val textVerticalOffset = textPaint.textSize * 0.35f

        // North
        drawTriangle(canvas, centerX, centerY - baseRadius * 0.85f, arrowSize, 0f)
        canvas.drawText("N", centerX, centerY - baseRadius * 0.63f, textPaint)

        // South
        drawTriangle(canvas, centerX, centerY + baseRadius * 0.85f, arrowSize, 180f)
        canvas.drawText("S", centerX, centerY + baseRadius * 0.72f, textPaint)

        // West
        drawTriangle(canvas, centerX - baseRadius * 0.85f, centerY, arrowSize, 270f)
        canvas.drawText("W", centerX - baseRadius * 0.65f, centerY + textVerticalOffset, textPaint)

        // East
        drawTriangle(canvas, centerX + baseRadius * 0.85f, centerY, arrowSize, 90f)
        canvas.drawText("E", centerX + baseRadius * 0.65f, centerY + textVerticalOffset, textPaint)

        // 4. Center knob (Thumb)
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint)
        canvas.drawCircle(knobX, knobY, knobRadius, knobInnerPaint)
    }

    private fun drawTriangle(canvas: Canvas, x: Float, y: Float, size: Float, rotationDeg: Float) {
        val path = Path().apply {
            moveTo(0f, -size)
            lineTo(-size * 0.6f, size * 0.5f)
            lineTo(size * 0.6f, size * 0.5f)
            close()
        }
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotationDeg)
        canvas.drawPath(path, arrowPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val dist = sqrt(dx * dx + dy * dy)
                val maxDist = baseRadius - knobRadius

                val clampedDist = min(dist, maxDist)
                val angleRad = atan2(dy, dx)
                knobX = centerX + clampedDist * cos(angleRad)
                knobY = centerY + clampedDist * sin(angleRad)

                // 0° = North, 90° = East, 180° = South, 270° = West
                val compassAngle = ((Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360) % 360).toFloat()
                val power = (clampedDist / maxDist).coerceIn(0f, 1f)

                listener?.onValueChanged(compassAngle, power)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isLocked) {
                    knobX = centerX
                    knobY = centerY
                    listener?.onReleased()
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun resetKnob() {
        knobX = centerX
        knobY = centerY
        invalidate()
    }
}