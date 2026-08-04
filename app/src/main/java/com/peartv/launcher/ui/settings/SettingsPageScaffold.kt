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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.peartv.launcher.ui.launcher.BlurredArtwork
import com.peartv.launcher.ui.theme.ambientBackground
import com.peartv.launcher.ui.theme.ambientPanelTint
import com.peartv.launcher.ui.theme.settingsBackground
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
 *
 * [cachedBackdrop] is a frosted backdrop built from the same *bitmap* the
 * dock/status pill already have (`BlurredArtwork` — `BlurredArtwork.kt`),
 * re-blurred a second time at a heavier radius before it's ever cached here
 * (`MainActivity`'s own `SettingsBackdropBlurRadius`/`reblurred` call) — not
 * a fresh capture/blur pipeline, just a heavier derivative of one that
 * already exists. Deliberately does *not* reuse the dock's own
 * `liquidGlassTint` (`GlassPanel.kt`) — that function's luminance-adaptive,
 * fairly transparent alpha range is tuned for a small panel that still
 * needs to read as *glass*; user-directed that Settings should read as a
 * proper frosted *panel* instead, so [settingsPanelTint] below is its own,
 * independently-tunable function with a fixed (not luminance-adaptive)
 * alpha per theme, and no specular top-edge highlight either.
 *
 * Settings has no live hero carousel of its own to source a blur from at
 * all (`MainActivity`'s own `when (screen)` swap fully disposes
 * `Screen.Launcher`'s composition when navigating here, confirmed by
 * investigation before this was built), so [cachedBackdrop] is whatever
 * `MainActivity` had most recently cached from Launcher before the swap,
 * hoisted above that `when` block specifically so it survives it. `null` on
 * true cold start into Settings (before Launcher has ever composed) — falls
 * back to the original flat `MaterialTheme.settingsBackground()` fill in
 * that case, same as before this existed. Static, not Ken-Burns-animated —
 * Settings has no equivalent "currently focused" concept to re-trigger
 * motion from, so this is a still frosted backdrop, not a moving one.
 */
@Composable
fun SettingsPageScaffold(
    title: String,
    cachedBackdrop: BlurredArtwork?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Computed here (composable scope, needs MaterialTheme) rather than
    // inside the draw lambda below (a plain DrawScope block can't call
    // @Composable functions) — captured by that lambda instead.
    val backdropTint = cachedBackdrop?.let { settingsPanelTint(it) }
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
            .ambientBackground(
                baseColor = MaterialTheme.settingsBackground(),
                backdrop = if (cachedBackdrop != null && backdropTint != null) {
                    {
                        drawScaledToCover(cachedBackdrop.bitmap)
                        drawRect(backdropTint)
                    }
                } else {
                    null
                },
            )
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

/**
 * Draws [image] scaled up (never down) to fully cover this `DrawScope`'s own
 * bounds and centered — the same `ContentScale.Crop` math `AsyncImage` and
 * `TopShelfRow`/`StatusBar`'s own artwork crops use, hand-rolled here since
 * a raw `drawBehind` block (this call's own site, `ambientBackground`'s
 * `backdrop` lambda) has no access to the layout-level `Image` composable or
 * its `contentScale` parameter. No source-region cropping needed the way
 * `positionAwareBackdropCrop` (`GlassPanel.kt`) does for the dock/pill —
 * this is a single full-screen backdrop with nothing else on screen to stay
 * spatially registered against, so the *whole* cached bitmap scaled to
 * cover is already the right picture, not a sub-region of it.
 */
private fun DrawScope.drawScaledToCover(image: ImageBitmap) {
    val scale = maxOf(size.width / image.width, size.height / image.height)
    val dstWidth = image.width * scale
    val dstHeight = image.height * scale
    drawImage(
        image = image,
        dstOffset = IntOffset(
            ((size.width - dstWidth) / 2f).roundToInt(),
            ((size.height - dstHeight) / 2f).roundToInt(),
        ),
        dstSize = IntSize(dstWidth.roundToInt(), dstHeight.roundToInt()),
    )
}

/**
 * Settings' own fixed, theme-aware backdrop tint — deliberately *not* the
 * dock's own `liquidGlassTint` (`GlassPanel.kt`, `TopShelfRow`/`StatusBar`'s
 * shared function): that one's alpha is luminance-adaptive and tuned to
 * stay fairly transparent, correct for a small panel meant to read as
 * *glass* over live, brightness-swinging carousel art. Settings' own
 * backdrop is a single cached still image behind a full page of text/rows —
 * user-directed it should read as a proper frosted *panel*, not glass, so
 * this uses a heavier, fixed (not luminance-adaptive) alpha per theme
 * instead — the backdrop being a still image is exactly why the adaptive
 * complexity isn't needed here the way it is for the dock's fast-rotating
 * artwork. [SettingsPanelTintAlphaDark]/[SettingsPanelTintAlphaLight] are
 * named and scoped separately from the dock's own alpha constant on purpose
 * — tuning one should never risk accidentally shifting the other.
 *
 * Still leans the tint's own hue toward [BlurredArtwork.averageColor], same
 * modest bias weight concept the dock's own tint uses — this part *is*
 * shared in spirit (a touch of the art's own color reads better than a
 * flat neutral tint regardless of context), just computed independently
 * here rather than calling into `GlassPanel.kt` at all.
 */
@Composable
private fun settingsPanelTint(artwork: BlurredArtwork): Color {
    val baseTint = MaterialTheme.ambientPanelTint()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alpha = if (isDarkTheme) SettingsPanelTintAlphaDark else SettingsPanelTintAlphaLight
    val tintColor = lerp(baseTint, artwork.averageColor, SettingsPanelTintColorBiasWeight)
    return tintColor.copy(alpha = alpha)
}

/** [settingsPanelTint]'s own fixed alpha in dark theme — see that function's own doc for why this is a separate constant from the dock's [com.peartv.launcher.ui.launcher.liquidGlassTint] range. */
private const val SettingsPanelTintAlphaDark = 0.65f

/** [settingsPanelTint]'s own fixed alpha in light theme — sits higher than [SettingsPanelTintAlphaDark], same reasoning `liquidGlassTint`'s own light/dark split already documented: light-theme foreground content loses legibility faster against a barely-tinted bright backdrop. */
private const val SettingsPanelTintAlphaLight = 0.75f

/** How far [settingsPanelTint]'s own color leans toward the art's average color — same modest-bias concept as the dock's own tint, tuned independently. */
private const val SettingsPanelTintColorBiasWeight = 0.3f

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

internal const val SettingsInitialFocusGraceMillis = 350L
