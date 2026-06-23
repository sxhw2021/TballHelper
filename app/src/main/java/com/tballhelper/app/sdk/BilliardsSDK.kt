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

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val w = bitmap.width
        val h = bitmap.height

        val template = templateBitmap
        if (template != null) {
            val center = findTemplateCenter(pixels, w, h, template)
            if (center != null) {
                result.templateCenter = center
                result.confidence = 0.8f
                val aimPoint = findAimLineEnd(pixels, w, h, center)
                if (aimPoint.x > 0 && aimPoint.y > 0) {
                    result.aimPoint = aimPoint
                }
                return result
            }
        }

        val tableTop = findTableTop(pixels, w, h)
        val searchTop = if (tableTop > 0) tableTop else h / 3
        val searchBottom = h - 100

        val cueBall = findCueBall(pixels, w, h, searchTop, searchBottom)
        if (cueBall != null) {
            result.templateCenter = cueBall
            val aimPoint = findAimTarget(pixels, w, h, cueBall)
            if (aimPoint.x > 0 && aimPoint.y > 0) {
                result.aimPoint = aimPoint
            }
            result.confidence = 0.7f
            return result
        }

        val anyWhiteBall = findAnyWhiteRegion(pixels, w, h)
        if (anyWhiteBall != null) {
            result.templateCenter = anyWhiteBall
            val aimPoint = findAimTarget(pixels, w, h, anyWhiteBall)
            if (aimPoint.x > 0 && aimPoint.y > 0) {
                result.aimPoint = aimPoint
            }
            result.confidence = 0.5f
        }

        return result
    }

    private fun findTableTop(pixels: IntArray, w: Int, h: Int): Int {
        val sampleCols = listOf(w / 4, w / 2, 3 * w / 4)
        for (y in 50 until h / 2) {
            var greenCount = 0
            for (col in sampleCols) {
                val c = pixels[y * w + col]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                if (g > 60 && g - r > 10 && g - b > 10) greenCount++
            }
            if (greenCount >= 2) return y
        }
        return -1
    }

    private fun findCueBall(pixels: IntArray, w: Int, h: Int, searchTop: Int, searchBottom: Int): PointF? {
        var bestScore = 0f
        var bestX = -1
        var bestY = -1

        for (y in searchTop until searchBottom step 3) {
            for (x in 0 until w step 3) {
                val c = pixels[y * w + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val brightness = r + g + b

                if (brightness > 600) {
                    var whiteCount = 0
                    var totalCount = 0
                    val radius = 20
                    for (dy in -radius..radius step 2) {
                        for (dx in -radius..radius step 2) {
                            val dist = sqrt((dx * dx + dy * dy).toFloat())
                            if (dist > radius) continue
                            val px = x + dx
                            val py = y + dy
                            if (px in 0 until w && py in 0 until h) {
                                val pc = pixels[py * w + px]
                                if (Color.red(pc) > 180 && Color.green(pc) > 180 && Color.blue(pc) > 180) {
                                    whiteCount++
                                }
                                totalCount++
                            }
                        }
                    }
                    val whiteRatio = if (totalCount > 0) whiteCount.toFloat() / totalCount else 0f
                    if (whiteRatio > 0.6f) {
                        val yBelow = y + radius * 2
                        if (yBelow < h) {
                            val bc = pixels[yBelow * w + x]
                            val br = Color.red(bc)
                            val bg = Color.green(bc)
                            val bb = Color.blue(bc)
                            if (bg > 60 && bg - br > 5 && bg - bb > 5) {
                                whiteRatio += 0.3f
                            }
                        }
                        if (whiteRatio > bestScore) {
                            bestScore = whiteRatio
                            bestX = x
                            bestY = y
                        }
                    }
                }
            }
        }

        return if (bestX >= 0) PointF(bestX.toFloat(), bestY.toFloat()) else null
    }

    private fun findAnyWhiteRegion(pixels: IntArray, w: Int, h: Int): PointF? {
        var bestScore = 0f
        var bestX = -1
        var bestY = -1
        val centerX = w / 2

        for (y in h / 3 until h * 3 / 4 step 4) {
            for (x in 0 until w step 4) {
                val c = pixels[y * w + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val total = r + g + b
                if (total > 550) {
                    var count = 0
                    for (dy in -20..20 step 2) {
                        for (dx in -20..20 step 2) {
                            val px = x + dx
                            val py = y + dy
                            if (px in 0 until w && py in 0 until h) {
                                val pc = pixels[py * w + px]
                                if (Color.red(pc) > 160 && Color.green(pc) > 160 && Color.blue(pc) > 160) {
                                    count++
                                }
                            }
                        }
                    }
                    if (count > 150) {
                        val score = count.toFloat()
                        if (score > bestScore) {
                            bestScore = score
                            bestX = x
                            bestY = y
                        }
                    }
                }
            }
        }

        return if (bestX >= 0) PointF(bestX.toFloat(), bestY.toFloat()) else null
    }

    private fun findAimTarget(pixels: IntArray, w: Int, h: Int, cueBall: PointF): PointF {
        val cx = cueBall.x.toInt()
        val cy = cueBall.y.toInt()
        val searchRadius = 400

        val targetPixels = mutableListOf<PointF>()
        val x0 = maxOf(0, cx - searchRadius)
        val x1 = minOf(w - 1, cx + searchRadius)
        val y0 = maxOf(0, cy - searchRadius)
        val y1 = minOf(h - 1, cy)

        for (y in y0..y1 step 2) {
            for (x in x0..x1 step 2) {
                val c = pixels[y * w + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val total = r + g + b
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt((dx * dx + dy * dy).toFloat())

                if (dist > 30 && total > 100 && total < 650) {
                    val maxC = maxOf(r, g, b)
                    val minC = minOf(r, g, b)
                    val saturation = if (maxC > 0) (maxC - minC) / maxC.toFloat() else 0f
                    if (saturation > 0.15f || (r > 200 && g > 200 && b > 200)) {
                        targetPixels.add(PointF(x.toFloat(), y.toFloat()))
                    }
                }
            }
        }

        if (targetPixels.size > 5) {
            targetPixels.sortBy { p ->
                sqrt((p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy))
            }
            var sumX = 0f
            var sumY = 0f
            val topN = minOf(20, targetPixels.size)
            for (i in 0 until topN) {
                sumX += targetPixels[i].x
                sumY += targetPixels[i].y
            }
            return PointF(sumX / topN, sumY / topN)
        }

        return PointF(-1f, -1f)
    }

    private fun findAimLineEnd(pixels: IntArray, w: Int, h: Int, center: PointF): PointF {
        val cx = center.x.toInt()
        val cy = center.y.toInt()
        val whitePixels = mutableListOf<PointF>()
        val searchRadius = 150

        for (y in maxOf(0, cy - searchRadius) until minOf(h - 1, cy + searchRadius) step 2) {
            for (x in maxOf(0, cx - searchRadius) until minOf(w - 1, cx + searchRadius) step 2) {
                val c = pixels[y * w + x]
                if (isWhitePixel(c)) {
                    val dx = x - cx
                    val dy = y - cy
                    val dist = sqrt((dx * dx + dy * dy).toFloat())
                    if (dist > 50 && dist < searchRadius) {
                        whitePixels.add(PointF(x.toFloat(), y.toFloat()))
                    }
                }
            }
        }

        if (whitePixels.isEmpty()) return PointF(-1f, -1f)

        whitePixels.sortByDescending { p ->
            sqrt((p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy))
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

    private fun findTemplateCenter(pixels: IntArray, w: Int, h: Int, template: Bitmap): PointF? {
        val tmplWidth = template.width
        val tmplHeight = template.height
        if (w < tmplWidth || h < tmplHeight) return null

        val tmplPixels = IntArray(tmplWidth * tmplHeight)
        template.getPixels(tmplPixels, 0, tmplWidth, 0, 0, tmplWidth, tmplHeight)

        var bestMatch = 0.7f
        var bestX = 0
        var bestY = 0

        for (y in tmplHeight / 2 until h - tmplHeight / 2 step 15) {
            for (x in tmplWidth / 2 until w - tmplWidth / 2 step 15) {
                val match = templateMatch(pixels, w, tmplPixels, tmplWidth, tmplHeight, x, y)
                if (match > bestMatch) {
                    bestMatch = match
                    bestX = x
                    bestY = y
                }
            }
        }

        if (bestMatch < 0.7f) return null

        for (dy in -20..20 step 5) {
            for (dx in -20..20 step 5) {
                val match = templateMatch(pixels, w, tmplPixels, tmplWidth, tmplHeight, bestX + dx, bestY + dy)
                if (match > bestMatch) {
                    bestMatch = match
                    bestX += dx
                    bestY += dy
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
        for (ty in 0 until tmplHeight step 3) {
            for (tx in 0 until tmplWidth step 3) {
                val sx = centerX - halfW + tx
                val sy = centerY - halfH + ty
                if (sx in 0 until screenWidth && sy >= 0) {
                    val screenIdx = sy * screenWidth + sx
                    val tmplIdx = ty * tmplWidth + tx
                    if (screenIdx in screenPixels.indices && tmplIdx in tmplPixels.indices) {
                        if (isSimilarColor(tmplPixels[tmplIdx], screenPixels[screenIdx])) matchCount++
                        totalCount++
                    }
                }
            }
        }
        return if (totalCount > 0) matchCount.toFloat() / totalCount else 0f
    }

    private fun isSimilarColor(c1: Int, c2: Int): Boolean {
        val diff = abs(Color.red(c1) - Color.red(c2)) +
            abs(Color.green(c1) - Color.green(c2)) +
            abs(Color.blue(c1) - Color.blue(c2))
        return diff < 100
    }

    private fun isWhitePixel(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r > 200 && g > 200 && b > 200
    }

    class ProcessingResult {
        var templateCenter: PointF? = null
        var aimPoint: PointF? = null
        var confidence: Float = 0f
    }
}