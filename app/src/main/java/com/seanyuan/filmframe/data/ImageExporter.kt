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
     * Output format matches the source format whenever possible — keeps the
     * generation count at 1 (decode → draw frame → encode once in same codec).
     *
     * Format-to-codec mapping:
     *   JPEG  → JPEG quality 100  (still a re-encode, but same family; one DCT round)
     *   PNG   → PNG               (truly lossless)
     *   WEBP  → WEBP_LOSSLESS on API 30+, falls back to PNG below
     *   HEIC  → JPEG quality 100  (Android Bitmap.compress can't write HEIC at all)
     *   ??    → JPEG quality 100  (safest default for an unknown raster source)
     *
     * Note: JPEG fundamentally has DCT block-transform loss even at quality 100.
     * It's "no perceivable loss" — not "zero loss". To get true zero loss for a
     * JPEG source you'd need lossless transforms via libjpeg-turbo (out of scope
     * for v0.1).
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, sourceUri: Uri): Uri? {
        val format = decideFormat(context, sourceUri)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val filename = "FilmFrame_$timestamp.${format.ext}"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
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
                val compressFormat = when (format) {
                    ExportFormat.Jpeg -> Bitmap.CompressFormat.JPEG
                    ExportFormat.Png -> Bitmap.CompressFormat.PNG
                    ExportFormat.WebpLossless -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSLESS
                        } else {
                            // pre-API 30 has no lossless WEBP — keep it lossless via PNG
                            Bitmap.CompressFormat.PNG
                        }
                    }
                }
                if (!bitmap.compress(compressFormat, 100, out)) {
                    throw IllegalStateException("bitmap compress ($compressFormat) failed")
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

    private fun decideFormat(context: Context, sourceUri: Uri): ExportFormat {
        val mime = context.contentResolver.getType(sourceUri)?.lowercase().orEmpty()
        return when {
            "png" in mime -> ExportFormat.Png
            "webp" in mime -> ExportFormat.WebpLossless
            // HEIC/HEIF: Android Bitmap.compress can't write back into the HEIF
            // container, so JPEG 100 is the closest non-WebP option.
            "heic" in mime || "heif" in mime -> ExportFormat.Jpeg
            // jpeg, jpg, or unknown raster — JPEG 100
            else -> ExportFormat.Jpeg
        }
    }

    private enum class ExportFormat(val mime: String, val ext: String) {
        Jpeg("image/jpeg", "jpg"),
        Png("image/png", "png"),
        WebpLossless("image/webp", "webp"),
    }
}
