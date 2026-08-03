package com.peartv.launcher.ui.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Canonical tvOS-style spring parameters — PRODUCT_SPEC.md §1.1.
 *
 * These are defined once and referenced everywhere motion happens; never
 * inline a `spring(...)` call at an animation site. If the feel needs
 * re-tuning, it happens here and only here.
 */
object TvSprings {

    /** Fast rise, one soft overshoot (≤ 2%), settles ~220ms. */
    val ScaleFocusGain: SpringSpec<Float> = spring(
        dampingRatio = 0.62f,
        stiffness = 1500f,
    )

    /** No overshoot on release — snappy retreat, no wobble. */
    val ScaleFocusLoss: SpringSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = 400f,
    )

    /**
     * Originally tuned to resolve faster than [ScaleFocusGain] (stiffness
     * 1500) so tilt never "lags" the scale-in — confirmed imperceptible on
     * the actual reference TV at that original stiffness (3000) paired
     * with too small an angle. Bumping the angle alone (`TvFocusable.kt`'s
     * `MaxTiltDegrees`, 6f → 14f) while staying this fast made the same
     * short settle window cover more than double the angular distance —
     * confirmed on-device that this read as a snap/stutter, not a tilt.
     * Slowed well below scale's own speed instead, prioritizing a motion
     * that's actually visible as *motion* over the original ordering rule.
     *
     * Slowed further still (350 → 180) — user-reported the tilt as only
     * visible in the focus shadow, not on the tile content itself. An
     * on-device audit confirmed the rotation itself was correct (a 35°
     * diagnostic build clearly skewed the tile), just too brief at this
     * spring's original settle speed for the eye to register on the tile's
     * own (sharper, less forgiving than a soft shadow) edges before it
     * relaxed back to flat. Same [MaxTiltDegrees] (14°) — only the settle
     * time changes.
     */
    val Tilt: SpringSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 180f,
    )

    /**
     * Critically damped — shadow/elevation changes never bounce.
     *
     * Split into gain/loss (like [ScaleFocusGain]/[ScaleFocusLoss]) because a
     * single stiffness=200 spring here settles in ~330ms, well after scale
     * (~220ms) and the border/dim tweens (~220-250ms, see TvFocusable's
     * FocusGainMillis/FocusLossMillis) — confirmed on-device: the shadow
     * visibly outlived the focus ring by 100-150ms on every focus change.
     * These stiffnesses are chosen to settle in the same ~220-250ms window
     * as everything else, so the whole focus transition releases as one
     * motion instead of the shadow trailing behind it.
     */
    val ElevationFocusGain: SpringSpec<Float> = spring(
        dampingRatio = 1.0f,
        stiffness = 450f,
    )

    /** See [ElevationFocusGain] — same fix, tuned to match [ScaleFocusLoss]'s ~250ms settle instead. */
    val ElevationFocusLoss: SpringSpec<Float> = spring(
        dampingRatio = 1.0f,
        stiffness = 350f,
    )
}
