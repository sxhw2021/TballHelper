package com.tballhelper.app.sdk

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.sqrt

class BilliardsSDK {

    private var templateBitmap: Bitmap? = null

    fun setTemplate(template: Bitmap) {
        templateBitmap = template
    }

    fun processFrame(bitmap: Bitmap): ProcessingResult {
        val result = ProcessingResult()
        val template = templateBitmap ?: return result

        val center = findTemplateCenter(bitmap, template)
        if (center != null) {
            result.templateCenter = center
            result.confidence = 0.8f

            val aimPoint = findAimLineEnd(bitmap, center)
            if (aimPoint.x > 0 && aimPoint.y > 0) {
                result.aimPoint = aimPoint
            }
        }

        return result
    }

    private fun findTemplateCenter(screen: Bitmap, template: Bitmap): PointF? {
        val screenWidth = screen.width
        val screenHeight = screen.height
        val tmplWidth = template.width
        val tmplHeight = template.height

        if (screenWidth < tmplWidth || screenHeight < tmplHeight) return null

        val screenPixels = IntArray(screenWidth * screenHeight)
        screen.getPixels(screenPixels, 0, screenWidth, 0, 0, screenWidth, screenHeight)

        val tmplPixels = IntArray(tmplWidth * tmplHeight)
        template.getPixels(tmplPixels, 0, tmplWidth, 0, 0, tmplWidth, tmplHeight)

        var bestMatch = 0.85f
        var bestX = 0
        var bestY = 0
        val step = 20

        for (y in tmplHeight / 2 until screenHeight - tmplHeight / 2 step step) {
            for (x in tmplWidth / 2 until screenWidth - tmplWidth / 2 step step) {
                val match = templateMatch(screenPixels, screenWidth, tmplPixels, tmplWidth, tmplHeight, x, y)
                if (match > bestMatch) {
                    bestMatch = match
                    bestX = x
                    bestY = y
                }
            }
        }

        if (bestMatch < 0.85f) return null

        for (dy in -30..30 step 10) {
            for (dx in -30..30 step 10) {
                val nx = bestX + dx
                val ny = bestY + dy
                val match = templateMatch(screenPixels, screenWidth, tmplPixels, tmplWidth, tmplHeight, nx, ny)
                if (match > bestMatch) {
                    bestMatch = match
                    bestX = nx
                    bestY = ny
                }
            }
        }

        return PointF(bestX.toFloat(), bestY.toFloat())
    }

    private fun templateMatch(
        screenPixels: IntArray, screenWidth: Int,
        tmplPixels: IntArray, tmplWidth: Int, tmplHeight: Int,
        centerX: Int, centerY: Int
    ): Float {
        val halfW = tmplWidth / 2
        val halfH = tmplHeight / 2

        if (centerX - halfW < 0 || centerX + halfW >= screenWidth ||
            centerY - halfH < 0 || centerY + halfH >= screenPixels.size / screenWidth) {
            return 0f
        }

        var matchCount = 0
        var totalCount = 0

        for (ty in 0 until tmplHeight step 2) {
            for (tx in 0 until tmplWidth step 2) {
                val sx = centerX - halfW + tx
                val sy = centerY - halfH + ty
                val screenIdx = sy * screenWidth + sx
                val tmplIdx = ty * tmplWidth + tx

                if (screenIdx >= 0 && screenIdx < screenPixels.size &&
                    tmplIdx >= 0 && tmplIdx < tmplPixels.size) {
                    val match = isSimilarColor(tmplPixels[tmplIdx], screenPixels[screenIdx])
                    if (match) matchCount++
                    totalCount++
                }
            }
        }

        return if (totalCount > 0) matchCount.toFloat() / totalCount else 0f
    }

    private fun isSimilarColor(c1: Int, c2: Int): Boolean {
        val r1 = Color.red(c1)
        val g1 = Color.green(c1)
        val b1 = Color.blue(c1)
        val r2 = Color.red(c2)
        val g2 = Color.green(c2)
        val b2 = Color.blue(c2)
        val diff = abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
        return diff < 60
    }

    private fun findAimLineEnd(bitmap: Bitmap, center: PointF): PointF {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val whitePixels = mutableListOf<PointF>()
        val searchRadius = 150
        val minDist = center.x * 0 + center.y * 0

        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val color = pixels[y * bitmap.width + x]
                if (isWhitePixel(color)) {
                    val dist = sqrt((x - center.x) * (x - center.x) + (y - center.y) * (y - center.y))
                    if (dist > 50 && dist < searchRadius) {
                        whitePixels.add(PointF(x.toFloat(), y.toFloat()))
                    }
                }
            }
        }

        if (whitePixels.isEmpty()) return PointF(-1f, -1f)

        whitePixels.sortByDescending { p ->
            sqrt((p.x - center.x) * (p.x - center.x) + (p.y - center.y) * (p.y - center.y))
        }

        var sumX = 0f
        var sumY = 0f
        val topN = minOf(50, whitePixels.size)
        for (i in 0 until topN) {
            sumX += whitePixels[i].x
            sumY += whitePixels[i].y
        }

        return PointF(sumX / topN, sumY / topN)
    }

    private fun isWhitePixel(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r > 200 && g > 200 && b > 200 && abs(r - g) < 30 && abs(g - b) < 30
    }

    class ProcessingResult {
        var templateCenter: PointF? = null
        var aimPoint: PointF? = null
        var confidence: Float = 0f
    }
}
