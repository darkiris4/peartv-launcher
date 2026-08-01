package com.peartv.launcher.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** How much smaller than the destination area the intermediate low-resolution buffer is — the blur "amount" for [Modifier.blurredBackdrop]. Larger reads softer/blurrier but more blocky. */
private const val BackdropBlurDownscaleFactor = 6

/**
 * This project's own per-composition [GraphicsLayer] holder. The Compose BOM
 * this app is pinned to was bumped specifically to get [GraphicsLayer] itself
 * (PRODUCT_SPEC.md's Decisions Log, "Dock/pill backdrop blur"), but that BOM
 * predates the official `rememberGraphicsLayer()` convenience function that
 * normally wraps [LocalGraphicsContext] — this is that same wrapper,
 * hand-rolled against the lower-level [androidx.compose.ui.graphics.GraphicsContext]
 * API this BOM does have.
 */
@Composable
fun rememberGraphicsLayer(): GraphicsLayer {
    val context = LocalGraphicsContext.current
    val layer = remember(context) { context.createGraphicsLayer() }
    DisposableEffect(layer) {
        onDispose { context.releaseGraphicsLayer(layer) }
    }
    return layer
}

/**
 * Records this composable's own drawn output into [layer] on every draw
 * pass, in addition to drawing it normally. [layer] is owned by the caller
 * (via [rememberGraphicsLayer]) rather than created here, since one recorded
 * source can feed more than one [blurredBackdrop] consumer — the hero
 * backdrop feeds both the dock and the status pill's blur.
 *
 * Relies on Compose's normal sibling draw order (declaration order within a
 * shared parent) for freshness: every call site in this codebase records a
 * source from a composable declared *before* anything that consumes it via
 * [blurredBackdrop], so a consumer always sees this frame's content, never a
 * frame-stale one.
 */
fun Modifier.recordBackdropSource(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record(
        density = this,
        layoutDirection = layoutDirection,
        size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    ) {
        this@drawWithContent.drawContent()
    }
    drawContent()
}

/**
 * Draws a soft "frosted glass" stand-in for a real blur of [source]'s live
 * content as this modifier's background — callers still add their own
 * translucent tint via [androidx.compose.foundation.background] on top of
 * this; it only draws the blurred layer itself.
 *
 * [sourceOffset] is this composable's own position relative to [source]'s
 * origin — [androidx.compose.ui.layout.LayoutCoordinates.positionInParent]
 * when both share a parent (`TopShelfRow`, inside the same hero `Box` as
 * `HeroBanner`), or a window-position difference when they don't
 * (`StatusBar`, a sibling of the whole launcher screen in `MainActivity`).
 * Without it, [source] would always draw from its own top-left corner
 * instead of showing whatever's actually behind this composable.
 *
 * No real (`RenderEffect`) blur — this project's confirmed reference
 * hardware floor is API 30, a full generation below `RenderEffect`'s API 31
 * floor (PRODUCT_SPEC.md's Decisions Log hit this same wall for the ambient
 * background wash). Instead, [source] is drawn shrunk by
 * `1/`[BackdropBlurDownscaleFactor] into [lowResLayer] — a genuine
 * low-resolution rasterization, not just a smaller on-screen size — which is
 * then drawn back at full size via [GraphicsLayer.scaleX]/[GraphicsLayer.scaleY]
 * blown back up by that same factor. The upscale's bilinear filtering is
 * what reads as "blur." [lowResLayer] is owned by the caller (via
 * [rememberGraphicsLayer]) since each consumer needs its own scratch layer —
 * sharing one between the tray and the pill would make each one's draw
 * stomp the other's.
 *
 * A multi-pass (progressive downsample/upsample through several scratch
 * layers) version of this was attempted for better quality and **reverted
 * immediately** — it crashed the renderer outright on the actual reference
 * hardware (`SIGSEGV` in `RenderThread`, confirmed via `adb logcat`, not a
 * Kotlin exception), chaining several `GraphicsLayer.record()`/`drawLayer()`
 * calls against each other within one draw pass (re-recording a layer that
 * was itself just read as another layer's source, several times over, in
 * the same frame). This single-pass version is the last one confirmed
 * stable on-device — see the Decisions Log entry for what was tried.
 */
fun Modifier.blurredBackdrop(
    source: GraphicsLayer,
    lowResLayer: GraphicsLayer,
    sourceOffset: () -> Offset,
): Modifier = drawWithContent {
    val smallSize = IntSize(
        (size.width / BackdropBlurDownscaleFactor).roundToInt().coerceAtLeast(1),
        (size.height / BackdropBlurDownscaleFactor).roundToInt().coerceAtLeast(1),
    )
    lowResLayer.record(density = this, layoutDirection = layoutDirection, size = smallSize) {
        scale(1f / BackdropBlurDownscaleFactor, pivot = Offset.Zero) {
            val offset = sourceOffset()
            translate(-offset.x, -offset.y) {
                drawLayer(source)
            }
        }
    }
    lowResLayer.pivotOffset = Offset.Zero
    lowResLayer.scaleX = BackdropBlurDownscaleFactor.toFloat()
    lowResLayer.scaleY = BackdropBlurDownscaleFactor.toFloat()
    drawLayer(lowResLayer)
    drawContent()
}
