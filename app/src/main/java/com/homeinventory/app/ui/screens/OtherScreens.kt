package com.homeinventory.app.ui.screens

import androidx.compose.runtime.Composable

/**
 * Phase 1 placeholders for the remaining screens in CLAUDE.md → App Screens. Each is a distinct
 * composable so later phases can flesh them out independently without touching the nav graph.
 */

@Composable
fun ReviewNewItemsScreen(onDone: () -> Unit) {
    PlaceholderScreen(
        title = "Review New Items",
        onBack = onDone,
        body = "After a scan finds items, you'll optionally assign a room here.",
    )
}

@Composable
fun SearchResultsScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenItem: (photoId: String) -> Unit,
) {
    PlaceholderScreen(
        title = "Search",
        onBack = onBack,
        body = "Local-first FTS5 search with a Gemini fallback lands in Phase 5.",
    )
}

@Composable
fun ItemDetailScreen(photoId: String, onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Item Detail",
        onBack = onBack,
        body = "Photo and linked items for photoId=$photoId.",
    )
}

@Composable
fun RoomsScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Rooms",
        onBack = onBack,
        body = "Add, rename, and delete rooms here.",
    )
}

@Composable
fun BuyCreditsScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Buy Credits",
        onBack = onBack,
        body = "Scan credit packs via Google Play Billing land in Phase 6.",
    )
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onBuyCredits: () -> Unit,
) {
    PlaceholderScreen(
        title = "Settings",
        onBack = onBack,
        body = "Scan interval, credit balance, and clear local data land in later phases.",
    )
}
