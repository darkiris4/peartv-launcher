package com.peartv.launcher.ui.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
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
 * Back cancels via [Popup]'s own `dismissOnBackPress`/`onDismissRequest`
 * (same mechanism `OptionsMenu`/`MergeConfirmPrompt` already rely on to
 * swallow Back before `LauncherScreen`'s outer `BackHandler` ever sees it) —
 * persisted permanently via `SettingsRepository.setChannelsPromptDismissed()`
 * either way (same callback as "Not Now"), since this is meant as a one-time
 * nudge, not a recurring nag (a deliberate, narrow exception to §3.1's
 * "no persistent chrome" principle — it only ever shows once, then never
 * again, regardless of whether the permission ends up granted). Tapping
 * "Open Settings" does *not* dismiss on its own — if the user backs out of
 * Android's Settings without actually granting it, the prompt should still
 * be here when they return, not silently gone.
 *
 * Wrapped in a focusable [Popup] (not composed inline into `LauncherScreen`'s
 * shared focus tree, as this originally was) so the initial `requestFocus()`
 * below reliably lands on "Open Settings" instead of racing the grid/tray's
 * own cold-launch initial-focus grab — confirmed on-device the inline
 * version left focus nowhere obvious, forcing a blind D-pad hunt to find the
 * buttons at all. Same fix `MergeConfirmPrompt` already uses.
 */
@Composable
fun ChannelsPermissionPrompt(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val openSettingsFocusRequester = remember { FocusRequester() }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
        ) {
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
                // Default tv-material3 Button focus state is a solid,
                // fully-opaque container fill — confirmed on-device this
                // reads as a hard block that swallows the label rather than
                // a focus highlight. Toned down to the same translucent-glass
                // fill used elsewhere in this app (`TranslucentPanelAlpha`)
                // instead, same fix `StatusBar`'s settings-gear button
                // already applies (`focusedContainerColor = Color.Transparent`
                // there, since that one relies on its outline ring instead).
                val focusColors = ButtonDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = TranslucentPanelAlpha),
                    focusedContentColor = MaterialTheme.colorScheme.onSurface,
                )
                Button(
                    onClick = { openAppInfoSettings(context) },
                    colors = focusColors,
                    modifier = Modifier.focusRequester(openSettingsFocusRequester),
                ) {
                    Text("Open Settings")
                }
                Button(onClick = onDismiss, colors = focusColors) {
                    Text("Not Now")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        openSettingsFocusRequester.requestFocus()
    }
}
