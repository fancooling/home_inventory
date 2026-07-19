package com.homeinventory.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row watermark table. Holds the MediaStore `DATE_ADDED` of the newest photo processed by
 * the last successful scan pass; only photos newer than this are considered on the next scan.
 *
 * There is always exactly one row ([SINGLETON_ID]). See CLAUDE.md → Background Scanning and the
 * `ScanCheckpoint` entry in the Data Model. The fuller schema (Photo, InventoryItem, Room,
 * UserProfile) arrives in Phase 4; Phase 2 only needs this checkpoint.
 */
@Entity(tableName = "scan_checkpoint")
data class ScanCheckpoint(
    @PrimaryKey val id: Int = SINGLETON_ID,
    /** MediaStore `DATE_ADDED` (seconds) of the newest photo seen. 0 means "never scanned". */
    val lastScanTimestamp: Long = 0L,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
