package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.peartv.launcher.ui.theme.ambientPanelTint
import kotlin.math.roundToInt

/**
 * The Liquid-Glass-style tint shared by every panel that sits over a
 * [BlurredArtwork] crop — `TopShelfRow` (the dock) and `StatusBar` (the
 * clock/settings pill), both real tvOS Top Shelf chrome floating over the
 * same rotating carousel artwork. User-directed move away from a flat
 * [TranslucentPanelAlpha] (read as "heavily frosted," not glass-like, since
 * a uniform non-adaptive tint has to stay heavy enough to hold legibility
 * against the *brightest* art the carousel could ever show).
 *
 * Content-aware instead: [BlurredArtwork.luminance] drives the tint's own
 * alpha — brighter art gets more cover (protects contrast), darker art gets
 * less (lets real detail/color through, since dark art already contrasts
 * against light foreground text/tiles on its own) — and
 * [BlurredArtwork.averageColor] pulls the tint's own hue a third of the way
 * toward the art's dominant color, on top of [ambientPanelTint]'s existing
 * theme-based base rather than replacing it outright.
 *
 * [artwork] `null` (Tier 1/2, nothing to sample, or Tier 3 between poster
 * loads) falls back to [fallbackAlpha] — each caller's own pre-Liquid-Glass
 * static value ([TranslucentPanelAlpha] for both current call sites).
 *
 * The alpha range itself is branched on theme, not shared — user-directed:
 * light-theme foreground content loses legibility faster against a barely-
 * tinted *bright* backdrop than dark-theme content does against a barely-
 * tinted *dark* one, so light mode's own floor sits a little higher.
 */
@Composable
fun liquidGlassTint(artwork: BlurredArtwork?, fallbackAlpha: Float = TranslucentPanelAlpha): Color {
    val baseTint = MaterialTheme.ambientPanelTint()
    if (artwork == null) return baseTint.copy(alpha = fallbackAlpha)

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (minAlpha, maxAlpha) = if (isDarkTheme) GlassTintAlphaRangeDark else GlassTintAlphaRangeLight
    val alpha = minAlpha + (maxAlpha - minAlpha) * artwork.luminance.coerceIn(0f, 1f)
    val tintColor = lerp(baseTint, artwork.averageColor, GlassTintColorBiasWeight)
    return tintColor.copy(alpha = alpha)
}

/** [liquidGlassTint]'s own alpha floor/ceiling in dark theme — see that function's own doc for why this differs from [GlassTintAlphaRangeLight]. */
private val GlassTintAlphaRangeDark = 0.15f to 0.35f

/** [liquidGlassTint]'s own alpha floor/ceiling in light theme — see that function's own doc for why this differs from [GlassTintAlphaRangeDark]. */
private val GlassTintAlphaRangeLight = 0.20f to 0.40f

/** How far [liquidGlassTint]'s own color leans toward the art's average color, `0f` = pure [ambientPanelTint], `1f` = the art's own color unfiltered. Deliberately modest — "most of the visual effect," per user direction, not a full color-sampling system. */
private const val GlassTintColorBiasWeight = 0.3f

private val GlassTopHighlightHeight = 2.dp
private const val GlassTopHighlightAlpha = 0.35f

/**
 * Liquid Glass's specular highlight/edge lensing, approximated as a cheap
 * static gradient rather than a real-time shader reacting to tilt/scroll
 * (user-directed: that's a large scope increase for a static-feeling win).
 * Callers draw this *last* (on top of their own tile/text content) so it
 * reads as light catching the panel's own top edge, not something that
 * content occludes. Fixed near-white regardless of theme — a real glass
 * highlight is reflected ambient light, not a theme-flipped surface color,
 * so it stays bright in both light and dark mode the same way
 * `SettingsRowShell`'s own opposite-luminance rule deliberately does *not*
 * apply here.
 *
 * [Modifier.matchParentSize] on the *outer* Box here, not a direct
 * `fillMaxWidth()` — confirmed on-device: `StatusBar`'s own panel is meant
 * to stay wrap-content sized around its pill content (unlike `TopShelfRow`'s,
 * which already forces its own width via an explicit `fillMaxWidth()`
 * upstream). A direct `fillMaxWidth()` child inside a Box that's otherwise
 * sizing itself from its *other* children pulls the whole Box out to the
 * maximum width its own parent offers instead — the pill visibly stretched
 * across most of the screen. `matchParentSize()` is explicitly exempted from
 * that sizing pass (same reason the artwork `Image` beside this already uses
 * it), so it can only ever match whatever size the real content already
 * settled on, never inflate it.
 */
@Composable
fun BoxScope.LiquidGlassTopHighlight() {
    Box(modifier = Modifier.matchParentSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(GlassTopHighlightHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = GlassTopHighlightAlpha), Color.Transparent),
                    ),
                ),
        )
    }
}

/**
 * Which region of [bitmapSize] (a [BlurredArtwork.bitmap]'s own pixel
 * dimensions) is actually behind a panel at [panelRect], given the sharp
 * reference image this blur was derived from is displayed full-bleed via
 * `ContentScale.Crop` across [heroRect] (`ContentCarousel`'s own real window
 * rect — its `AsyncImage` poster fills it exactly). Reproduces
 * `ContentScale.Crop`'s own scale-to-cover-then-center math by hand, since
 * [panelRect] belongs to a *different*, independently-drawn element (a small
 * crop of the blurred copy) rather than something read back from whatever
 * was actually composited on screen — confirmed on-device that the more
 * "obvious" approach (capturing the real composited frame,
 * `GraphicsLayer.toImageBitmap()`, `BackdropBlur.kt`) returns blank data on
 * this project's own reference hardware, which is *why* this is computed
 * from known geometry instead.
 */
private fun cropRegion(bitmapSize: IntSize, heroRect: Rect, panelRect: Rect): Pair<IntOffset, IntSize> {
    if (heroRect.width <= 0f || heroRect.height <= 0f || bitmapSize.width <= 0 || bitmapSize.height <= 0) {
        return IntOffset.Zero to IntSize.Zero
    }
    val scale = maxOf(heroRect.width / bitmapSize.width, heroRect.height / bitmapSize.height)
    val displayedWidth = bitmapSize.width * scale
    val displayedHeight = bitmapSize.height * scale
    // `ContentScale.Crop` centers the scaled-to-cover image within its own
    // bounds — this is almost always a negative offset (the scaled image is
    // larger than heroRect on the axis that wasn't the tight constraint).
    val imageLeft = heroRect.left + (heroRect.width - displayedWidth) / 2f
    val imageTop = heroRect.top + (heroRect.height - displayedHeight) / 2f

    val srcLeft = ((panelRect.left - imageLeft) / scale).roundToInt().coerceIn(0, bitmapSize.width)
    val srcTop = ((panelRect.top - imageTop) / scale).roundToInt().coerceIn(0, bitmapSize.height)
    val srcRight = ((panelRect.right - imageLeft) / scale).roundToInt().coerceIn(srcLeft, bitmapSize.width)
    val srcBottom = ((panelRect.bottom - imageTop) / scale).roundToInt().coerceIn(srcTop, bitmapSize.height)
    return IntOffset(srcLeft, srcTop) to IntSize(srcRight - srcLeft, srcBottom - srcTop)
}

/**
 * Draws the actual region of [bitmap] that sits behind this composable on
 * screen, cropped via [cropRegion] — not a centered crop of the *whole*
 * artwork the way a plain `ContentScale.Crop` on this element alone would
 * produce (confirmed user-reported: that read as an arbitrary, unpositioned
 * slice of the same rotating image, obvious on `StatusBar`'s small corner
 * pill and present if less noticeable on `TopShelfRow`'s own wide dock too).
 *
 * [heroRect]/[panelRect] are lambdas, not plain `Rect` values, so this
 * modifier always reads *this* draw pass's live window geometry rather than
 * whatever it happened to be when the modifier chain was first built.
 *
 * Ken Burns motion is layered on top by the caller (via
 * [com.peartv.launcher.ui.motion.kenBurnsTransform], same shared progress
 * the sharp poster itself animates with) rather than folded into this
 * crop — the base region here matches the sharp poster's own resting frame;
 * the animated pan/zoom on top of it is a proportional approximation (scaled
 * to *this* panel's own size, not recomputed against the source image every
 * frame) rather than a pixel-exact tracking of the sharp image's own
 * zoomed viewport. Close enough at this panel's scale and blur radius to
 * read as the same moving artwork, not worth the extra complexity of
 * recomputing [cropRegion] every frame against a live Ken Burns transform.
 */
fun Modifier.positionAwareBackdropCrop(
    bitmap: ImageBitmap?,
    heroRect: () -> Rect,
    panelRect: () -> Rect,
): Modifier = drawWithContent {
    if (bitmap != null) {
        val (srcOffset, srcSize) = cropRegion(IntSize(bitmap.width, bitmap.height), heroRect(), panelRect())
        if (srcSize.width > 0 && srcSize.height > 0) {
            drawImage(
                image = bitmap,
                srcOffset = srcOffset,
                srcSize = srcSize,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
        }
    }
    drawContent()
}
