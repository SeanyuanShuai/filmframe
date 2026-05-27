package com.seanyuan.filmframe.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class GalleryEntry(
    val id: Long,
    val uri: Uri,
    val dateAddedSec: Long,
)

object MediaGallery {

    /**
     * Lists images known to MediaStore, newest first. Requires
     * READ_MEDIA_IMAGES (API 33+) or READ_EXTERNAL_STORAGE (older).
     */
    fun listImages(context: Context, limit: Int = 500): List<GalleryEntry> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $limit"

        val results = mutableListOf<GalleryEntry>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val date = cursor.getLong(dateCol)
                    results += GalleryEntry(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        dateAddedSec = date,
                    )
                }
            }
        }
        return results
    }
}
