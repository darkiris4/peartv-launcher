package com.peartv.launcher.ui.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private const val ReadTvListingsPermission = "android.permission.READ_TV_LISTINGS"

/**
 * Live [ReadTvListingsPermission] check, re-run on every `ON_RESUME` — not
 * just once at cold launch — so coming back from actually granting it in
 * Android's own Settings (this prompt's own "Open Settings" button, or
 * `SettingsScreen`'s identical CTA) is reflected immediately, no app
 * restart needed.
 */
@Composable
fun rememberIsChannelsPermissionGranted(): Boolean {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(ReadTvListingsPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = context.checkSelfPermission(ReadTvListingsPermission) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/** This app's own App Info page — the same deep link `SettingsScreen`'s "Open app settings" CTA uses (§4); no confirmed direct intent action exists for the TV-listings special permission specifically. */
fun openAppInfoSettings(context: Context) {
    val intent = Intent(
        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    context.startActivity(intent)
}

/**
 * First-launch discoverability nudge (Decisions Log: "First-launch Channels
 * permission prompt") — user-directed, after confirming the pre-existing
 * `SettingsScreen` CTA alone wasn't discoverable: nothing in the app ever
 * indicated this capability existed, since a missing [ReadTvListingsPermission]
 * degrades Tier 3 silently, by design (§2.4), not as an error a user would
 * ever see. `LauncherScreen` shows this once, centered, no scrim — the same
 * zero-heavy-chrome treatment as `EditModeHint`, deliberately lighter than a
 * blocking modal.
 *
 * [onDismiss] fires for *both* "Not Now" and the Back key (`LauncherScreen`
 * wires this into its existing root `BackHandler`) — persisted permanently
 * via `SettingsRepository.setChannelsPromptDismissed()` either way, since
 * this is meant as a one-time nudge, not a recurring nag (a deliberate,
 * narrow exception to §3.1's "no persistent chrome" principle — it only
 * ever shows once, then never again, regardless of whether the permission
 * ends up granted). Tapping "Open Settings" does *not* dismiss on its own —
 * if the user backs out of Android's Settings without actually granting it,
 * the prompt should still be here when they return, not silently gone.
 */
@Composable
fun ChannelsPermissionPrompt(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val openSettingsFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .widthIn(max = 480.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
    ) {
        Column {
            Text(
                text = "Get more from your apps",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Some apps can show full-screen previews here. Enable TV listings access in Settings to turn this on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { openAppInfoSettings(context) },
                    modifier = Modifier.focusRequester(openSettingsFocusRequester),
                ) {
                    Text("Open Settings")
                }
                Button(onClick = onDismiss) {
                    Text("Not Now")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        openSettingsFocusRequester.requestFocus()
    }
}
