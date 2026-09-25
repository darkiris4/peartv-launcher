package com.peartv.launcher.ui.focus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
 * strength — a shadow at full elevation reads heavier than the small version.
 * Split by theme: a dark scene shows a cast shadow far more readily than a
 * light one, so the dark value is pulled well down (user-reported: fine in
 * light, too strong in dark). Selected via [glowColor]'s own luminance —
 * that colour is always `onBackground`, near-white in dark theme and
 * near-black in light, so it doubles as a theme probe without extra plumbing.
 */
private const val FocusShadowScaleLight = 0.35f
private const val FocusShadowScaleDark = 0.16f

/**
 * A physical light/shadow pair replacing the old single isotropic
 * white-tinted elevation shadow — user-reported against the real tvOS
 * reference: tinting the cast shadow with [glowColor] (near-white in dark
 * theme) didn't read as a shadow at all, it read as a soft backlit glow,
 * with nothing simulating tvOS's own directional bevel (a bright edge on
 * the tile's upper-left, a dark cast shadow toward the lower-right).
 *
 * Both fixed, not theme-flipped like [glowColor] — a real light source
 * doesn't change color when a UI's theme toggles: a cast shadow reads dark
 * and a highlight reads light regardless of app theme. This is also safer
 * than the color it replaces, not just different: [glowColor]'s own doc
 * warns a stray plain-white shadow default once shipped invisible in light
 * theme unnoticed; a fixed *dark* shadow is visible against both themes'
 * backgrounds, so this removes that failure mode rather than risking it
 * again. [glowColor] still drives [shadowScale] below (still a valid
 * dark/light-theme probe) — only the shadow's own rendered color, and the
 * highlight's existence at all, are new.
 */
private val FocusShadowColor = Color.Black
private val FocusHighlightColor = Color.White

/** [FocusHighlightColor]'s own strength at full focus — kept subtle, a rim not a border. */
private const val FocusHighlightAlpha = 0.5f

/** Width of the top-left highlight rim drawn in [tvOSFocusable]'s own trailing `drawWithContent`. */
private val FocusHighlightStrokeWidth = 1.5.dp

/**
 * How far an *unfocused* focusable dims while a sibling holds focus —
 * tvOS's own home-screen behavior (the focused icon reads brighter because
 * everything around it steps back, not just because it grew). Kept gentle:
 * on a 10-foot display a heavier value reads as the whole grid going dark
 * rather than one tile stepping forward. Snapped, not animated, under
 * reduced motion.
 */
private const val UnfocusedDimAlpha = 0.72f

// internal, not private: AppTile's/FolderTile's per-tile focus label (§1.4)
// reuse these same durations for their own label fade rather than
// introducing new constants that could drift out of sync with this one.
internal const val FocusGainMillis = 220
internal const val FocusLossMillis = 250

/**
 * The current 3D-tilt of a focusable, reported through [Modifier.tvOSFocusable]'s
 * `onTilt` callback so a caller ([com.peartv.launcher.ui.launcher.AppTile])
 * can offset a layer *inside* the tile against it for parallax depth (§1.2)
 * — the tilt itself stays owned here so it composites on the same
 * `graphicsLayer` as scale/clip.
 */
data class TvTilt(val rotationX: Float, val rotationY: Float)

/**
 * The tvOS-style focus interaction described in PRODUCT_SPEC.md §1.1/§1.2:
 * spring-driven scale, a directional tilt that decays back to rest, and a
 * critically-damped elevation/shadow lift — all expressed inside a single
 * [Modifier.graphicsLayer] block so the whole effect runs on RenderThread,
 * independent of Compose's UI-thread recomposition/layout pass (§2.3).
 *
 * Focus indication is scale + a directional tilt + a small elevated shadow
 * ([FocusShadowScaleLight]/[FocusShadowScaleDark]) + a gentle dim on every
 * *un*focused sibling ([UnfocusedDimAlpha], [dimUnfocused]) — no border/ring.
 * The dim is tvOS's own home-screen read (the focused tile stands out because
 * its neighbours step back, not only because it grew).
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
 * @param dimUnfocused whether this element fades to [UnfocusedDimAlpha] while
 *   it doesn't hold focus. Default on; pass `false` for a lone focusable
 *   with no peers to contrast against.
 * @param elevateOnFocus whether the focused element casts a lifted shadow.
 *   Default on for tiles; pass `false` for a flat full-width row (a Settings
 *   list) where the shadow adds nothing and its spring lingers behind a fast
 *   scroll.
 * @param onTilt, when non-null, is called on every frame of the tilt
 *   animation with the current [TvTilt] — for a caller that wants to offset
 *   an inner layer against it for parallax depth (§1.2). The tilt rotation
 *   itself stays applied here.
 */
fun Modifier.tvOSFocusable(
    focusedScale: Float = 1.15f,
    pressedScale: Float = 1.08f,
    cornerRadius: Dp = 12.dp,
    glowColor: Color,
    dimUnfocused: Boolean = true,
    elevateOnFocus: Boolean = true,
    onFocusChange: (Boolean) -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    longPressMillis: Long = 1000L,
    onTilt: ((TvTilt) -> Unit)? = null,
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
    val contentDim = remember { Animatable(1f) }
    val shadowScale = when {
        !elevateOnFocus -> 0f
        glowColor.luminance() > 0.5f -> FocusShadowScaleDark
        else -> FocusShadowScaleLight
    }

    LaunchedEffect(isFocused, isPressed) {
        val targetScale = when {
            isPressed -> pressedScale
            isFocused -> focusedScale
            else -> 1f
        }
        val targetDim = if (isFocused || isPressed || !dimUnfocused) 1f else UnfocusedDimAlpha
        if (reduceMotion) {
            // Snap, not spring — focus still needs *some* visible feedback
            // to stay usable, just without the bounce reduced motion exists
            // to suppress.
            scale.snapTo(targetScale)
            elevation.snapTo(if (isFocused) 1f else 0f)
            contentDim.snapTo(targetDim)
            return@LaunchedEffect
        }
        val isGaining = isFocused || isPressed
        val scaleSpec = if (isGaining) TvSprings.ScaleFocusGain else TvSprings.ScaleFocusLoss
        val elevationSpec = if (isGaining) TvSprings.ElevationFocusGain else TvSprings.ElevationFocusLoss
        launch { scale.animateTo(targetScale, scaleSpec) }
        launch { elevation.animateTo(if (isFocused) 1f else 0f, elevationSpec) }
        launch {
            contentDim.animateTo(
                targetDim,
                tween(if (isGaining) FocusGainMillis else FocusLossMillis),
            )
        }
    }

    if (onTilt != null) {
        val currentOnTilt by rememberUpdatedState(onTilt)
        LaunchedEffect(Unit) {
            snapshotFlow { TvTilt(tiltX.value, tiltY.value) }.collect { currentOnTilt(it) }
        }
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
            alpha = contentDim.value
            this.shape = shape
            clip = true
            shadowElevation = elevation.value * MaxShadowElevationPx * shadowScale
            spotShadowColor = FocusShadowColor
            ambientShadowColor = FocusShadowColor
        }
        // The bevel's other half (see [FocusHighlightColor]'s own doc) — a
        // thin bright rim along the tile's own top-left edge, diagonally
        // faded out toward the bottom-right corner the cast shadow leans
        // into, so both read as one directional light source rather than
        // two independently-tuned effects. Chained *after* the graphicsLayer
        // above (not before it) so this draw lands inside that layer's own
        // transform/clip — it scales, tilts, and gets rounded-corner-clipped
        // together with the tile's real content, not as a separate
        // unclipped overlay. Cheap on purpose: a single stroked round-rect
        // with a linear-gradient brush, no RenderEffect/blur — this runs on
        // every focused tile, so it needs to stay light on Shield hardware
        // the same way the rest of this file's effects were already tuned
        // to.
        .drawWithContent {
            drawContent()
            if (elevateOnFocus && elevation.value > 0f) {
                val strokeWidthPx = FocusHighlightStrokeWidth.toPx()
                val cornerRadiusPx = cornerRadius.toPx()
                val inset = strokeWidthPx / 2f
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            FocusHighlightColor.copy(alpha = FocusHighlightAlpha * elevation.value),
                            FocusHighlightColor.copy(alpha = 0f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width * 0.7f, size.height * 0.7f),
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Stroke(width = strokeWidthPx),
                )
            }
        }
}
