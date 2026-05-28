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
    val width: Int = 0,
    val height: Int = 0,
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
    fun listImages(context: Context, limit: Int = 500): List<GalleryEntry> =
        queryImages(context, limit, selection = null, selectionArgs = null)

    /**
     * Lists FilmFrame outputs only — photos under Pictures/FilmFrame, newest
     * first. Used by Landing's "recent works" exhibit row.
     *
     * On API 29+ uses MediaStore.Images.Media.RELATIVE_PATH; older API falls
     * back to DATA path matching (deprecated but functional).
     */
    fun listFilmFrameOutputs(context: Context, limit: Int = 12): List<GalleryEntry> {
        // Match both Pictures/JustFrame (current) and Pictures/FilmFrame (legacy
        // pre-rename) so dogfood users don't lose their archive at rename time.
        val (sel, args) = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to
                arrayOf("Pictures/JustFrame/%", "Pictures/FilmFrame/%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?" to
                arrayOf("%/Pictures/JustFrame/%", "%/Pictures/FilmFrame/%")
        }
        return queryImages(context, limit, sel, args)
    }

    private fun queryImages(
        context: Context,
        limit: Int,
        selection: String?,
        selectionArgs: Array<String>?,
    ): List<GalleryEntry> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val queryArgs = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )
            if (selection != null) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
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
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val date = cursor.getLong(dateCol)
                    val w = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val h = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    results += GalleryEntry(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        dateAddedSec = date,
                        width = w,
                        height = h,
                    )
                }
            }
        }
        return results
    }
}
