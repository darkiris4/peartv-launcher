package com.peartv.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.tv.material3.MaterialTheme

/**
 * The launcher grid, folder modal, and Settings pages all used a single flat
 * `colorScheme.background` fill — real tvOS never reads as *quite* that flat;
 * there's a soft, low-contrast ambient glow, brightest near the top-center
 * and falling off toward the edges, well short of an actual gradient anyone
 * would consciously notice. This reproduces that with one radial gradient
 * layered over the flat fill, not a replacement for it.
 *
 * Two things confirmed wrong on-device in the first pass, both fixed here:
 *
 * 1. **Tint color**: `colorScheme.surface` is *deliberately* only a hair
 *    lighter than `colorScheme.background` (the "subtle elevated surface"
 *    principle this app's whole dark palette is built on) — which meant even
 *    this gradient's brightest point never got far past black, invisible on
 *    a real TV panel's own black-crush well before a screenshot's pixel
 *    values would suggest. `colorScheme.onSurfaceVariant` (the secondary-text
 *    gray) has real, visible luminance instead of being background-adjacent
 *    by design, so it actually reads as a lift.
 * 2. **Radius**: sized off `size.width`, which on a landscape TV (width ≫
 *    height) meant the falloff barely completed within the screen's own
 *    height — the gradient was nearly uniform top-to-bottom instead of
 *    visibly fading, flattening whatever contrast the tint did have. Sized
 *    off `size.height` instead, so the fade actually completes within the
 *    visible frame.
 *
 * Deliberately theme-token-only, no raw color either way.
 *
 * Not used by `HeroBanner`/`ContentCarousel`/`PortraitPosterBackdrop`,
 * whose own backdrop art, Ken Burns motion, and vignettes are a genuinely
 * content-derived treatment already (Tier 1/2's own accent/icon-color
 * blending, Tier 3's real artwork) — this is only for the *global* chrome
 * behind/around that content, which has no artwork of its own to derive
 * from. Applying this *underneath* those surfaces, not instead of them,
 * keeps that separation: callers still composite hero/carousel content on
 * top of this, same as they did over the old flat fill.
 */
@Composable
fun Modifier.ambientBackground(): Modifier {
    val background = MaterialTheme.colorScheme.background
    val ambientTint = MaterialTheme.colorScheme.onSurfaceVariant
    return this.drawBehind {
        drawRect(background)
        drawRect(
            Brush.radialGradient(
                colors = listOf(ambientTint.copy(alpha = AmbientGlowAlpha), Color.Transparent),
                center = Offset(size.width / 2f, 0f),
                radius = size.height * AmbientGlowRadiusFraction,
            ),
        )
    }
}

/**
 * Visibly "lit" without reading as a colored panel — see this file's own doc
 * for the on-device tuning that landed here. Confirmed on the actual
 * reference Shield TV Pro that 0.35 (already ~21% gray at the gradient's own
 * peak, clearly visible in an `adb screencap` pixel-for-pixel) still read as
 * *zero* visible effect on the physical display — almost certainly the TV's
 * own black-level/contrast processing crushing it, not a rendering bug (the
 * install was confirmed current, not stale). Pushed well past what a
 * screenshot alone would suggest is needed, specifically to survive that.
 */
private const val AmbientGlowAlpha = 0.65f
private const val AmbientGlowRadiusFraction = 1.8f

/**
 * The top-shelf tray and status pill (`TopShelfRow`/`StatusBar`, which
 * already deliberately share one panel tone — Dimens.kt's own
 * `TranslucentPanelAlpha` doc: "so both panels read as one consistent glass
 * material") sit at `TranslucentPanelAlpha` (0.9, nearly opaque) over
 * [ambientBackground]. That's opaque enough that the panel reads as almost
 * pure flat `colorScheme.surface` regardless of what's behind it — which,
 * once the background it sits on actually got bright enough to be visible
 * (this file's own tuning history above), created a hard seam confirmed
 * on-device: the tray's own bottom edge against the now-lit background
 * behind it, sharp enough to read as "a black line." Blending a touch of the
 * same [ambientBackground] tint into the panel's own base color — not a full
 * gradient, a single flat lift, appropriate for a small bounded card rather
 * than the whole screen — keeps the panel reading as its own elevated
 * surface while no longer looking like an unlit island dropped onto a lit
 * background.
 */
@Composable
fun MaterialTheme.ambientPanelTint(): Color =
    lerp(colorScheme.surface, colorScheme.onSurfaceVariant, AmbientPanelLiftFraction)

private const val AmbientPanelLiftFraction = 0.18f
