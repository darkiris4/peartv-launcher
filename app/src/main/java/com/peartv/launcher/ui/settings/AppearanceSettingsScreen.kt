package com.peartv.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text

/** §4 sub-page (`design/settings-menu.png` rework) — just the dark theme toggle, the one appearance-related setting this app has. */
@Composable
fun AppearanceSettingsScreen(
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val switchFocusRequester = remember { FocusRequester() }

    SettingsPageScaffold(title = "Appearance", modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(SettingsCategoryListWidth),
        ) {
            Text(text = "Dark theme", color = MaterialTheme.colorScheme.onBackground)
            Switch(
                checked = isDarkTheme,
                onCheckedChange = onDarkThemeChange,
                modifier = Modifier.focusRequester(switchFocusRequester),
            )
        }
    }

    LaunchedEffect(Unit) {
        switchFocusRequester.requestFocus()
    }
}
