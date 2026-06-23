package com.tballhelper.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.View

class OverlayView(context: Context) : View(context) {

    private val linePaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val circlePaint = Paint().apply {
        color = Color.parseColor("#FFFF00")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        textSize = 48f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val statusPaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")
        textSize = 36f
        isAntiAlias = true
    }

    data class AimLine(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float
    )

    data class PredictionPath(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
        val pocketX: Float,
        val pocketY: Float
    )

    private var aimLines: List<AimLine> = emptyList()
    private var predictionPaths: List<PredictionPath> = emptyList()
    private var templateCenter: PointF? = null
    private var aimPoint: PointF? = null
    private var statusText: String? = "正在检测..."
    private var showTestPattern = false

    fun showTestPattern() {
        showTestPattern = true
        invalidate()
    }

    fun hideTestPattern() {
        showTestPattern = false
        invalidate()
    }

    fun setStatus(text: String?) {
        statusText = text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (showTestPattern) {
            val cx = width / 2f
            val cy = height / 2f
            linePaint.color = Color.parseColor("#FF0000")
            linePaint.strokeWidth = 6f
            canvas.drawLine(cx - 100, cy, cx + 100, cy, linePaint)
            canvas.drawLine(cx, cy - 100, cx, cy + 100, linePaint)
            canvas.drawCircle(cx, cy, 50f, circlePaint)
            textPaint.color = Color.parseColor("#FF0000")
            canvas.drawText("Overlay Test", cx - 100, cy + 150, textPaint)
            return
        }

        predictionPaths.forEach { path ->
            linePaint.color = Color.parseColor("#0088FF")
            linePaint.strokeWidth = 3f
            canvas.drawLine(path.fromX, path.fromY, path.toX, path.toY, linePaint)

            linePaint.color = Color.parseColor("#FF8800")
            canvas.drawLine(path.toX, path.toY, path.pocketX, path.pocketY, linePaint)

            circlePaint.color = Color.parseColor("#FF8800")
            canvas.drawCircle(path.pocketX, path.pocketY, 20f, circlePaint)
        }

        aimLines.forEach { line ->
            linePaint.color = Color.parseColor("#00FF00")
            linePaint.strokeWidth = 5f
            canvas.drawLine(line.startX, line.startY, line.endX, line.endY, linePaint)
        }

        templateCenter?.let { center ->
            circlePaint.color = Color.parseColor("#FFFF00")
            canvas.drawCircle(center.x, center.y, 30f, circlePaint)
        }

        aimPoint?.let { point ->
            circlePaint.color = Color.parseColor("#FF00FF")
            canvas.drawCircle(point.x, point.y, 15f, circlePaint)
        }

        statusText?.let { text ->
            var y = height - 50f
            val lines = text.split("\n")
            for (line in lines) {
                canvas.drawText(line, 20f, y, statusPaint)
                y += 40f
            }
        }
    }

    fun updateAimingData(
        center: PointF?,
        aim: PointF?,
        lines: List<AimLine>,
        predictions: List<PredictionPath>
    ) {
        showTestPattern = false
        templateCenter = center
        aimPoint = aim
        aimLines = lines
        predictionPaths = predictions
        if (center != null && aim != null) {
            statusText = "已检测到目标"
        } else {
            statusText = "检测中..."
        }
        invalidate()
    }

    fun clear() {
        templateCenter = null
        aimPoint = null
        aimLines = emptyList()
        predictionPaths = emptyList()
        statusText = "未检测到目标"
        invalidate()
    }
}