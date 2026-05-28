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
        loadSampled(context, uri, targetMaxDim, exact = true)?.bitmap

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
                // exact = true so High/Medium/Low respect their maxLongEdge cap
                // precisely. Original passes Int.MAX_VALUE; the rescale branch is a
                // no-op there because no real bitmap exceeds MAX_VALUE.
                val loaded = loadSampled(context, uri, cap, exact = true)
                if (loaded != null) return loaded
            } catch (_: OutOfMemoryError) {
                // try a smaller cap
            }
        }
        return null
    }

    private fun loadSampled(
        context: Context,
        uri: Uri,
        targetMaxDim: Int,
        exact: Boolean = false,
    ): LoadedBitmap? {
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

        // Pick the smallest power-of-two sample that keeps decoded dim >= target.
        // We deliberately decode slightly larger than target so the subsequent
        // exact rescale can downsample with a real filter — a power-of-two
        // inSampleSize that lands BELOW target leaves the preview blurry on
        // hi-density screens (the original v1 bug).
        var sample = 1
        while (maxDim / (sample * 2) >= targetMaxDim) sample *= 2
        val downsampled = maxDim > targetMaxDim

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

        // Exact rescale for preview path: turns the always-slightly-too-large
        // decoded bitmap into exactly targetMaxDim on its long edge using a
        // bilinear filter. Skipped for export (exact=false) — there we want
        // the largest possible decode, not a scaled-down preview.
        val finalBmp = if (exact && max(rotated.width, rotated.height) > targetMaxDim) {
            val srcMax = max(rotated.width, rotated.height)
            val scale = targetMaxDim.toFloat() / srcMax
            val nw = max(1, (rotated.width * scale).toInt())
            val nh = max(1, (rotated.height * scale).toInt())
            try {
                Bitmap.createScaledBitmap(rotated, nw, nh, true).also {
                    if (it !== rotated) rotated.recycle()
                }
            } catch (_: OutOfMemoryError) {
                rotated
            }
        } else {
            rotated
        }

        return LoadedBitmap(
            bitmap = finalBmp,
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
