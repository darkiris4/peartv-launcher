package com.peartv.launcher.ui.theme

import androidx.compose.ui.graphics.Color

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
