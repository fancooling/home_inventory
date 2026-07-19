package com.homeinventory.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Whether the app can currently read the camera roll, and how completely. */
enum class PhotoAccess {
    /** Full library access — background scanning works as intended. */
    FULL,

    /** Android 14+ "selected photos" only — new camera photos won't be seen automatically. */
    PARTIAL,

    /** No access. */
    NONE,
}

/**
 * Photo-permission logic across API levels (see CLAUDE.md → Permissions & Play Store Compliance).
 *
 * - API 33+: `READ_MEDIA_IMAGES`
 * - API 34+: also `READ_MEDIA_VISUAL_USER_SELECTED` for partial ("selected photos") access
 * - API < 33: `READ_EXTERNAL_STORAGE`
 */
object PhotoPermissions {

    /** The permission(s) to request for the current device, in the order to request them. */
    fun requestPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun currentAccess(context: Context): PhotoAccess {
        val granted = { perm: String ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> when {
                granted(Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccess.FULL
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.PARTIAL
                else -> PhotoAccess.NONE
            }
            else -> if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                PhotoAccess.FULL
            } else {
                PhotoAccess.NONE
            }
        }
    }
}
