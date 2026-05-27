package com.seanyuan.filmframe.frame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.seanyuan.filmframe.data.PhotoExif
import com.seanyuan.filmframe.data.WatermarkPosition
import com.seanyuan.filmframe.data.WatermarkSettings
import kotlin.math.max

sealed interface FrameTemplate {
    val id: String
    val displayName: String
    fun render(
        context: Context,
        source: Bitmap,
        exif: PhotoExif?,
        watermark: WatermarkSettings = WatermarkSettings.Default,
    ): Bitmap
}

// ──────────────────────────────────────────────────────────────────────────
// Classic — Magnum / Aperture book style.
// ──────────────────────────────────────────────────────────────────────────
data class ClassicTemplate(
    val borderColor: Int = 0xFFFAFAFA.toInt(),
    val titleColor: Int = 0xFF1A1A1A.toInt(),
    val paramsColor: Int = 0xCC1A1A1A.toInt(),
    val sideMarginPct: Float = 0.05f,
    val bottomMarginPct: Float = 0.14f,
) : FrameTemplate {

    override val id = "classic"
    override val displayName = "Classic"

    override fun render(
        context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings,
    ): Bitmap {
        val out = matCanvas(source, sideMarginPct, sideMarginPct, sideMarginPct, bottomMarginPct, borderColor)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        val sideMargin = (longEdge * sideMarginPct).toInt()
        val bottomMargin = (longEdge * bottomMarginPct).toInt()
        if (exif != null) {
            drawClassicCaption(
                context, canvas, exif,
                canvasWidth = out.width,
                captionTop = source.height + sideMargin,
                captionHeight = bottomMargin,
                longEdge = longEdge,
                titleColor = titleColor,
                paramsColor = paramsColor,
            )
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, color = 0x99000000.toInt())
        return out
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Solid — equal pure-color mat, no text.
// ──────────────────────────────────────────────────────────────────────────
data class SolidTemplate(
    val borderColor: Int = 0xFFFAFAFA.toInt(),
    val marginPct: Float = 0.07f,
) : FrameTemplate {

    override val id = "solid"
    override val displayName = "纯色"

    override fun render(
        context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings,
    ): Bitmap {
        val out = matCanvas(source, marginPct, marginPct, marginPct, marginPct, borderColor)
        drawWatermark(
            context, Canvas(out), watermark,
            out.width, out.height, max(source.width, source.height),
            color = 0x99000000.toInt(),
        )
        return out
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Bold — gallery dark mount.
// ──────────────────────────────────────────────────────────────────────────
data class BoldTemplate(
    val borderColor: Int = 0xFF0A0A0A.toInt(),
    val titleColor: Int = 0xFFFAFAFA.toInt(),
    val paramsColor: Int = 0xAAFAFAFA.toInt(),
    val sideMarginPct: Float = 0.08f,
    val bottomMarginPct: Float = 0.18f,
) : FrameTemplate {

    override val id = "bold"
    override val displayName = "Bold"

    override fun render(
        context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings,
    ): Bitmap {
        val out = matCanvas(source, sideMarginPct, sideMarginPct, sideMarginPct, bottomMarginPct, borderColor)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        val sideMargin = (longEdge * sideMarginPct).toInt()
        val bottomMargin = (longEdge * bottomMarginPct).toInt()
        if (exif != null) {
            drawBoldCaption(
                context, canvas, exif,
                canvasWidth = out.width,
                captionTop = source.height + sideMargin,
                captionHeight = bottomMargin,
                longEdge = longEdge,
                titleColor = titleColor,
                paramsColor = paramsColor,
            )
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, color = 0x99FAFAFA.toInt())
        return out
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Minimal — hairline white border. No text.
// ──────────────────────────────────────────────────────────────────────────
data class MinimalTemplate(
    val borderColor: Int = 0xFFFAFAFA.toInt(),
    val marginPct: Float = 0.012f,
) : FrameTemplate {

    override val id = "minimal"
    override val displayName = "Minimal"

    override fun render(
        context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings,
    ): Bitmap {
        val out = matCanvas(source, marginPct, marginPct, marginPct, marginPct, borderColor)
        drawWatermark(
            context, Canvas(out), watermark,
            out.width, out.height, max(source.width, source.height),
            color = 0x99000000.toInt(),
        )
        return out
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Polaroid — asymmetric. Wide bottom for handwritten title.
// ──────────────────────────────────────────────────────────────────────────
data class PolaroidTemplate(
    val borderColor: Int = 0xFFFAF7F0.toInt(),
    val titleColor: Int = 0xFF2A2520.toInt(),
    val topSideMarginPct: Float = 0.045f,
    val bottomMarginPct: Float = 0.24f,
) : FrameTemplate {

    override val id = "polaroid"
    override val displayName = "Polaroid"

    override fun render(
        context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings,
    ): Bitmap {
        val out = matCanvas(source, topSideMarginPct, topSideMarginPct, topSideMarginPct, bottomMarginPct, borderColor)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        val topMargin = (longEdge * topSideMarginPct).toInt()
        val bottomMargin = (longEdge * bottomMarginPct).toInt()
        if (exif != null) {
            drawPolaroidCaption(
                context, canvas, exif,
                canvasWidth = out.width,
                captionTop = source.height + topMargin,
                captionHeight = bottomMargin,
                longEdge = longEdge,
                titleColor = titleColor,
            )
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, color = 0x882A2520.toInt())
        return out
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Shared helpers
// ──────────────────────────────────────────────────────────────────────────

private fun matCanvas(
    source: Bitmap, leftPct: Float, rightPct: Float, topPct: Float, bottomPct: Float, bgColor: Int,
): Bitmap {
    val longEdge = max(source.width, source.height)
    val l = (longEdge * leftPct).toInt()
    val r = (longEdge * rightPct).toInt()
    val t = (longEdge * topPct).toInt()
    val b = (longEdge * bottomPct).toInt()
    val outW = source.width + l + r
    val outH = source.height + t + b
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(bgColor)
    canvas.drawBitmap(source, l.toFloat(), t.toFloat(), null)
    return out
}

private fun drawClassicCaption(
    context: Context, canvas: Canvas, exif: PhotoExif,
    canvasWidth: Int, captionTop: Int, captionHeight: Int, longEdge: Int,
    titleColor: Int, paramsColor: Int,
) {
    val titleSize = longEdge * 0.024f
    val paramsSize = longEdge * 0.014f

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = titleColor
        typeface = Fonts.cormorantItalic(context)
        textSize = titleSize
        letterSpacing = 0.14f
        textAlign = Paint.Align.CENTER
    }
    val paramsPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = paramsColor
        typeface = Fonts.inter(context)
        textSize = paramsSize
        letterSpacing = 0.18f
        textAlign = Paint.Align.CENTER
    }

    val cx = canvasWidth / 2f
    val titleY = captionTop + captionHeight * 0.42f
    val paramsY = titleY + titleSize * 1.75f

    composeTitle(exif).takeIf { it.isNotBlank() }?.let { canvas.drawText(it, cx, titleY, titlePaint) }
    composeParams(exif).takeIf { it.isNotBlank() }?.let { canvas.drawText(it, cx, paramsY, paramsPaint) }
}

private fun drawBoldCaption(
    context: Context, canvas: Canvas, exif: PhotoExif,
    canvasWidth: Int, captionTop: Int, captionHeight: Int, longEdge: Int,
    titleColor: Int, paramsColor: Int,
) {
    val titleSize = longEdge * 0.038f
    val paramsSize = longEdge * 0.016f

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = titleColor
        typeface = Fonts.dmSerif(context)
        textSize = titleSize
        letterSpacing = 0.05f
        textAlign = Paint.Align.CENTER
    }
    val paramsPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = paramsColor
        typeface = Fonts.inter(context)
        textSize = paramsSize
        letterSpacing = 0.22f
        textAlign = Paint.Align.CENTER
    }

    val cx = canvasWidth / 2f
    val titleY = captionTop + captionHeight * 0.45f
    val paramsY = titleY + titleSize * 1.05f

    composeTitle(exif).takeIf { it.isNotBlank() }?.let { canvas.drawText(it, cx, titleY, titlePaint) }
    composeParams(exif).takeIf { it.isNotBlank() }?.let { canvas.drawText(it, cx, paramsY, paramsPaint) }
}

private fun drawPolaroidCaption(
    context: Context, canvas: Canvas, exif: PhotoExif,
    canvasWidth: Int, captionTop: Int, captionHeight: Int, longEdge: Int,
    titleColor: Int,
) {
    val titleSize = longEdge * 0.028f
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = titleColor
        typeface = Fonts.cormorantItalic(context)
        textSize = titleSize
        letterSpacing = 0.02f
        textAlign = Paint.Align.CENTER
    }
    val cx = canvasWidth / 2f
    val titleY = captionTop + captionHeight * 0.55f
    val title = listOfNotNull(
        composeTitle(exif).takeIf { it.isNotBlank() },
        exif.dateTaken?.take(10)?.replace(":", "."),
    ).joinToString("   ")
    if (title.isNotBlank()) canvas.drawText(title, cx, titleY, titlePaint)
}

/**
 * Right-aligned watermark in the bottom margin of the output. Pads ~2.2% in
 * from the right and bottom edges of the canvas so it sits inside the frame
 * area (never overlapping the photo proper).
 */
private fun drawWatermark(
    context: Context,
    canvas: Canvas,
    options: WatermarkSettings,
    outWidth: Int,
    outHeight: Int,
    longEdge: Int,
    color: Int,
) {
    if (!options.active) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.color = color
        typeface = Fonts.cormorantItalic(context)
        textSize = longEdge * 0.013f
        letterSpacing = 0.04f
    }
    val pad = longEdge * 0.022f
    val textHeight = paint.textSize
    when (options.position) {
        WatermarkPosition.TopLeft -> {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(options.text, pad, pad + textHeight, paint)
        }
        WatermarkPosition.TopRight -> {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(options.text, outWidth - pad, pad + textHeight, paint)
        }
        WatermarkPosition.BottomLeft -> {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(options.text, pad, outHeight - pad, paint)
        }
        WatermarkPosition.BottomRight -> {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(options.text, outWidth - pad, outHeight - pad, paint)
        }
    }
}

private fun composeTitle(exif: PhotoExif): String {
    val make = exif.cameraMake.orEmpty().trim()
    val model = exif.cameraModel.orEmpty().trim()
    return when {
        make.isEmpty() -> model
        model.isEmpty() -> make
        model.startsWith(make, ignoreCase = true) -> model
        else -> "$make $model"
    }
}

private fun composeParams(exif: PhotoExif): String {
    return listOfNotNull(
        exif.focalLength,
        exif.aperture,
        exif.shutterSpeed,
        exif.iso?.takeIf { it.isNotBlank() }?.let { "ISO $it" },
    ).filter { it.isNotBlank() }.joinToString("  ·  ")
}

object FrameRenderer {
    fun deframe(source: Bitmap, insets: FrameInsets): Bitmap {
        val left = insets.left.coerceAtLeast(0)
        val top = insets.top.coerceAtLeast(0)
        val width = (source.width - left - insets.right).coerceAtLeast(1)
        val height = (source.height - top - insets.bottom).coerceAtLeast(1)
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    val all: List<FrameTemplate> = listOf(
        ClassicTemplate(),
        BoldTemplate(),
        SolidTemplate(),
        MinimalTemplate(),
        PolaroidTemplate(),
    )
}
