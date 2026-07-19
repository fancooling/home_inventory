package com.homeinventory.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.homeinventory.app.MainActivity
import com.homeinventory.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Posts the "scan finished" notification (see CLAUDE.md → Background Scanning, step 6).
 *
 * In Phase 2 there is no Gemini step yet, so the notification reports how many photos passed the
 * on-device pre-filter and are ready for identification. Phase 3 changes this to "N new items
 * found" and deep-links to the Review New Items screen.
 */
class ScanNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.scan_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.scan_channel_description)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun notifyScanComplete(photosReady: Int, scannedCount: Int) {
        if (!hasPostPermission()) return
        ensureChannel()

        val contentText = if (photosReady > 0) {
            context.getString(R.string.scan_complete_ready, photosReady)
        } else {
            context.getString(R.string.scan_complete_none, scannedCount)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.scan_complete_title))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun hasPostPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "scan_results"
        private const val NOTIFICATION_ID = 1001
    }
}
