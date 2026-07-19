package com.homeinventory.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.homeinventory.app.notifications.ScanNotifier
import com.homeinventory.app.work.ScanScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * - [HiltAndroidApp] bootstraps Hilt's dependency graph for the whole app.
 * - Implements [Configuration.Provider] so WorkManager uses Hilt's [HiltWorkerFactory], letting
 *   [com.homeinventory.app.work.PhotoScanWorker] receive its injected dependencies.
 * - Firebase App Check (Play Integrity) is installed here so every Firebase AI Logic (Gemini
 *   relay) call is attested as coming from a genuine install — see CLAUDE.md → Gemini Integration.
 *
 * Firebase only initializes when a real `google-services.json` is present; without it we skip App
 * Check rather than crash, keeping the app runnable before Firebase credentials are wired up.
 */
@HiltAndroidApp
class HomeInventoryApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scanScheduler: ScanScheduler
    @Inject lateinit var scanNotifier: ScanNotifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initAppCheck()
        scanNotifier.ensureChannel()
        // Register the recurring scan (KEEP — won't reset an already-scheduled timer).
        scanScheduler.ensurePeriodicScan()
    }

    private fun initAppCheck() {
        try {
            // Throws if google-services.json / FirebaseApp isn't configured yet.
            FirebaseApp.getInstance()
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Firebase not configured; skipping App Check init. Add google-services.json.", e)
        }
    }

    private companion object {
        const val TAG = "HomeInventoryApp"
    }
}
