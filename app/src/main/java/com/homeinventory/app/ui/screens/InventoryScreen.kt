package com.homeinventory.app.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homeinventory.app.ui.permissions.PhotoPermissionGate

/**
 * Inventory — the app's home screen (start destination, no sign-in).
 *
 * Wrapped in [PhotoPermissionGate]: until the user grants photo access, the rationale/partial
 * explainer is shown instead of the inventory. The photo grid and room/category filters arrive
 * in Phase 5; Phase 2 wires the working "Scan now" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRooms: () -> Unit,
    onOpenItem: (photoId: String) -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    // Ask for notification permission (API 33+) so the scan-complete notification can show.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — scanning still works without notifications */ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home Inventory") },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        PhotoPermissionGate {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = "No items yet",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Your inventory will fill in automatically as background scans find " +
                        "belongings in your camera photos.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.scanNow()
                    Toast.makeText(context, "Scanning your photos…", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Scan now")
                }
                OutlinedButton(onClick = onOpenRooms) {
                    Text("Manage rooms")
                }
            }
        }
    }
}
