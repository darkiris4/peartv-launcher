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

    /** Must resolve faster than scale so tilt never "lags" the scale-in. */
    val Tilt: SpringSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 3000f,
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
