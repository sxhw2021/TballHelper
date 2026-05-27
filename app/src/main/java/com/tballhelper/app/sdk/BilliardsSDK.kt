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

        result.templateCenter = findCueBall(pixels, w, h)
        result.templateCenter?.let { center ->
            val aimPoint = findAimDirection(pixels, w, h, center)
            if (aimPoint.x > 0 && aimPoint.y > 0) {
                result.aimPoint = aimPoint
            } else {
                result.aimPoint = findNearestColoredBall(pixels, w, h, center)
            }
        }

        return result
    }

    private fun findCueBall(pixels: IntArray, w: Int, h: Int): PointF? {
        val searchTop = h / 2
        var bestScore = 0f
        var bestX = -1
        var bestY = -1
        val minR = 10
        val maxR = 40

        for (y in searchTop until h step 4) {
            for (x in 0 until w step 4) {
                val c = pixels[y * w + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                if (r + g + b > 600) {
                    val radius = estimateWhiteCircleRadius(pixels, w, h, x, y, maxR)
                    if (radius in minR..maxR) {
                        val score = radius.toFloat() * ((r + g + b) / 765f)
                        if (score > bestScore) {
                            val cy = y + radius
                            val cx = x
                            if (cy in 0 until h && cx in 0 until w) {
                                val centerColor = pixels[cy * w + cx]
                                val cr = Color.red(centerColor)
                                val cg = Color.green(centerColor)
                                val cb = Color.blue(centerColor)
                                if (cr > 180 && cg > 180 && cb > 180 && cr + cb + cg > 300) {
                                    bestScore = score
                                    bestX = cx
                                    bestY = cy
                                } else {
                                    val lowerY = y + radius * 2
                                    if (lowerY < h) {
                                        val lc = pixels[lowerY * w + cx]
                                        if (isGreenPixel(lc)) {
                                            bestScore = score
                                            bestX = cx
                                            bestY = cy
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (bestX == -1) {
            for (y in searchTop until h step 3) {
                for (x in 0 until w step 3) {
                    val c = pixels[y * w + x]
                    val r = Color.red(c)
                    val g = Color.green(c)
                    val b = Color.blue(c)
                    if (r > 200 && g > 200 && b > 200 && abs(r - g) < 30 && abs(g - b) < 30) {
                        val whiteCount = countWhitePixelsAround(pixels, w, h, x, y, 30)
                        if (whiteCount > 200 && whiteCount > bestScore) {
                            bestScore = whiteCount.toFloat()
                            bestX = x
                            bestY = y
                        }
                    }
                }
            }
        }

        return if (bestX >= 0 && bestY >= 0) PointF(bestX.toFloat(), bestY.toFloat()) else null
    }

    private fun estimateWhiteCircleRadius(pixels: IntArray, w: Int, h: Int, cx: Int, cy: Int, maxR: Int): Int {
        for (r in 1..maxR) {
            val checkX = cx + r
            if (checkX >= w) return r - 1
            val c = pixels[cy * w + checkX]
            if (Color.red(c) + Color.green(c) + Color.blue(c) < 450) return r - 1
        }
        return maxR
    }

    private fun countWhitePixelsAround(pixels: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int): Int {
        var count = 0
        val x0 = maxOf(0, cx - radius)
        val x1 = minOf(w - 1, cx + radius)
        val y0 = maxOf(0, cy - radius)
        val y1 = minOf(h - 1, cy + radius)
        for (y in y0..y1 step 2) {
            for (x in x0..x1 step 2) {
                val c = pixels[y * w + x]
                if (Color.red(c) > 200 && Color.green(c) > 200 && Color.blue(c) > 200) {
                    count++
                }
            }
        }
        return count
    }

    private fun findAimDirection(pixels: IntArray, w: Int, h: Int, center: PointF): PointF {
        val cx = center.x.toInt()
        val cy = center.y.toInt()
        val searchRadius = 300

        val brightPixels = mutableListOf<PointF>()
        val x0 = maxOf(0, cx - searchRadius)
        val x1 = minOf(w - 1, cx + searchRadius)
        val y0 = maxOf(0, cy - searchRadius)
        val y1 = minOf(h - 1, cy + searchRadius)

        for (y in y0..y1 step 2) {
            for (x in x0..x1 step 2) {
                val c = pixels[y * w + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                if (r > 220 && g > 100 && g < 200 && b > 100 && b < 200) {
                    val dx = x - cx
                    val dy = y - cy
                    if (dx * dx + dy * dy > 100) {
                        brightPixels.add(PointF(x.toFloat(), y.toFloat()))
                    }
                }
            }
        }

        if (brightPixels.size > 10) {
            var sumX = 0f
            var sumY = 0f
            for (p in brightPixels) {
                sumX += p.x
                sumY += p.y
            }
            return PointF(sumX / brightPixels.size, sumY / brightPixels.size)
        }

        for (y in y0..y1 step 2) {
            for (x in x0..x1 step 2) {
                val c = pixels[y * w + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                if (r > 200 && g > 200 && b > 200 && abs(r - g) < 30) {
                    val dx = x - cx
                    val dy = y - cy
                    if (dx * dx + dy * dy > 100 && dy < 0) {
                        brightPixels.add(PointF(x.toFloat(), y.toFloat()))
                    }
                }
            }
        }

        return if (brightPixels.size > 5) {
            var sumX = 0f
            var sumY = 0f
            for (p in brightPixels) {
                sumX += p.x
                sumY += p.y
            }
            PointF(sumX / brightPixels.size, sumY / brightPixels.size)
        } else PointF(-1f, -1f)
    }

    private fun findNearestColoredBall(pixels: IntArray, w: Int, h: Int, center: PointF): PointF {
        val cx = center.x.toInt()
        val cy = center.y.toInt()
        val searchRadius = 500
        var nearestDist = Float.MAX_VALUE
        var nearestX = -1
        var nearestY = -1

        val x0 = maxOf(0, cx - searchRadius)
        val x1 = minOf(w - 1, cx + searchRadius)
        val y0 = maxOf(0, cy - searchRadius)
        val y1 = minOf(h - 1, cy + searchRadius)

        for (y in y0..y1 step 2) {
            for (x in x0..x1 step 2) {
                val c = pixels[y * w + x]
                val r = Color.red(c).toFloat()
                val g = Color.green(c).toFloat()
                val b = Color.blue(c).toFloat()
                val total = r + g + b
                if (total > 100 && total < 650) {
                    val maxC = maxOf(r, g, b)
                    val minC = minOf(r, g, b)
                    val saturation = (maxC - minC) / maxOf(maxC, 1f)
                    if (saturation > 0.15f) {
                        val dx = x - cx
                        val dy = y - cy
                        val dist = sqrt((dx * dx + dy * dy).toFloat())
                        if (dist > 30 && dist < nearestDist) {
                            nearestDist = dist
                            nearestX = x
                            nearestY = y
                        }
                    }
                }
            }
        }

        return if (nearestX >= 0 && nearestY >= 0) PointF(nearestX.toFloat(), nearestY.toFloat()) else PointF(-1f, -1f)
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

        var bestMatch = 0.75f
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

        if (bestMatch < 0.75f) return null

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
                val screenIdx = sy * screenWidth + sx
                val tmplIdx = ty * tmplWidth + tx
                if (screenIdx in screenPixels.indices && tmplIdx in tmplPixels.indices) {
                    if (isSimilarColor(tmplPixels[tmplIdx], screenPixels[screenIdx])) matchCount++
                    totalCount++
                }
            }
        }
        return if (totalCount > 0) matchCount.toFloat() / totalCount else 0f
    }

    private fun isGreenPixel(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        return g > 80 && g > r + 15 && g > b + 15
    }

    private fun isSimilarColor(c1: Int, c2: Int): Boolean {
        val diff = abs(Color.red(c1) - Color.red(c2)) +
            abs(Color.green(c1) - Color.green(c2)) +
            abs(Color.blue(c1) - Color.blue(c2))
        return diff < 80
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