package com.homeinventory.app

import com.homeinventory.app.ui.navigation.Destinations
import com.homeinventory.app.ui.navigation.itemDetailRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationsTest {

    @Test
    fun inventoryIsStartDestination() {
        assertEquals(Destinations.Inventory, Destinations.Start)
    }

    @Test
    fun itemDetailRouteFillsPhotoIdArgument() {
        assertEquals("item_detail/abc-123", itemDetailRoute("abc-123"))
    }
}
