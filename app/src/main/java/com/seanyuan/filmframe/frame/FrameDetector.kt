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
 * v4 — robust frame-color voting + 3-of-4-side acceptance.
 *
 * The v3 detector missed obvious frames in two situations the dogfood surfaced:
 *
 *   1. A logo, brand text, or uneven light in ONE corner (Leica red dot,
 *      "HASSELBLAD" wordmark, a date stamp) pushed the 4-corner colour spread
 *      over a hard variance gate and the detector bailed with "no frame".
 *
 *   2. A frame the photo bleeds into on one edge (content runs to one side,
 *      a 3-sided mat) failed the all-four-sides requirement and was dropped
 *      wholesale, even though three clean framed sides were right there.
 *
 * Fixes:
 *
 *   FRAME COLOUR by vote, not median. Sample 8 points around the border
 *   (4 corners + 4 edge midpoints), then take the largest cluster within
 *   tolerance as the frame colour. One odd corner (logo/text) is outvoted by
 *   the other seven, instead of derailing the whole detection. If no majority
 *   colour exists (top sky vs bottom ground, a busy edge) there's no uniform
 *   border — return no-frame.
 *
 *   SIDES detected independently, frame confirmed at 3-of-4. Each side still
 *   has to prove a full-span uniform run of the frame colour (the real
 *   false-positive guard — a side only "passes" if its entire outer row/column
 *   is frame-coloured, which sky/ground/bokeh photos can't fake on the
 *   verticals). A side that doesn't pass simply gets inset 0 — we crop the
 *   framed sides and leave the bleeding one alone.
 *
 * Two-pass walk per passing side is unchanged from v3: a STRICT pass proves the
 * frame exists, a LOOSE pass extends past caption text / AA fringe, capped at
 * strict×6 so it can't run away into photo content. Safety margin + 50% hard cap.
 *
 * Out of scope: rounded-corner frames, gradient frames, non-rectangular masks.
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
    // Border-colour vote: 8 samples, largest cluster wins. Need a majority (5/8)
    // agreeing within tolerance to call it a uniform border at all. Cluster
    // tolerance is a touch looser than the pixel-match tolerance so a faintly
    // vignetted white mat still votes together.
    private const val BORDER_SAMPLES = 8
    private const val MIN_BORDER_CLUSTER = 5
    private const val CLUSTER_TOLERANCE = 26
    // Confirm a frame at 3 of 4 sides — one bleeding edge is allowed. Fewer than
    // 3 clean framed sides is more likely a false positive than a real frame.
    private const val MIN_FRAMED_SIDES = 3
    // Text-heavy mats break strict early, so loose extends further. Still bounded
    // so a near-uniform photo doesn't get cropped in half.
    private const val LOOSE_OVER_STRICT_CAP = 6f
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
     * No Android dependencies — usable as-is in a KMP shared module or ported
     * verbatim to Swift on iOS.
     *
     * Inset values returned are in the SAME pixel space as the input.
     */
    fun detectFromPixels(pixels: IntArray, w: Int, h: Int): FrameDetectionResult {
        val ring = sampleBorderColors(pixels, w, h)
        val (frameColor, clusterCount) = dominantColor(ring)
        if (clusterCount < MIN_BORDER_CLUSTER) {
            // No majority border colour → no uniform frame.
            return FrameDetectionResult(false, FrameInsets(0, 0, 0, 0), frameColor, 0f)
        }

        // STRICT pass per side — does a full-span uniform run of the frame
        // colour exist on this edge? This is the false-positive guard.
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

        val passTop = strictTop >= minV
        val passBottom = strictBottom >= minV
        val passLeft = strictLeft >= minH
        val passRight = strictRight >= minH
        val framedSides = listOf(passTop, passBottom, passLeft, passRight).count { it }

        if (framedSides < MIN_FRAMED_SIDES) {
            return FrameDetectionResult(false, FrameInsets(0, 0, 0, 0), frameColor, 0f)
        }

        // LOOSE pass extends each PASSING side past caption text / AA fringe.
        // A side that didn't pass strict gets inset 0 (left uncropped).
        val topFinal = sideInset(passTop, strictTop, pixels, w, h, frameColor, vertical = true, fromStart = true)
        val bottomFinal = sideInset(passBottom, strictBottom, pixels, w, h, frameColor, vertical = true, fromStart = false)
        val leftFinal = sideInset(passLeft, strictLeft, pixels, w, h, frameColor, vertical = false, fromStart = true)
        val rightFinal = sideInset(passRight, strictRight, pixels, w, h, frameColor, vertical = false, fromStart = false)

        val confidence = computeConfidence(
            if (passTop) strictTop else 0,
            if (passBottom) strictBottom else 0,
            if (passLeft) strictLeft else 0,
            if (passRight) strictRight else 0,
            w, h,
        )

        return FrameDetectionResult(
            hasFrame = true,
            insets = FrameInsets(top = topFinal, bottom = bottomFinal, left = leftFinal, right = rightFinal),
            frameColor = frameColor,
            confidence = confidence,
        )
    }

    /** Loose-extend one passing side, cap, add safety margin, clamp. */
    private fun sideInset(
        passed: Boolean, strict: Int,
        pixels: IntArray, w: Int, h: Int, frameColor: Int,
        vertical: Boolean, fromStart: Boolean,
    ): Int {
        if (!passed) return 0
        val tol = looseMissTol(strict)
        val loose = if (vertical) {
            walkVertical(pixels, w, h, frameColor, fromTop = fromStart, threshold = LOOSE_ROW_THRESHOLD, missTol = tol)
        } else {
            walkHorizontal(pixels, w, h, frameColor, fromLeft = fromStart, threshold = LOOSE_ROW_THRESHOLD, missTol = tol)
        }
        val capped = min(loose, (strict * LOOSE_OVER_STRICT_CAP).toInt())
        val dim = if (vertical) h else w
        val safety = (dim * SAFETY_INSET_RATIO).toInt()
        val cap = (dim * MAX_INSET_RATIO).toInt()
        return (capped + safety).coerceAtMost(cap)
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

    /** 4 corners + 4 edge midpoints, each averaged over a small patch. */
    private fun sampleBorderColors(pixels: IntArray, w: Int, h: Int): IntArray {
        val sw = max(2, (w * CORNER_SAMPLE_FRACTION).toInt())
        val sh = max(2, (h * CORNER_SAMPLE_FRACTION).toInt())
        val cx = ((w - sw) / 2).coerceAtLeast(0)
        val cy = ((h - sh) / 2).coerceAtLeast(0)
        return intArrayOf(
            averageRegion(pixels, w, 0, 0, sw, sh),            // top-left
            averageRegion(pixels, w, w - sw, 0, sw, sh),       // top-right
            averageRegion(pixels, w, 0, h - sh, sw, sh),       // bottom-left
            averageRegion(pixels, w, w - sw, h - sh, sw, sh),  // bottom-right
            averageRegion(pixels, w, cx, 0, sw, sh),           // top-mid
            averageRegion(pixels, w, cx, h - sh, sw, sh),      // bottom-mid
            averageRegion(pixels, w, 0, cy, sw, sh),           // left-mid
            averageRegion(pixels, w, w - sw, cy, sw, sh),      // right-mid
        )
    }

    /**
     * Largest cluster of samples within [CLUSTER_TOLERANCE]. Returns the
     * cluster's average colour and how many samples agreed (out of 8). An
     * outlier corner (logo/text) lands in a cluster of one and is ignored.
     */
    private fun dominantColor(samples: IntArray): Pair<Int, Int> {
        var bestIndex = 0
        var bestCount = 0
        for (i in samples.indices) {
            var count = 0
            for (j in samples.indices) {
                if (colorClose(samples[i], samples[j], CLUSTER_TOLERANCE)) count++
            }
            if (count > bestCount) {
                bestCount = count
                bestIndex = i
            }
        }
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (j in samples.indices) {
            if (colorClose(samples[bestIndex], samples[j], CLUSTER_TOLERANCE)) {
                r += (samples[j] shr 16) and 0xFF
                g += (samples[j] shr 8) and 0xFF
                b += samples[j] and 0xFF
                n++
            }
        }
        if (n == 0L) return samples[bestIndex] to bestCount
        return rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()) to bestCount
    }

    private fun colorClose(a: Int, b: Int, tol: Int): Boolean {
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return dr <= tol && dg <= tol && db <= tol
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
        if (n == 0L) return rgb(0, 0, 0)
        return rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
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
        val avgRatio = (tRatio + bRatio + lRatio + rRatio) / 4
        val framedSides = listOf(top, bottom, left, right).count { it > 0 }
        return min(1f, (avgRatio * 4f + framedSides * 0.15f).coerceIn(0f, 1f))
    }
}
