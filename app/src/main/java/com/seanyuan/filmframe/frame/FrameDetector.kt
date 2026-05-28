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
 * v3 — solves both the v1 'leaves residual edges on OPPO HASSELBLAD' bug AND
 * the v2 'aggressively crops dark photos into a 1cm strip' regression.
 *
 * Approach: two-pass walk per edge.
 *
 *   STRICT pass (0.92 match / 3 miss tolerance):
 *     "Is there actually a frame on this side?" Strict thresholds avoid
 *     false positives on photos where the corner happens to be near-uniform
 *     (dark sky, snow, bokeh). If strict gives < MIN_FRAME_RATIO inset on
 *     any side, we declare 'no frame' and return.
 *
 *   LOOSE pass (0.75 match / 10 miss tolerance):
 *     "How far does the frame actually extend?" Only run when strict
 *     confirms a frame. The loose pass walks past anti-aliased fringe and
 *     dark text bands (the OPPO HASSELBLAD bottom mat case).
 *
 *   Anti-runaway cap:
 *     Loose inset is capped at strict_inset × 3. Typical frame extension
 *     past strict boundary is just a few text rows (1.5-2× max). A loose
 *     inset 40× the strict inset means LOOSE ran away into photo content —
 *     fall back to strict on that side.
 *
 *   Safety margin (0.6% of dim) added after the cap, then everything
 *   maxed at 50% of dim.
 *
 * Out of scope: rounded corner frames, gradient frames, polaroid-style
 * (top-only inset), non-rectangular masks.
 */
object FrameDetector {

    private const val MAX_ANALYSIS_DIM = 900
    private const val PER_CHANNEL_TOLERANCE = 22
    private const val STRICT_ROW_THRESHOLD = 0.92f
    private const val STRICT_MISS_TOLERANCE = 3
    private const val LOOSE_ROW_THRESHOLD = 0.70f
    // Adaptive: loose pass tolerates min(MAX, strict_inset / DIVISOR) consecutive
    // misses. A HASSELBLAD bottom mat (~200 analysis rows) gets ~50-row tolerance
    // so a single text line doesn't cut the walk short. A thin polaroid frame
    // (~20 rows) still uses the FLOOR (10) so we don't run away.
    private const val LOOSE_MISS_TOLERANCE_FLOOR = 10
    private const val LOOSE_MISS_TOLERANCE_DIVISOR = 4
    private const val LOOSE_MISS_TOLERANCE_MAX = 80
    private const val MIN_FRAME_RATIO = 0.012f  // 1.2% of dim — typical real frames are >5%
    private const val CORNER_SAMPLE_FRACTION = 0.05f
    private const val CORNER_INTRA_VARIANCE_LIMIT = 18
    // Increased from 3× — text-heavy HASSELBLAD mats break strict pass early,
    // so loose has to extend further. Still bounded so a near-uniform photo
    // (snow, dark sky) doesn't get cropped in half.
    private const val LOOSE_OVER_STRICT_CAP = 6f
    // 1.2% safety margin — at typical export 6000px long edge this is 72px,
    // enough to cover 1-2 analysis-px detection error (= 10-20 source px) plus
    // AA fringe. Was 0.6% which left a hairline residual on dogfood.
    private const val SAFETY_INSET_RATIO = 0.012f
    private const val MAX_INSET_RATIO = 0.5f

    /**
     * Android-bound entry point. Downsamples the bitmap, extracts its pixel
     * array, then defers to the pure-Kotlin [detectFromPixels]. Insets are
     * then scaled back from analysis resolution to source resolution.
     *
     * Cross-platform note: `detectFromPixels` is platform-independent. iOS /
     * KMP can call it directly by feeding an IntArray of ARGB pixels.
     */
    fun detect(source: Bitmap): FrameDetectionResult {
        val sample = downsample(source)
        val w = sample.width
        val h = sample.height
        val pixels = IntArray(w * h)
        sample.getPixels(pixels, 0, w, 0, 0, w, h)

        val analysisResult = detectFromPixels(pixels, w, h)
        if (!analysisResult.hasFrame) return analysisResult

        // Scale insets back to source resolution.
        val scaleX = source.width.toFloat() / w
        val scaleY = source.height.toFloat() / h
        val ins = analysisResult.insets
        return analysisResult.copy(
            insets = FrameInsets(
                top = (ins.top * scaleY).toInt(),
                bottom = (ins.bottom * scaleY).toInt(),
                left = (ins.left * scaleX).toInt(),
                right = (ins.right * scaleX).toInt(),
            ),
        )
    }

    /**
     * Pure-Kotlin detection given an IntArray of ARGB pixels and dimensions.
     * No Android dependencies — usable as-is in KMP shared module or
     * ported verbatim to Swift on iOS.
     *
     * Inset values returned are in the SAME pixel space as the input
     * (i.e., already at analysis resolution). Callers in lazier languages
     * can pass the original image directly if it's small enough.
     */
    fun detectFromPixels(pixels: IntArray, w: Int, h: Int): FrameDetectionResult {
        val cornerColors = sampleCornerColors(pixels, w, h)
        val cornerVar = maxColorSpread(cornerColors)
        if (cornerVar > CORNER_INTRA_VARIANCE_LIMIT) {
            return FrameDetectionResult(false, FrameInsets(0, 0, 0, 0), cornerColors[0], 0f)
        }

        val frameColor = medianColor(cornerColors)

        // STRICT pass — does a real frame exist?
        val strictTop = walkVertical(pixels, w, h, frameColor, fromTop = true,
            threshold = STRICT_ROW_THRESHOLD, missTol = STRICT_MISS_TOLERANCE)
        val strictBottom = walkVertical(pixels, w, h, frameColor, fromTop = false,
            threshold = STRICT_ROW_THRESHOLD, missTol = STRICT_MISS_TOLERANCE)
        val strictLeft = walkHorizontal(pixels, w, h, frameColor, fromLeft = true,
            threshold = STRICT_ROW_THRESHOLD, missTol = STRICT_MISS_TOLERANCE)
        val strictRight = walkHorizontal(pixels, w, h, frameColor, fromLeft = false,
            threshold = STRICT_ROW_THRESHOLD, missTol = STRICT_MISS_TOLERANCE)

        val minV = max(1, (h * MIN_FRAME_RATIO).toInt())
        val minH = max(1, (w * MIN_FRAME_RATIO).toInt())

        val frameConfirmed = strictTop >= minV &&
            strictBottom >= minV &&
            strictLeft >= minH &&
            strictRight >= minH

        if (!frameConfirmed) {
            return FrameDetectionResult(false, FrameInsets(0, 0, 0, 0), frameColor, 0f)
        }

        // LOOSE pass — extend past text bands / AA fringe.
        // Per-side adaptive miss tolerance: a thicker strict result implies a
        // thicker real frame that may contain longer non-matching text rows.
        val tolTop = looseMissTol(strictTop)
        val tolBottom = looseMissTol(strictBottom)
        val tolLeft = looseMissTol(strictLeft)
        val tolRight = looseMissTol(strictRight)
        val looseTop = walkVertical(pixels, w, h, frameColor, fromTop = true,
            threshold = LOOSE_ROW_THRESHOLD, missTol = tolTop)
        val looseBottom = walkVertical(pixels, w, h, frameColor, fromTop = false,
            threshold = LOOSE_ROW_THRESHOLD, missTol = tolBottom)
        val looseLeft = walkHorizontal(pixels, w, h, frameColor, fromLeft = true,
            threshold = LOOSE_ROW_THRESHOLD, missTol = tolLeft)
        val looseRight = walkHorizontal(pixels, w, h, frameColor, fromLeft = false,
            threshold = LOOSE_ROW_THRESHOLD, missTol = tolRight)

        // Cap loose by strict×3 to prevent runaway into photo content.
        val topInset = min(looseTop, (strictTop * LOOSE_OVER_STRICT_CAP).toInt())
        val bottomInset = min(looseBottom, (strictBottom * LOOSE_OVER_STRICT_CAP).toInt())
        val leftInset = min(looseLeft, (strictLeft * LOOSE_OVER_STRICT_CAP).toInt())
        val rightInset = min(looseRight, (strictRight * LOOSE_OVER_STRICT_CAP).toInt())

        // Safety margin + hard cap.
        val safetyV = (h * SAFETY_INSET_RATIO).toInt()
        val safetyH = (w * SAFETY_INSET_RATIO).toInt()
        val capV = (h * MAX_INSET_RATIO).toInt()
        val capH = (w * MAX_INSET_RATIO).toInt()
        val topFinal = (topInset + safetyV).coerceAtMost(capV)
        val bottomFinal = (bottomInset + safetyV).coerceAtMost(capV)
        val leftFinal = (leftInset + safetyH).coerceAtMost(capH)
        val rightFinal = (rightInset + safetyH).coerceAtMost(capH)

        val confidence = computeConfidence(strictTop, strictBottom, strictLeft, strictRight, w, h)

        return FrameDetectionResult(
            hasFrame = true,
            insets = FrameInsets(top = topFinal, bottom = bottomFinal, left = leftFinal, right = rightFinal),
            frameColor = frameColor,
            confidence = confidence,
        )
    }

    private fun looseMissTol(strictInset: Int): Int {
        val adaptive = strictInset / LOOSE_MISS_TOLERANCE_DIVISOR
        return adaptive.coerceIn(LOOSE_MISS_TOLERANCE_FLOOR, LOOSE_MISS_TOLERANCE_MAX)
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
            averageRegion(pixels, w, 0, 0, sw, sh),
            averageRegion(pixels, w, w - sw, 0, sw, sh),
            averageRegion(pixels, w, 0, h - sh, sw, sh),
            averageRegion(pixels, w, w - sw, h - sh, sw, sh),
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

    private fun walkVertical(
        pixels: IntArray, w: Int, h: Int, frameColor: Int, fromTop: Boolean,
        threshold: Float, missTol: Int,
    ): Int {
        val maxScan = (h * MAX_INSET_RATIO).toInt()
        var inset = 0
        var consecutiveMiss = 0
        for (i in 0 until maxScan) {
            val y = if (fromTop) i else h - 1 - i
            if (rowMatchRatio(pixels, w, y, frameColor) >= threshold) {
                inset = i + 1
                consecutiveMiss = 0
            } else {
                consecutiveMiss++
                if (consecutiveMiss >= missTol) break
            }
        }
        return inset
    }

    private fun walkHorizontal(
        pixels: IntArray, w: Int, h: Int, frameColor: Int, fromLeft: Boolean,
        threshold: Float, missTol: Int,
    ): Int {
        val maxScan = (w * MAX_INSET_RATIO).toInt()
        var inset = 0
        var consecutiveMiss = 0
        for (i in 0 until maxScan) {
            val x = if (fromLeft) i else w - 1 - i
            if (colMatchRatio(pixels, w, h, x, frameColor) >= threshold) {
                inset = i + 1
                consecutiveMiss = 0
            } else {
                consecutiveMiss++
                if (consecutiveMiss >= missTol) break
            }
        }
        return inset
    }

    private fun computeConfidence(top: Int, bottom: Int, left: Int, right: Int, w: Int, h: Int): Float {
        val tRatio = top.toFloat() / h
        val bRatio = bottom.toFloat() / h
        val lRatio = left.toFloat() / w
        val rRatio = right.toFloat() / w
        val minRatio = min(min(tRatio, bRatio), min(lRatio, rRatio))
        val avgRatio = (tRatio + bRatio + lRatio + rRatio) / 4
        return min(1f, (minRatio * 8f + avgRatio * 2f).coerceIn(0f, 1f))
    }
}
