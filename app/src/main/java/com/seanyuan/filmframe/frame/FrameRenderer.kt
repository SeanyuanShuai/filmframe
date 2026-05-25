package com.seanyuan.filmframe.frame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.seanyuan.filmframe.data.PhotoExif
import kotlin.math.max

sealed interface FrameTemplate {
    val id: String
    val displayName: String
    fun render(source: Bitmap, exif: PhotoExif?): Bitmap
}

/**
 * Classic gallery white border.
 *
 * Proportions reference Magnum / Aperture photo books:
 *   - top/sides equal at 5% of the long edge
 *   - bottom 14% of the long edge for caption block
 *   - title sits at 42% down the caption block (visual golden ratio)
 *   - title: italic serif, all caps, generous letter-spacing — bookish
 *   - params: sans-serif, slightly smaller, middle-dot separated
 *
 * The slightly off-white background (#FAFAFA) avoids harsh pure-white on OLED
 * screens and feels closer to printed paper.
 */
data class ClassicTemplate(
    val borderColor: Int = 0xFFFAFAFA.toInt(),
    val titleColor: Int = 0xFF1A1A1A.toInt(),
    val paramsColor: Int = 0xCC1A1A1A.toInt(),
    val sideMarginPct: Float = 0.05f,
    val bottomMarginPct: Float = 0.14f,
    val showCaption: Boolean = true,
) : FrameTemplate {

    override val id = "classic"
    override val displayName = "Classic"

    override fun render(source: Bitmap, exif: PhotoExif?): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val longEdge = max(srcW, srcH)
        val sideMargin = (longEdge * sideMarginPct).toInt()
        val topMargin = sideMargin
        val bottomMargin = (longEdge * bottomMarginPct).toInt()

        val outW = srcW + sideMargin * 2
        val outH = srcH + topMargin + bottomMargin

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(borderColor)
        canvas.drawBitmap(source, sideMargin.toFloat(), topMargin.toFloat(), null)

        if (showCaption && exif != null) {
            drawCaption(
                canvas = canvas,
                exif = exif,
                canvasWidth = outW,
                captionTop = srcH + topMargin,
                captionHeight = bottomMargin,
                longEdge = longEdge,
            )
        }
        return output
    }

    private fun drawCaption(
        canvas: Canvas,
        exif: PhotoExif,
        canvasWidth: Int,
        captionTop: Int,
        captionHeight: Int,
        longEdge: Int,
    ) {
        val titleSize = longEdge * 0.020f
        val paramsSize = longEdge * 0.013f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = titleColor
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textSize = titleSize
            letterSpacing = 0.18f
            textAlign = Paint.Align.CENTER
        }
        val paramsPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = paramsColor
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = paramsSize
            letterSpacing = 0.12f
            textAlign = Paint.Align.CENTER
        }

        val cx = canvasWidth / 2f
        val titleY = captionTop + captionHeight * 0.42f
        val paramsY = titleY + titleSize * 1.7f

        composeTitle(exif).takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it, cx, titleY, titlePaint)
        }
        composeParams(exif).takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it, cx, paramsY, paramsPaint)
        }
    }

    private fun composeTitle(exif: PhotoExif): String {
        val make = exif.cameraMake.orEmpty().trim()
        val model = exif.cameraModel.orEmpty().trim()
        val body = when {
            make.isEmpty() -> model
            model.isEmpty() -> make
            model.startsWith(make, ignoreCase = true) -> model
            else -> "$make $model"
        }
        return body.uppercase()
    }

    private fun composeParams(exif: PhotoExif): String {
        return listOfNotNull(
            exif.focalLength,
            exif.aperture,
            exif.shutterSpeed,
            exif.iso?.takeIf { it.isNotBlank() }?.let { "ISO $it" },
        ).filter { it.isNotBlank() }.joinToString("  ·  ")
    }
}

/**
 * Pure-color mat — equal margins on all 4 sides, no text, no decoration.
 *
 * The default 7% margin lands between "framed print" and "mat board" — visually
 * generous enough to read as intentional, narrow enough not to feel poster-y.
 * Pair with a darker color (#0E0E0E) for the gallery dark-mount look.
 */
data class SolidTemplate(
    val borderColor: Int = 0xFFFAFAFA.toInt(),
    val marginPct: Float = 0.07f,
) : FrameTemplate {

    override val id = "solid"
    override val displayName = "纯色"

    override fun render(source: Bitmap, exif: PhotoExif?): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val longEdge = max(srcW, srcH)
        val margin = (longEdge * marginPct).toInt()
        val outW = srcW + margin * 2
        val outH = srcH + margin * 2

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(borderColor)
        canvas.drawBitmap(source, margin.toFloat(), margin.toFloat(), null)
        return output
    }
}

object FrameRenderer {
    /**
     * Crops the existing frame off the source bitmap before applying a new one.
     * Used after FrameDetector reports a frame and the user confirms removal.
     */
    fun deframe(source: Bitmap, insets: FrameInsets): Bitmap {
        val left = insets.left.coerceAtLeast(0)
        val top = insets.top.coerceAtLeast(0)
        val width = (source.width - left - insets.right).coerceAtLeast(1)
        val height = (source.height - top - insets.bottom).coerceAtLeast(1)
        return Bitmap.createBitmap(source, left, top, width, height)
    }
}
