package com.peartv.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme

/**
 * The launcher grid, folder modal, and Settings pages all used a single flat
 * `colorScheme.background` fill — real tvOS never reads as *quite* that flat;
 * there's a soft, low-contrast ambient glow, brightest near the top-center
 * and falling off toward the edges, well short of an actual gradient anyone
 * would consciously notice. This reproduces that with one radial gradient
 * layered over the flat fill, not a replacement for it.
 *
 * Deliberately theme-token-only, no new raw color: the tint is
 * `colorScheme.surface`, which this app's palette already keeps a step
 * *lighter* than `colorScheme.background` in both schemes (the same
 * elevation relationship every card/panel in this app relies on — see
 * `Theme.kt`) — so a low-alpha wash of it over `background` reads as gentle
 * ambient lift, not a color shift, and needs no separate light/dark case.
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
    val ambientTint = MaterialTheme.colorScheme.surface
    return this.drawBehind {
        drawRect(background)
        drawRect(
            Brush.radialGradient(
                colors = listOf(ambientTint.copy(alpha = AmbientGlowAlpha), Color.Transparent),
                center = Offset(size.width / 2f, 0f),
                radius = size.width * AmbientGlowRadiusFraction,
            ),
        )
    }
}

/** Low enough to read as "lit," not "colored" — see this file's own doc for why no separate light/dark tuning was needed here. */
private const val AmbientGlowAlpha = 0.16f
private const val AmbientGlowRadiusFraction = 0.9f
