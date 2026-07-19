package com.homeinventory.app.data.scan

import android.net.Uri

/**
 * A single photo discovered in the camera folder by [MediaStoreScanner].
 *
 * @param uri content:// URI usable to open the image bytes
 * @param dateAddedSeconds MediaStore `DATE_ADDED` (seconds since epoch) — the scan watermark
 * @param displayName file name, for logging/debugging only
 */
data class CameraPhoto(
    val uri: Uri,
    val dateAddedSeconds: Long,
    val displayName: String,
)
