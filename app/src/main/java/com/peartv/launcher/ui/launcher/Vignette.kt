package com.peartv.launcher.ui.launcher

import androidx.compose.ui.graphics.Color

/**
 * Shared "top shelf" vignette tuning — `HeroBanner.kt` (Tier 1/2's hero) and
 * `ContentCarousel.kt` (Tier 3's full-screen carousel) each fade toward the
 * page background along their bottom/left edges so whatever overlaps them
 * (the tray, the lower-left title/metadata text) stays legible regardless
 * of how bright the backdrop art underneath is. Shared here, not duplicated
 * per file, so every surface reads as one consistent treatment rather than
 * each accumulating its own slightly-different tuning over time — confirmed
 * on-device this had already happened once (`ContentCarousel` still had an
 * older, unbounded/unfeathered version when this was written).
 *
 * User-directed, tuned down twice from an initial pass: "length" (how much
 * of the surface the fade covers) and "strength" (how opaque it gets at its
 * most extreme edge) were both confirmed too much at first, then the fade
 * boundary itself was confirmed to have a visible hard edge — see
 * [featheredEdgeStops]'s own doc for that fix. These are the values after
 * both rounds of on-device correction.
 */
const val VignetteBottomFraction = 0.22f
const val VignetteLeftFraction = 0.14f

/** Max opacity either vignette fade reaches at its most extreme edge ("strength") — the tray's own near-opaque panel fill (TranslucentPanelAlpha, Dimens.kt) already handles its own legibility independently, so capping this below full opacity doesn't reopen the legibility problem the vignette originally existed to solve. */
const val VignetteMaxAlpha = 0.65f

/**
 * A second, separately-tuned vertical fade — deliberately *not* reusing
 * [VignetteBottomFraction]/[VignetteMaxAlpha] above. That pair blends an
 * entire Top Shelf surface into the surrounding chrome and was tuned
 * subtle by explicit user direction ("strength and length both too
 * much"); this pair exists specifically to guarantee legible title/
 * metadata text over arbitrary, unpredictable photographic artwork, a
 * stricter requirement that would force the ambient fade back toward
 * "too much" if the two shared one tuning. Taller (covers most of the
 * surface, not just its bottom edge) and stronger (reaches near-opaque)
 * for exactly that reason. Callers pair this with a *fixed* dark color
 * (`PearTvBackgroundDark`, not the theme-flipped `backgroundColor` the
 * ambient vignette above uses) — see `HeroBanner.kt`/`ContentCarousel.kt`'s
 * own call sites for why: real tvOS keeps Top Shelf title/metadata text a
 * consistent white-on-dark regardless of system light/dark appearance,
 * since the artwork behind it is arbitrary photographic content of
 * unknown brightness. A theme-flipped scrim+text pairing (dark text on a
 * light scrim, in light theme) can't offer that same legibility guarantee
 * against unpredictable art the way a fixed dark scrim under fixed light
 * text can.
 */
const val TopShelfTextScrimFraction = 0.75f
const val TopShelfTextScrimMaxAlpha = 0.85f

/** Sample points for [featheredEdgeStops]'s piecewise approximation of a smoothstep curve — enough for the curve to read as genuinely smooth (not visibly faceted) at the scale a full-hero/carousel gradient renders at. */
private const val VignetteFeatherSteps = 8

/**
 * Ease-in-out curve (zero slope at both `t=0` and `t=1`) — used instead of a
 * plain linear ramp so a fade's alpha *approaches* its start/end value
 * gradually rather than snapping into a constant-rate ramp. A plain 2-stop
 * linear `Brush.verticalGradient`/`horizontalGradient` has a real slope
 * discontinuity exactly where the fade begins (flat at zero slope right
 * before, then an abrupt non-zero slope right after) — confirmed on-device
 * that this reads as a visible hard edge, worse in light theme where the
 * eye is more sensitive to gradation in brighter tones (Weber's law) than
 * the same fade looked in dark theme.
 */
private fun smoothstep(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

/**
 * Generates gradient color stops for a feathered fade of [color] between
 * [start] and [end] fractions of a gradient's own axis. Fixes two separate
 * artifacts a plain two-stop `colorStops` fade has, both confirmed
 * on-device as a visible hard edge (worse in light theme):
 *
 * 1. Interpolating against [Color.Transparent] means the *RGB* channels
 *    ramp too — [Color.Transparent] is black `(0,0,0)` at zero alpha, not
 *    "[color] at zero alpha," so a fade toward/from it briefly blends
 *    through black-tinted intermediate colors instead of a pure alpha
 *    ramp of [color] itself. Every stop here holds [color]'s own RGB fixed
 *    and only varies alpha, via [Color.copy].
 * 2. A plain 2-stop linear alpha ramp has a real slope discontinuity right
 *    at the fade's boundary (see [smoothstep]'s own doc). [steps] extra
 *    stops sampled along a smoothstep curve approximate a genuinely
 *    feathered fade instead of Compose's own straight-line interpolation
 *    between just two points.
 *
 * [reversed] flips which end is transparent — a bottom vignette fades
 * transparent→opaque as its fraction increases (top of the fade zone to
 * the very bottom edge); a left vignette fades opaque→transparent (the
 * screen edge inward), the opposite direction.
 *
 * [maxAlpha] caps the opaque end's own alpha below fully opaque (1.0) —
 * user-directed "strength" reduction: even a properly-feathered fade still
 * read as too strong reaching full opacity at its most extreme edge.
 */
fun featheredEdgeStops(
    color: Color,
    start: Float,
    end: Float,
    reversed: Boolean = false,
    maxAlpha: Float = VignetteMaxAlpha,
    steps: Int = VignetteFeatherSteps,
): Array<Pair<Float, Color>> = Array(steps + 1) { i ->
    val t = i / steps.toFloat()
    val fraction = start + (end - start) * t
    val eased = smoothstep(t)
    val alpha = (if (reversed) 1f - eased else eased) * maxAlpha
    fraction to color.copy(alpha = alpha)
}
