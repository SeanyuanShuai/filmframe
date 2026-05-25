package com.seanyuan.filmframe.frame

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class FrameInsets(val top: Int, val bottom: Int, val left: Int, val right: Int) {
    val any: Boolean get() = top > 0 || bottom > 0 || left > 0 || right > 0
}

data class FrameDetectionResult(
    val hasFrame: Boolean,
    val insets: FrameInsets,
    val frameColor: Int,
    val confidence: Float,
) {
    val isBottomHeavy: Boolean
        get() = hasFrame && insets.bottom > insets.top * 1.6f && insets.bottom > insets.left
}

/**
 * Detects whether a photo already has a solid-color border (the kind added by
 * OPPO HASSELBLAD, Xiaomi Leica, NOMO, 黄油相机 etc.) so we can strip it before
 * applying FilmFrame's own border.
 *
 * Approach:
 *   1. Downsample to ~600px long edge for cheap analysis.
 *   2. Sample 4 corner regions. If their color is not uniform, no frame.
 *   3. Take the median corner color as the candidate frame color.
 *   4. Walk inward from each of the 4 edges, row by row / col by col.
 *      A row counts as "frame" if ≥85% of its pixels match the frame color
 *      within a per-channel tolerance. (Tolerates small dark text glyphs.)
 *   5. Require all 4 sides to have at least 0.5% frame inset to declare HasFrame.
 *   6. Map the detected insets back to the original bitmap resolution.
 *
 * Out of scope (v0.1): non-rectangular masks, gradient frames, rounded corners,
 * polaroid-style frames where top inset is 0.
 */
object FrameDetector {

    private const val MAX_ANALYSIS_DIM = 600
    private const val PER_CHANNEL_TOLERANCE = 14
    private const val ROW_MATCH_THRESHOLD = 0.85f
    private const val MIN_FRAME_RATIO = 0.005f
    private const val CORNER_SAMPLE_FRACTION = 0.04f // 4% from corner inward
    private const val CORNER_INTRA_VARIANCE_LIMIT = 18

    fun detect(source: Bitmap): FrameDetectionResult {
        val sample = downsample(source)
        val w = sample.width
        val h = sample.height
        val pixels = IntArray(w * h)
        sample.getPixels(pixels, 0, w, 0, 0, w, h)

        val cornerColors = sampleCornerColors(pixels, w, h)
        val cornerVar = maxColorSpread(cornerColors)
        if (cornerVar > CORNER_INTRA_VARIANCE_LIMIT) {
            return FrameDetectionResult(false, FrameInsets(0, 0, 0, 0), cornerColors[0], 0f)
        }

        val frameColor = medianColor(cornerColors)

        val topInset = walkVertical(pixels, w, h, frameColor, fromTop = true)
        val bottomInset = walkVertical(pixels, w, h, frameColor, fromTop = false)
        val leftInset = walkHorizontal(pixels, w, h, frameColor, fromLeft = true)
        val rightInset = walkHorizontal(pixels, w, h, frameColor, fromLeft = false)

        val minTop = max(1, (h * MIN_FRAME_RATIO).toInt())
        val minBottom = max(1, (h * MIN_FRAME_RATIO).toInt())
        val minLeft = max(1, (w * MIN_FRAME_RATIO).toInt())
        val minRight = max(1, (w * MIN_FRAME_RATIO).toInt())

        val hasFrame = topInset >= minTop &&
            bottomInset >= minBottom &&
            leftInset >= minLeft &&
            rightInset >= minRight

        if (!hasFrame) {
            return FrameDetectionResult(false, FrameInsets(0, 0, 0, 0), frameColor, 0f)
        }

        val scaleX = source.width.toFloat() / w
        val scaleY = source.height.toFloat() / h

        val insetsFull = FrameInsets(
            top = (topInset * scaleY).toInt(),
            bottom = (bottomInset * scaleY).toInt(),
            left = (leftInset * scaleX).toInt(),
            right = (rightInset * scaleX).toInt(),
        )

        val confidence = computeConfidence(topInset, bottomInset, leftInset, rightInset, w, h)

        return FrameDetectionResult(true, insetsFull, frameColor, confidence)
    }

    private fun downsample(source: Bitmap): Bitmap {
        val maxDim = max(source.width, source.height)
        if (maxDim <= MAX_ANALYSIS_DIM) return source
        val scale = MAX_ANALYSIS_DIM.toFloat() / maxDim
        val nw = max(1, (source.width * scale).toInt())
        val nh = max(1, (source.height * scale).toInt())
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }

    private fun sampleCornerColors(pixels: IntArray, w: Int, h: Int): IntArray {
        val sw = max(2, (w * CORNER_SAMPLE_FRACTION).toInt())
        val sh = max(2, (h * CORNER_SAMPLE_FRACTION).toInt())
        return intArrayOf(
            averageRegion(pixels, w, 0, 0, sw, sh),                  // TL
            averageRegion(pixels, w, w - sw, 0, sw, sh),              // TR
            averageRegion(pixels, w, 0, h - sh, sw, sh),              // BL
            averageRegion(pixels, w, w - sw, h - sh, sw, sh),         // BR
        )
    }

    private fun averageRegion(pixels: IntArray, stride: Int, x: Int, y: Int, w: Int, h: Int): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (yy in y until y + h) {
            val rowStart = yy * stride
            for (xx in x until x + w) {
                val p = pixels[rowStart + xx]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
        }
        return rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun maxColorSpread(colors: IntArray): Int {
        var minR = 255; var maxR = 0
        var minG = 255; var maxG = 0
        var minB = 255; var maxB = 0
        for (c in colors) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            minR = min(minR, r); maxR = max(maxR, r)
            minG = min(minG, g); maxG = max(maxG, g)
            minB = min(minB, b); maxB = max(maxB, b)
        }
        return max(max(maxR - minR, maxG - minG), maxB - minB)
    }

    private fun medianColor(colors: IntArray): Int {
        val rs = colors.map { (it shr 16) and 0xFF }.sorted()
        val gs = colors.map { (it shr 8) and 0xFF }.sorted()
        val bs = colors.map { it and 0xFF }.sorted()
        return rgb(rs[rs.size / 2], gs[gs.size / 2], bs[bs.size / 2])
    }

    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun pixelMatches(pixel: Int, target: Int): Boolean {
        val dr = abs(((pixel shr 16) and 0xFF) - ((target shr 16) and 0xFF))
        val dg = abs(((pixel shr 8) and 0xFF) - ((target shr 8) and 0xFF))
        val db = abs((pixel and 0xFF) - (target and 0xFF))
        return dr <= PER_CHANNEL_TOLERANCE && dg <= PER_CHANNEL_TOLERANCE && db <= PER_CHANNEL_TOLERANCE
    }

    private fun rowMatchRatio(pixels: IntArray, w: Int, y: Int, frameColor: Int): Float {
        var match = 0
        val start = y * w
        for (x in 0 until w) {
            if (pixelMatches(pixels[start + x], frameColor)) match++
        }
        return match.toFloat() / w
    }

    private fun colMatchRatio(pixels: IntArray, w: Int, h: Int, x: Int, frameColor: Int): Float {
        var match = 0
        for (y in 0 until h) {
            if (pixelMatches(pixels[y * w + x], frameColor)) match++
        }
        return match.toFloat() / h
    }

    private fun walkVertical(pixels: IntArray, w: Int, h: Int, frameColor: Int, fromTop: Boolean): Int {
        val maxScan = (h * 0.5f).toInt()
        var inset = 0
        var consecutiveMiss = 0
        for (i in 0 until maxScan) {
            val y = if (fromTop) i else h - 1 - i
            if (rowMatchRatio(pixels, w, y, frameColor) >= ROW_MATCH_THRESHOLD) {
                inset = i + 1
                consecutiveMiss = 0
            } else {
                consecutiveMiss++
                if (consecutiveMiss >= 3) break  // tolerate 2 jitter rows
            }
        }
        return inset
    }

    private fun walkHorizontal(pixels: IntArray, w: Int, h: Int, frameColor: Int, fromLeft: Boolean): Int {
        val maxScan = (w * 0.5f).toInt()
        var inset = 0
        var consecutiveMiss = 0
        for (i in 0 until maxScan) {
            val x = if (fromLeft) i else w - 1 - i
            if (colMatchRatio(pixels, w, h, x, frameColor) >= ROW_MATCH_THRESHOLD) {
                inset = i + 1
                consecutiveMiss = 0
            } else {
                consecutiveMiss++
                if (consecutiveMiss >= 3) break
            }
        }
        return inset
    }

    private fun computeConfidence(top: Int, bottom: Int, left: Int, right: Int, w: Int, h: Int): Float {
        // Higher confidence when frame is detected on all 4 sides with meaningful width
        val tRatio = top.toFloat() / h
        val bRatio = bottom.toFloat() / h
        val lRatio = left.toFloat() / w
        val rRatio = right.toFloat() / w
        val minRatio = min(min(tRatio, bRatio), min(lRatio, rRatio))
        val avgRatio = (tRatio + bRatio + lRatio + rRatio) / 4
        return min(1f, (minRatio * 8f + avgRatio * 2f).coerceIn(0f, 1f))
    }
}
