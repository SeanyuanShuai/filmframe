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

    // ---- Bottom watermark-bar (Android co-brand strips) ----
    // The dominant Android built-in frame is a solid bar appended below the
    // photo (Leica / Hasselblad / Zeiss / XMAGE / Honor), white or black, with
    // a logo on one end and EXIF text on the other, and a HARD top edge.
    private const val BAR_MAX_RATIO = 0.20f      // bars run ~7-16% of height; cap at 20%
    private const val BAR_MIN_RATIO = 0.05f
    private const val BAR_ROW_FRACTION = 0.5f    // a bar row is ≥50% background colour (logo/text take the rest)
    private const val BAR_MISS_RATIO = 0.02f     // tolerate a text line ~2% tall before stopping the walk
    private const val BAR_EDGE_STEP_MIN = 60     // sum of per-channel deltas across the seam: bars step, gradients ramp
    private const val BAR_FILL_MIN = 0.6f        // the bar region is ≥60% background colour

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
        // 1. Four-side mat (white/black border, polaroid, 3-sided).
        val mat = detectMat(pixels, w, h)
        if (mat.hasFrame) return mat
        // 2. Bottom watermark bar — the common Android co-brand strip the mat
        //    detector can't see, because the frame is on one side only.
        val (barHeight, barColor) = detectBottomBar(pixels, w, h)
        if (barHeight > 0) {
            return FrameDetectionResult(
                hasFrame = true,
                insets = FrameInsets(top = 0, bottom = barHeight, left = 0, right = 0),
                frameColor = barColor,
                confidence = 0.9f,
            )
        }
        return FrameDetectionResult(false, FrameInsets(0, 0, 0, 0), mat.frameColor, 0f)
    }

    private fun detectMat(pixels: IntArray, w: Int, h: Int): FrameDetectionResult {
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

    /**
     * Detect a solid watermark bar appended at the bottom (the Android co-brand
     * strip). Returns (barHeight, barColor); height 0 if none.
     *
     * Pipeline: take the bar's background colour from the outer rows, require it
     * to be near-white or near-black, walk up while rows stay mostly that colour
     * (tolerating logo/text rows), then gate on three things — a height in the
     * 5-20% band, a HARD step across the seam (a real bar steps; a sky/ground
     * gradient ramps, and the step test is what tells them apart), and a bar
     * region that's predominantly the background colour.
     */
    private fun detectBottomBar(pixels: IntArray, w: Int, h: Int): Pair<Int, Int> {
        val barColor = dominantBottomColor(pixels, w, h)
        if (!isBarColor(barColor)) return 0 to barColor

        val maxBar = (h * BAR_MAX_RATIO).toInt()
        val minBar = max(1, (h * BAR_MIN_RATIO).toInt())
        val missTol = max(4, (h * BAR_MISS_RATIO).toInt())
        if (maxBar < minBar) return 0 to barColor

        var bar = 0
        var miss = 0
        for (i in 0 until maxBar) {
            val y = h - 1 - i
            if (rowMatchRatio(pixels, w, y, barColor) >= BAR_ROW_FRACTION) {
                bar = i + 1
                miss = 0
            } else {
                miss++
                if (miss > missTol) break
            }
        }
        if (bar < minBar) return 0 to barColor

        val seamY = h - bar
        if (seamY < 4) return 0 to barColor

        // Hard-edge test — the decisive signal. Rows just inside the bar vs rows
        // just above (photo) must differ sharply. A gradient's cross-seam step is
        // tiny, so this rejects uniform sky/ground.
        val inside = rowsAverage(pixels, w, seamY, 3)
        val above = rowsAverage(pixels, w, (seamY - 3).coerceAtLeast(0), 3)
        if (colorDistance(inside, above) < BAR_EDGE_STEP_MIN) return 0 to barColor

        // The bar must be predominantly its background colour, not just one step line.
        if (regionFraction(pixels, w, seamY, h - 1, barColor) < BAR_FILL_MIN) return 0 to barColor

        val safety = (h * SAFETY_INSET_RATIO).toInt()
        return (bar + safety).coerceAtMost((h * MAX_INSET_RATIO).toInt()) to barColor
    }

    /** Modal colour of the outermost bottom rows, via a coarse 16-level histogram. */
    private fun dominantBottomColor(pixels: IntArray, w: Int, h: Int): Int {
        val rows = max(2, (h * 0.01f).toInt())
        val counts = HashMap<Int, Int>()
        for (y in (h - rows) until h) {
            val start = y * w
            for (x in 0 until w) {
                val p = pixels[start + x]
                val key = (((p shr 20) and 0xF) shl 8) or (((p shr 12) and 0xF) shl 4) or ((p shr 4) and 0xF)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        val topKey = counts.maxByOrNull { it.value }?.key ?: return rgb(255, 255, 255)
        return rgb(((topKey shr 8) and 0xF) * 17, ((topKey shr 4) and 0xF) * 17, (topKey and 0xF) * 17)
    }

    /** A bar background is near-neutral white/off-white or near-black. */
    private fun isBarColor(c: Int): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val luma = (r * 299 + g * 587 + b * 114) / 1000
        val spread = max(max(r, g), b) - min(min(r, g), b)
        return (luma >= 210 && spread <= 30) || luma <= 48
    }

    private fun rowsAverage(pixels: IntArray, w: Int, yStart: Int, count: Int): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in yStart until (yStart + count)) {
            val start = y * w
            for (x in 0 until w) {
                val p = pixels[start + x]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
        }
        if (n == 0L) return rgb(0, 0, 0)
        return rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun regionFraction(pixels: IntArray, w: Int, yFrom: Int, yTo: Int, color: Int): Float {
        var match = 0; var total = 0
        for (y in yFrom..yTo) {
            val start = y * w
            for (x in 0 until w) {
                if (pixelMatches(pixels[start + x], color)) match++
                total++
            }
        }
        return if (total == 0) 0f else match.toFloat() / total
    }

    private fun colorDistance(a: Int, b: Int): Int {
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return dr + dg + db
    }
}
