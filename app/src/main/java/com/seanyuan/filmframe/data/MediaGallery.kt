package com.seanyuan.filmframe.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

data class GalleryEntry(
    val id: Long,
    val uri: Uri,
    val dateAddedSec: Long,
)

object MediaGallery {

    /**
     * Lists images known to MediaStore, newest first.
     *
     * Uses Bundle query args with QUERY_ARG_LIMIT + QUERY_ARG_SQL_SORT_ORDER —
     * available since API 26. The old "ORDER BY ... LIMIT N" string trick in
     * sortOrder is silently ignored on API 30+, so on a phone with 50k photos
     * we previously loaded the entire library into a List<GalleryEntry>.
     *
     * Requires READ_MEDIA_IMAGES (API 33+) or READ_EXTERNAL_STORAGE (older).
     */
    fun listImages(context: Context, limit: Int = 500): List<GalleryEntry> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val queryArgs = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )
        }

        val results = ArrayList<GalleryEntry>(limit)
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                queryArgs,
                null,
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
