package com.homeinventory.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homeinventory.app.ui.screens.BuyCreditsScreen
import com.homeinventory.app.ui.screens.InventoryScreen
import com.homeinventory.app.ui.screens.ItemDetailScreen
import com.homeinventory.app.ui.screens.ReviewNewItemsScreen
import com.homeinventory.app.ui.screens.RoomsScreen
import com.homeinventory.app.ui.screens.SearchResultsScreen
import com.homeinventory.app.ui.screens.SettingsScreen

/**
 * Root navigation graph. Inventory is the start destination — no sign-in step.
 */
@Composable
fun HomeInventoryNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.Start.route,
    ) {
        composable(Destinations.Inventory.route) {
            InventoryScreen(
                onOpenSearch = { navController.navigate(Destinations.SearchResults.route) },
                onOpenSettings = { navController.navigate(Destinations.Settings.route) },
                onOpenRooms = { navController.navigate(Destinations.Rooms.route) },
                onOpenItem = { photoId -> navController.navigate(itemDetailRoute(photoId)) },
            )
        }
        composable(Destinations.ReviewNewItems.route) {
            ReviewNewItemsScreen(onDone = { navController.popBackStack() })
        }
        composable(Destinations.SearchResults.route) {
            SearchResultsScreen(
                onBack = { navController.popBackStack() },
                onOpenItem = { photoId -> navController.navigate(itemDetailRoute(photoId)) },
            )
        }
        composable(
            route = Destinations.ItemDetail.route,
            arguments = listOf(navArgument("photoId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getString("photoId").orEmpty()
            ItemDetailScreen(photoId = photoId, onBack = { navController.popBackStack() })
        }
        composable(Destinations.Rooms.route) {
            RoomsScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.BuyCredits.route) {
            BuyCreditsScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onBuyCredits = { navController.navigate(Destinations.BuyCredits.route) },
            )
        }
    }
}
