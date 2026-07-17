package com.homeinventory.app.work

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guards against concurrent scan passes. The periodic worker and a manual "Scan now" are
 * separate WorkManager requests that can otherwise run at the same time and double-process
 * photos (a real cost problem once Gemini calls are wired in Phase 3).
 *
 * Application-scoped: WorkManager runs all workers in the same process, so a single in-memory
 * flag is sufficient. A scan that can't acquire the flag simply no-ops.
 */
@Singleton
class ScanCoordinator @Inject constructor() {
    private val running = AtomicBoolean(false)

    /** Returns true if the caller acquired the scan slot; false if a scan is already running. */
    fun tryBegin(): Boolean = running.compareAndSet(false, true)

    fun end() {
        running.set(false)
    }
}
