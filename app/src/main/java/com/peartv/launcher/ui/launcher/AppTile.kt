package com.peartv.launcher.ui.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.peartv.launcher.R
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.LaunchOrigin
import com.peartv.launcher.ui.focus.tvOSFocusable
import com.peartv.launcher.ui.motion.TvSprings
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** User-supplied tile art override (`design/settings-{light,dark}.png`) replaces the real Android TV Settings app's own icon/banner — see the `packageName` check below. Not `private`: [HeroBanner]'s Tier 2 fallback needs the same override for its own backdrop (same package, no import needed — same file package). */
const val SystemSettingsPackageName = "com.android.tv.settings"

/**
 * A single grid or top-shelf tile — PRODUCT_SPEC.md §3.1.1's tile spec
 * (solid accent-color fill, contain-scaled artwork, initial-letter fallback)
 * wired to [tvOSFocusable] for the actual focus motion, plus §1.4's per-tile
 * focus label rendered below the tile itself.
 *
 * Sizing is entirely the caller's responsibility via [modifier] — this used
 * to default to a fixed width/height here, but `LazyVerticalGrid`'s
 * `GridCells.Fixed` computes its own column width and ignores a child's
 * requested width, which (confirmed on-device) silently produced ~1.22:1
 * tiles instead of 16:9. `AppGrid` now applies `Modifier.aspectRatio(...)`
 * instead, which derives height from whatever width the grid assigns — this
 * composable has no business assuming which sizing strategy is in use.
 * [modifier] is applied only to the tile itself (not the label below it), so
 * that aspect-ratio math here stays exactly what the caller asked for.
 *
 * [showFocusLabel] defaults on for the grid; `TopShelfRow` passes `false` —
 * the tray's own scale/glow focus motion already reads as "you're focused
 * here" without a label, and the tray needs every tile packed at just its
 * own tile height (no reserved label row) to match the grid's row height
 * expectations one-for-one.
 *
 * Grid Reordering & Folders §2 "Jiggle Mode" — [isEditMode]/[isActiveDrag]/
 * [isDimmed]/[jigglePhaseSeed] drive the oscillation/scale/dim visuals;
 * [onLongPress] (forwarded to [tvOSFocusable]) opens the Options popover
 * (user-directed model: a long-press always opens that small menu first —
 * Edit Mode is one of *its* actions, not the long-press's own direct
 * effect). [isOptionsMenuTarget] is the plain dim-only treatment the
 * long-pressed tile gets while that popover is open — no jiggle, since Edit
 * Mode hasn't started yet at that point. [onPositioned] reports this tile's
 * real on-screen bounds so the popover (which anchors next to whichever
 * tile opened it, not a centered modal) knows where to draw itself; only
 * ever wired to a non-noop callback for the one tile that's currently either
 * the Options-menu target or Edit Mode's active tile. All new parameters
 * default to "off," so every existing call site renders exactly as before.
 *
 * [onClick] receives this tile's own real screen bounds at the moment it
 * was pressed (`null` if layout hasn't reported them yet) — separate from
 * [onPositioned] above, which only fires for the current Options-menu/
 * Edit-Mode target; every tile needs its own bounds available at click
 * time, not just whichever one currently owns that callback. Feeds
 * [com.peartv.launcher.domain.repository.LaunchOrigin], which callers
 * thread down to [com.peartv.launcher.data.launcher.AppLauncherImpl] for
 * the cross-process launch-zoom transition (`ActivityOptions.makeScaleUpAnimation`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: TvApp,
    onClick: (LaunchOrigin?) -> Unit,
    modifier: Modifier = Modifier,
    focusedScale: Float = 1.15f,
    showFocusLabel: Boolean = true,
    onFocus: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    isEditMode: Boolean = false,
    isActiveDrag: Boolean = false,
    isDimmed: Boolean = false,
    isOptionsMenuTarget: Boolean = false,
    jigglePhaseSeed: Int = 0,
    onPositioned: (LayoutCoordinates) -> Unit = {},
) {
    // MaterialTheme.colorScheme.surfaceVariant, not a hardcoded dark-gray
    // constant — this is the fallback tile background, and it needs to
    // flip with the theme toggle same as everything else (a light theme
    // with a fixed dark-gray fallback would look like a bug, not a choice).
    val fallbackColor = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = app.accentColorArgb?.let { Color(it) } ?: fallbackColor
    // The tile's own background fill above still uses accentColor (each
    // app's curated brand color, when present) — but the focus glow is
    // always onBackground regardless, uniform across every tile (near-white
    // in dark theme, near-black in light theme). Previously glowed with
    // accentColor when present, which produced inconsistent shadow tones
    // across the dock (a dark-branded app's tile glowed dark even in dark
    // theme) — user-directed: shadow color should be uniform, not per-app.
    val glowColor = MaterialTheme.colorScheme.onBackground

    var isFocused by remember { mutableStateOf(false) }
    // Real tvOS Top Shelf behavior (user-supplied): "The label fades in and
    // out as focus arrives and leaves ... coordinated with the
    // parallax/lift animation on the tile itself" — reusing
    // TvSprings.ElevationFocusGain/Loss (the tile's own shadow/lift spring,
    // `tvOSFocusable`'s own doc) instead of a plain duration-matched tween
    // makes the label track the *exact* curve the lift moves on, not just
    // settle in the same rough window.
    val labelAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = if (isFocused) TvSprings.ElevationFocusGain else TvSprings.ElevationFocusLoss,
        label = "tileFocusLabelAlpha",
    )

    // Compose's default scroll-into-view on focus only considers the
    // *focusable* node's own bounds — here, the tile Box — not its sibling
    // label below it. Confirmed on-device: a tile focused at the bottom edge
    // of the grid's viewport left its label clipped off-screen underneath
    // it, since the grid only scrolled far enough to reveal the tile itself.
    // Requesting the whole Column (tile + label) into view on focus instead
    // of relying on the implicit per-node behavior fixes this.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Grid Reordering & Folders — explicitly re-claims real Android focus
    // the instant this tile becomes Edit Mode's active/dragged tile.
    // Confirmed on-device this is necessary, not redundant: closing the
    // Options popover (whose own row held focus until just now) leaves
    // Compose's focus system to pick a new target on its own, and it doesn't
    // reliably land back on this tile — observed falling back to the dock's
    // first item instead, which (via `isTopShelfFocused`) re-expanded the
    // hero and made the actual active tile scroll out of view entirely.
    val tileFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isActiveDrag) {
        if (isActiveDrag) tileFocusRequester.requestFocus()
    }

    // Grid Reordering & Folders §2 — always running (cheap: one float), only
    // ever applied to rotation when [isEditMode] is true. Declaring this
    // unconditionally (rather than inside an `if`) keeps this composable's
    // slot table stable across Edit Mode toggling on/off for a tile that
    // stays composed the whole time.
    val jiggleTransition = rememberInfiniteTransition(label = "jiggle")
    val jigglePhase by jiggleTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(JiggleLoopMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(jigglePhaseSeed % JiggleLoopMillis),
        ),
        label = "jigglePhase",
    )
    val jiggleScale = when {
        isActiveDrag -> JiggleActiveScale
        isDimmed -> JiggleNonActiveScale
        else -> 1f
    }
    val jiggleAlpha = when {
        isActiveDrag -> 1f
        isDimmed -> JiggleDimmedAlpha
        isOptionsMenuTarget -> OptionsMenuTargetDimAlpha
        else -> 1f
    }

    // This tile's own real window coordinates, independent of [onPositioned]
    // above (that one only ever fires for the current Options-menu/Edit-Mode
    // target) — every tile needs its own bounds on hand the instant it's
    // clicked, for [onClick]'s own doc.
    var tileCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .graphicsLayer {
                rotationZ = if (isEditMode) jigglePhase * JiggleRotationDegrees else 0f
                scaleX = jiggleScale
                scaleY = jiggleScale
                alpha = jiggleAlpha
            },
    ) {
        Box(
            modifier = modifier
                .onGloballyPositioned {
                    tileCoordinates = it
                    onPositioned(it)
                }
                .focusRequester(tileFocusRequester)
                .tvOSFocusable(
                    // The active drag tile is guaranteed focused for the whole
                    // Edit Mode session — its enlarged look comes entirely from
                    // [JiggleActiveScale] above, so tvOSFocusable's own focus
                    // scale is suppressed here rather than compounding both.
                    focusedScale = if (isActiveDrag) 1f else focusedScale,
                    cornerRadius = TileCornerRadius,
                    glowColor = glowColor,
                    onFocusChange = { focused ->
                        isFocused = focused
                        if (focused) {
                            onFocus()
                            coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    },
                    onLongPress = onLongPress,
                    onClick = {
                        val bounds = tileCoordinates?.boundsInWindow()
                        onClick(
                            bounds?.let {
                                LaunchOrigin(
                                    x = it.left.roundToInt(),
                                    y = it.top.roundToInt(),
                                    width = it.width.roundToInt(),
                                    height = it.height.roundToInt(),
                                )
                            },
                        )
                    },
                )
                .background(accentColor),
            contentAlignment = Alignment.Center,
        ) {
            val banner = app.banner
            if (app.packageName == SystemSettingsPackageName) {
                // User-supplied override (`design/settings-{light,dark}.png`)
                // for the real Android TV Settings app's own tile — the only
                // app-specific icon override in this codebase, deliberately
                // scoped to this one package rather than a general mechanism
                // nothing else has asked for yet.
                val isDarkBackground = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Image(
                    painter = painterResource(if (isDarkBackground) R.drawable.settings_tile_dark else R.drawable.settings_tile_light),
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (banner != null) {
                val painter: Painter = remember(banner) {
                    BitmapPainter(banner.asImageBitmap())
                }
                Image(
                    painter = painter,
                    contentDescription = app.label,
                    // Crop, not Fit — tiles are 5:3 (Dimens.kt's own doc),
                    // while most system banners are authored at the native
                    // 16:9, so a slight crop is the common case now, not the
                    // rare one. Keeps the tile's flat fallback-color
                    // background from ever showing through as a letterbox
                    // gap regardless of the source asset's own ratio.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    // onSurfaceVariant pairs semantically with the
                    // surfaceVariant fallback background above — readable in
                    // both themes, unlike a hardcoded white.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // §1.4's per-tile focus label — always composed when [showFocusLabel]
        // (never conditionally skipped per-focus-state) so its height is
        // reserved permanently, per that section's own resolution of its
        // "unresolved detail": a conditionally-present label would reflow
        // every neighboring row on each focus change. Only alpha reacts to
        // focus. Omitted entirely for tray tiles (`showFocusLabel = false`).
        if (showFocusLabel) {
            Box(
                modifier = Modifier
                    .padding(top = FocusLabelSpacing)
                    .height(FocusLabelHeight),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(labelAlpha),
                )
            }
        }
    }
}
