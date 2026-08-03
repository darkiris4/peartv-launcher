package com.peartv.launcher.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * `SettingsScreen`'s `SettingsRoute.Screensaver` content pane — §5 #11's
 * own resolution — deliberately not a custom idle-timeout/video-playback
 * mechanism this app owns (Decisions Log: "Idle-state screensaver —
 * deferred to Android's own Daydream system"). "Open System Screensaver
 * Settings" deep-links to Android TV's own screensaver settings, where a
 * Daydream service like "Aerial Views" gets selected — unchanged logic,
 * now a full-width `SettingsActionRow` instead of a small centered `Button`
 * to match this page's own row language. "Preview" is a placeholder — an
 * in-app live preview of whatever Daydream is active isn't something
 * Android exposes an API for.
 */
@Composable
fun ScreensaverSettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val firstRowFocusRequester = remember { FocusRequester() }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "For an aerial screensaver like tvOS, install the free \"Aerial Views\" app, then choose it here.",
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(SettingsRowSpacing)) {
            SettingsActionRow(
                text = "Open System Screensaver Settings",
                onClick = { openScreensaverSettings(context) },
                modifier = Modifier.settingsInitialFocus(firstRowFocusRequester),
                description = "Configure screensaver timing and style in Android's system settings",
            )
            SettingsActionRow(
                text = "Preview",
                onClick = {},
                description = "See how your screensaver will look",
            )
        }
    }
}

/**
 * Android TV's own Daydream/screensaver settings page — where a Daydream
 * service like Aerial Views gets selected and configured, not something
 * this app has any reason to duplicate. [AndroidSettings.ACTION_DREAM_SETTINGS]
 * isn't guaranteed to resolve on every OEM skin — confirmed crashing
 * outright on-device (`ActivityNotFoundException`) on the actual Shield TV
 * Pro reference hardware this app targets, despite being a documented
 * public API. Falls back to the general system Settings root so the CTA
 * still does *something* useful (the user can navigate from there) rather
 * than crash the app on a build where the specific deep link isn't wired up.
 */
private fun openScreensaverSettings(context: Context) {
    try {
        context.startActivity(Intent(AndroidSettings.ACTION_DREAM_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
    }
}
