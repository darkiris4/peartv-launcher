package com.peartv.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * Reduce Motion / Transparency Effects are now both real, persisted
 * (`SettingsRepository`) and functional: Reduce Motion is an override that
 * combines with the pre-existing system-sourced signal
 * (`ui/motion/ReduceMotion.kt`'s `isReduceMotionEnabled()`) rather than
 * replacing it — see that field's own doc on `SettingsRepository`. Transparency
 * Effects gates `BackdropBlur.kt`'s `LocalTransparencyEffectsEnabled`.
 */
@Composable
fun AppearanceSettingsContent(
    themeMode: ThemeMode,
    reduceMotion: Boolean,
    onReduceMotionChange: (Boolean) -> Unit,
    transparencyEffects: Boolean,
    onTransparencyEffectsChange: (Boolean) -> Unit,
    onOpenTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstRowFocusRequester = remember { FocusRequester() }

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
            onCheckedChange = onReduceMotionChange,
            description = "Minimize animations and transitions throughout the interface",
        )
        SettingsToggleRow(
            text = "Transparency Effects",
            checked = transparencyEffects,
            onCheckedChange = onTransparencyEffectsChange,
            description = "Reduce blur and see-through panels for a more solid look",
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
