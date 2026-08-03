package com.peartv.launcher.ui.focus

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.peartv.launcher.ui.motion.LocalReduceMotion
import com.peartv.launcher.ui.motion.TvSprings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// User-directed: the original 6f was confirmed imperceptible on the actual
// reference TV at normal viewing distance (a temporary 35f diagnostic build
// confirmed the mechanism itself was working, just too subtle to notice).
// Split the difference — noticeable, not dramatic.
private const val MaxTiltDegrees = 14f
private const val PressHoldMillis = 80L
private const val MaxShadowElevationPx = 48f

/**
 * Scales down the focus shadow from [MaxShadowElevationPx]'s original full
 * strength — confirmed on-device (same finding as the dock tray's own
 * shadow, Decisions Log: "§3.1.1 'liquid glass' tray/pill styling —
 * removed") that a shadow this strong reads heavier than the small version
 * does. Applies in both themes.
 */
private const val FocusShadowScale = 0.35f

// internal, not private: AppTile's/FolderTile's per-tile focus label (§1.4)
// reuse these same durations for their own label fade rather than
// introducing new constants that could drift out of sync with this one.
internal const val FocusGainMillis = 220
internal const val FocusLossMillis = 250

/**
 * The tvOS-style focus interaction described in PRODUCT_SPEC.md §1.1/§1.2:
 * spring-driven scale, a directional tilt that decays back to rest, and a
 * critically-damped elevation/shadow lift — all expressed inside a single
 * [Modifier.graphicsLayer] block so the whole effect runs on RenderThread,
 * independent of Compose's UI-thread recomposition/layout pass (§2.3).
 *
 * Focus indication is scale + a small elevated shadow ([FocusShadowScale])
 * — no border/ring, no unfocused-dim (removed per the Decisions Log's
 * "§3.1.1 'liquid glass' tray/pill styling — removed" entry, along with
 * every other shadow/tint/blur/dim effect in the app except this one). A
 * ring was tried and removed again: scale/shadow alone were never actually
 * verified insufficient in isolation — the original "can't tell what's
 * focused" report predated a separate fix (the onFocusChanged/focusable
 * ordering bug below), which meant animations weren't firing *at all*, not
 * just reading as too subtle. The ring was added in the same pass as that
 * fix, so this combination was never tested on its own until now.
 *
 * Deliberately built on [Modifier.composed] rather than a `Modifier.Node`
 * for this scaffolding pass — simpler to get correct first. If profiling
 * later shows composed-modifier recomposition overhead on the grid, this is
 * the natural candidate for a ModifierNodeElement rewrite.
 *
 * @param focusedScale steady-state scale while focused (§1.1: 1.15x for grid
 *   tiles, 1.08x for top-shelf tiles — pass per call site, never hardcode).
 * @param glowColor tints the focus shadow — no default on purpose. Always
 *   pass `MaterialTheme.colorScheme.onBackground`/`onSurface` (near-white in
 *   dark theme, near-black in light theme), uniform across every focusable
 *   element, never a per-app accent color (user-directed: an earlier version
 *   glowed with the focused app's own accent color when present, which made
 *   shadow tone inconsistent tile-to-tile independent of theme). A silently
 *   unused `Color.White` default here is exactly how one call site
 *   (`OptionsMenu`'s `OptionRow`) previously shipped an invisible-in-light-
 *   theme focus shadow without anyone noticing — no default forces every new
 *   call site to make this choice explicitly instead.
 * @param onFocusChange reports every focus transition (true = gained), so a
 *   parent (e.g. the hero banner tracking "which app is active") can react
 *   without duplicating focus observation elsewhere — see the onFocusChanged/
 *   focusable ordering note below for why this must be the only place that
 *   observes focus state.
 */
fun Modifier.tvOSFocusable(
    focusedScale: Float = 1.15f,
    pressedScale: Float = 1.08f,
    cornerRadius: Dp = 12.dp,
    glowColor: Color,
    onFocusChange: (Boolean) -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    longPressMillis: Long = 1000L,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    // Whether a plain-click (no [onLongPress]) commit is currently scheduled
    // — see the `KeyDown` handler's own doc for why this exists as a plain
    // flag rather than living inside `LaunchedEffect(isPressed)` the way it
    // used to.
    var commitPending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val lastDirection = LocalLastDpadDirection.current
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    // PRODUCT_SPEC.md §5 #7 — Apple's HIG requires depth/parallax/animated-
    // blur effects to be disabled when the platform's reduced-motion signal
    // is on. Read once at app startup (ReduceMotion.kt's own doc), not
    // observed live.
    val reduceMotion = LocalReduceMotion.current

    val scale = remember { Animatable(1f) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val elevation = remember { Animatable(0f) }

    LaunchedEffect(isFocused, isPressed) {
        val targetScale = when {
            isPressed -> pressedScale
            isFocused -> focusedScale
            else -> 1f
        }
        if (reduceMotion) {
            // Snap, not spring — focus still needs *some* visible feedback
            // to stay usable, just without the bounce reduced motion exists
            // to suppress.
            scale.snapTo(targetScale)
            elevation.snapTo(if (isFocused) 1f else 0f)
            return@LaunchedEffect
        }
        val isGaining = isFocused || isPressed
        val scaleSpec = if (isGaining) TvSprings.ScaleFocusGain else TvSprings.ScaleFocusLoss
        val elevationSpec = if (isGaining) TvSprings.ElevationFocusGain else TvSprings.ElevationFocusLoss
        launch { scale.animateTo(targetScale, scaleSpec) }
        launch { elevation.animateTo(if (isFocused) 1f else 0f, elevationSpec) }
    }

    LaunchedEffect(isFocused) {
        // Tilt is pure parallax/depth simulation, not a functional focus
        // indicator (scale/elevation above already cover that) — skipped
        // entirely under reduced motion rather than just sped up, matching
        // the HIG's own "depth simulation (including parallax effects)"
        // wording exactly, not just "less bouncy."
        if (!isFocused || reduceMotion) return@LaunchedEffect
        // Tilt originates from the D-pad direction focus arrived from, then
        // springs back to rest — a discrete focus-transition effect, not
        // continuous pointer/gyro tracking (§1.2: Shield TV has neither).
        val (startTiltX, startTiltY) = when (lastDirection) {
            DpadDirection.Up -> -MaxTiltDegrees to 0f
            DpadDirection.Down -> MaxTiltDegrees to 0f
            DpadDirection.Left -> 0f to -MaxTiltDegrees
            DpadDirection.Right -> 0f to MaxTiltDegrees
            null -> 0f to 0f
        }
        tiltX.snapTo(startTiltX)
        tiltY.snapTo(startTiltY)
        launch { tiltX.animateTo(0f, TvSprings.Tilt) }
        launch { tiltY.animateTo(0f, TvSprings.Tilt) }
    }

    LaunchedEffect(isPressed) {
        if (!isPressed) {
            longPressFired = false
            return@LaunchedEffect
        }
        if (onLongPress != null) {
            // Grid Reordering & Folders §2 — a genuine hold, not the fixed
            // 80ms commit below: only fires if Select is still down once
            // [longPressMillis] elapses, so a normal short press never
            // triggers it (that path returns via the KeyUp branch instead).
            // Fires immediately here (not deferred to the eventual KeyUp) —
            // an earlier version deferred it to dodge a stray-KeyUp hazard
            // on whatever new composable it opens, but that made the whole
            // gesture feel exactly as slow as however long the user happened
            // to keep holding after the threshold (confirmed on-device: felt
            // like a 5-second hold instead of ~1s). The hazard is handled
            // downstream instead — see `OptionRow`'s own small focus-request
            // delay in `OptionsMenu.kt`.
            delay(longPressMillis)
            if (isPressed) {
                longPressFired = true
                onLongPress()
            }
        }
        // The plain-click (no onLongPress) commit used to live here as an
        // `else` branch — moved to the `KeyDown` handler below as an
        // independently-scoped coroutine. It doesn't belong in a block keyed
        // on `isPressed`: PRODUCT_SPEC.md §1.1/§3.3's own "80ms hold before
        // intent fires" was specified as a settle timer (wait 80ms after the
        // press starts, then fire, to get tvOS's tactile commit feel and
        // guard against a fast double-press double-launching), not as a
        // "key must still be down 80ms later" requirement. Because
        // `LaunchedEffect(isPressed)` cancels and relaunches whenever its key
        // changes, and `KeyUp` always sets `isPressed = false` immediately on
        // release, any tap shorter than 80ms cancelled this coroutine before
        // its `delay` ever completed — confirmed user-reported: a quick
        // select press visibly started the compress animation but never
        // actually navigated; only a hold past ~80ms (feels like "half a
        // second" against real remote/dispatch timing) let the delay survive
        // long enough to fire.
    }

    this
        // onFocusChanged MUST precede focusable() — it observes the focus
        // target's state, so it has to wrap that target, not follow it. Had
        // this backwards originally: isFocused silently never flipped to
        // true, so scale/tilt/elevation never fired even though real
        // Android input focus (and LazyRow's built-in scroll-into-view) was
        // moving correctly — confirmed the hard way, via a real device where
        // nothing visibly reacted to a tile the accessibility tree swore was
        // focused.
        .onFocusChanged {
            isFocused = it.isFocused
            onFocusChange(it.isFocused)
        }
        .focusable(interactionSource = interactionSource)
        .onKeyEvent { event ->
            val isSelectKey = event.key == Key.DirectionCenter || event.key == Key.Enter
            if (!isFocused || !isSelectKey) return@onKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    // A held Select key auto-repeats at the OS level
                    // (repeated KeyDown events, repeatCount > 0). If real
                    // focus moves to a brand-new node *while* an earlier
                    // press is still physically held — exactly what
                    // happens when a long-press opens the Options popover
                    // and the user keeps holding — those repeat events land
                    // on the newly-focused node next, which never saw the
                    // original, real KeyDown (`isPressed` is still false
                    // here). Treating that as a fresh press auto-fires this
                    // node's own click 80ms later with no real new input at
                    // all — confirmed on-device: holding past ~1.4s made
                    // the Options popover's first row (Edit Home Screen)
                    // select itself. A genuine new press always starts at
                    // repeatCount 0, so this only ever discards orphaned
                    // repeats, never real input.
                    val isOrphanedRepeat = !isPressed && event.nativeKeyEvent.repeatCount > 0
                    if (!isOrphanedRepeat) {
                        isPressed = true
                        // Plain-click commit, scheduled independently of
                        // `isPressed` (bug fix — see `LaunchedEffect(isPressed)`'s
                        // own doc above for the full story): launched on this
                        // scope, not `LaunchedEffect`, specifically so `KeyUp`
                        // setting `isPressed = false` moments later can't cancel
                        // it. `commitPending` blocks scheduling a *second* one
                        // while this one is still in flight — a fast double-tap
                        // must still only fire [onClick] once, matching
                        // PRODUCT_SPEC.md §1.1/§3.3's "avoids accidental
                        // double-launch from a fast double-press."
                        if (onLongPress == null && !commitPending) {
                            commitPending = true
                            coroutineScope.launch {
                                delay(PressHoldMillis)
                                commitPending = false
                                onClick()
                            }
                        }
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    // Only the long-press-capable branch needs to act here —
                    // the plain-click case's onClick is scheduled from
                    // `KeyDown` above and fires on its own timer, independent
                    // of this KeyUp.
                    if (onLongPress != null && isPressed && !longPressFired) {
                        onClick()
                    }
                    isPressed = false
                    true
                }
                else -> false
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationX = tiltX.value
            rotationY = tiltY.value
            cameraDistance = 8f * density
            this.shape = shape
            clip = true
            shadowElevation = elevation.value * MaxShadowElevationPx * FocusShadowScale
            spotShadowColor = glowColor
            ambientShadowColor = glowColor
        }
}
