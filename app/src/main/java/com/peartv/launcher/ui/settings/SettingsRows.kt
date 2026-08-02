package com.peartv.launcher.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.peartv.launcher.ui.focus.tvOSFocusable

/**
 * Shared pill-row shell every Settings row builds on — the same focus-swap-
 * background language this screen's original single `SettingsCategoryRow`
 * established: solid `onSurface` fill + inverted text on focus, `surface`
 * fill otherwise. Opposite-luminance in either theme by construction (not a
 * literal white-on-black), so it reads correctly in both the dark and light
 * scheme without a separate light-theme treatment.
 *
 * [trailing] is the only thing that varies between row kinds — a chevron, a
 * checkmark, or nothing. [SettingsToggleRow] doesn't build on this: a real
 * `Switch` needs to be the row's *only* focusable target (see its own doc)
 * rather than living inside another focusable row.
 */
@Composable
private fun SettingsRowShell(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (contentColor: Color) -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusedBackground = MaterialTheme.colorScheme.onSurface
    val unfocusedBackground = MaterialTheme.colorScheme.surface
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) focusedBackground else unfocusedBackground,
        label = "settingsRowBackground",
    )
    val contentColor = if (isFocused) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsRowCornerRadius))
            .background(backgroundColor)
            .tvOSFocusable(
                focusedScale = 1f,
                cornerRadius = SettingsRowCornerRadius,
                glowColor = MaterialTheme.colorScheme.onBackground,
                onFocusChange = { isFocused = it },
                onClick = onClick,
            )
            .padding(horizontal = SettingsRowHorizontalPadding, vertical = SettingsRowVerticalPadding),
    ) {
        Text(text = text, color = contentColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing(contentColor)
    }
}

/**
 * Navigates to a sub-page — label + chevron, optionally with the current
 * [value] shown ahead of the chevron (e.g. "Theme" showing "Dark ›") the
 * same way real tvOS Settings previews a category's active choice without
 * requiring a drill-in to see it.
 */
@Composable
fun SettingsCategoryRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    SettingsRowShell(text = text, onClick = onClick, modifier = modifier) { contentColor ->
        if (value != null) {
            Text(
                text = value,
                color = contentColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Text(text = "›", color = contentColor, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * A boolean preference — label + `Switch`. The *row* is the D-pad target
 * (via [SettingsRowShell], same as every other row kind), not the `Switch`
 * itself — `Modifier.focusProperties { canFocus = false }` keeps the Switch
 * purely a visual indicator of the current state rather than a second,
 * independently-focusable target inside an already-focusable row (which
 * would need an extra D-pad press to reach and breaks every other row's
 * "one row, one focus stop" behavior). Matches real tvOS: the whole row
 * toggles, not just the small control.
 */
@Composable
fun SettingsToggleRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRowShell(text = text, onClick = { onCheckedChange(!checked) }, modifier = modifier) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

/**
 * One option in a value picker (e.g. Theme's Automatic/Light/Dark) — label +
 * a `primary`-colored checkmark when [selected]. The one deliberate, minimal
 * accent-color use across these rows.
 */
@Composable
fun SettingsSelectionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRowShell(text = text, onClick = onClick, modifier = modifier) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** A single triggered action — label only, no chevron/switch/checkmark. Refresh Metadata, Clear Artwork Cache, Open System Screensaver Settings, Reset Settings. */
@Composable
fun SettingsActionRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRowShell(text = text, onClick = onClick, modifier = modifier)
}

/** Non-interactive label + value — e.g. Version. Not `tvOSFocusable`: there's nothing to select, matching this row's real tvOS counterpart (informational only, never highlights). */
@Composable
fun SettingsInfoRow(
    text: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsRowHorizontalPadding, vertical = SettingsRowVerticalPadding),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

val SettingsRowCornerRadius = 28.dp
val SettingsRowHorizontalPadding = 28.dp
val SettingsRowVerticalPadding = 18.dp
val SettingsRowSpacing = 6.dp
