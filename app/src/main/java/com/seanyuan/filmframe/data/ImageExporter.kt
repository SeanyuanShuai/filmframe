package com.seanyuan.filmframe.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageExporter {

    private const val SUBFOLDER = "FilmFrame"

    /**
     * Saves bitmap as PNG to Pictures/FilmFrame/. PNG is truly lossless (no
     * JPEG-style 8×8 block transform), at the cost of larger file size
     * (~5-10× JPEG quality 95 for typical photos).
     */
    fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val filename = "FilmFrame_$timestamp.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$SUBFOLDER"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                // PNG compress() ignores the quality int — always lossless.
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("bitmap compress (PNG) failed")
                }
            } ?: throw IllegalStateException("openOutputStream returned null")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalize = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, finalize, null, null)
            }
            uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
