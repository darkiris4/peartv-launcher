package com.peartv.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.tv.material3.MaterialTheme

// Dark palette — the app's original (and still default) look.
val PearTvBackgroundDark = Color(0xFF000000)
val PearTvSurfaceDark = Color(0xFF1C1C1E)
val PearTvOnBackgroundDark = Color(0xFFFFFFFF)

/** Neutral tile fill for apps with no accent-color enrichment data (§3.2.1) — dark variant. */
val PearTvTileFallbackDark = Color(0xFF2A2A2C)

/** Secondary metadata text (episode badges, description, meta lines) — dimmer than [PearTvOnBackgroundDark] but still clearly legible, tvOS's own secondary-label tone. */
val PearTvOnSurfaceVariantDark = Color(0xFF98989D)

/** Tertiary text — the dimmest legible tier, one step below [PearTvOnSurfaceVariantDark] (e.g. de-emphasized captions). */
val PearTvTertiaryDark = Color(0xFF636366)

/** Hairline separators/rings (e.g. `StatusBar`'s settings-gear `OutlinedIconButton` ring) — tvOS keeps borders minimal, so this sits close to the surface tone rather than reading as a strong line. */
val PearTvBorderDark = Color(0xFF3A3A3C)

/** An even more subtle divider variant, for separators that shouldn't compete with [PearTvBorderDark]'s own hairlines. */
val PearTvBorderVariantDark = Color(0xFF242426)

/** tvOS-style system-blue accent — sparingly used for selection/on-state chrome (e.g. the Appearance settings `Switch`), never per-app branding (that's [com.peartv.launcher.domain.model.TvApp.accentColorArgb]). */
val PearTvAccentDark = Color(0xFF0A84FF)

/** Destructive actions (e.g. OptionsMenu's "Delete App"). */
val PearTvErrorDark = Color(0xFFFF453A)

/**
 * Settings-only background — user-directed against the real tvOS reference
 * (`design/settings-menu.png`): the rest of the app's true-black
 * [PearTvBackgroundDark] made the focused-row invert to white read as a
 * "flashbulb" jump with almost nothing in between. Lifted one step (same
 * tone as [PearTvSurfaceDark]) — kept as its own named constant rather than
 * reusing that one directly, since this role is deliberately independent
 * and scoped to Settings only (the launcher grid/folder modal keep true
 * black). Neutral gray, not the cool/blue-tinted charcoal the reference
 * photo itself has — user-directed to leave the color temperature alone.
 * No light-theme counterpart needed: [PearTvBackgroundLight] is already a
 * soft off-white, not a harsh extreme, so light theme is untouched (see
 * `settingsBackground()`).
 */
val PearTvSettingsBackgroundDark = Color(0xFF1C1C1E)

/**
 * Settings row (unfocused) fill — clearly lighter than
 * [PearTvSettingsBackgroundDark] so rows read as raised pills instead of
 * nearly blending into the page background (the same relationship the real
 * tvOS reference has between its own row fill and background, just kept
 * neutral instead of blue-tinted). No light-theme counterpart needed: light
 * theme's existing row fill (`colorScheme.surface`, pure white) already
 * sits clearly above its own soft background the same way (see
 * `settingsRowFill()`).
 */
val PearTvSettingsRowFillDark = Color(0xFF333335)

// Light palette — added per the tvOS photo reference (`design/`), which is a
// light-mode capture (Decisions Log: "Theme"). Approximate values, not
// measured from the photos pixel-for-pixel; revisit if it reads wrong
// on-device.
val PearTvBackgroundLight = Color(0xFFF2F2F5)
val PearTvSurfaceLight = Color(0xFFFFFFFF)
val PearTvOnBackgroundLight = Color(0xFF111112)
val PearTvTileFallbackLight = Color(0xFFE1E1E6)

/** Secondary metadata text — light-theme counterpart of [PearTvOnSurfaceVariantDark]. */
val PearTvOnSurfaceVariantLight = Color(0xFF6E6E73)

/** Tertiary text — light-theme counterpart of [PearTvTertiaryDark]. */
val PearTvTertiaryLight = Color(0xFFC4C4C6)

/** Hairline separators/rings — light-theme counterpart of [PearTvBorderDark]. */
val PearTvBorderLight = Color(0xFFD0D0D5)

/** Subtle divider variant — light-theme counterpart of [PearTvBorderVariantDark]. */
val PearTvBorderVariantLight = Color(0xFFE4E4E8)

/** tvOS-style system-blue accent — light-theme counterpart of [PearTvAccentDark]. */
val PearTvAccentLight = Color(0xFF007AFF)

/** Destructive actions — light-theme counterpart of [PearTvErrorDark]. */
val PearTvErrorLight = Color(0xFFFF3B30)

/** White content color atop the blue accent fill, both themes — Apple's system blue is dark enough at both tones for white content to stay legible. */
val PearTvOnAccent = Color(0xFFFFFFFF)

/** White content color atop [PearTvErrorDark]/[PearTvErrorLight], both themes. */
val PearTvOnError = Color(0xFFFFFFFF)

/**
 * Settings-only background — dark theme lifts to [PearTvSettingsBackgroundDark];
 * light theme is untouched (`colorScheme.background` is already a soft
 * off-white, not a harsh extreme, so there's nothing to lift). The
 * luminance check (not a `darkTheme` param) matches the existing pattern
 * elsewhere in this app (e.g. `AppTile`'s Settings-icon override) for
 * telling the two schemes apart without a dedicated CompositionLocal.
 */
@Composable
fun MaterialTheme.settingsBackground(): Color =
    if (colorScheme.background.luminance() < 0.5f) PearTvSettingsBackgroundDark else colorScheme.background

/**
 * Settings row (unfocused) fill — dark theme lifts to [PearTvSettingsRowFillDark];
 * light theme reuses `colorScheme.surface` (pure white), which already sits
 * clearly above its own soft background the same way.
 */
@Composable
fun MaterialTheme.settingsRowFill(): Color =
    if (colorScheme.background.luminance() < 0.5f) PearTvSettingsRowFillDark else colorScheme.surface
