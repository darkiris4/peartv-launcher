package com.peartv.launcher.ui.theme

import androidx.compose.ui.graphics.Color

// Dark palette — the app's original (and still default) look.
val PearTvBackgroundDark = Color(0xFF111112)
val PearTvSurfaceDark = Color(0xFF1E1E1E)
val PearTvOnBackgroundDark = Color(0xFFFFFFFF)

/** Neutral tile fill for apps with no accent-color enrichment data (§3.2.1) — dark variant. */
val PearTvTileFallbackDark = Color(0xFF2A2A2C)

// Light palette — added per the tvOS photo reference (`design/`), which is a
// light-mode capture (Decisions Log: "Theme"). Approximate values, not
// measured from the photos pixel-for-pixel; revisit if it reads wrong
// on-device.
val PearTvBackgroundLight = Color(0xFFF2F2F5)
val PearTvSurfaceLight = Color(0xFFFFFFFF)
val PearTvOnBackgroundLight = Color(0xFF111112)
val PearTvTileFallbackLight = Color(0xFFE1E1E6)
