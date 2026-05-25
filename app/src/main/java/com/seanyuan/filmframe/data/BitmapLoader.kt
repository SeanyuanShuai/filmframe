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
    fun loadForAnalysis(context: Context, uri: Uri, targetMaxDim: Int = 1200): Bitmap? {
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
