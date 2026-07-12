package com.homeinventory.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * - [HiltAndroidApp] bootstraps Hilt's dependency graph for the whole app.
 * - Firebase App Check (Play Integrity) is installed here so that every Firebase AI Logic
 *   (Gemini relay) call is attested as coming from a genuine, unmodified install — no user
 *   account required. See CLAUDE.md → Gemini Integration / Anti-abuse.
 *
 * Firebase only initializes when a real `google-services.json` is present. On a fresh checkout
 * without it, [FirebaseApp.getInstance] throws, and we skip App Check rather than crash — this
 * keeps the Phase 1 scaffold runnable before Firebase credentials are wired up.
 */
@HiltAndroidApp
class HomeInventoryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initAppCheck()
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
