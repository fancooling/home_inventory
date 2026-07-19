package com.homeinventory.app.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.homeinventory.app.data.db.ScanCheckpointDao
import com.homeinventory.app.data.prefilter.PhotoPreFilter
import com.homeinventory.app.data.prefilter.PreFilterDecision
import com.homeinventory.app.data.scan.MediaStoreScanner
import com.homeinventory.app.notifications.ScanNotifier
import com.homeinventory.app.ui.permissions.PhotoAccess
import com.homeinventory.app.ui.permissions.PhotoPermissions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Background scan pass (see CLAUDE.md → Background Scanning).
 *
 * 1. Read the checkpoint watermark.
 * 2. Query MediaStore for new `DCIM/Camera` photos since then (oldest first).
 * 3. Process at most [MAX_PHOTOS_PER_PASS] per run through the on-device [PhotoPreFilter] (person
 *    exclusion + object cost filter) — a cap so a large backlog can't exceed WorkManager's ~10-min
 *    execution limit. Any remainder is picked up by the next pass.
 * 4. Advance the checkpoint once, to the newest photo processed.
 * 5. Notify with how many photos are ready for identification.
 *
 * Phase 2 stops at "ready for Gemini": photos with [PreFilterDecision.SEND] are counted but the
 * actual Gemini call, item creation, and private-copy step arrive in Phase 3.
 */
@HiltWorker
class PhotoScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scanner: MediaStoreScanner,
    private val preFilter: PhotoPreFilter,
    private val checkpointDao: ScanCheckpointDao,
    private val notifier: ScanNotifier,
    private val coordinator: ScanCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Only one scan pass at a time (periodic + "Scan now" can otherwise overlap).
        if (!coordinator.tryBegin()) {
            Log.i(TAG, "A scan is already running; skipping this pass")
            return Result.success()
        }
        return try {
            // If photo access was revoked (or never granted), don't churn: succeed quietly so
            // WorkManager doesn't retry-loop with backoff until the user grants it again.
            if (PhotoPermissions.currentAccess(applicationContext) == PhotoAccess.NONE) {
                Log.i(TAG, "No photo access; skipping scan")
                return Result.success()
            }

            val since = checkpointDao.getLastScanTimestamp() ?: 0L
            val allPhotos = scanner.queryNewPhotos(since)
            // Cap per pass so a large backlog can't blow WorkManager's ~10-min limit.
            val photos = allPhotos.take(MAX_PHOTOS_PER_PASS)
            Log.i(
                TAG,
                "Scan starting: processing ${photos.size} of ${allPhotos.size} new photo(s) since $since",
            )

            if (photos.isEmpty()) {
                return Result.success()
            }

            var readyForGemini = 0
            var blockedPerson = 0
            var skippedNoObject = 0
            var newestSeen = since

            for (photo in photos) {
                when (preFilter.classify(photo.uri)) {
                    PreFilterDecision.SEND -> readyForGemini++
                    PreFilterDecision.BLOCK_PERSON -> blockedPerson++
                    PreFilterDecision.SKIP_NO_OBJECT -> skippedNoObject++
                }
                newestSeen = maxOf(newestSeen, photo.dateAddedSeconds)
            }

            // One checkpoint write at the end (not per-photo). With the per-pass cap, at most
            // MAX_PHOTOS_PER_PASS are re-examined if a pass is interrupted before this point.
            if (newestSeen > since) {
                checkpointDao.setLastScanTimestamp(newestSeen)
            }

            Log.i(
                TAG,
                "Scan done: ready=$readyForGemini blockedPerson=$blockedPerson " +
                    "skippedNoObject=$skippedNoObject",
            )
            notifier.notifyScanComplete(photosReady = readyForGemini, scannedCount = photos.size)
            Result.success()
        } catch (e: CancellationException) {
            // The worker was stopped (e.g. app closed / system reclaimed it). Never swallow this —
            // let it propagate so WorkManager reschedules cleanly.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed; will retry next pass", e)
            Result.retry()
        } finally {
            // Release the scan slot and the native ML Kit models (only those actually initialized).
            coordinator.end()
            preFilter.close()
        }
    }

    companion object {
        const val TAG = "PhotoScanWorker"

        /** Max photos processed in a single pass; the rest are handled by the next scan. */
        private const val MAX_PHOTOS_PER_PASS = 100
    }
}
