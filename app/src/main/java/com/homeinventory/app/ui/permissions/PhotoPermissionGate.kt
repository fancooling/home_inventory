package com.homeinventory.app.ui.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.homeinventory.app.R

/**
 * Gates [content] behind photo access (see CLAUDE.md → Permissions & Play Store Compliance).
 *
 * - [PhotoAccess.NONE] → shows an in-app rationale *before* the OS dialog, then requests.
 * - [PhotoAccess.PARTIAL] (Android 14+ selected-photos) → shows an explainer plus a shortcut to
 *   the system "Manage access" screen, rather than silently under-scanning. The user may still
 *   continue with limited access.
 * - [PhotoAccess.FULL] → renders [content].
 *
 * Access is re-checked whenever the app resumes, so returning from system settings updates the UI.
 */
@Composable
fun PhotoPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(PhotoPermissions.currentAccess(context)) }

    // Re-check access on resume (e.g. after returning from the system settings screen).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = PhotoPermissions.currentAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        access = PhotoPermissions.currentAccess(context)
    }

    when (access) {
        PhotoAccess.FULL -> content()

        PhotoAccess.NONE -> RationaleContent(
            title = stringResource(R.string.perm_title),
            body = stringResource(R.string.perm_rationale),
            primaryLabel = stringResource(R.string.perm_grant),
            onPrimary = { launcher.launch(PhotoPermissions.requestPermissions()) },
        )

        PhotoAccess.PARTIAL -> RationaleContent(
            title = stringResource(R.string.perm_partial_title),
            body = stringResource(R.string.perm_partial_rationale),
            primaryLabel = stringResource(R.string.perm_manage),
            onPrimary = { context.startActivity(appSettingsIntent(context.packageName)) },
            secondaryLabel = stringResource(R.string.perm_continue),
            onSecondary = { access = PhotoAccess.FULL }, // proceed with limited access this session
        )
    }
}

@Composable
private fun RationaleContent(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onPrimary) { Text(primaryLabel) }
        if (secondaryLabel != null && onSecondary != null) {
            OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
