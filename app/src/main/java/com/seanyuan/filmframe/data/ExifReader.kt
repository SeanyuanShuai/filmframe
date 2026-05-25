package com.seanyuan.filmframe.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

data class PhotoExif(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lensModel: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val dateTaken: String? = null,
)

object ExifReader {
    fun read(context: Context, uri: Uri): PhotoExif {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                PhotoExif(
                    cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim(),
                    cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim(),
                    lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim(),
                    focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let(::formatFocal),
                    aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let(::formatAperture),
                    shutterSpeed = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let(::formatShutter),
                    iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
                    dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                )
            } ?: PhotoExif()
        }.getOrDefault(PhotoExif())
    }

    private fun formatFocal(raw: String): String {
        val mm = raw.toFloatOrNull() ?: parseRational(raw) ?: return raw
        return "${mm.toInt()}mm"
    }

    private fun formatAperture(raw: String): String {
        val f = raw.toFloatOrNull() ?: parseRational(raw) ?: return "f/$raw"
        return "f/${"%.1f".format(f).trimEnd('0').trimEnd('.')}"
    }

    private fun formatShutter(raw: String): String {
        val sec = raw.toFloatOrNull() ?: parseRational(raw) ?: return raw
        return when {
            sec >= 1f -> "${sec.toInt()}s"
            sec > 0f -> "1/${(1f / sec).toInt()}s"
            else -> raw
        }
    }

    private fun parseRational(raw: String): Float? {
        val parts = raw.split("/")
        if (parts.size != 2) return null
        val num = parts[0].toFloatOrNull() ?: return null
        val den = parts[1].toFloatOrNull() ?: return null
        return if (den == 0f) null else num / den
    }
}
