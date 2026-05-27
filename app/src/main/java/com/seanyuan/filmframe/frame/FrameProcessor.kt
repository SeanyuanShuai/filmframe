package com.seanyuan.filmframe.frame

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.seanyuan.filmframe.data.BitmapLoader
import com.seanyuan.filmframe.data.ExifReader
import com.seanyuan.filmframe.data.PhotoExif
import com.seanyuan.filmframe.data.WatermarkSettings

/**
 * Bundle of analysis outputs for one source image.
 */
data class ProcessedSource(
    val source: Bitmap,
    val exif: PhotoExif,
    val detection: FrameDetectionResult,
)

/**
 * Shared pipeline used by both the single-photo editor and the batch screen.
 * Keeps render logic identical across flows; future improvements (e.g. tiled
 * full-res render) only need to land here.
 */
object FrameProcessor {

    fun loadAndAnalyze(context: Context, uri: Uri, targetMaxDim: Int = 1600): ProcessedSource? {
        val source = BitmapLoader.loadForAnalysis(context, uri, targetMaxDim) ?: return null
        val exif = ExifReader.read(context, uri)
        val detection = FrameDetector.detect(source)
        return ProcessedSource(source, exif, detection)
    }

    fun loadFullForExport(context: Context, uri: Uri): ProcessedSource? {
        val source = BitmapLoader.loadForExport(context, uri) ?: return null
        val exif = ExifReader.read(context, uri)
        val detection = FrameDetector.detect(source)
        return ProcessedSource(source, exif, detection)
    }

    fun render(
        context: Context,
        processed: ProcessedSource,
        template: FrameTemplate,
        stripExistingFrame: Boolean,
        watermark: WatermarkSettings = WatermarkSettings.Default,
    ): Bitmap {
        val base = if (stripExistingFrame && processed.detection.hasFrame) {
            FrameRenderer.deframe(processed.source, processed.detection.insets)
        } else {
            processed.source
        }
        return template.render(context, base, processed.exif, watermark)
    }
}
