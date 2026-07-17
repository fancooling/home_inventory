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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Background scan pass (see CLAUDE.md → Background Scanning).
 *
 * 1. Read the checkpoint watermark.
 * 2. Query MediaStore for new `DCIM/Camera` photos since then (oldest first).
 * 3. Run each through the on-device [PhotoPreFilter] (person exclusion + object cost filter).
 * 4. Advance the checkpoint to the newest photo seen.
 * 5. Notify with how many photos are ready for identification.
 *
 * Phase 2 stops at "ready for Gemini": photos with [PreFilterDecision.SEND] are counted but the
 * actual Gemini call, item creation, and private-copy step arrive in Phase 3. The checkpoint is
 * still advanced past every photo examined, so a photo is pre-filtered exactly once.
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
            val since = checkpointDao.getLastScanTimestamp() ?: 0L
            val photos = scanner.queryNewPhotos(since)
            Log.i(TAG, "Scan starting: ${photos.size} new photo(s) since $since")

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
                // Advance incrementally so a mid-pass failure doesn't re-scan everything.
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
            // let it propagate so WorkManager reschedules cleanly. The checkpoint is advanced
            // per-photo, so the next pass resumes where this one left off.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed; will retry next pass", e)
            Result.retry()
        } finally {
            coordinator.end()
        }
    }

    companion object {
        const val TAG = "PhotoScanWorker"
    }
}
