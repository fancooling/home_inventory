package com.homeinventory.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's sole persistent store (see CLAUDE.md → Local storage).
 *
 * Phase 2 registers only [ScanCheckpoint]; Phase 4 adds Photo, InventoryItem, Room, and
 * UserProfile (bumping the version with a migration at that point).
 */
@Database(
    entities = [ScanCheckpoint::class],
    version = 1,
    // Schema export (with a configured directory + migrations) is enabled in Phase 4 when the
    // full schema lands; kept off here to avoid an unconfigured-directory build warning.
    exportSchema = false,
)
abstract class HomeInventoryDatabase : RoomDatabase() {
    abstract fun scanCheckpointDao(): ScanCheckpointDao

    companion object {
        const val NAME = "home_inventory.db"
    }
}
