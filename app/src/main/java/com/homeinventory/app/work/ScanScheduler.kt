package com.homeinventory.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the [PhotoScanWorker] (see CLAUDE.md → Background Scanning).
 *
 * - [ensurePeriodicScan] registers the recurring pass (default every 6h; WorkManager's practical
 *   minimum is 15 min). It's KEEP-based so re-registering on each launch doesn't reset the timer.
 * - [scanNow] triggers an immediate one-time pass for the "Scan now" action.
 */
@Singleton
class ScanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager get() = WorkManager.getInstance(context)

    fun ensurePeriodicScan(intervalHours: Long = DEFAULT_INTERVAL_HOURS) {
        val request = PeriodicWorkRequestBuilder<PhotoScanWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(scanConstraints())
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Re-registers the periodic work with a new interval (used when the user changes Settings). */
    fun reschedulePeriodicScan(intervalHours: Long) {
        val request = PeriodicWorkRequestBuilder<PhotoScanWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(scanConstraints())
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun scanNow() {
        val request = OneTimeWorkRequestBuilder<PhotoScanWorker>()
            .setConstraints(scanConstraints())
            .build()
        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    // The pre-filter is on-device; Gemini (Phase 3) will add a network constraint at that step.
    private fun scanConstraints() = Constraints.Builder().build()

    companion object {
        const val PERIODIC_WORK_NAME = "periodic_photo_scan"
        const val ONE_TIME_WORK_NAME = "one_time_photo_scan"
        const val DEFAULT_INTERVAL_HOURS = 6L
    }
}
