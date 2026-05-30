package com.seanyuan.filmframe.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the pure-Kotlin [FrameDetector.detectFromPixels] on synthetic
 * images — no Android dependencies needed. Covers the v4 fixes: colour voting
 * (a logo in one corner must not derail detection) and 3-of-4-side acceptance
 * (a frame the photo bleeds into on one edge is still found).
 */
class FrameDetectorTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private val white = argb(255, 255, 255)
    private val black = argb(0, 0, 0)
    private val red = argb(220, 30, 30)

    private fun image(w: Int, h: Int, fill: (Int, Int) -> Int): IntArray {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = fill(x, y)
        return px
    }

    private fun framed(
        w: Int, h: Int, top: Int, bottom: Int, left: Int, right: Int,
        frame: Int = white, center: Int = black,
    ) = image(w, h) { x, y ->
        if (y < top || y >= h - bottom || x < left || x >= w - right) frame else center
    }

    @Test
    fun detectsFourSidedWhiteFrame() {
        val w = 400; val h = 600
        val r = FrameDetector.detectFromPixels(framed(w, h, 60, 60, 40, 40), w, h)
        assertTrue("a clean white frame should be detected", r.hasFrame)
        assertTrue("top inset near the frame width", r.insets.top in 50..95)
        assertTrue("left inset near the frame width", r.insets.left in 30..75)
    }

    @Test
    fun rejectsGradientWithNoUniformBorder() {
        val w = 400; val h = 600
        // Vertical gradient — the border ring has no majority colour, so there
        // is no frame to find.
        val px = image(w, h) { _, y -> val g = y * 255 / h; argb(g, g, g) }
        assertFalse("a gradient is not a frame", FrameDetector.detectFromPixels(px, w, h).hasFrame)
    }

    @Test
    fun survivesLogoInOneCorner() {
        val w = 400; val h = 600
        val px = framed(w, h, 80, 80, 40, 40)
        // A small brand mark in the top-left, inside the frame band — the exact
        // case that broke the old 4-corner variance gate.
        for (y in 0 until 30) for (x in 0 until 18) px[y * w + x] = red
        val r = FrameDetector.detectFromPixels(px, w, h)
        assertTrue("frame should survive a corner logo", r.hasFrame)
        assertTrue("voted frame colour stays white, not the logo red", ((r.frameColor shr 16) and 0xFF) > 200)
    }

    @Test
    fun detectsThreeSidedFrameBleedingBottom() {
        val w = 400; val h = 600
        // Frame on top/left/right; content runs to the bottom edge.
        val r = FrameDetector.detectFromPixels(framed(w, h, 60, 0, 40, 40), w, h)
        assertTrue("3 clean sides should still count as a frame", r.hasFrame)
        assertEquals("the bleeding side is left uncropped", 0, r.insets.bottom)
        assertTrue(r.insets.top > 0 && r.insets.left > 0 && r.insets.right > 0)
    }

    @Test
    fun detectsBottomHeavyPolaroid() {
        val w = 400; val h = 600
        val r = FrameDetector.detectFromPixels(framed(w, h, 24, 120, 24, 24), w, h)
        assertTrue(r.hasFrame)
        assertTrue("polaroid mat is bottom-heavy", r.insets.bottom > r.insets.top)
    }

    @Test
    fun detectsBlackMatFrame() {
        val w = 400; val h = 600
        // Inverted: black mat around a white photo. Voting keys on whatever the
        // border actually is.
        val r = FrameDetector.detectFromPixels(framed(w, h, 50, 50, 50, 50, frame = black, center = white), w, h)
        assertTrue("a dark mat is a frame too", r.hasFrame)
        assertTrue("voted frame colour is dark", ((r.frameColor shr 16) and 0xFF) < 40)
    }

    // ---- Android bottom watermark bar (Leica / Hasselblad / Zeiss / XMAGE …) ----

    private fun withBottomBar(
        w: Int, h: Int, barH: Int, bar: Int,
        leftPhoto: Int, rightPhoto: Int, logo: Int? = null,
    ) = image(w, h) { x, y ->
        when {
            y >= h - barH -> if (logo != null && (x < w * 15 / 100 || x > w * 80 / 100)) logo else bar
            x < w / 2 -> leftPhoto
            else -> rightPhoto
        }
    }

    @Test
    fun detectsWhiteBottomBar() {
        val w = 400; val h = 600
        val barH = (h * 0.12f).toInt()
        val px = withBottomBar(w, h, barH, white, argb(40, 80, 160), argb(70, 130, 60))
        val r = FrameDetector.detectFromPixels(px, w, h)
        assertTrue("white watermark bar should be detected", r.hasFrame)
        assertEquals("only the bottom is cropped", 0, r.insets.top)
        assertEquals(0, r.insets.left)
        assertEquals(0, r.insets.right)
        assertTrue("bottom inset ≈ bar height", r.insets.bottom in (barH - 5)..(barH + 20))
    }

    @Test
    fun detectsBlackBottomBar() {
        val w = 400; val h = 600
        val barH = (h * 0.1f).toInt()
        val px = withBottomBar(w, h, barH, black, argb(180, 200, 230), argb(150, 170, 120))
        val r = FrameDetector.detectFromPixels(px, w, h)
        assertTrue("black watermark bar should be detected", r.hasFrame)
        assertTrue(r.insets.bottom in (barH - 5)..(barH + 20))
    }

    @Test
    fun bottomBarSurvivesLogoAndText() {
        val w = 400; val h = 600
        val barH = (h * 0.12f).toInt()
        // White bar with a dark logo on the left and dark text block on the right.
        val px = withBottomBar(w, h, barH, white, argb(40, 80, 160), argb(70, 130, 60), logo = black)
        assertTrue("bar with logo + text still detected", FrameDetector.detectFromPixels(px, w, h).hasFrame)
    }

    @Test
    fun rejectsColoredBottomRegionAsBar() {
        val w = 400; val h = 600
        // Bottom third is uniform green grass — a coloured region, not a
        // near-white/black watermark bar.
        val px = withBottomBar(w, h, (h * 0.3f).toInt(), argb(40, 120, 40), argb(40, 80, 160), argb(40, 80, 160))
        assertFalse("green ground is not a watermark bar", FrameDetector.detectFromPixels(px, w, h).hasFrame)
    }
}
