package com.peartv.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text

/** `SettingsScreen`'s `SettingsRoute.Appearance` content pane — just the dark theme toggle, the one appearance-related setting this app has. */
@Composable
fun AppearanceSettingsContent(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val switchFocusRequester = remember { FocusRequester() }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = "Dark theme", color = MaterialTheme.colorScheme.onBackground)
        Switch(
            checked = isDarkTheme,
            onCheckedChange = onDarkThemeChange,
            modifier = Modifier.settingsInitialFocus(switchFocusRequester),
        )
    }
}
