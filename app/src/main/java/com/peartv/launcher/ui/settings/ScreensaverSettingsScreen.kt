package com.peartv.launcher.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * §4 sub-page (`design/settings-menu.png` rework), §5 #11's own resolution
 * — deliberately not a custom idle-timeout/video-playback mechanism this
 * app owns (Decisions Log: "Idle-state screensaver — deferred to Android's
 * own Daydream system"). Just a deep link to Android TV's own screensaver
 * settings, where a Daydream service like "Aerial Views" gets selected.
 */
@Composable
fun ScreensaverSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val buttonFocusRequester = remember { FocusRequester() }

    SettingsPageScaffold(title = "Screensaver", modifier = modifier) {
        Column(modifier = Modifier.width(SettingsCategoryListWidth)) {
            Text(
                text = "For an aerial screensaver like tvOS, install the free \"Aerial Views\" app, then choose it here.",
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { openScreensaverSettings(context) },
                modifier = Modifier.focusRequester(buttonFocusRequester),
            ) {
                Text("Open Screensaver Settings")
            }
        }
    }

    LaunchedEffect(Unit) {
        buttonFocusRequester.requestFocus()
    }
}

/** Android TV's own Daydream/screensaver settings page — where a Daydream service like Aerial Views gets selected and configured, not something this app has any reason to duplicate. */
private fun openScreensaverSettings(context: Context) {
    context.startActivity(Intent(AndroidSettings.ACTION_DREAM_SETTINGS))
}
