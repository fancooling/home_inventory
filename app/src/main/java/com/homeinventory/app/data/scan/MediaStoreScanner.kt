package com.homeinventory.app.data.scan

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads new photos from the device's native camera folder (`DCIM/Camera`) via [MediaStore].
 *
 * Scoping to `DCIM/Camera` deliberately excludes screenshots, downloads, and messaging-app
 * images (see CLAUDE.md → Background Scanning). Originals are never moved or modified — this is
 * a read-only query.
 */
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Returns camera photos with `DATE_ADDED` strictly greater than [sinceTimestampSeconds],
     * oldest first, so the caller can advance the checkpoint monotonically.
     */
    fun queryNewPhotos(sinceTimestampSeconds: Long): List<CameraPhoto> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
        )

        // RELATIVE_PATH exists on API 29+; below that fall back to the legacy DATA path column.
        val (selection, selectionArgs) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.DATE_ADDED} > ? AND " +
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to
                arrayOf(sinceTimestampSeconds.toString(), "%DCIM/Camera%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Images.Media.DATE_ADDED} > ? AND " +
                "${MediaStore.Images.Media.DATA} LIKE ?" to
                arrayOf(sinceTimestampSeconds.toString(), "%DCIM/Camera%")
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC"

        val results = mutableListOf<CameraPhoto>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                results += CameraPhoto(
                    uri = ContentUris.withAppendedId(collection, id),
                    dateAddedSeconds = cursor.getLong(dateCol),
                    displayName = cursor.getString(nameCol) ?: "",
                )
            }
        }
        return results
    }
}
