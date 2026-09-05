package com.peartv.launcher.ui.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** How much the image scales up at the peak of each Ken Burns cycle — subtle, per user direction. `ContentScale.Crop` already fills the container at 1x, so this scale is exactly the overscan margin the accompanying pan has to drift within without ever revealing an edge. */
private const val KenBurnsMaxScale = 1.06f

/** Fraction of the image's own size the slow pan drifts by, per axis — small enough to stay safely inside the zoom margin [KenBurnsMaxScale] provides. */
private const val KenBurnsPanFraction = 0.02f

/** One full zoom-in-then-out cycle — slow and long on purpose (real Ken Burns pacing, not a UI micro-interaction). */
private const val KenBurnsCycleMillis = 14000

/**
 * A slow, continuous, looping zoom+pan on whatever this modifier is applied
 * to — real tvOS's own ambient motion for Top Shelf artwork while it holds
 * focus (a Ken Burns effect, not true multi-layer parallax: this project's
 * backdrop art is a single flat image per app/program, and there's no
 * Android equivalent of a multi-layer icon/art format to derive real depth
 * parallax from — see the tvOS design-parity ledger's "layered image depth"
 * finding, a platform/ecosystem constraint, not something this modifier can
 * work around).
 *
 * Respects [LocalReduceMotion] — this is exactly the kind of ambient
 * depth-simulation effect Apple's own Reduce Motion guidance calls out, so
 * it's skipped entirely (no scale/pan at all, `this` returned unchanged)
 * when that's on, mirroring `TvFocusable.kt`'s own tilt-skip for the same
 * setting.
 *
 * Callers rely on being recomposed fresh per new image (`Crossfade`/
 * `AnimatedContent`'s own per-target-state composition, already the case
 * at every call site this is used from — `HeroBanner`'s backdrop,
 * `ContentCarousel`'s `PosterBackdrop`) for the cycle to restart per image;
 * this modifier has no explicit restart key of its own.
 *
 * `clip = true` in the [graphicsLayer] block below is load-bearing, not
 * decorative: [androidx.compose.ui.graphics.GraphicsLayerScope]'s `clip`
 * defaults to `false`, so a scaled-up layer paints beyond its own layout
 * bounds rather than being confined to them. Confirmed on-device: without
 * it, the zoomed image bled past the hero's own bottom edge — outside
 * where the (unscaled, fixed-bounds) Top Shelf vignette actually covers —
 * and peeked out right above the dock. Same fix `TvFocusable.kt`'s own
 * `graphicsLayer` already uses for exactly this reason.
 */
@Composable
fun Modifier.kenBurns(): Modifier = composed {
    val progress by rememberKenBurnsProgress()
    this.kenBurnsTransform(progress)
}

/**
 * The bare 0..1 progress [kenBurns] drives its own [graphicsLayer] transform
 * from — split out so a second draw target (`ContentCarousel`'s dock-backdrop
 * blur, sourced from the same artwork as the sharp poster this progress
 * already animates) can apply the *exact same instantaneous value* via
 * [kenBurnsTransform] rather than running its own independent
 * [rememberInfiniteTransition]. Two separate infinite transitions, even with
 * identical parameters, don't start in phase with each other — confirmed
 * user-reported requirement: they need to share one clock, not just one
 * config, to actually stay in sync.
 *
 * Still respects [LocalReduceMotion] the same way [kenBurns] always has —
 * pinned at `0f` (no motion) rather than skipping the transform entirely, so
 * every caller can unconditionally apply [kenBurnsTransform] without its own
 * reduce-motion branch.
 */
@Composable
fun rememberKenBurnsProgress(): State<Float> {
    val reduceMotion = LocalReduceMotion.current
    if (reduceMotion) return remember { mutableFloatStateOf(0f) }

    val transition = rememberInfiniteTransition(label = "kenBurns")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(KenBurnsCycleMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenBurnsProgress",
    )
}

/** Applies [progress] (0..1, from [rememberKenBurnsProgress]) as the same scale+pan transform [kenBurns] itself uses. */
fun Modifier.kenBurnsTransform(progress: Float): Modifier = this.graphicsLayer {
    val scale = 1f + (KenBurnsMaxScale - 1f) * progress
    scaleX = scale
    scaleY = scale
    translationX = size.width * KenBurnsPanFraction * progress
    translationY = size.height * KenBurnsPanFraction * 0.5f * progress
    clip = true
}
