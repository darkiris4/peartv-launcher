package com.peartv.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Shared shell for every settings page (the root category list and each of
 * its sub-pages) — user-directed rework against the real tvOS Settings
 * reference (`design/settings-menu.png`): a centered title over a plain
 * full-bleed background, everything else specific to that one page.
 * Deliberately no on-screen "Back" affordance on any page — matches both
 * the reference (Apple TV Settings has none either, relying entirely on the
 * remote's own Back/Menu button) and this app's existing overlays (the
 * Options popover, the folder modal), which already rely on the hardware
 * Back key (`MainActivity`'s `BackHandler`) rather than a widget.
 */
@Composable
fun SettingsPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = SettingsHorizontalPadding, vertical = SettingsVerticalPadding),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(SettingsTitleSpacing))
        content()
    }
}

val SettingsHorizontalPadding = 64.dp
val SettingsVerticalPadding = 48.dp
val SettingsTitleSpacing = 40.dp
