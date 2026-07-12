package com.homeinventory.app.ui.navigation

/**
 * All navigation destinations in the app.
 *
 * [Inventory] is the start/home destination — there is deliberately no sign-in or onboarding
 * gate before it (the app has no accounts; see CLAUDE.md → Core Principles). The remaining
 * screens mirror CLAUDE.md → App Screens and are scaffolded as placeholders in Phase 1.
 */
enum class Destinations(val route: String) {
    Inventory("inventory"),
    ReviewNewItems("review_new_items"),
    SearchResults("search_results"),
    ItemDetail("item_detail/{photoId}"),
    Rooms("rooms"),
    BuyCredits("buy_credits"),
    Settings("settings"),
    ;

    companion object {
        val Start: Destinations = Inventory
    }
}

/** Builds the concrete route for [Destinations.ItemDetail] for a given photo. */
fun itemDetailRoute(photoId: String): String = "item_detail/$photoId"
