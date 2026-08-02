package com.peartv.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.peartv.launcher.domain.repository.ThemeMode

/**
 * `SettingsScreen`'s `SettingsRoute.Appearance` content pane — the theme
 * choice is now its own sub-page ([ThemeSettingsContent] below), not an
 * inline `Switch`: a 3-way Automatic/Light/Dark choice doesn't fit a boolean
 * toggle the way the original 2-way dark/light choice did. [value] on the
 * "Theme" row previews the active choice without requiring a drill-in,
 * matching real tvOS Settings rows.
 *
 * Reduce Motion / Transparency Effects are visually complete placeholders
 * (local-only state, nothing persisted) — see this feature's own concerns
 * write-up: this app already has a *real*, system-sourced Reduce Motion
 * mechanism (`ui/motion/ReduceMotion.kt`, read once at startup from
 * `Settings.Global.ANIMATOR_DURATION_SCALE`); this toggle doesn't override
 * it yet.
 */
@Composable
fun AppearanceSettingsContent(
    themeMode: ThemeMode,
    onOpenTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstRowFocusRequester = remember { FocusRequester() }
    var reduceMotion by remember { mutableStateOf(false) }
    var transparencyEffects by remember { mutableStateOf(true) }

    Column(
        verticalArrangement = Arrangement.spacedBy(SettingsRowSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        SettingsCategoryRow(
            text = "Theme",
            value = themeMode.name,
            onClick = onOpenTheme,
            modifier = Modifier.settingsInitialFocus(firstRowFocusRequester),
        )
        SettingsToggleRow(
            text = "Reduce Motion",
            checked = reduceMotion,
            onCheckedChange = { reduceMotion = it },
        )
        SettingsToggleRow(
            text = "Transparency Effects",
            checked = transparencyEffects,
            onCheckedChange = { transparencyEffects = it },
        )
    }
}

/**
 * `SettingsRoute.Theme` — `Automatic` follows the system's own light/dark
 * appearance; `Light`/`Dark` are the pre-existing fixed choices. Picking one
 * persists it and immediately returns to Appearance (`SettingsScreen`'s own
 * `onBack` call alongside `onThemeModeChange`), matching real tvOS pickers'
 * select-and-return behavior rather than requiring a separate confirm/back
 * step.
 *
 * Initial focus lands on whichever row matches the *current* [themeMode],
 * not always the first row — opening a picker already scrolled/focused to
 * today's value is what real tvOS pickers do, and is more useful than
 * always resetting to "Automatic."
 */
@Composable
fun ThemeSettingsContent(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val automaticFocusRequester = remember { FocusRequester() }
    val lightFocusRequester = remember { FocusRequester() }
    val darkFocusRequester = remember { FocusRequester() }

    Column(
        verticalArrangement = Arrangement.spacedBy(SettingsRowSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        SettingsSelectionRow(
            text = "Automatic",
            selected = themeMode == ThemeMode.Automatic,
            onClick = { onThemeModeChange(ThemeMode.Automatic) },
            modifier = if (themeMode == ThemeMode.Automatic) {
                Modifier.settingsInitialFocus(automaticFocusRequester)
            } else {
                Modifier
            },
        )
        SettingsSelectionRow(
            text = "Light",
            selected = themeMode == ThemeMode.Light,
            onClick = { onThemeModeChange(ThemeMode.Light) },
            modifier = if (themeMode == ThemeMode.Light) {
                Modifier.settingsInitialFocus(lightFocusRequester)
            } else {
                Modifier
            },
        )
        SettingsSelectionRow(
            text = "Dark",
            selected = themeMode == ThemeMode.Dark,
            onClick = { onThemeModeChange(ThemeMode.Dark) },
            modifier = if (themeMode == ThemeMode.Dark) {
                Modifier.settingsInitialFocus(darkFocusRequester)
            } else {
                Modifier
            },
        )
    }
}
