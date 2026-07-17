package com.homeinventory.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScanCheckpointDao {

    @Query("SELECT lastScanTimestamp FROM scan_checkpoint WHERE id = :id")
    suspend fun getLastScanTimestamp(id: Int = ScanCheckpoint.SINGLETON_ID): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: ScanCheckpoint)

    /** Convenience: advance the watermark to [timestamp]. */
    suspend fun setLastScanTimestamp(timestamp: Long) {
        upsert(ScanCheckpoint(lastScanTimestamp = timestamp))
    }
}
