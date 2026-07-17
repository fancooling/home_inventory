package com.homeinventory.app.ui.screens

import androidx.lifecycle.ViewModel
import com.homeinventory.app.work.ScanScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val scanScheduler: ScanScheduler,
) : ViewModel() {

    /** Triggers an immediate one-time scan pass (the "Scan now" action). */
    fun scanNow() {
        scanScheduler.scanNow()
    }
}
