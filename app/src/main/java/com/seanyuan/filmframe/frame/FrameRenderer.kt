package com.seanyuan.filmframe.frame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.seanyuan.filmframe.data.PhotoExif
import com.seanyuan.filmframe.data.WatermarkPosition
import com.seanyuan.filmframe.data.WatermarkSettings
import kotlin.math.max

/**
 * A single frame style. Templates live inside a [FrameGroup]; the home screen
 * picks the group, the editor shows only that group's templates.
 */
sealed interface FrameTemplate {
    val id: String
    val displayName: String
    val zhName: String          // Chinese label shown on the editor swatch
    val swatchColor: Long       // fallback swatch tint (editor renders a live preview on top)
    fun render(
        context: Context,
        source: Bitmap,
        exif: PhotoExif?,
        watermark: WatermarkSettings = WatermarkSettings.Default,
        adjustments: TemplateAdjustments = TemplateAdjustments.Default,
    ): Bitmap
}

private const val ACCENT = 0xFFF05023.toInt()

// ══════════════════════════════════════════════════════════════════════════
// Group: 杂志留白 Editorial Margin — warm paper white, clean whitespace.
// ══════════════════════════════════════════════════════════════════════════

/** Full-bleed photo with a narrow warm foot carrying one centered serif line. */
data class EditorialFoot(
    val bg: Int = 0xFFF7F4EE.toInt(),
    val ink: Int = 0xFF2A2824.toInt(),
    val footPct: Float = 0.075f,
) : FrameTemplate {
    override val id = "ed_foot"
    override val displayName = "Foot"
    override val zhName = "窄底注脚"
    override val swatchColor = 0xFFF7F4EE
    override fun render(context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings, adjustments: TemplateAdjustments): Bitmap {
        val b = (footPct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val out = matCanvas(source, 0f, 0f, 0f, b, bg)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        if (exif != null && adjustments.showCaption) {
            val line = composeTitle(exif).ifBlank { composeParams(exif) }
            if (line.isNotBlank()) {
                val p = tp(Fonts.cormorantItalic(context), ink, longEdge * 0.026f * adjustments.titleSizeMultiplier, 0.04f)
                canvas.drawText(line, out.width / 2f, source.height + (longEdge * b) * 0.62f, p)
            }
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, 0x88000000.toInt())
        return out
    }
}

/** Album page — even warm margins, a heavier foot, a quiet centered EXIF line. */
data class EditorialAlbum(
    val bg: Int = 0xFFF7F4EE.toInt(),
    val ink: Int = 0xFF6B6760.toInt(),
    val sidePct: Float = 0.06f,
    val bottomPct: Float = 0.10f,
) : FrameTemplate {
    override val id = "ed_album"
    override val displayName = "Album"
    override val zhName = "相册留白"
    override val swatchColor = 0xFFF1EDE4
    override fun render(context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings, adjustments: TemplateAdjustments): Bitmap {
        val s = (sidePct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val b = (bottomPct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val out = matCanvas(source, s, s, s, b, bg)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        if (exif != null && adjustments.showCaption) {
            val line = composeParams(exif)
            if (line.isNotBlank()) {
                val p = tp(Fonts.cormorant(context), ink, longEdge * 0.018f * adjustments.titleSizeMultiplier, 0.16f)
                canvas.drawText(line, out.width / 2f, source.height + (longEdge * s) + (longEdge * b) * 0.6f, p)
            }
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, 0x88000000.toInt())
        return out
    }
}

/** Magazine header — warm band on top, photo full-bleed below; title left, EXIF right. */
data class EditorialHeader(
    val bg: Int = 0xFFF7F4EE.toInt(),
    val ink: Int = 0xFF2A2824.toInt(),
    val topPct: Float = 0.11f,
) : FrameTemplate {
    override val id = "ed_header"
    override val displayName = "Header"
    override val zhName = "顶部页眉"
    override val swatchColor = 0xFFFBF9F4
    override fun render(context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings, adjustments: TemplateAdjustments): Bitmap {
        val t = (topPct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val out = matCanvas(source, 0f, 0f, t, 0f, bg)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        val bandH = longEdge * t
        if (exif != null && adjustments.showCaption) {
            val pad = longEdge * 0.04f
            val title = composeTitle(exif)
            if (title.isNotBlank()) {
                val p = tp(Fonts.dmSerif(context), ink, longEdge * 0.03f * adjustments.titleSizeMultiplier, 0f, Paint.Align.LEFT)
                canvas.drawText(title, pad, bandH * 0.64f, p)
            }
            val params = composeParams(exif).uppercase()
            if (params.isNotBlank()) {
                val p = tp(Fonts.inter(context), 0xAA2A2824.toInt(), longEdge * 0.013f * adjustments.titleSizeMultiplier, 0.18f, Paint.Align.RIGHT)
                canvas.drawText(params, out.width - pad, bandH * 0.62f, p)
            }
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, 0x88000000.toInt())
        return out
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Group: 美术馆装裱 Passepartout — cool museum white, bottom-weighted mat.
// ══════════════════════════════════════════════════════════════════════════

/** Single mat — equal top/side margins, heavier bottom plate, centered caption. */
data class PasseSingle(
    val bg: Int = 0xFFFAFAF8.toInt(),
    val titleInk: Int = 0xFF1C1C1C.toInt(),
    val metaInk: Int = 0xFF8A8A8A.toInt(),
    val sidePct: Float = 0.08f,
    val bottomPct: Float = 0.15f,
) : FrameTemplate {
    override val id = "pa_single"
    override val displayName = "Single Mat"
    override val zhName = "单层裱框"
    override val swatchColor = 0xFFFAFAF8
    override fun render(context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings, adjustments: TemplateAdjustments): Bitmap {
        val s = (sidePct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val b = (bottomPct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val out = matCanvas(source, s, s, s, b, bg)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        val m = (longEdge * s).toInt()
        keyline(canvas, m, m, source.width, source.height, 0x1F000000, max(1f, longEdge * 0.0012f))
        if (exif != null && adjustments.showCaption) {
            val cx = out.width / 2f
            val titleY = source.height + m + (longEdge * b) * 0.4f
            composeTitle(exif).takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, cx, titleY, tp(Fonts.cormorant(context), titleInk, longEdge * 0.026f * adjustments.titleSizeMultiplier, 0.04f))
            }
            composeParams(exif).uppercase().takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, cx, titleY + longEdge * 0.034f * adjustments.titleSizeMultiplier, tp(Fonts.inter(context), metaInk, longEdge * 0.013f * adjustments.titleSizeMultiplier, 0.22f))
            }
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, 0x88000000.toInt())
        return out
    }
}

/** Plate caption — same mat, caption block aligned to the photo's left edge. */
data class PassePlate(
    val bg: Int = 0xFFFAFAF8.toInt(),
    val titleInk: Int = 0xFF1C1C1C.toInt(),
    val metaInk: Int = 0xFF8A8A8A.toInt(),
    val sidePct: Float = 0.08f,
    val bottomPct: Float = 0.15f,
) : FrameTemplate {
    override val id = "pa_plate"
    override val displayName = "Plate"
    override val zhName = "左对齐说明"
    override val swatchColor = 0xFFF4F4F0
    override fun render(context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings, adjustments: TemplateAdjustments): Bitmap {
        val s = (sidePct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val b = (bottomPct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val out = matCanvas(source, s, s, s, b, bg)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        val m = (longEdge * s).toInt()
        keyline(canvas, m, m, source.width, source.height, 0x1F000000, max(1f, longEdge * 0.0012f))
        if (exif != null && adjustments.showCaption) {
            val x = m.toFloat()
            val titleY = source.height + m + (longEdge * b) * 0.42f
            composeTitle(exif).takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, x, titleY, tp(Fonts.cormorantItalic(context), titleInk, longEdge * 0.026f * adjustments.titleSizeMultiplier, 0.02f, Paint.Align.LEFT))
            }
            composeParams(exif).uppercase().takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, x, titleY + longEdge * 0.032f * adjustments.titleSizeMultiplier, tp(Fonts.inter(context), metaInk, longEdge * 0.013f * adjustments.titleSizeMultiplier, 0.2f, Paint.Align.LEFT))
            }
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, 0x88000000.toInt())
        return out
    }
}

/** Accession — single mat with a small orange accession code in the plate. */
data class PasseAccession(
    val bg: Int = 0xFFFAFAF8.toInt(),
    val titleInk: Int = 0xFF1C1C1C.toInt(),
    val sidePct: Float = 0.08f,
    val bottomPct: Float = 0.15f,
) : FrameTemplate {
    override val id = "pa_accession"
    override val displayName = "Accession"
    override val zhName = "编号裱框"
    override val swatchColor = 0xFFFAFAF8
    override fun render(context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings, adjustments: TemplateAdjustments): Bitmap {
        val s = (sidePct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val b = (bottomPct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val out = matCanvas(source, s, s, s, b, bg)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        val m = (longEdge * s).toInt()
        keyline(canvas, m, m, source.width, source.height, 0x1F000000, max(1f, longEdge * 0.0012f))
        if (exif != null && adjustments.showCaption) {
            val plateY = source.height + m + (longEdge * b) * 0.44f
            composeTitle(exif).takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, m.toFloat(), plateY, tp(Fonts.cormorant(context), titleInk, longEdge * 0.024f * adjustments.titleSizeMultiplier, 0.02f, Paint.Align.LEFT))
            }
            val code = accessionCode(exif)
            canvas.drawText(code, (out.width - m).toFloat(), plateY, tp(Fonts.inter(context), ACCENT, longEdge * 0.013f * adjustments.titleSizeMultiplier, 0.24f, Paint.Align.RIGHT))
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, 0x88000000.toInt())
        return out
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Group: 片边底纹 Rebate — 35mm sprocket black, warm film-edge markings.
// ══════════════════════════════════════════════════════════════════════════

/** Full 35mm — sprocket bands top and bottom, mono film-edge text below. */
data class RebateFull(
    val bg: Int = 0xFF0B0B0B.toInt(),
    val bandPct: Float = 0.11f,
) : FrameTemplate {
    override val id = "re_full"
    override val displayName = "35mm"
    override val zhName = "全幅片孔"
    override val swatchColor = 0xFF0B0B0B
    override fun render(context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings, adjustments: TemplateAdjustments): Bitmap {
        val band = (bandPct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val out = matCanvas(source, 0f, 0f, band, band, bg)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        val bh = longEdge * band
        sprockets(canvas, 0f, bh, out.width, 0xFF3A3A38.toInt())
        sprockets(canvas, source.height + bh, bh, out.width, 0xFF3A3A38.toInt())
        if (exif != null && adjustments.showCaption) {
            filmEdge(context, canvas, exif, out.width, source.height + bh, bh, longEdge, adjustments.titleSizeMultiplier)
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, 0x99FFFFFF.toInt())
        return out
    }
}

/** Bottom strip — a single sprocket band at the foot, photo full-bleed above. */
data class RebateStrip(
    val bg: Int = 0xFF0B0B0B.toInt(),
    val bandPct: Float = 0.12f,
) : FrameTemplate {
    override val id = "re_strip"
    override val displayName = "Strip"
    override val zhName = "单边片边"
    override val swatchColor = 0xFF151515
    override fun render(context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings, adjustments: TemplateAdjustments): Bitmap {
        val band = (bandPct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val out = matCanvas(source, 0f, 0f, 0f, band, bg)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        val bh = longEdge * band
        sprockets(canvas, source.height.toFloat(), bh, out.width, 0xFF3A3A38.toInt())
        if (exif != null && adjustments.showCaption) {
            filmEdge(context, canvas, exif, out.width, source.height.toFloat(), bh, longEdge, adjustments.titleSizeMultiplier)
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, 0x99FFFFFF.toInt())
        return out
    }
}

/** Date-back — full-bleed photo, mono foot, an orange date stamp in the corner. */
data class RebateDateback(
    val bg: Int = 0xFF0B0B0B.toInt(),
    val bandPct: Float = 0.09f,
) : FrameTemplate {
    override val id = "re_dateback"
    override val displayName = "Date-Back"
    override val zhName = "日期背"
    override val swatchColor = 0xFF0B0B0B
    override fun render(context: Context, source: Bitmap, exif: PhotoExif?, watermark: WatermarkSettings, adjustments: TemplateAdjustments): Bitmap {
        val band = (bandPct * adjustments.borderWidthMultiplier).coerceAtLeast(0.001f)
        val out = matCanvas(source, 0f, 0f, 0f, band, bg)
        val canvas = Canvas(out)
        val longEdge = max(source.width, source.height)
        val bh = longEdge * band
        if (exif != null && adjustments.showCaption) {
            val params = composeParams(exif)
            if (params.isNotBlank()) {
                val p = tp(Typeface.MONOSPACE, 0xFFE6E6E6.toInt(), longEdge * 0.014f * adjustments.titleSizeMultiplier, 0.05f)
                canvas.drawText(params, out.width / 2f, source.height + bh * 0.62f, p)
            }
            // orange seven-segment-style date stamp inside the photo's lower-right
            val date = exif.dateTaken?.take(10)?.replace(":", " ")?.let { "'" + it.substring(2) } ?: ""
            if (date.isNotBlank()) {
                val p = tp(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD), ACCENT, longEdge * 0.02f * adjustments.titleSizeMultiplier, 0.08f, Paint.Align.RIGHT)
                canvas.drawText(date, source.width - longEdge * 0.03f, source.height - longEdge * 0.03f, p)
            }
        }
        drawWatermark(context, canvas, watermark, out.width, out.height, longEdge, 0x99FFFFFF.toInt())
        return out
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Shared helpers
// ══════════════════════════════════════════════════════════════════════════

private fun matCanvas(source: Bitmap, leftPct: Float, rightPct: Float, topPct: Float, bottomPct: Float, bgColor: Int): Bitmap {
    val longEdge = max(source.width, source.height)
    val l = (longEdge * leftPct).toInt()
    val r = (longEdge * rightPct).toInt()
    val t = (longEdge * topPct).toInt()
    val b = (longEdge * bottomPct).toInt()
    val out = Bitmap.createBitmap(source.width + l + r, source.height + t + b, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(bgColor)
    canvas.drawBitmap(source, l.toFloat(), t.toFloat(), null)
    return out
}

private fun tp(face: Typeface, color: Int, size: Float, tracking: Float = 0f, align: Paint.Align = Paint.Align.CENTER) =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.color = color
        typeface = face
        textSize = size
        letterSpacing = tracking
        textAlign = align
    }

private fun keyline(canvas: Canvas, l: Int, t: Int, w: Int, h: Int, color: Int, stroke: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = stroke
    }
    val off = stroke / 2f + max(1f, stroke)
    canvas.drawRect(l - off, t - off, l + w + off, t + h + off, paint)
}

private fun sprockets(canvas: Canvas, top: Float, bandH: Float, canvasW: Int, color: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val holeW = bandH * 0.4f
    val holeH = bandH * 0.26f
    val gap = holeW * 0.72f
    val y = top + bandH / 2f - holeH / 2f
    val radius = holeH * 0.3f
    var x = gap
    while (x + holeW < canvasW - gap) {
        canvas.drawRoundRect(x, y, x + holeW, y + holeH, radius, radius, paint)
        x += holeW + gap
    }
}

/** Warm mono film-stock + frame-number markings across a sprocket band. */
private fun filmEdge(context: Context, canvas: Canvas, exif: PhotoExif, canvasW: Int, bandTop: Float, bandH: Float, longEdge: Int, sizeMul: Float) {
    val warm = 0xFFE69646.toInt()
    val size = longEdge * 0.014f * sizeMul
    val y = bandTop + bandH * 0.62f
    val pad = longEdge * 0.03f
    val left = composeParams(exif).ifBlank { composeTitle(exif) }
    if (left.isNotBlank()) {
        canvas.drawText(left, pad, y, tp(Typeface.MONOSPACE, warm, size, 0.04f, Paint.Align.LEFT))
    }
    canvas.drawText(frameNumber(exif), canvasW - pad, y, tp(Typeface.MONOSPACE, warm, size, 0.06f, Paint.Align.RIGHT))
}

private fun frameNumber(exif: PhotoExif): String {
    // A stable pseudo frame number derived from the capture time, in the
    // "36A → 37" film-counter idiom.
    val secs = exif.dateTaken?.takeLast(2)?.toIntOrNull() ?: 12
    val n = secs % 36 + 1
    return "${n}A → ${n + 1}"
}

private fun accessionCode(exif: PhotoExif): String {
    val year = exif.dateTaken?.take(4)?.takeIf { it.toIntOrNull() != null } ?: "2026"
    val tail = ((exif.dateTaken?.filter { it.isDigit() }?.takeLast(4)?.toIntOrNull() ?: 142) % 9999)
    return "JF · $year · " + tail.toString().padStart(4, '0')
}

private fun drawWatermark(context: Context, canvas: Canvas, options: WatermarkSettings, outWidth: Int, outHeight: Int, longEdge: Int, color: Int) {
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
        WatermarkPosition.TopLeft -> { paint.textAlign = Paint.Align.LEFT; canvas.drawText(options.text, pad, pad + textHeight, paint) }
        WatermarkPosition.TopRight -> { paint.textAlign = Paint.Align.RIGHT; canvas.drawText(options.text, outWidth - pad, pad + textHeight, paint) }
        WatermarkPosition.BottomLeft -> { paint.textAlign = Paint.Align.LEFT; canvas.drawText(options.text, pad, outHeight - pad, paint) }
        WatermarkPosition.BottomRight -> { paint.textAlign = Paint.Align.RIGHT; canvas.drawText(options.text, outWidth - pad, outHeight - pad, paint) }
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

private fun composeParams(exif: PhotoExif): String =
    listOfNotNull(
        exif.focalLength,
        exif.aperture,
        exif.shutterSpeed,
        exif.iso?.takeIf { it.isNotBlank() }?.let { "ISO $it" },
    ).filter { it.isNotBlank() }.joinToString("  ·  ")

/** A named family of templates; the home screen picks one of these. */
data class FrameGroup(
    val id: String,
    val zh: String,
    val en: String,
    val tagline: String,
    val swatch: Long,
    val templates: List<FrameTemplate>,
)

object FrameRenderer {
    fun deframe(source: Bitmap, insets: FrameInsets): Bitmap {
        val left = insets.left.coerceAtLeast(0)
        val top = insets.top.coerceAtLeast(0)
        val width = (source.width - left - insets.right).coerceAtLeast(1)
        val height = (source.height - top - insets.bottom).coerceAtLeast(1)
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    val groups: List<FrameGroup> = listOf(
        FrameGroup(
            "editorial", "杂志留白", "Editorial Margin", "暖白满幅 · 窄底注脚", 0xFFF7F4EE,
            listOf(EditorialFoot(), EditorialAlbum(), EditorialHeader()),
        ),
        FrameGroup(
            "passepartout", "美术馆装裱", "Passepartout", "冷白裱框 · 底边加宽留白", 0xFFFAFAF8,
            listOf(PasseSingle(), PassePlate(), PasseAccession()),
        ),
        FrameGroup(
            "rebate", "片边底纹", "Rebate", "35mm 片孔黑边 · 暖橙片基号", 0xFF101010,
            listOf(RebateFull(), RebateStrip(), RebateDateback()),
        ),
    )

    val all: List<FrameTemplate> = groups.flatMap { it.templates }

    fun byId(id: String): FrameTemplate = all.firstOrNull { it.id == id } ?: all.first()

    fun groupById(id: String): FrameGroup = groups.firstOrNull { it.id == id } ?: groups.first()

    fun groupOf(templateId: String): FrameGroup =
        groups.firstOrNull { g -> g.templates.any { it.id == templateId } } ?: groups.first()
}
