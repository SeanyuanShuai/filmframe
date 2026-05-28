package com.seanyuan.filmframe.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportResult(
    val uri: Uri,
    val outputFormat: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val downsampled: Boolean,
    val quality: ExportQuality,
)

object ImageExporter {

    private const val SUBFOLDER = "JustFrame"

    /**
     * Output format & quality follow user's ExportQuality choice:
     *
     *   Original / High → match source format (JPEG → JPEG, PNG → PNG,
     *     WebP → WEBP_LOSSLESS where supported). One generation, lossless
     *     where the source format allows it.
     *   Medium / Low    → force JPEG with specified quality (92 / 85). User
     *     trades source-format fidelity for predictable, smaller files.
     *
     * For JPEG outputs we copy the source EXIF (camera/lens/GPS/etc) into
     * the new file so Lightroom & similar pipelines see the same metadata.
     */
    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        sourceUri: Uri,
        loaded: LoadedBitmap? = null,
        quality: ExportQuality = ExportQuality.Original,
    ): ExportResult? {
        val format = if (quality.forceJpeg) {
            ExportFormat.Jpeg
        } else {
            decideFormat(context, sourceUri)
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val filename = "JustFrame_$timestamp.${format.ext}"

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
        val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            resolver.openOutputStream(outputUri)?.use { out ->
                val compressFormat = when (format) {
                    ExportFormat.Jpeg -> Bitmap.CompressFormat.JPEG
                    ExportFormat.Png -> Bitmap.CompressFormat.PNG
                    ExportFormat.WebpLossless -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSLESS
                        } else {
                            Bitmap.CompressFormat.PNG
                        }
                    }
                }
                if (!bitmap.compress(compressFormat, quality.jpegQuality, out)) {
                    throw IllegalStateException("bitmap compress ($compressFormat) failed")
                }
            } ?: throw IllegalStateException("openOutputStream returned null")

            if (format == ExportFormat.Jpeg) {
                runCatching { copyExif(context, sourceUri, outputUri) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalize = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(outputUri, finalize, null, null)
            }

            ExportResult(
                uri = outputUri,
                outputFormat = format.ext.uppercase(),
                outputWidth = bitmap.width,
                outputHeight = bitmap.height,
                originalWidth = loaded?.originalWidth ?: 0,
                originalHeight = loaded?.originalHeight ?: 0,
                downsampled = loaded?.downsampled ?: false,
                quality = quality,
            )
        } catch (t: Throwable) {
            resolver.delete(outputUri, null, null)
            null
        }
    }

    private fun copyExif(context: Context, sourceUri: Uri, outputUri: Uri) {
        val sourceExif = context.contentResolver.openInputStream(sourceUri)?.use {
            ExifInterface(it)
        } ?: return

        context.contentResolver.openFileDescriptor(outputUri, "rw")?.use { pfd ->
            val outputExif = ExifInterface(pfd.fileDescriptor)
            for (tag in EXIF_TAGS_TO_COPY) {
                sourceExif.getAttribute(tag)?.let { outputExif.setAttribute(tag, it) }
            }
            outputExif.saveAttributes()
        }
    }

    private fun decideFormat(context: Context, sourceUri: Uri): ExportFormat {
        val mime = context.contentResolver.getType(sourceUri)?.lowercase().orEmpty()
        return when {
            "png" in mime -> ExportFormat.Png
            "webp" in mime -> ExportFormat.WebpLossless
            "heic" in mime || "heif" in mime -> ExportFormat.Jpeg
            else -> ExportFormat.Jpeg
        }
    }

    private enum class ExportFormat(val mime: String, val ext: String) {
        Jpeg("image/jpeg", "jpg"),
        Png("image/png", "png"),
        WebpLossless("image/webp", "webp"),
    }

    private val EXIF_TAGS_TO_COPY = listOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_ARTIST,
    )
}
