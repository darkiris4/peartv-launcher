package com.peartv.launcher.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.peartv.launcher.ui.theme.settingsRowFill

/**
 * Reports the currently-focused row's own [description] text (user-directed:
 * a tip/description line below the Settings icon panel, updating live as
 * focus moves between rows) — a setter function, not a raw `MutableState`,
 * so `SettingsScreen` can scope *where the write actually lands* per route
 * rather than every row sharing one always-live target.
 *
 * That scoping matters: `AnimatedContent`'s outgoing page is still composed
 * (mid exit-fade) at the same time the incoming page is composing — a row on
 * the *old* page can still gain real Android focus for a moment during that
 * handoff (Compose's own focus system redirecting away from a row about to
 * be disposed) and fire this same callback. A raw shared `MutableState`
 * can't tell that write apart from a legitimate one on the page actually
 * being shown, which is exactly what caused a confirmed-user-reported
 * "flash" of stale/wrong description text right after entering a sub-page.
 * `SettingsScreen`'s own provided setter closes over the route each
 * `AnimatedContent` branch actually belongs to and only forwards the write
 * if that route is still the active one.
 */
val LocalFocusedSettingsDescription = staticCompositionLocalOf<(String?) -> Unit> {
    error("LocalFocusedSettingsDescription not provided — read only from within SettingsScreen")
}

/**
 * Whether the current page is still inside its own
 * [SettingsInitialFocusGraceMillis] window — read by every row via
 * [SettingsRowShell] so *all* of them, not just whichever one carries
 * [settingsInitialFocus], stay genuinely unfocusable
 * (`focusProperties { canFocus = ... }`) until that window closes.
 *
 * Root-caused a confirmed-user-reported bug: [settingsInitialFocus] only
 * gated its own single target — real Android focus, finding that target
 * unfocusable, fell back to the *next* focusable node in the page (the
 * second row) instead, which visibly claimed focus (and, for a row with its
 * own [description], the focused-description text below the icon panel)
 * for the length of the grace window before the intended first row's own
 * `requestFocus()` call finally landed and corrected it — a real, briefly
 * wrong-then-right flash of both the row highlight and its description
 * text, not merely an animation artifact. Gating every row in lockstep
 * closes off that fallback entirely: there's nothing else for real focus to
 * land on until the grace window ends and the intended target claims it
 * explicitly.
 *
 * A plain `State<Boolean>` (not a setter function like
 * [LocalFocusedSettingsDescription]) — every row only ever *reads* this,
 * never writes it; `SettingsScreen` owns the single per-route timer that
 * flips it.
 */
val LocalSettingsInitialFocusGraceActive = staticCompositionLocalOf<State<Boolean>> {
    error("LocalSettingsInitialFocusGraceActive not provided — read only from within SettingsScreen")
}

/**
 * Shared pill-row shell every Settings row builds on — the same focus-swap-
 * background language this screen's original single `SettingsCategoryRow`
 * established: solid `onSurface` fill + inverted text on focus,
 * [settingsRowFill] otherwise. Opposite-luminance in either theme by
 * construction (not a literal white-on-black), so it reads correctly in both
 * the dark and light scheme without a separate light-theme treatment.
 *
 * Unfocused fill is [settingsRowFill], not the plain `colorScheme.surface`
 * every other row-like element in this app uses — user-directed against the
 * real tvOS reference (`design/settings-menu.png`): `surface` sits too close
 * to this screen's own background to read as a raised pill, which made the
 * jump to the focused white fill feel like a "flashbulb" rather than a step.
 * See `settingsRowFill()`'s own doc.
 *
 * [trailing] is the only thing that varies between row kinds — a chevron, a
 * checkmark, or nothing. [SettingsToggleRow] doesn't build on this: a real
 * `Switch` needs to be the row's *only* focusable target (see its own doc)
 * rather than living inside another focusable row.
 *
 * The label `Text` is deliberately *not* `Modifier.weight(1f)` — a Row with a
 * weighted child doesn't report a well-defined intrinsic width, and this
 * row's own width comes from an ancestor `IntrinsicSize.Max` query
 * (`SettingsScreen`'s own doc on that). Confirmed on-device: the widest row
 * on a page (whichever one actually determines the shared pill width) had its
 * trailing chevron crowd right up against the label with no gap at all, since
 * the intrinsic query undercounted the weighted text's contribution. A fixed
 * [SettingsRowTrailingGap] plus a *separate*, empty weighted spacer fixes
 * this: the fixed gap is unambiguous, real space that's always part of this
 * row's own intrinsic width (so the widest row still gets a gap before its
 * chevron), while the empty spacer (whose own intrinsic width is
 * unambiguously zero) absorbs whatever's left over to push `trailing` to the
 * pill's far edge on every shorter row.
 */
@Composable
private fun SettingsRowShell(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: @Composable (contentColor: Color) -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusedBackground = MaterialTheme.colorScheme.onSurface
    val unfocusedBackground = MaterialTheme.settingsRowFill()
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) focusedBackground else unfocusedBackground,
        label = "settingsRowBackground",
    )
    val contentColor = if (isFocused) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    val setFocusedDescription = LocalFocusedSettingsDescription.current
    val initialFocusGraceActive = LocalSettingsInitialFocusGraceActive.current.value

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsRowCornerRadius))
            .background(backgroundColor)
            // See [LocalSettingsInitialFocusGraceActive]'s own doc — every
            // row, not just whichever one carries [settingsInitialFocus],
            // stays unfocusable until the page's own grace window closes,
            // so real focus has nothing else to fall back to in the
            // meantime.
            .focusProperties { canFocus = !initialFocusGraceActive }
            .tvOSFocusable(
                focusedScale = 1f,
                cornerRadius = SettingsRowCornerRadius,
                glowColor = MaterialTheme.colorScheme.onBackground,
                onFocusChange = { focused ->
                    isFocused = focused
                    if (focused) setFocusedDescription(description)
                },
                onClick = onClick,
            )
            .padding(horizontal = SettingsRowHorizontalPadding, vertical = SettingsRowVerticalPadding),
    ) {
        Text(text = text, color = contentColor, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(SettingsRowTrailingGap))
        Spacer(modifier = Modifier.weight(1f))
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
    description: String? = null,
) {
    SettingsRowShell(text = text, onClick = onClick, modifier = modifier, description = description) { contentColor ->
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
    description: String? = null,
) {
    SettingsRowShell(text = text, onClick = { onCheckedChange(!checked) }, modifier = modifier, description = description) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

/**
 * One option in a value picker (e.g. Theme's Automatic/Light/Dark) — label +
 * a checkmark when [selected]. Muted grey (`contentColor` at the same 0.6
 * alpha [SettingsCategoryRow]'s own value preview uses), not the `primary`
 * accent color — user-directed against the real tvOS reference: its own
 * Settings checkmarks are grey, never the system-blue accent.
 */
@Composable
fun SettingsSelectionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    SettingsRowShell(text = text, onClick = onClick, modifier = modifier, description = description) { contentColor ->
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.6f),
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
    description: String? = null,
) {
    SettingsRowShell(text = text, onClick = onClick, modifier = modifier, description = description)
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
        )
        Spacer(modifier = Modifier.width(SettingsRowTrailingGap))
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

val SettingsRowCornerRadius = 28.dp
val SettingsRowHorizontalPadding = 28.dp

/**
 * User-directed against the real tvOS reference (`design/settings-menu.png`,
 * measured directly): rows there are compact to the text line, roughly a
 * third of this row's old 18dp*2 padding — not the generous, roomier padding
 * this app's rows used to carry. Trimmed further (8dp → 4dp) — `titleMedium`
 * already carries its own line-height leading above/below the glyphs, so
 * 8dp was stacking extra space on top of that rather than replacing it.
 */
val SettingsRowVerticalPadding = 4.dp
val SettingsRowSpacing = 6.dp

/** Guaranteed minimum gap between a row's label and its trailing content — see [SettingsRowShell]'s own doc for why this exists as a fixed value alongside the flexible spacer, not weight alone. */
val SettingsRowTrailingGap = 16.dp
