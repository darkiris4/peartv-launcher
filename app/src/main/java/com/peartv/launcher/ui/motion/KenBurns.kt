package com.peartv.launcher.ui.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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

/**
 * [rememberKenBurnsProgress]'s cousin for a backdrop that needs to *pause*
 * mid-cycle rather than restart fresh every time it remounts — the app
 * grid's own collapsed-hero backdrop (`GridBackdrop.kt`), which freezes its
 * Ken Burns motion while the grid holds focus and needs to pick back up
 * smoothly, not snap, once the hero regains focus. `rememberInfiniteTransition`
 * has no pause primitive: cancelling its own `LaunchedEffect` only stops it
 * from composing, it doesn't stop the *next* mount from restarting at `0f`
 * — confirmed relevant here because `ContentCarousel` (the real source of
 * [DockBackdrop]'s own shared clock) fully unmounts once the hero finishes
 * collapsing and mounts fresh, with a brand new clock starting at `0f`, the
 * next time the hero expands. A caller that just mirrored [DockBackdrop]'s
 * clock the whole time would inherit that same restart-from-zero snap.
 *
 * This owns a plain float [progress] instead, driven by hand via
 * `withFrameNanos` — a `LaunchedEffect` keyed on [active] advances it only
 * while [active] is true; being cancelled ([active] flipping `false`) simply
 * leaves [progress] wherever it was, a real pause rather than a reset.
 * [goingUp] (which end of the 0..1 cycle it's currently headed toward)
 * survives the pause the same way (a plain `remember`, untouched by the
 * effect above stopping), so resuming continues toward the same target
 * instead of restarting the cycle from the top.
 *
 * [seed] is read once, the very first time this ever goes active, to start
 * this local clock in phase with whatever the *shared* hero/dock clock was
 * already showing at that instant — so this backdrop's first-ever freeze
 * lines up with the hero's own actual on-screen motion rather than starting
 * cold at `0f`. Every subsequent resume is already primed by its own prior
 * pause, so [seed] is deliberately not re-read past that first run.
 */
@Composable
fun rememberFreezableKenBurnsProgress(active: Boolean, seed: () -> Float): State<Float> {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { mutableFloatStateOf(0f) }
    var goingUp by remember { mutableStateOf(true) }
    var hasSeeded by remember { mutableStateOf(false) }

    LaunchedEffect(active, reduceMotion) {
        if (!active || reduceMotion) return@LaunchedEffect
        if (!hasSeeded) {
            progress.floatValue = seed().coerceIn(0f, 1f)
            hasSeeded = true
        }
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val deltaFraction = (frameNanos - lastFrameNanos) / 1_000_000f / KenBurnsCycleMillis
            lastFrameNanos = frameNanos
            var next = progress.floatValue + if (goingUp) deltaFraction else -deltaFraction
            if (next >= 1f) {
                next = 1f
                goingUp = false
            } else if (next <= 0f) {
                next = 0f
                goingUp = true
            }
            progress.floatValue = next
        }
    }
    return progress
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
