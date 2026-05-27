package com.seanyuan.filmframe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

/**
 * Wraps a loaded bitmap with provenance info — original pixel dimensions and
 * whether we had to downsample to fit. Callers that promise the user "no loss"
 * can read this back to warn when downsampled.
 */
data class LoadedBitmap(
    val bitmap: Bitmap,
    val originalWidth: Int,
    val originalHeight: Int,
    val downsampled: Boolean,
)

object BitmapLoader {

    fun loadForAnalysis(context: Context, uri: Uri, targetMaxDim: Int = 1200): Bitmap? =
        loadSampled(context, uri, targetMaxDim)?.bitmap

    /**
     * For export: try the requested cap first, fall back to ever-smaller caps
     * on OOM. Default behaves like "full resolution requested" path.
     */
    fun loadForExport(context: Context, uri: Uri, maxLongEdge: Int = Int.MAX_VALUE): LoadedBitmap? {
        val targets = if (maxLongEdge == Int.MAX_VALUE) {
            intArrayOf(Int.MAX_VALUE, 8192, 6144, 4096)
        } else {
            intArrayOf(
                maxLongEdge,
                (maxLongEdge * 0.75).toInt().coerceAtLeast(1024),
                (maxLongEdge * 0.5).toInt().coerceAtLeast(1024),
            )
        }
        for (cap in targets) {
            try {
                val loaded = loadSampled(context, uri, cap)
                if (loaded != null) return loaded
            } catch (_: OutOfMemoryError) {
                // try a smaller cap
            }
        }
        return null
    }

    private fun loadSampled(context: Context, uri: Uri, targetMaxDim: Int): LoadedBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        val originalW = bounds.outWidth
        val originalH = bounds.outHeight
        val maxDim = max(originalW, originalH)
        if (maxDim <= 0) return null

        var sample = 1
        while (maxDim / sample > targetMaxDim) sample *= 2
        val downsampled = sample > 1

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull() ?: return null

        val rotated = applyExifRotation(context, uri, raw)
        return LoadedBitmap(
            bitmap = rotated,
            originalWidth = originalW,
            originalHeight = originalH,
            downsampled = downsampled,
        )
    }

    /**
     * Many phones store the sensor pixels in landscape and use the EXIF
     * Orientation tag to indicate display rotation. If we ignore the tag, a
     * portrait photo decodes as a landscape bitmap and the frame ends up
     * wrapping the wrong axis.
     */
    private fun applyExifRotation(context: Context, uri: Uri, source: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: return source

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return source
        }
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                .also { if (it !== source) source.recycle() }
        } catch (_: OutOfMemoryError) {
            source
        }
    }
}
