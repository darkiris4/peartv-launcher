package com.peartv.launcher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.peartv.launcher.ui.theme.ambientBackground
import com.peartv.launcher.ui.theme.settingsBackground
import kotlinx.coroutines.delay

/**
 * Shared shell for every settings page (the root category list and each of
 * its sub-pages) — user-directed rework against the real tvOS Settings
 * reference (`design/settings-menu.png`): a centered title over a full-bleed
 * background (`ambientBackground()` — a soft global glow, not a flat fill;
 * see `ui/theme/AmbientBackground.kt`), everything else specific to that one
 * page.
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
        // User-directed fix for the icon-shift bug: was CenterHorizontally,
        // which re-centered the icon+content Row as a single unit every
        // time a route's content changed width (`SettingsScreen`'s own doc
        // on this). `Start` anchors the whole page from a fixed left edge
        // instead, so nothing here shifts based on sibling content width —
        // paired with the icon+description column's own new fixed width
        // (`SettingsScreen`'s `SettingsIconColumnWidth`), the icon's
        // position is now independent of route entirely.
        //
        // The title keeps its own explicit centering below
        // (`Modifier.fillMaxWidth()` + `textAlign = Center`) rather than
        // inheriting it from this alignment, so this fix doesn't also shift
        // the title text off-center as a side effect.
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .fillMaxSize()
            .ambientBackground(baseColor = MaterialTheme.settingsBackground())
            .padding(horizontal = SettingsHorizontalPadding, vertical = SettingsVerticalPadding),
    ) {
        Text(
            text = title,
            // Neither pure white nor pure black — user-directed: "opposite
            // shades of grey" in either theme, `onSurfaceVariant` (the
            // existing secondary-text tier) already is exactly that by
            // construction. Bold, same Inter family as everywhere else
            // (PearTvTypography's own doc: only fontFamily is overridden
            // app-wide, so weight is this call site's own choice to make).
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(SettingsTitleSpacing))
        content()
    }
}

val SettingsHorizontalPadding = 64.dp
val SettingsVerticalPadding = 48.dp
val SettingsTitleSpacing = 40.dp

/**
 * Applied to whatever a settings pane wants as its own initial focus target
 * (a category row, a switch, a text field, a button) in place of a plain
 * `Modifier.focusRequester(requester)` + a bare `LaunchedEffect { requester
 * .requestFocus() }`. On-device diagnostic logging (timestamps on the
 * button's own focus/click callbacks, cross-referenced against
 * `WindowManager`'s own `handleComboKeys` log for the physical key) proved
 * out the real mechanism behind a real bug this caused: navigating into
 * `SettingsRoute.Screensaver` disposes the category row that was focused,
 * and **Compose's own focus system immediately redirects focus to the next
 * available focusable element on its own** — the sub-page's button, here —
 * well before any of our own code runs. The *original* press's key-up
 * (still in flight at the Android input-dispatch level; confirmed via the
 * `WindowManager` log landing chronologically between the button's own
 * focus-gained and click callbacks) then lands on that button and fires it,
 * immediately launching an external Settings Activity before the screen was
 * ever visibly reachable. A plain delay on *our own* `requestFocus()` call
 * (the first fix attempted here) did nothing, because the problem was never
 * our call — it was Compose's automatic fallback beating it there.
 *
 * The actual fix: keep the target genuinely unfocusable
 * (`focusProperties { canFocus = false }`, not just undelayed) for
 * [SettingsInitialFocusGraceMillis] — Compose's focus-search fallback skips
 * an unfocusable node entirely, so it has nothing to land the stale
 * key-up's click on until the grace window closes and this modifier flips
 * `canFocus` back on and claims focus explicitly itself. Every settings
 * pane's own initial target carries the identical risk (root's first
 * category row when returning from a sub-page, same as every sub-page's own
 * control), so this is shared rather than fixed once and left latent
 * elsewhere.
 *
 * [isInitialFocusEnabled], not `canFocus`, names the local state — `Modifier
 * .focusProperties { canFocus = ... }`'s lambda receiver ([FocusProperties])
 * *itself* declares a `canFocus` property. A same-named outer variable read
 * on the right-hand side of `canFocus = canFocus` doesn't reach past that
 * implicit receiver the way it would in an ordinary function body; Kotlin
 * resolves *both* sides to the receiver's own property, making it a silent
 * `this.canFocus = this.canFocus` no-op. Confirmed on-device as the reason
 * the first version of this fix compiled cleanly (no warning) and changed
 * nothing at all — the gate was never actually engaging.
 */
@Composable
fun Modifier.settingsInitialFocus(requester: FocusRequester): Modifier {
    var isInitialFocusEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(SettingsInitialFocusGraceMillis)
        isInitialFocusEnabled = true
        requester.requestFocus()
    }

    return this
        .focusRequester(requester)
        .focusProperties { canFocus = isInitialFocusEnabled }
}

private const val SettingsInitialFocusGraceMillis = 350L
