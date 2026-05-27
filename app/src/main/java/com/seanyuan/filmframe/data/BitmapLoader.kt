package com.seanyuan.filmframe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

object BitmapLoader {

    /**
     * Loads a downsampled bitmap suitable for analysis (frame detection, preview).
     * Uses inSampleSize so we never decode the full 50MP RAW into memory.
     */
    fun loadForAnalysis(context: Context, uri: Uri, targetMaxDim: Int = 1200): Bitmap? =
        loadSampled(context, uri, targetMaxDim)

    /**
     * For export: load at up to 4096px on long edge. Caps high enough for print
     * and 4K screens while bounding memory (50MP raw would blow up the bitmap).
     */
    /**
     * For export: try full resolution, fall back to ever-smaller caps if memory
     * doesn't permit. 8192 covers up to ~50 MP cleanly; 4096 is the safety net.
     */
    fun loadForExport(context: Context, uri: Uri): Bitmap? {
        for (cap in intArrayOf(Int.MAX_VALUE, 8192, 6144, 4096)) {
            try {
                val bmp = loadSampled(context, uri, cap)
                if (bmp != null) return bmp
            } catch (_: OutOfMemoryError) {
                // try a smaller cap
            }
        }
        return null
    }

    private fun loadSampled(context: Context, uri: Uri, targetMaxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val maxDim = max(bounds.outWidth, bounds.outHeight)
        if (maxDim <= 0) return null

        var sample = 1
        while (maxDim / sample > targetMaxDim) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }
}
