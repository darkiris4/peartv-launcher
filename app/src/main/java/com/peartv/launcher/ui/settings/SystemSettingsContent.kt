package com.peartv.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** `SettingsScreen`'s `SettingsRoute.System` content pane — About/Version/Licenses/Reset, matching real tvOS's own "General" category shape. */
@Composable
fun SystemSettingsContent(
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
    onResetSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val firstRowFocusRequester = remember { FocusRequester() }
    var showResetConfirm by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(SettingsRowSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        SettingsCategoryRow(
            text = "About PearTV",
            onClick = onOpenAbout,
            modifier = Modifier.settingsInitialFocus(firstRowFocusRequester),
        )
        SettingsInfoRow(text = "Version", value = versionName)
        SettingsCategoryRow(text = "Open Source Licenses", onClick = onOpenLicenses)
        SettingsActionRow(
            text = "Reset Settings",
            onClick = { showResetConfirm = true },
            description = "Restores all settings to their default values",
        )
    }

    if (showResetConfirm) {
        ResetSettingsConfirmPrompt(
            onConfirm = {
                onResetSettings()
                showResetConfirm = false
            },
            onCancel = { showResetConfirm = false },
        )
    }
}

/**
 * Reset Settings is destructive and irreversible (clears the TMDB key and
 * theme choice back to defaults) — same confirm-before-acting shape as
 * `MergeConfirmPrompt`/`ChannelsPermissionPrompt` (this app's other
 * confirm-or-dismiss prompts), not a shared component: generalizing those
 * would mean touching their existing call sites, out of scope for this
 * pass.
 */
@Composable
private fun ResetSettingsConfirmPrompt(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val confirmFocusRequester = remember { FocusRequester() }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onCancel,
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
                text = "Reset Settings?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "This clears your TMDB key and theme choice back to their defaults.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.focusRequester(confirmFocusRequester),
                ) {
                    Text("Reset")
                }
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        confirmFocusRequester.requestFocus()
    }
}

/** `SettingsRoute.About` — placeholder informational page. */
@Composable
fun AboutSettingsContent(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "PearTV is a tvOS-inspired launcher for Android TV — a focused home screen, curated app enrichment, and content-aware recommendations, built for the 10-foot experience.",
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** `SettingsRoute.Licenses` — placeholder; no licenses-metadata dependency (e.g. `oss-licenses`) exists in this project yet to back a real listing. */
@Composable
fun LicensesSettingsContent(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Open source license information isn't available yet — this page previews the eventual layout.",
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
